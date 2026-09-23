package com.snef.sgbf.mission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.entity.FIPHVersion;
import com.snef.sgbf.fiph.entity.JourSemaine;
import com.snef.sgbf.fiph.entity.OrigineFiph;
import com.snef.sgbf.fiph.entity.Pointage;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import com.snef.sgbf.fiph.repository.FiphRepository;
import com.snef.sgbf.fiph.repository.FiphVersionRepository;
import com.snef.sgbf.fiph.repository.PointageRepository;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.StatutAffectation;
import com.snef.sgbf.mission.repository.AffectationMissionRepository;
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
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
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
 * Reaffectation d'un agent vers une nouvelle mission alors que sa mission
 * actuelle est encore ACTIVE (evolution du 2026-08-20, brief section 9-13) :
 * couvre les cas 1 (lendemain), 2 (milieu de mission - scenario exact du
 * brief : mission 1 scindee en deux au jour du debut de la nouvelle
 * affectation moins un jour), 5 (aucune affectation active), 6 (periode
 * incoherente), 7 (hors perimetre service), ainsi que le refus de
 * retroactivite (decision confirmee) sur un jour deja pointe.
 *
 * <p>Toutes les dates sont calculees relativement a {@link LocalDate#now()}
 * (jamais de date absolue codee en dur) : {@code CreerMissionRequest.dateFinPrevue}
 * porte une contrainte {@code @FutureOrPresent} qui rendrait toute date
 * passee fixe invalide au fil du temps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReaffectationMiMissionIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private AffectationMissionRepository affectationMissionRepository;
    @Autowired private FiphRepository fiphRepository;
    @Autowired private FiphVersionRepository fiphVersionRepository;
    @Autowired private PointageRepository pointageRepository;
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
    private long mis001Id;
    private long affectationMis001Id;
    private LocalDate debutMission1;

    @BeforeEach
    void construireJeuDeDonnees() throws Exception {
        suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeMis001 = codeHNRepository.save(nouveauCodeHN("MIS001-" + suffixe, chantier));
        codeMis002 = codeHNRepository.save(nouveauCodeHN("MIS002-" + suffixe, chantier));

        agent = utilisateurRepository.save(nouvelAgent("MAT" + suffixe, "Test", "Agent", service));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);

        debutMission1 = LocalDate.now();
        String tokenCa = seConnecter(ca.getIdentifiant());
        mis001Id = creerMission(tokenCa, codeMis001, debutMission1, debutMission1.plusDays(20));
        final LocalDate debutAffectation1 = debutMission1;
        String reponseAffectation = mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", mis001Id);
                            put("dateDebutAffectation", debutAffectation1.toString());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        affectationMis001Id = objectMapper.readTree(reponseAffectation).get("id").asLong();
    }

    /**
     * Scenario exact du brief (section 9-10) : nouvelle affectation au
     * milieu de la mission en cours -&gt; mission 1 se termine
     * automatiquement la veille, mission 2 commence ce jour-la, en une
     * seule operation.
     */
    @Test
    void reaffectationAuMilieuDeLaMissionScindeLaPeriodeAutomatiquement() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate pointDeScission = debutMission1.plusDays(10);

        String reponse = mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsReaffectation(agent.getId(), codeMis002, pointDeScission, debutMission1.plusDays(30), pointDeScission)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAffectation").value("ACTIVE"))
                .andExpect(jsonPath("$.dateDebutAffectation").value(pointDeScission.toString()))
                .andExpect(jsonPath("$.affectationPrecedenteId").value(affectationMis001Id))
                .andReturn().getResponse().getContentAsString();
        JsonNode nouvelleAffectation = objectMapper.readTree(reponse);
        long nouvelleAffectationId = nouvelleAffectation.get("id").asLong();
        long mis002Id = nouvelleAffectation.get("missionId").asLong();

        LocalDate finMission1Attendue = pointDeScission.minusDays(1);

        // Mission 1 automatiquement scindee : se termine la veille du point de scission, jamais supprimee.
        mockMvc.perform(get("/api/affectations-mission/" + affectationMis001Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAffectation").value("INTERROMPUE"))
                .andExpect(jsonPath("$.dateFinAffectation").value(finMission1Attendue.toString()))
                .andExpect(jsonPath("$.motifInterruptionCode").value("NOUVELLE_MISSION"));
        mockMvc.perform(get("/api/missions/" + mis001Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("INTERROMPUE"))
                .andExpect(jsonPath("$.dateFinReelle").value(finMission1Attendue.toString()));

        // Mission 2 demarree, agent effectivement dessus.
        mockMvc.perform(get("/api/missions/" + mis002Id).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
        mockMvc.perform(get("/api/affectations-mission/" + nouvelleAffectationId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agent.getId()))
                .andExpect(jsonPath("$.missionId").value(mis002Id));
    }

    /** Cas 1 : la nouvelle affectation commence le lendemain du debut de l'ancienne - split minimal, toujours accepte. */
    @Test
    void reaffectationDesLeLendemainDuDebutEstAcceptee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate lendemain = debutMission1.plusDays(1);

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsReaffectation(agent.getId(), codeMis002, lendemain, debutMission1.plusDays(30), lendemain)))
                .andExpect(status().isCreated());
    }

    /** Cas 6 : periode incoherente - la nouvelle date ne peut pas etre anterieure ou egale au debut de l'affectation active. */
    @Test
    void reaffectationAvantOuAuDebutDeLAffectationActiveEstRefusee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        // egale au debut, pas strictement posterieure
                        .content(corpsReaffectation(agent.getId(), codeMis002, debutMission1.plusDays(5), debutMission1.plusDays(30), debutMission1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-010"));
    }

    /** Cas 5 : un agent sans affectation active ne peut pas faire l'objet d'une reaffectation mi-mission. */
    @Test
    void agentSansAffectationActiveEstRefuse() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        Utilisateur autreAgent = utilisateurRepository.save(nouvelAgent("MAT2-" + suffixe, "Sans", "Affectation", service));
        LocalDate dateReaffectation = debutMission1.plusDays(10);

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsReaffectation(autreAgent.getId(), codeMis002, dateReaffectation, debutMission1.plusDays(30), dateReaffectation)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-009"));
    }

    /** Cas 7 : un CA d'un autre service ne peut pas reaffecter un agent hors de son perimetre. */
    @Test
    void reaffectationHorsPerimetreEstRefusee() throws Exception {
        String tokenCaAutreService = seConnecter(caAutreService.getIdentifiant());
        LocalDate dateReaffectation = debutMission1.plusDays(10);

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCaAutreService)
                        .contentType("application/json")
                        .content(corpsReaffectation(agent.getId(), codeMis002, dateReaffectation, debutMission1.plusDays(30), dateReaffectation)))
                .andExpect(status().isForbidden());
    }

    /**
     * Retroactivite refusee (decision confirmee, section 9-13) : un pointage
     * existe deja pour cet agent posterieurement a la date de reaffectation
     * demandee - impossible de reaffecter, ce qui reecrirait silencieusement
     * ce jour deja valide.
     */
    @Test
    void reaffectationRetroactiveSurUnJourDejaPointeEstRefusee() throws Exception {
        LocalDate dernierJourPointe = debutMission1.plusDays(15);
        creerPointagePourAgent(agent, dernierJourPointe);

        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate dateReaffectation = debutMission1.plusDays(11);

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsReaffectation(agent.getId(), codeMis002, dateReaffectation, debutMission1.plusDays(31), dateReaffectation)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-011"));
    }

    /** Une reaffectation a une date strictement posterieure au dernier jour pointe reste acceptee. */
    @Test
    void reaffectationApresLeDernierJourPointeEstAcceptee() throws Exception {
        LocalDate dernierJourPointe = debutMission1.plusDays(10);
        creerPointagePourAgent(agent, dernierJourPointe);

        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate dateReaffectation = debutMission1.plusDays(11);

        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsReaffectation(agent.getId(), codeMis002, dateReaffectation, debutMission1.plusDays(31), dateReaffectation)))
                .andExpect(status().isCreated());
    }

    // --- Aides de scenario ---

    /** Cree directement, sans passer par un bon de sortie, une ligne de pointage validee pour l'agent a la date donnee. */
    private void creerPointagePourAgent(Utilisateur agentPointe, LocalDate date) {
        FIPH fiph = new FIPH();
        fiph.setAgent(agentPointe);
        fiph.setService(agentPointe.getService());
        fiph.setOrigine(OrigineFiph.BON_SORTIE);
        fiph.setCreePar(agentPointe);
        fiph.setAnnee(date.get(IsoFields.WEEK_BASED_YEAR));
        fiph.setMois(date.getMonthValue());
        fiph.setNumeroSemaine(date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        fiph.setDateDebutPeriode(date.with(java.time.DayOfWeek.MONDAY));
        fiph.setStatut(StatutFiphVersion.BROUILLON);
        fiph.setDateCreation(LocalDateTime.now());
        fiph = fiphRepository.save(fiph);

        FIPHVersion version = new FIPHVersion();
        version.setFiph(fiph);
        version.setNumeroVersion(1);
        version.setDateCreation(LocalDateTime.now());
        version.setCreePar(agentPointe);
        version.setStatutVersion(StatutFiphVersion.BROUILLON);
        version.setDateFinPeriode(date.with(java.time.DayOfWeek.SUNDAY));
        version.setLockVersion(0);
        version = fiphVersionRepository.save(version);

        fiph.setVersionCourante(version);
        fiphRepository.save(fiph);

        AffectationMission affectation = affectationMissionRepository.findByAgent_IdAndStatutAffectation(
                agentPointe.getId(), StatutAffectation.ACTIVE).orElseThrow();

        Pointage pointage = new Pointage();
        pointage.setFiphVersion(version);
        pointage.setJourSemaine(JourSemaine.depuis(date.getDayOfWeek()));
        pointage.setDatePointage(date);
        pointage.setAffectationMission(affectation);
        pointageRepository.save(pointage);
    }

    private long creerMission(String token, CodeHN codeHN, LocalDate debut, LocalDate fin) throws Exception {
        String reponse = mockMvc.perform(post("/api/missions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("codeChantier", chantier.getCodeAffaire());
                            put("libelleChantier", chantier.getLibelle());
                            put("codeMission", codeHN.getCode());
                            put("libelleCodeMission", codeHN.getLibelle());
                            put("dateDebutPrevue", debut.toString());
                            put("dateFinPrevue", fin.toString());
                            put("missionPrecedenteId", null);
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("id").asLong();
    }

    /** Corps JSON de {@code /reaffecter-mi-mission} - la mission cible n'est plus choisie dans une liste mais saisie librement (evolution du 2026-08-26). */
    private String corpsReaffectation(long agentId, CodeHN codeMissionCible, LocalDate dateDebutPrevueMission,
                                       LocalDate dateFinPrevueMission, LocalDate dateDebutAffectation) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("agentId", agentId);
            put("codeChantier", chantier.getCodeAffaire());
            put("libelleChantier", chantier.getLibelle());
            put("codeMission", codeMissionCible.getCode());
            put("libelleCodeMission", codeMissionCible.getLibelle());
            put("dateDebutPrevueMission", dateDebutPrevueMission.toString());
            put("dateFinPrevueMission", dateFinPrevueMission.toString());
            put("dateDebutAffectation", dateDebutAffectation.toString());
        }});
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

    private Utilisateur nouvelAgent(String matricule, String nom, String prenom, Service svc) {
        Utilisateur a = new Utilisateur();
        a.setMatricule(matricule);
        a.setNom(nom);
        a.setPrenom(prenom);
        a.setService(svc);
        return a;
    }
}
