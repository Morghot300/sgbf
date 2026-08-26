package com.snef.sgbf.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.RoleMetierRepository;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie, au niveau HTTP reel, l'authentification simple (identifiant/e-mail
 * + mot de passe uniquement) mise en place le 2026-08-17 en remplacement du
 * second facteur par code e-mail (voir section K de l'analyse fonctionnelle) :
 * {@code POST /api/auth/login} authentifie desormais en un seul appel,
 * sans jamais de {@code challengeId} ni d'etape de verification separee.
 *
 * <p>Couvre exactement les six scenarios requis par la demande : connexion
 * reussie (par identifiant ET par e-mail), mauvais mot de passe, utilisateur
 * inexistant, compte desactive, conservation des autorisations par role apres
 * connexion, et rejet des API protegees pour un appel non authentifie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthentificationSimpleIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Utilisateur compteActif;
    private Utilisateur compteDesactive;
    private Utilisateur compteVerrouille;
    private Utilisateur chargeAffaires;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = System.nanoTime();
        Service service = new Service();
        service.setCodeService("SVC" + suffixe);
        service.setLibelle("Service de test");
        service = serviceRepository.save(service);

        compteActif = creerUtilisateur("actif_" + suffixe, StatutCompte.ACTIF, service);
        compteDesactive = creerUtilisateur("desactive_" + suffixe, StatutCompte.DESACTIVE, service);
        compteVerrouille = creerUtilisateur("verrouille_" + suffixe, StatutCompte.VERROUILLE, service);

        chargeAffaires = creerUtilisateur("ca_" + suffixe, StatutCompte.ACTIF, service);
        RoleMetier roleCa = roleMetierRepository.findByCode(CodeRoleMetier.CHARGE_AFFAIRES.name())
                .orElseThrow(() -> new IllegalStateException("Role seed manquant : CHARGE_AFFAIRES"));
        Habilitation habilitation = new Habilitation();
        habilitation.setUtilisateur(chargeAffaires);
        habilitation.setRoleMetier(roleCa);
        habilitation.setService(service);
        habilitation.setDateDebut(LocalDate.now().minusDays(1));
        habilitation.setActif(true);
        habilitation.setCreePar(chargeAffaires);
        habilitationRepository.save(habilitation);
    }

    /** Scenario 1 (bis) : connexion reussie par identifiant -> jeton emis directement, sans challengeId. */
    @Test
    void connexionReussieParIdentifiant_accesDirect() throws Exception {
        String reponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpsLogin(compteActif.getIdentifiant())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jetonAcces").exists())
                .andExpect(jsonPath("$.challengeId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String jeton = objectMapper.readTree(reponse).get("jetonAcces").asText();
        assertThat(jeton).isNotBlank();

        // Le jeton emis en une seule etape donne un acces immediat a une API protegee.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiant").value(compteActif.getIdentifiant()));
    }

    /** Scenario 1 : la page de connexion accepte aussi l'adresse e-mail a la place de l'identifiant. */
    @Test
    void connexionReussieParEmail_accesDirect() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpsLogin(compteActif.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jetonAcces").exists());
    }

    /** Scenario 2 : identifiant correct, mauvais mot de passe -> acces refuse. */
    @Test
    void mauvaisMotDePasse_accesRefuse() throws Exception {
        String corps = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("identifiant", compteActif.getIdentifiant());
            put("motDePasse", "CeciEstFaux123!");
        }});
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(corps))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.jetonAcces").doesNotExist());
    }

    /** Scenario 3 : utilisateur inexistant -> acces refuse (meme message generique qu'un mauvais mot de passe). */
    @Test
    void utilisateurInexistant_accesRefuse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpsLogin("ce_compte_n_existe_pas")))
                .andExpect(status().isUnauthorized());
    }

    /** Scenario 4 : identifiants corrects mais compte desactive -> acces refuse. */
    @Test
    void compteDesactive_accesRefuse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpsLogin(compteDesactive.getIdentifiant())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.jetonAcces").doesNotExist());
    }

    /** Scenario 4 (bis) : identifiants corrects mais compte verrouille -> acces refuse. */
    @Test
    void compteVerrouille_accesRefuse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(corpsLogin(compteVerrouille.getIdentifiant())))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Scenario 5 : la suppression du second facteur ne touche a aucune
     * autorisation - un compte sans habilitation reste refuse sur une action
     * reservee au Charge d'Affaires, un compte habilite y accede.
     */
    @Test
    void autorisationsParRoleConservees() throws Exception {
        String jetonSansHabilitation = jetonPour(compteActif.getIdentifiant());
        String jetonChargeAffaires = jetonPour(chargeAffaires.getIdentifiant());

        // Endpoint reserve par @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')").
        String corpsFiph = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("agentId", 999999);
            put("dateDebut", java.time.LocalDate.of(2026, 1, 5).toString());
        }});
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + jetonSansHabilitation)
                        .contentType("application/json").content(corpsFiph))
                .andExpect(status().isForbidden());

        // Le meme appel, avec un identifiant d'agent inexistant, echoue plus loin (404) pour le
        // Charge d'Affaires habilite - la difference de statut (403 vs 404) prouve que le controle
        // par role a bien ete traverse dans ce second cas.
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + jetonChargeAffaires)
                        .contentType("application/json").content(corpsFiph))
                .andExpect(status().isNotFound());
    }

    /**
     * Scenario 6 : un appel non authentifie a une API protegee est refuse -
     * 403 (jamais 200), coherent avec le reste de l'application ou toute
     * absence d'autorisation valide se traduit par un refus d'acces
     * (voir {@code SecurityConfig}/{@code GlobalExceptionHandler}).
     */
    @Test
    void apiProtegee_refuseSansJeton() throws Exception {
        mockMvc.perform(get("/api/fiph")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bons-sortie")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isForbidden());
    }

    /** Un jeton invalide/mal forme est refuse au meme titre qu'une absence de jeton. */
    @Test
    void apiProtegee_refuseAvecJetonInvalide() throws Exception {
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer un.jeton.invalide"))
                .andExpect(status().isForbidden());
    }

    private String jetonPour(String identifiant) throws Exception {
        String reponse = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(corpsLogin(identifiant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("jetonAcces").asText();
    }

    private String corpsLogin(String identifiant) throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("identifiant", identifiant);
            put("motDePasse", MOT_DE_PASSE);
        }});
    }

    private Utilisateur creerUtilisateur(String identifiant, StatutCompte statut, Service service) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(statut);
        utilisateur.setService(service);
        return utilisateurRepository.save(utilisateur);
    }
}
