package com.snef.sgbf.fiph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.mission.entity.StatutAffectation;
import com.snef.sgbf.mission.entity.StatutMission;
import com.snef.sgbf.mission.repository.AffectationMissionRepository;
import com.snef.sgbf.mission.repository.MissionRepository;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
import com.snef.sgbf.referentiel.repository.CodeHNRepository;
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
 * Verifie de bout en bout, au niveau HTTP, la chaine complete decrite
 * section 12.2 du document source : validation d'un bon de sortie -&gt;
 * generation et prerempissage automatiques de la FIPH (RG-BS-007,
 * RG-FIPH-001) -&gt; complement -&gt; signature de l'emetteur -&gt; soumission
 * -&gt; validation aux trois niveaux -&gt; FIPH definitivement validee (avec
 * empreinte d'integrite, RG-VER-006) -&gt; nouvelle version post-validation
 * (RG-VER-001 a 007).
 *
 * <p>Chaque acteur s'authentifie reellement via {@code POST /api/auth/login}
 * (authentification simple - identifiant + mot de passe uniquement, sans
 * seconde etape, voir {@code AuthentificationSimpleIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiphWorkflowIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private AffectationMissionRepository affectationMissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Utilisateur emetteurUtilisateur;
    private Utilisateur ca1;
    private Utilisateur ca2;
    private Utilisateur responsableActivite;
    private Utilisateur direction;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = System.nanoTime();
        Service service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        Chantier chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        Agent emetteurAgent = agentRepository.save(nouvelAgent("MAT" + suffixe, "Test", "Emetteur", service));
        emetteurUtilisateur = creerUtilisateur("emetteur" + suffixe, service);
        emetteurAgent.setUtilisateur(emetteurUtilisateur);
        agentRepository.save(emetteurAgent);

        ca1 = creerUtilisateurAvecHabilitation("ca1_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        ca2 = creerUtilisateurAvecHabilitation("ca2_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        responsableActivite = creerUtilisateurAvecHabilitation("ra_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        direction = creerUtilisateurAvecHabilitation("direction_" + suffixe, service, CodeRoleMetier.DIRECTION);

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        mission = missionRepository.save(mission);

        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(emetteurAgent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(ca1);
        affectationMissionRepository.save(affectation);
    }

    /**
     * Parcours complet : bon de sortie valide -&gt; FIPH generee et
     * preremplie -&gt; completee -&gt; signee -&gt; soumise -&gt; validee aux
     * trois niveaux -&gt; definitivement validee -&gt; nouvelle version.
     */
    @Test
    void chaineBonDeSortieVersFiphDefinitivementValidee() throws Exception {
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCa1 = seConnecter(ca1.getIdentifiant());
        String tokenCa2 = seConnecter(ca2.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());

        // 1. Creation + visa + validation du bon de sortie.
        String corpsBonSortie = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("kilometrage", 30);
            put("dateSortie", LocalDate.now().toString());
            put("heureSortie", "08:00:00");
            put("lieu", "Chantier de test");
            put("codeAffaireSaisi", "CODE-TEST"); // non contraignant : resolu via l'affectation active, pas via ce champ
            put("motifSortie", "Livraison materiel de test");
        }});
        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json").content(corpsBonSortie))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser").header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        // 2. La FIPH doit avoir ete generee et preremplie automatiquement (RG-BS-007, RG-FIPH-001).
        String reponseFiphListe = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponseFiphListe);
        assertThat(fiphs.isArray()).isTrue();
        JsonNode fiphGeneree = fiphs.get(fiphs.size() - 1);
        long fiphId = fiphGeneree.get("id").asLong();
        assertThat(fiphGeneree.get("origine").asText()).isEqualTo("BON_SORTIE");
        assertThat(fiphGeneree.get("statut").asText()).isEqualTo("BROUILLON");
        long versionId = fiphGeneree.get("versionCouranteId").asLong();

        // 3. Complement des heures (RG-FIPH-009/010) - jamais deduit automatiquement d'un bon de sortie.
        String corpsCompletion = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("datePointage", LocalDate.now().toString());
            put("heuresNormales", 8);
            put("heuresSup", 0);
        }});
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsCompletion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("EN_COMPLEMENT"))
                .andExpect(jsonPath("$.totalHN").value(8));

        // 4. Signature de l'emetteur (RG-FIPH-019) - reservee au titulaire.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/signer").header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("SIGNEE"));

        // 5. Soumission au circuit.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("SOUMISE"));

        // 6. Validation niveau 2 - PAR CA2, jamais CA1 qui a cree la version (RG-HAB-004).
        String decisionValidee = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa2)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        // 7. Validation niveau 3 (Responsable d'activite).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));

        // 8. Validation niveau 4 (Direction) - definitive, empreinte calculee (RG-VER-006).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"))
                .andExpect(jsonPath("$.empreinteIntegrite").isNotEmpty());

        // 9. RG-VER-001 : une version definitivement validee est figee - le complement redevient impossible.
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsCompletion))
                .andExpect(status().isUnprocessableEntity());

        // 10. RG-VER-001 a 007 : correction post-validation -> nouvelle version, motif obligatoire.
        String corpsNouvelleVersion = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("motifModification", "Test : correction post-validation");
        }});
        String reponseNouvelleVersion = mockMvc.perform(post("/api/fiph-versions/fiph/" + fiphId + "/nouvelle-version")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsNouvelleVersion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroVersion").value(2))
                .andExpect(jsonPath("$.statutVersion").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();

        JsonNode nouvelleVersion = objectMapper.readTree(reponseNouvelleVersion);
        assertThat(nouvelleVersion.get("pointages").size()).isEqualTo(1); // contenu copie de la version 1 (RG-VER-003)

        // L'ancienne version reste consultable et intacte (RG-VER-003).
        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));
    }

    /** Authentification simple (identifiant + mot de passe, sans seconde etape - decision du 2026-08-17). */
    private String seConnecter(String identifiant) throws Exception {
        String corps = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
        habilitation.setCreePar(utilisateur); // auto-reference acceptable en test (bootstrap)
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

    private Chantier nouveauChantier(String code, String libelle) {
        Chantier chantier = new Chantier();
        chantier.setCodeAffaire(code);
        chantier.setLibelle(libelle);
        return chantier;
    }

    private CodeHN nouveauCodeHN(String code, Chantier chantier) {
        CodeHN codeHN = new CodeHN();
        codeHN.setCode(code);
        codeHN.setLibelle("Code mission de test");
        codeHN.setChantier(chantier);
        return codeHN;
    }

    private Agent nouvelAgent(String matricule, String nom, String prenom, Service service) {
        Agent agent = new Agent();
        agent.setMatricule(matricule);
        agent.setNom(nom);
        agent.setPrenom(prenom);
        agent.setService(service);
        return agent;
    }
}
