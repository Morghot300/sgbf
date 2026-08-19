package com.snef.sgbf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.EvenementAudit;
import com.snef.sgbf.common.audit.EvenementAuditRepository;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
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
 * Verifie de bout en bout, au niveau HTTP, l'evolution "Perimetre strict par
 * service" du 2026-08-19 :
 * <ul>
 *   <li>un Charge d'Affaires / une Personne habilitee / un Responsable
 *       d'Activite ne peut jamais detenir deux habilitations actives du meme
 *       role sur deux services differents (section 1, 10) ;</li>
 *   <li>un acteur metier ne peut consulter/creer/modifier/valider que les
 *       FIPH de son propre service, verifie cote backend, y compris par
 *       appel direct a l'API (section 2-5, 24) ;</li>
 *   <li>les 10 scenarios explicitement numerotes de la section 18 ;</li>
 *   <li>creation par Agent (bon de sortie)/Charge d'Affaires/Personne
 *       habilitee, avec visa automatique du createur (section 8, 19) ;</li>
 *   <li>parcours complet bout-en-bout sur DEUX services (Littoral puis
 *       Centre, section 20), prouvant l'etancheite entre eux.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionPerimetreServiceFiphIT {

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
    @Autowired private EvenementAuditRepository evenementAuditRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service littoral;
    private Service centre;
    private Chantier chantier;
    private CodeHN codeHN;

    private Utilisateur caLittoral;
    private Utilisateur phLittoral;
    private Utilisateur raLittoral;
    private Utilisateur caCentre;
    private Utilisateur phCentre;
    private Utilisateur raCentre;

    private Agent agentLittoral1;
    private Agent agentLittoral2;
    private Agent agentCentre1;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        long court = suffixe % 100_000L;

        littoral = serviceRepository.save(nouveauService("LIT" + court, "Littoral " + suffixe));
        centre = serviceRepository.save(nouveauService("CTR" + court, "Centre " + suffixe));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        caLittoral = creerUtilisateurAvecHabilitation("ca_lit_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        phLittoral = creerUtilisateurAvecHabilitation("ph_lit_" + suffixe, littoral, CodeRoleMetier.PERSONNE_HABILITEE);
        raLittoral = creerUtilisateurAvecHabilitation("ra_lit_" + suffixe, littoral, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        caCentre = creerUtilisateurAvecHabilitation("ca_ctr_" + suffixe, centre, CodeRoleMetier.CHARGE_AFFAIRES);
        phCentre = creerUtilisateurAvecHabilitation("ph_ctr_" + suffixe, centre, CodeRoleMetier.PERSONNE_HABILITEE);
        raCentre = creerUtilisateurAvecHabilitation("ra_ctr_" + suffixe, centre, CodeRoleMetier.RESPONSABLE_ACTIVITE);

        agentLittoral1 = agentRepository.save(nouvelAgent("LIT1-" + court, "Ateba", "Alice", littoral));
        agentLittoral2 = agentRepository.save(nouvelAgent("LIT2-" + court, "Bikoro", "Bruno", littoral));
        agentCentre1 = agentRepository.save(nouvelAgent("CTR1-" + court, "Ekwalla", "Paul", centre));

        Mission missionLittoral = nouvelleMission();
        affecterAgentAMission(agentLittoral1, missionLittoral, caLittoral);
        affecterAgentAMission(agentLittoral2, missionLittoral, caLittoral);
        Mission missionCentre = nouvelleMission();
        affecterAgentAMission(agentCentre1, missionCentre, caCentre);
    }

    // =========================================================================================
    // Un seul service actif par role (section 1, 10)
    // =========================================================================================

    @Test
    void unSeulServiceActifParRole_secondeAttributionRefuseePourChargeAffaires() throws Exception {
        String tokenAdmin = seConnecterAdministrateur();
        // caLittoral detient deja CHARGE_AFFAIRES sur Littoral (voir @BeforeEach) : une seconde
        // attribution du MEME role, sur le Centre, doit etre refusee.
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", caLittoral.getId());
                            put("roleMetierCode", "CHARGE_AFFAIRES");
                            put("serviceId", centre.getId());
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unSeulServiceActifParRole_secondeAttributionRefuseePourPersonneHabiliteeEtResponsableActivite() throws Exception {
        String tokenAdmin = seConnecterAdministrateur();
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", phLittoral.getId());
                            put("roleMetierCode", "PERSONNE_HABILITEE");
                            put("serviceId", centre.getId());
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", raLittoral.getId());
                            put("roleMetierCode", "RESPONSABLE_ACTIVITE");
                            put("serviceId", centre.getId());
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void directionResteMultiServiceParConstruction() throws Exception {
        String tokenAdmin = seConnecterAdministrateur();
        Utilisateur direction = creerUtilisateurAvecHabilitation("dir_" + suffixe, littoral, CodeRoleMetier.DIRECTION);
        // Contrairement a CA/PH/RA, la Direction peut detenir plusieurs habilitations actives
        // (portee transverse voulue par la mission, section 12).
        mockMvc.perform(post("/api/habilitations")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("utilisateurId", direction.getId());
                            put("roleMetierCode", "DIRECTION");
                            put("serviceId", centre.getId());
                            put("dateDebut", LocalDate.now().toString());
                        }})))
                .andExpect(status().isCreated());
    }

    @Test
    void changerServiceHabilitation_reaffecteAtomiquementEtTrace() throws Exception {
        String tokenAdmin = seConnecterAdministrateur();
        Long habilitationCaLittoralId = habilitationRepository.findByUtilisateur_IdAndActifTrue(caLittoral.getId())
                .stream().filter(h -> "CHARGE_AFFAIRES".equals(h.getRoleMetier().getCode())).findFirst().orElseThrow().getId();

        String reponse = mockMvc.perform(put("/api/habilitations/" + habilitationCaLittoralId + "/service")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouveauServiceId", centre.getId());
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(centre.getId()))
                .andExpect(jsonPath("$.roleMetierCode").value("CHARGE_AFFAIRES"))
                .andReturn().getResponse().getContentAsString();
        long nouvelleHabilitationId = objectMapper.readTree(reponse).get("id").asLong();
        assertThat(nouvelleHabilitationId).isNotEqualTo(habilitationCaLittoralId);

        List<Habilitation> toutes = habilitationRepository.findByUtilisateur_Id(caLittoral.getId());
        Habilitation ancienne = toutes.stream().filter(h -> h.getId().equals(habilitationCaLittoralId)).findFirst().orElseThrow();
        assertThat(ancienne.isActif()).isFalse();
        assertThat(ancienne.getDateFin()).isNotNull();
        Habilitation nouvelle = toutes.stream().filter(h -> h.getId().equals(nouvelleHabilitationId)).findFirst().orElseThrow();
        assertThat(nouvelle.isActif()).isTrue();
        assertThat(nouvelle.getService().getId()).isEqualTo(centre.getId());

        // Un seul evenement CHANGEMENT_SERVICE_HABILITATION, pas deux (RETRAIT + ATTRIBUTION distincts).
        List<EvenementAudit> evenements = evenementAuditRepository.findByEntiteTypeAndEntiteIdOrderByDateActionAsc(
                EntiteAuditable.HABILITATION, String.valueOf(nouvelleHabilitationId));
        assertThat(evenements).hasSize(1);
        assertThat(evenements.get(0).getAction().name()).isEqualTo("CHANGEMENT_SERVICE_HABILITATION");

        // caLittoral peut desormais agir sur le Centre (verifie indirectement par le reste de la
        // suite - la nouvelle habilitation est active sur le Centre).
    }

    // =========================================================================================
    // Section 18 - les 10 scenarios explicitement demandes
    // =========================================================================================

    /** Test 1 : Charge d'Affaires Littoral consulte les FIPH Littoral -> REUSSI. */
    @Test
    void test1_chargeAffairesLittoralConsulteFiphLittoral_reussi() throws Exception {
        long fiphId = creerFiphManuelle(caLittoral, agentLittoral1);
        String token = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(get("/api/fiph/" + fiphId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + fiphId + ")]").exists());
    }

    /** Test 2 : Charge d'Affaires Littoral tente de consulter les FIPH Centre -> REFUSE. */
    @Test
    void test2_chargeAffairesLittoralConsulteFiphCentre_refuse() throws Exception {
        long fiphCentreId = creerFiphManuelle(caCentre, agentCentre1);
        String tokenLittoral = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(get("/api/fiph/" + fiphCentreId).header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isForbidden());
        // Absente aussi de la liste.
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + fiphCentreId + ")]").doesNotExist());
    }

    /** Test 3 : Charge d'Affaires Littoral valide une FIPH Littoral (niveau 2) -> REUSSI. */
    @Test
    void test3_chargeAffairesLittoralValideFiphLittoral_reussi() throws Exception {
        long fiphId = creerFiphManuelle(caLittoral, agentLittoral2);
        long versionId = versionCouranteDe(caLittoral, fiphId);
        String token = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
    }

    /** Test 4 : Charge d'Affaires Littoral tente de valider une FIPH Centre -> REFUSE. */
    @Test
    void test4_chargeAffairesLittoralValideFiphCentre_refuse() throws Exception {
        long fiphCentreId = creerFiphManuelle(caCentre, agentCentre1);
        long versionId = versionCouranteDe(caCentre, fiphCentreId);
        String tokenLittoral = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenLittoral)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isForbidden());
    }

    /** Test 5 : Personne habilitee Littoral intervient sur FIPH Littoral -> REUSSI. */
    @Test
    void test5_personneHabiliteeLittoralIntervientSurFiphLittoral_reussi() throws Exception {
        long fiphId = creerFiphManuelle(phLittoral, agentLittoral1);
        long versionId = versionCouranteDe(phLittoral, fiphId);
        String token = seConnecter(phLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
    }

    /** Test 6 : Personne habilitee Littoral intervient sur FIPH Centre -> REFUSE. */
    @Test
    void test6_personneHabiliteeLittoralIntervientSurFiphCentre_refuse() throws Exception {
        long fiphCentreId = creerFiphManuelle(phCentre, agentCentre1);
        long versionId = versionCouranteDe(phCentre, fiphCentreId);
        String tokenLittoral = seConnecter(phLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenLittoral)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isForbidden());
    }

    /** Test 7 : Responsable Littoral valide une FIPH Littoral (niveau 3) -> REUSSI. */
    @Test
    void test7_responsableLittoralValideFiphLittoral_reussi() throws Exception {
        long fiphId = creerFiphManuelle(caLittoral, agentLittoral1);
        long versionId = versionCouranteDe(caLittoral, fiphId);
        validerNiveau(caLittoral, versionId, 2);
        String tokenRa = seConnecter(raLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));
    }

    /** Test 8 : Responsable Littoral tente de valider une FIPH Centre -> REFUSE. */
    @Test
    void test8_responsableLittoralValideFiphCentre_refuse() throws Exception {
        long fiphCentreId = creerFiphManuelle(caCentre, agentCentre1);
        long versionId = versionCouranteDe(caCentre, fiphCentreId);
        validerNiveau(caCentre, versionId, 2);
        String tokenRaLittoral = seConnecter(raLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRaLittoral)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isForbidden());
    }

    /** Test 9 : tentative directe via API pour contourner le filtre de service (identifiant connu) -> REFUSE. */
    @Test
    void test9_tentativeDirecteApiContournementFiltreService_refuse() throws Exception {
        long fiphCentreId = creerFiphManuelle(caCentre, agentCentre1);
        long versionId = versionCouranteDe(caCentre, fiphCentreId);
        String tokenLittoral = seConnecter(caLittoral.getIdentifiant());
        // GET direct sur la FIPH, sur sa version, sur ses validations et sur son PDF-eligible historique.
        mockMvc.perform(get("/api/fiph/" + fiphCentreId).header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/fiph-versions/" + versionId + "/validations").header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit/fiph/" + fiphCentreId).header("Authorization", "Bearer " + tokenLittoral))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 10 : modification frauduleuse du service dans la requete -> REFUSE. Le service d'une
     * FIPH est copie depuis l'agent a la creation et n'existe dans aucune requete cliente
     * modifiable (voir FiphService#creerFiphEtVersionInitiale) - la seule surface d'attaque
     * plausible est de forcer un agentId d'un AUTRE service dans la requete de creation
     * manuelle : verifiee ici, refusee par verifierPerimetreGestionnaire (l'agent cible n'est
     * pas du service de l'appelant).
     */
    @Test
    void test10_modificationFrauduleuseServiceDansRequete_refuse() throws Exception {
        String tokenCaLittoral = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCaLittoral)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agentCentre1.getId()); // agent d'un AUTRE service que l'appelant
                            put("annee", 2026);
                            put("numeroSemaine", 10);
                        }})))
                .andExpect(status().isForbidden());
    }

    // =========================================================================================
    // Section 19 - tests de creation (createur / service / agent / visa / statut)
    // =========================================================================================

    /** Creation via bon de sortie (createur effectif = l'agent lui-meme, visa automatique acquis d'office). */
    @Test
    void creation_parAgentViaBonDeSortie_visaAutomatiqueEtStatutSignee() throws Exception {
        Utilisateur agentUtilisateur = creerUtilisateur("agent_creation_" + suffixe, littoral);
        Agent agent = agentRepository.save(nouvelAgent("CRE1-" + (suffixe % 100_000L), "Nouveau", "Agent", littoral));
        agent.setUtilisateur(agentUtilisateur);
        agentRepository.save(agent);
        affecterAgentAMission(agent, nouvelleMission(), caLittoral);

        String tokenAgent = seConnecter(agentUtilisateur.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        long bonSortieId = creerViserEtValiderBonDeSortie(tokenAgent, tokenCa);

        String reponse = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponse);
        JsonNode fiph = trouverFiphDeLAgent(fiphs, agent.getId());
        assertThat(fiph.get("origine").asText()).isEqualTo("BON_SORTIE");
        assertThat(fiph.get("statut").asText()).isEqualTo("SIGNEE");
        assertThat(fiph.get("serviceId").asLong()).isEqualTo(littoral.getId());
        assertThat(fiph.get("bonSortieId").asLong()).isEqualTo(bonSortieId);
    }

    /** Creation manuelle par le Charge d'Affaires : createur = CA, visa automatique du CREATEUR (pas de l'agent), statut SIGNEE directement. */
    @Test
    void creation_parChargeAffaires_visaAutomatiqueDuCreateurEtStatutSignee() throws Exception {
        // Agent SANS compte utilisateur (cas courant) : avant l'evolution du 2026-08-19, cette FIPH
        // serait restee bloquee a BROUILLON pour toujours (signer() exige un compte agent).
        Agent agentSansCompte = agentRepository.save(nouvelAgent("CRE2-" + (suffixe % 100_000L), "SansCompte", "Test", littoral));
        String tokenCa = seConnecter(caLittoral.getIdentifiant());

        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agentSansCompte.getId());
                            put("annee", 2026);
                            put("numeroSemaine", 15);
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origine").value("MANUELLE"))
                .andExpect(jsonPath("$.statut").value("SIGNEE"))
                .andExpect(jsonPath("$.serviceId").value(littoral.getId()))
                .andReturn().getResponse().getContentAsString();
        long fiphId = objectMapper.readTree(reponse).get("id").asLong();

        // Directement eligible au niveau 2, sans jamais avoir besoin d'un compte agent pour signer.
        long versionId = objectMapper.readTree(reponse).get("versionCouranteId").asLong();
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        // L'historique porte le visa automatique du CREATEUR (le CA), explicitement distinct d'un visa agent.
        String reponseHistorique = mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(reponseHistorique).contains("Visa automatique du createur");
    }

    /** Creation manuelle par la Personne habilitee : meme comportement que le Charge d'Affaires - createur = la personne habilitee, jamais l'agent. */
    @Test
    void creation_parPersonneHabilitee_visaAutomatiqueDuCreateur() throws Exception {
        Agent agent = agentRepository.save(nouvelAgent("CRE3-" + (suffixe % 100_000L), "Test", "PH", littoral));
        String tokenPh = seConnecter(phLittoral.getIdentifiant());

        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenPh)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("annee", 2026);
                            put("numeroSemaine", 16);
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("SIGNEE"))
                .andReturn().getResponse().getContentAsString();
        long versionId = objectMapper.readTree(reponse).get("versionCouranteId").asLong();

        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenPh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creeParIdentifiant").value(phLittoral.getIdentifiant()));
    }

    // =========================================================================================
    // Section 20 - parcours complet bout-en-bout, Littoral PUIS Centre (etancheite)
    // =========================================================================================

    @Test
    void parcoursCompletBoutEnBout_littoralPuisCentre_etancheiteDemontree() throws Exception {
        // --- Littoral ---
        long fiphLittoral = creerFiphManuelle(caLittoral, agentLittoral1);
        long versionLittoral = versionCouranteDe(caLittoral, fiphLittoral);
        validerNiveau(caLittoral, versionLittoral, 2);
        validerNiveau(raLittoral, versionLittoral, 3);
        String tokenDirection = seConnecter(direction(littoral).getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionLittoral + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));

        // --- Centre, avec les utilisateurs du Centre - totalement independant ---
        long fiphCentre = creerFiphManuelle(caCentre, agentCentre1);
        long versionCentre = versionCouranteDe(caCentre, fiphCentre);
        validerNiveau(caCentre, versionCentre, 2);
        validerNiveau(raCentre, versionCentre, 3);
        String tokenDirectionCentre = seConnecter(direction(centre).getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionCentre + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirectionCentre)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));

        // Etancheite : le Responsable et le Charge d'Affaires du Littoral ne voient/valident jamais le Centre.
        String tokenRaLittoral = seConnecter(raLittoral.getIdentifiant());
        mockMvc.perform(get("/api/fiph/" + fiphCentre).header("Authorization", "Bearer " + tokenRaLittoral))
                .andExpect(status().isForbidden());
    }

    // =========================================================================================
    // Aides de scenario
    // =========================================================================================

    private Utilisateur direction(Service service) {
        return creerUtilisateurAvecHabilitation("dir_" + service.getCodeService() + "_" + suffixe, service, CodeRoleMetier.DIRECTION);
    }

    private long creerFiphManuelle(Utilisateur createur, Agent agent) throws Exception {
        String token = seConnecter(createur.getIdentifiant());
        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("annee", 2026);
                            put("numeroSemaine", (int) (20 + (agent.getId() % 20)));
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("id").asLong();
    }

    private long versionCouranteDe(Utilisateur lecteurAutorise, long fiphId) throws Exception {
        String token = seConnecter(lecteurAutorise.getIdentifiant());
        String reponse = mockMvc.perform(get("/api/fiph/" + fiphId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("versionCouranteId").asLong();
    }

    private void validerNiveau(Utilisateur validateur, long versionId, int niveau) throws Exception {
        String token = seConnecter(validateur.getIdentifiant());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/" + niveau)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(decisionValidee()))
                .andExpect(status().isOk());
    }

    private String decisionValidee() throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
    }

    private JsonNode trouverFiphDeLAgent(JsonNode fiphs, Long agentId) {
        for (JsonNode f : fiphs) {
            if (f.get("agentId").asLong() == agentId) {
                return f;
            }
        }
        throw new AssertionError("Aucune FIPH trouvee pour l'agent id=" + agentId);
    }

    private long creerViserEtValiderBonDeSortie(String tokenEmetteur, String tokenValidateur) throws Exception {
        String corpsBonSortie = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("kilometrage", 30);
            put("dateSortie", LocalDate.now().toString());
            put("heureSortie", "08:00:00");
            put("lieu", "Chantier de test");
            put("codeAffaireSaisi", "CODE-TEST");
            put("motifSortie", "Test perimetre service");
        }});
        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json").content(corpsBonSortie))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser").header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider").header("Authorization", "Bearer " + tokenValidateur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
        return bonSortieId;
    }

    private void affecterAgentAMission(Agent agent, Mission mission, Utilisateur creePar) {
        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(creePar);
        affectationMissionRepository.save(affectation);
    }

    private Mission nouvelleMission() {
        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        return missionRepository.save(mission);
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

    private String seConnecterAdministrateur() throws Exception {
        Utilisateur admin = creerUtilisateurAvecHabilitation("admin_perim_" + suffixe, null, CodeRoleMetier.ADMINISTRATEUR);
        return seConnecter(admin.getIdentifiant());
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

    private Agent nouvelAgent(String matricule, String nom, String prenom, Service service) {
        Agent agent = new Agent();
        agent.setMatricule(matricule);
        agent.setNom(nom);
        agent.setPrenom(prenom);
        agent.setService(service);
        return agent;
    }
}
