package com.snef.sgbf.identite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
import com.snef.sgbf.referentiel.repository.CodeHNRepository;
import com.snef.sgbf.referentiel.repository.RoleMetierRepository;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import com.snef.sgbf.support.IdentifiantsTest;
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
 * Evolution du 2026-08-26 : un Super Administrateur doit pouvoir valider un
 * Bon de Sortie, une FIPH (niveaux 2/3/4, un a un) et gerer une Mission/
 * Affectation sur N'IMPORTE QUEL service - sans detenir la moindre
 * habilitation par service (la sienne, SUPER_ADMINISTRATEUR, porte toujours
 * un perimetre global, service=null) et SANS passer par la "prise en main"
 * (chaque niveau reste declenche explicitement, un a la fois, exactement
 * comme un gestionnaire ordinaire du service concerne).
 *
 * <p>Verifie aussi, en regression, qu'un utilisateur ordinaire hors
 * perimetre reste refuse : l'exception ne doit beneficier qu'a
 * SUPER_ADMINISTRATEUR, jamais elargir le controle pour personne d'autre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuperAdministrateurValidateurIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Service autreService;
    private Utilisateur emetteur;
    private Utilisateur caService;
    private Utilisateur superAdmin;
    private Utilisateur caAutreService;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        emetteur = creerPersonneAvecCompte("EMT" + (suffixe % 100_000L), "Test", "Emetteur", "emetteur_sa_" + suffixe, service);
        caService = creerUtilisateurAvecHabilitation("ca_sa_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        superAdmin = creerUtilisateurAvecHabilitation("superadmin_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_sa_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    /**
     * Le Super Administrateur valide une FIPH aux niveaux 2, 3 et 4 sur un
     * service ou il ne detient AUCUNE habilitation, chacun par un appel
     * explicite distinct (jamais la prise en main) - PUR validateur ici (le
     * Charge d'Affaires du service prepare la FIPH : RG-HAB-004, separation
     * des responsabilites, continue de s'appliquer au Super Administrateur
     * comme a quiconque des le niveau 3, et refuserait a bon droit qu'il
     * valide une fiche qu'il aurait lui-meme completee).
     */
    @Test
    void superAdministrateurValideBonDeSortieEtFiphNiveauParNiveauSurNimporteQuelService() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCaService = seConnecter(caService.getIdentifiant());
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());

        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 10);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test validation Super Administrateur");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());

        // Le Super Administrateur ne detient aucune habilitation sur "service" - seul son
        // role global doit suffire, exactement comme un Charge d'Affaires ordinaire du service.
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider")
                        .header("Authorization", "Bearer " + tokenSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        long fiphId = trouverFiphDeLAgent(tokenSuperAdmin, emetteur.getId()).get("id").asLong();
        long versionId = trouverFiphDeLAgent(tokenSuperAdmin, emetteur.getId()).get("versionCouranteId").asLong();

        // RG-FIPH-033 : date de fin obligatoire avant soumission - preparee ici par le Charge
        // d'Affaires du service (jamais par le Super Administrateur lui-meme : RG-HAB-004 lui
        // interdirait ensuite, a bon droit, de valider aux niveaux 3/4 une fiche qu'il aurait
        // lui-meme completee - voir Javadoc de la methode).
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCaService)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", LocalDate.now().toString());
                        }})))
                .andExpect(status().isOk());

        // Une FIPH issue d'un bon de sortie est deja auto-signee (visa acquis via le bon
        // de sortie declencheur, voir FiphService#creerFiphEtVersionInitiale) : directement
        // SIGNEE, prete pour la soumission par le Super Administrateur (role gestionnaire).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre")
                        .header("Authorization", "Bearer " + tokenSuperAdmin))
                .andExpect(status().isOk());

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
        // Niveau 2, 3 et 4 : trois appels EXPLICITES et DISTINCTS - jamais un saut direct a VALIDEE_DEFINITIVEMENT.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenSuperAdmin).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenSuperAdmin).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenSuperAdmin).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));

        org.assertj.core.api.Assertions.assertThat(fiphId).isPositive();
    }

    /** Regression : un Charge d'Affaires ORDINAIRE d'un autre service reste refuse - l'exception ne beneficie qu'au Super Administrateur. */
    @Test
    void gestionnaireOrdinaireHorsPerimetreResteRefuse() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCaAutreService = seConnecter(caAutreService.getIdentifiant());

        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 10);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test regression perimetre");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider")
                        .header("Authorization", "Bearer " + tokenCaAutreService))
                .andExpect(status().isForbidden());
    }

    // --- Aides ---

    private JsonNode trouverFiphDeLAgent(String token, long agentId) throws Exception {
        String reponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/fiph").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode n : objectMapper.readTree(reponse)) {
            if (n.get("agentId").asLong() == agentId) {
                return n;
            }
        }
        throw new IllegalStateException("Aucune FIPH trouvee pour l'agent " + agentId);
    }

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

    private Utilisateur creerPersonneAvecCompte(String matricule, String nom, String prenom, String identifiant, Service svc) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
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

    private Chantier nouveauChantier(String code, String libelle) {
        Chantier c = new Chantier();
        c.setCodeAffaire(code);
        c.setLibelle(libelle);
        return c;
    }

    private CodeHN nouveauCodeHN(String code, Chantier c) {
        CodeHN codeHN = new CodeHN();
        codeHN.setCode(code);
        codeHN.setLibelle("Code mission de test");
        codeHN.setChantier(c);
        return codeHN;
    }
}
