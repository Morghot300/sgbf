package com.snef.sgbf.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Verifie de bout en bout, au niveau HTTP, le cycle de vie des missions et
 * affectations (section 5 a 8 du document source, RG-MIS-001 a 008) :
 * creation d'une mission, affectation d'un agent, interruption, chainage
 * vers une nouvelle mission (MIS-001 -&gt; interrompue -&gt; MIS-002), et
 * consultation de l'historique de la chaine. Complete par le controle de
 * perimetre (RG-HAB-003) et le conflit "une seule affectation active a la
 * fois" (section 20.1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MissionAffectationIT {

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
    private Chantier chantier;
    private CodeHN codeMis001;
    private CodeHN codeMis002;
    private Utilisateur agent;
    private Utilisateur ca;
    private Utilisateur caAutreService;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeMis001 = codeHNRepository.save(nouveauCodeHN("MIS001-" + suffixe, chantier));
        codeMis002 = codeHNRepository.save(nouveauCodeHN("MIS002-" + suffixe, chantier));

        agent = utilisateurRepository.save(nouvelAgent("MAT" + suffixe, "Test", "Agent", service));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    /**
     * Parcours complet : creation de MIS-001, affectation de l'agent,
     * interruption (motif CHANGEMENT_AFFECTATION), creation de MIS-002,
     * reaffectation vers MIS-002 (chainage RG-MIS-005/006), verification que
     * MIS-001 reste intacte et consultable, et que l'historique retrace la
     * chaine complete.
     */
    @Test
    void chaineMissionInterrompueVersNouvelleMission() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());

        // 1. Creation de MIS-001.
        long mis001Id = creerMission(tokenCa, codeMis001, null);

        // 2. Affectation de l'agent sur MIS-001.
        String reponseAffectation = mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", mis001Id);
                            put("dateDebutAffectation", LocalDate.now().minusDays(5).toString());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAffectation").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long affectationMis001Id = objectMapper.readTree(reponseAffectation).get("id").asLong();

        // 3. Mission passee EN_COURS suite a l'affectation active.
        mockMvc.perform(get("/api/missions/" + mis001Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));

        // 4. Une seconde affectation active pour le meme agent est refusee (conflit).
        long mis002IdPourConflit = creerMission(tokenCa, codeMis002, null);
        mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", mis002IdPourConflit);
                            put("dateDebutAffectation", LocalDate.now().toString());
                        }})))
                .andExpect(status().isConflict());

        // 5. Interruption de l'affectation sur MIS-001 (motif CHANGEMENT_AFFECTATION).
        mockMvc.perform(post("/api/affectations-mission/" + affectationMis001Id + "/interrompre")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("motifCode", "CHANGEMENT_AFFECTATION");
                            put("dateInterruption", LocalDate.now().toString());
                            put("commentaire", null);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAffectation").value("INTERROMPUE"));

        // 6. Creation de MIS-002, chainee a MIS-001 via missionPrecedenteId.
        long mis002Id = creerMission(tokenCa, codeMis002, mis001Id);

        // 7. Reaffectation de l'agent, de l'affectation interrompue vers MIS-002.
        String reponseReaffectation = mockMvc.perform(post("/api/affectations-mission/" + affectationMis001Id + "/reaffecter")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("missionCibleId", mis002Id);
                            put("dateDebutAffectation", LocalDate.now().toString());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAffectation").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode nouvelleAffectation = objectMapper.readTree(reponseReaffectation);
        assertThat(nouvelleAffectation.get("missionId").asLong()).isEqualTo(mis002Id);

        // 8. MIS-001 reste intacte, consultable, INTERROMPUE - jamais supprimee ni ecrasee (RG-MIS-003/006).
        mockMvc.perform(get("/api/missions/" + mis001Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("INTERROMPUE"));
        mockMvc.perform(get("/api/affectations-mission/" + affectationMis001Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAffectation").value("INTERROMPUE"))
                .andExpect(jsonPath("$.missionId").value(mis001Id));

        // 9. Historique de MIS-002 retrace la chaine complete (MIS-001 -> MIS-002).
        String reponseHistorique = mockMvc.perform(get("/api/missions/" + mis002Id + "/historique").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode historique = objectMapper.readTree(reponseHistorique);
        assertThat(historique.isArray()).isTrue();
        assertThat(historique.size()).isEqualTo(2); // MIS-001 puis MIS-002

        // 10. Un CA d'un AUTRE service ne peut ni consulter ni interrompre une affectation hors de son perimetre.
        String tokenCaAutreService = seConnecter(caAutreService.getIdentifiant());
        mockMvc.perform(post("/api/affectations-mission/" + affectationMis001Id + "/interrompre")
                        .header("Authorization", "Bearer " + tokenCaAutreService)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("motifCode", "AUTRE");
                            put("dateInterruption", LocalDate.now().toString());
                            put("commentaire", "Tentative hors perimetre");
                        }})))
                .andExpect(status().isForbidden());

        // 11. Cas limite : mission et affectation inexistantes -> 404 explicite.
        mockMvc.perform(get("/api/missions/999999999").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/affectations-mission/999999999").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isNotFound());
    }

    /** Le motif "Autre" exige un commentaire (section 6.2) ; un commentaire vide est refuse. */
    @Test
    void interruptionAvecMotifAutreExigeUnCommentaire() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        long missionId = creerMission(tokenCa, codeMis001, null);
        String reponseAffectation = mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", missionId);
                            put("dateDebutAffectation", LocalDate.now().toString());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long affectationId = objectMapper.readTree(reponseAffectation).get("id").asLong();

        mockMvc.perform(post("/api/affectations-mission/" + affectationId + "/interrompre")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("motifCode", "AUTRE");
                            put("dateInterruption", LocalDate.now().toString());
                            put("commentaire", "   ");
                        }})))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- Aides de scenario ---

    private long creerMission(String token, CodeHN codeHN, Long missionPrecedenteId) throws Exception {
        String reponse = mockMvc.perform(post("/api/missions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("codeChantier", chantier.getCodeAffaire());
                            put("libelleChantier", chantier.getLibelle());
                            put("codeMission", codeHN.getCode());
                            put("libelleCodeMission", codeHN.getLibelle());
                            put("dateDebutPrevue", LocalDate.now().toString());
                            put("dateFinPrevue", LocalDate.now().plusMonths(1).toString());
                            put("missionPrecedenteId", missionPrecedenteId);
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("id").asLong();
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

    private Utilisateur nouvelAgent(String matricule, String nom, String prenom, Service service) {
        Utilisateur agent = new Utilisateur();
        agent.setMatricule(matricule);
        agent.setNom(nom);
        agent.setPrenom(prenom);
        agent.setService(service);
        return agent;
    }
}
