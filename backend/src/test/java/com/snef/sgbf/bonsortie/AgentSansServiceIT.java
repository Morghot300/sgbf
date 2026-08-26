package com.snef.sgbf.bonsortie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.bonsortie.entity.OrigineBonSortie;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.bonsortie.repository.BonSortieRepository;
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
import java.util.LinkedHashMap;
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
 * Bug reel corrige le 2026-08-26 : un compte sans service (Administrateur,
 * Super Administrateur, RH - aucun service ne leur est jamais impose, cf.
 * {@code HabilitationService.validerCoherencePerimetre}) qui emettait son
 * propre Bon de Sortie faisait planter en {@code NullPointerException} la
 * liste des bons de sortie pour TOUS LES AUTRES UTILISATEURS (une seule
 * entree fautive dans {@code BonSortieRepository.findAll()} cassait le
 * filtre entier), puis une violation de contrainte {@code NOT NULL} sur
 * {@code fiph.service_id} des la validation.
 *
 * <p>Corrige a la racine (creation refusee, RG-BS-009) et en profondeur
 * (contrôles de perimetre et listing tolerants a un agent sans service,
 * pour les enregistrements heritage crees avant ce correctif).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentSansServiceIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private BonSortieRepository bonSortieRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Service service;
    private Utilisateur superAdminSansService;
    private Utilisateur caService;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        superAdminSansService = creerUtilisateurAvecHabilitation("superadmin_ss_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);
        caService = creerUtilisateurAvecHabilitation("ca_ss_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    /** RG-BS-009 : la creation est desormais refusee a la racine pour un titulaire sans service. */
    @Test
    void creationRefuseePourUnTitulaireSansService() throws Exception {
        String token = seConnecter(superAdminSansService.getIdentifiant());
        mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 10);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test agent sans service");
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-BS-009"));
    }

    /**
     * Enregistrement heritage (cree avant ce correctif, insere directement en base pour simuler
     * une donnee existante) : la liste des bons de sortie ne doit plus jamais planter pour un
     * AUTRE utilisateur normal, meme si un tel enregistrement fautif existe encore.
     */
    @Test
    void listeNePlanteJamaisMemeAvecUnBonDeSortieHeritageSansService() throws Exception {
        BonSortie bonHeritage = new BonSortie();
        bonHeritage.setAgent(superAdminSansService);
        bonHeritage.setMoyenUtilise(MoyenUtilise.OMNIUM_SERVICE);
        bonHeritage.setKilometrage(5);
        bonHeritage.setDateSortie(LocalDate.now());
        bonHeritage.setHeureSortie(java.time.LocalTime.of(8, 0));
        bonHeritage.setLieu("Heritage");
        bonHeritage.setCodeAffaireSaisi("CODE-HERITAGE");
        bonHeritage.setMotifSortie("Enregistrement heritage sans service");
        bonHeritage.setOrigine(OrigineBonSortie.PRINCIPALE);
        bonHeritage.setStatut(StatutBonSortie.BROUILLON);
        bonSortieRepository.save(bonHeritage);

        String tokenCa = seConnecter(caService.getIdentifiant());
        mockMvc.perform(get("/api/bons-sortie").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());

        String tokenSuperAdmin = seConnecter(superAdminSansService.getIdentifiant());
        String reponse = mockMvc.perform(get("/api/bons-sortie").header("Authorization", "Bearer " + tokenSuperAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean present = false;
        for (JsonNode n : objectMapper.readTree(reponse)) {
            if (n.get("id").asLong() == bonHeritage.getId()) {
                present = true;
            }
        }
        assertThat(present).isTrue(); // visible pour son propre titulaire, malgre l'absence de service.
    }

    // --- Aides ---

    private String seConnecter(String identifiant) throws Exception {
        String corps = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("identifiant", identifiant);
            put("motDePasse", MOT_DE_PASSE);
        }});
        String reponseLogin = mockMvc.perform(post("/api/auth/login").contentType("application/json").content(corps))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponseLogin).get("jetonAcces").asText();
    }

    private Utilisateur creerUtilisateurAvecHabilitation(String identifiant, Service svc, CodeRoleMetier role) {
        Utilisateur utilisateur = creerUtilisateur(identifiant, svc);
        RoleMetier roleMetier = roleMetierRepository.findByCode(role.name())
                .orElseThrow(() -> new IllegalStateException("Role seed manquant : " + role));
        Habilitation habilitation = new Habilitation();
        habilitation.setUtilisateur(utilisateur);
        habilitation.setRoleMetier(roleMetier);
        habilitation.setService(svc);
        habilitation.setDateDebut(LocalDate.now().minusDays(1));
        habilitation.setActif(true);
        habilitation.setCreePar(utilisateur);
        habilitationRepository.save(habilitation);
        return utilisateur;
    }

    private Utilisateur creerUtilisateur(String identifiant, Service svc) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        utilisateur.setService(svc);
        return utilisateurRepository.save(utilisateur);
    }

    private Service nouveauService(String code, String libelle) {
        Service svc = new Service();
        svc.setCodeService(code);
        svc.setLibelle(libelle);
        return svc;
    }
}
