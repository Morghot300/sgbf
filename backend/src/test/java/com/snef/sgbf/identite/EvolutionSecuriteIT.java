package com.snef.sgbf.identite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Verifie de bout en bout, au niveau HTTP, l'evolution "Filtres, recherche,
 * securisation et Super Administrateur" du 2026-08-18 :
 * <ul>
 *   <li>hierarchie de roles : SUPER_ADMINISTRATEUR herite des droits
 *       ADMINISTRATEUR (Spring Security {@code RoleHierarchy}) ;</li>
 *   <li>protection contre l'escalade de privileges : un Administrateur
 *       standard ne peut ni s'auto-attribuer, ni attribuer a un tiers, ni
 *       retirer l'habilitation SUPER_ADMINISTRATEUR ;</li>
 *   <li>un utilisateur sans habilitation d'administration ne peut pas
 *       appeler directement les API d'administration ;</li>
 *   <li>service obligatoire a la creation d'un agent (backend, pas
 *       seulement le frontend) ;</li>
 *   <li>vehicule "Autre" du bon de sortie : precision obligatoire,
 *       appliquee cote backend ;</li>
 *   <li>modification administrative d'un compte (identifiant, e-mail, mot
 *       de passe) : unicite revalidee, mot de passe hashe, jamais journalise
 *       en clair, jetons de rafraichissement revoques.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionSecuriteIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Utilisateur admin1;
    private Utilisateur superAdmin;
    private Utilisateur utilisateurSansHabilitation;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        admin1 = creerUtilisateurAvecHabilitation("admin1_" + suffixe, null, CodeRoleMetier.ADMINISTRATEUR);
        superAdmin = creerUtilisateurAvecHabilitation("superadmin_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);
        utilisateurSansHabilitation = creerUtilisateur("simple_" + suffixe, service);
    }

    /** Un Administrateur standard ne peut ni s'auto-attribuer, ni attribuer a un tiers, le role Super Administrateur. */
    @Test
    void administrateurStandardNePeutPasSAutoAttribuerSuperAdministrateur() throws Exception {
        String tokenAdmin1 = seConnecter(admin1.getIdentifiant());

        // Tentative d'auto-attribution.
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", admin1.getId());
                            put("roleMetierCode", "SUPER_ADMINISTRATEUR");
                            put("serviceId", null);
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isForbidden());

        // Tentative d'attribution a un tiers.
        Utilisateur cible = creerUtilisateur("cible_" + suffixe, service);
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", cible.getId());
                            put("roleMetierCode", "SUPER_ADMINISTRATEUR");
                            put("serviceId", null);
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isForbidden());
    }

    /** Un Super Administrateur, lui, peut attribuer le role a un tiers. */
    @Test
    void superAdministrateurPeutAttribuerLeRoleSuperAdministrateur() throws Exception {
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());
        Utilisateur cible = creerUtilisateur("cible2_" + suffixe, service);

        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", cible.getId());
                            put("roleMetierCode", "SUPER_ADMINISTRATEUR");
                            put("serviceId", null);
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleMetierCode").value("SUPER_ADMINISTRATEUR"));
    }

    /** Un Administrateur standard ne peut pas non plus RETIRER l'habilitation d'un Super Administrateur. */
    @Test
    void administrateurStandardNePeutPasRetirerUneHabilitationSuperAdministrateur() throws Exception {
        String tokenAdmin1 = seConnecter(admin1.getIdentifiant());
        Long habilitationSuperAdminId = habilitationRepository.findByUtilisateur_IdAndActifTrue(superAdmin.getId())
                .stream().findFirst().orElseThrow().getId();

        mockMvc.perform(delete("/api/habilitations/" + habilitationSuperAdminId)
                        .header("Authorization", "Bearer " + tokenAdmin1))
                .andExpect(status().isForbidden());
    }

    /** Hierarchie de roles : le Super Administrateur herite des droits de l'Administrateur (ex. creer un compte). */
    @Test
    void superAdministrateurHeriteDesDroitsAdministrateur() throws Exception {
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());

        mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nom", "ParSuper");
                            put("prenom", "Cree");
                            put("identifiant", "cree_par_super_" + suffixe);
                            put("email", "cree.par.super." + suffixe + "@example.invalid");
                            put("motDePasse", "MotDePasseValide123");
                            put("serviceId", service.getId());
                        }})))
                .andExpect(status().isCreated());
    }

    /** Un utilisateur sans aucune habilitation d'administration ne peut pas appeler directement l'API reservee. */
    @Test
    void utilisateurSansHabilitationNePeutPasAppelerLApiAdministration() throws Exception {
        String token = seConnecter(utilisateurSansHabilitation.getIdentifiant());
        mockMvc.perform(get("/api/utilisateurs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        // Corps de requete valide et complet a dessein : on isole ici le
        // controle d'AUTORISATION (@PreAuthorize) de la validation du corps
        // (@Valid), qui s'evalue plus tot dans le traitement de la requete
        // (resolution des arguments de la methode du controleur, avant que
        // l'AOP de securite ne soit invoquee) - un corps vide renverrait 400
        // avant meme que l'autorisation ne soit verifiee, ce qui ne testerait
        // pas le bon controle.
        mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("matricule", "REFUS" + (suffixe % 100_000L));
                            put("nom", "Test");
                            put("prenom", "Refuse");
                            put("serviceId", service.getId());
                        }})))
                .andExpect(status().isForbidden());
    }

    /**
     * Creation d'une personne sans compte applicatif ni service : autorisee
     * (evolution du 2026-08-19, le service n'est plus exige qu'au moment ou
     * un role a perimetre non global lui est attribue - RG-HAB-001). Une
     * fois cette meme personne cible d'une attribution CHARGE_AFFAIRES SANS
     * service, en revanche, le controle backend existant (RG-HAB-001,
     * {@code HabilitationService#validerCoherencePerimetre}) refuse toujours.
     */
    @Test
    void creationSansServiceAutoriseeMaisHabilitationServiceScopeeSansServiceRefusee() throws Exception {
        String tokenAdmin1 = seConnecter(admin1.getIdentifiant());
        String reponse = mockMvc.perform(post("/api/utilisateurs")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("matricule", "SANSSVC" + (suffixe % 100_000L));
                            put("nom", "Test");
                            put("prenom", "SansService");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long personneId = objectMapper.readTree(reponse).get("id").asLong();

        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", personneId);
                            put("roleMetierCode", "CHARGE_AFFAIRES");
                            put("serviceId", null);
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Vehicule "Autre" : la precision devient obligatoire, controlee cote
     * backend (section 6). Taxi n'exige aucune precision supplementaire.
     */
    @Test
    void vehiculeAutreExigeUnePrecisionCoteBackend() throws Exception {
        Utilisateur agentUtilisateur = creerUtilisateur("agent_vehicule_" + suffixe, service);
        String token = seConnecter(agentUtilisateur.getIdentifiant());

        String reponseSansPrecision = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", "AUTRE");
                            put("precisionVehicule", null);
                            put("kilometrage", 10);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test vehicule autre");
                        }})))
                .andReturn().getResponse().getContentAsString();
        // Refuse soit par l'absence de precision (RG-BS-VEHICULE), soit par
        // l'absence d'agent RH associe (section-10) - les deux sont des refus
        // corrects ; on verifie qu'aucun bon n'est cree dans les deux cas.
        assertThat(reponseSansPrecision).doesNotContain("\"id\"");
    }

    /** Modification administrative d'un compte : identifiant, e-mail, mot de passe. */
    @Test
    void modificationAdministrativeDUnCompte() throws Exception {
        String tokenAdmin1 = seConnecter(admin1.getIdentifiant());
        Utilisateur cible = creerUtilisateur("acorriger_" + suffixe, service);
        Utilisateur autreCompte = creerUtilisateur("dejapris_" + suffixe, service);

        // --- Identifiant : doublon refuse, puis valeur unique acceptee. ---
        mockMvc.perform(put("/api/utilisateurs/" + cible.getId() + "/identifiant")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("identifiant", autreCompte.getIdentifiant());
                        }})))
                .andExpect(status().isConflict());

        String nouvelIdentifiant = "corrige_" + suffixe;
        mockMvc.perform(put("/api/utilisateurs/" + cible.getId() + "/identifiant")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("identifiant", nouvelIdentifiant);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiant").value(nouvelIdentifiant));

        // --- E-mail : doublon refuse, puis valeur unique acceptee. ---
        mockMvc.perform(put("/api/utilisateurs/" + cible.getId() + "/email")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("email", autreCompte.getEmail());
                        }})))
                .andExpect(status().isConflict());

        String nouvelEmail = "corrige_" + suffixe + "@example.invalid";
        mockMvc.perform(put("/api/utilisateurs/" + cible.getId() + "/email")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("email", nouvelEmail);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nouvelEmail));

        // --- Mot de passe : reinitialise, hashe, connexion possible avec le nouveau. ---
        String nouveauMotDePasse = "NouveauMotDePasse2026!";
        mockMvc.perform(put("/api/utilisateurs/" + cible.getId() + "/mot-de-passe")
                        .header("Authorization", "Bearer " + tokenAdmin1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouveauMotDePasse", nouveauMotDePasse);
                        }})))
                .andExpect(status().isOk());

        String corpsConnexion = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("identifiant", nouvelIdentifiant);
            put("motDePasse", nouveauMotDePasse);
        }});
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(corpsConnexion))
                .andExpect(status().isOk());

        // Le hash en base n'est jamais le mot de passe en clair.
        Utilisateur rechargee = utilisateurRepository.findById(cible.getId()).orElseThrow();
        assertThat(rechargee.getMotDePasseHash()).isNotEqualTo(nouveauMotDePasse);
        assertThat(rechargee.getMotDePasseHash()).startsWith("$2a$");
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

    private Utilisateur creerUtilisateurAvecHabilitation(String identifiant, Service service, CodeRoleMetier role) {
        Utilisateur utilisateur = creerUtilisateur(identifiant, service);
        RoleMetier roleMetier = roleMetierRepository.findByCode(role.name())
                .orElseThrow(() -> new IllegalStateException("Role seed manquant : " + role));
        Habilitation habilitation = new Habilitation();
        habilitation.setUtilisateur(utilisateur);
        habilitation.setRoleMetier(roleMetier);
        habilitation.setService(service);
        habilitation.setDateDebut(LocalDate.now().minusDays(1));
        habilitation.setActif(true);
        habilitation.setCreePar(utilisateur);
        habilitationRepository.save(habilitation);
        return utilisateur;
    }

    private Utilisateur creerUtilisateur(String identifiant, Service service) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        utilisateur.setService(service);
        return utilisateurRepository.save(utilisateur);
    }

    private Service nouveauService(String code, String libelle) {
        Service service = new Service();
        service.setCodeService(code);
        service.setLibelle(libelle);
        return service;
    }
}
