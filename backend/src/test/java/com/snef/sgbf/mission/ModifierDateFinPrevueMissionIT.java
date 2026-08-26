package com.snef.sgbf.mission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.snef.sgbf.notification.entity.TypeNotification;
import com.snef.sgbf.notification.repository.NotificationRepository;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
import com.snef.sgbf.referentiel.repository.CodeHNRepository;
import com.snef.sgbf.referentiel.repository.RoleMetierRepository;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
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
 * Prolongation/reduction de la date de fin prevue d'une mission en cours
 * (evolution du 2026-08-26, "MIS-001 reste MIS-001", distincte de
 * {@code reaffecterPendantMissionEnCours} qui fait naitre une nouvelle
 * mission - voir {@link ReaffectationMiMissionIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ModifierDateFinPrevueMissionIT {

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
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Service autreService;
    private Chantier chantier;
    private Utilisateur agent;
    private Utilisateur ca;
    private Utilisateur caAutreService;
    private long missionId;
    private LocalDate debut;
    private LocalDate finInitiale;

    @BeforeEach
    void construireJeuDeDonnees() throws Exception {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        agent = utilisateurRepository.save(nouvelAgentAvecCompte("MAT" + suffixe, "Test", "Agent", "agent_" + suffixe, service));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);

        debut = LocalDate.now();
        finInitiale = debut.plusDays(20);
        String tokenCa = seConnecter(ca.getIdentifiant());
        missionId = creerMission(tokenCa, codeHN, debut, finInitiale);

        mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", missionId);
                            put("dateDebutAffectation", debut.toString());
                        }})))
                .andExpect(status().isCreated());
    }

    /** Prolongation valide : la mission reste la MEME (identifiant inchange), seule sa date de fin prevue avance. */
    @Test
    void prolongationValideModifieLaDateEtNotifieLAgent() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate nouvelleFin = finInitiale.plusDays(10);

        mockMvc.perform(patch("/api/missions/" + missionId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", nouvelleFin.toString());
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(missionId))
                .andExpect(jsonPath("$.dateFinPrevue").value(nouvelleFin.toString()));

        mockMvc.perform(get("/api/missions/" + missionId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateFinPrevue").value(nouvelleFin.toString()));

        List<com.snef.sgbf.notification.entity.Notification> notifs =
                notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(agent.getId());
        boolean notifieMissionModifiee = notifs.stream().anyMatch(n -> n.getType() == TypeNotification.MISSION_MODIFIEE
                && missionId == n.getEntiteId());
        org.assertj.core.api.Assertions.assertThat(notifieMissionModifiee).isTrue();
    }

    /** Reduction valide : aucune heure pointee au-dela de la nouvelle date de fin -> autorisee. */
    @Test
    void reductionSansJourPointeAuDelaEstAcceptee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate nouvelleFin = debut.plusDays(5);

        mockMvc.perform(patch("/api/missions/" + missionId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", nouvelleFin.toString());
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateFinPrevue").value(nouvelleFin.toString()));
    }

    /** Reduction bloquee : des heures sont deja pointees au-dela de la nouvelle date de fin demandee. */
    @Test
    void reductionSousDesHeuresDejaPointeesEstRefusee() throws Exception {
        LocalDate journeeDejaTravaillee = debut.plusDays(15);
        creerPointageAvecHeures(agent, journeeDejaTravaillee, new BigDecimal("8"));

        String tokenCa = seConnecter(ca.getIdentifiant());
        LocalDate nouvelleFin = debut.plusDays(10);

        mockMvc.perform(patch("/api/missions/" + missionId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", nouvelleFin.toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-015"));
    }

    /** Une date de fin prevue deja passee est toujours refusee. */
    @Test
    void dateDejaPasseeEstRefusee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());

        mockMvc.perform(patch("/api/missions/" + missionId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", LocalDate.now().minusDays(1).toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-013"));
    }

    /**
     * La date de fin prevue ne peut pas etre anterieure a la date de debut
     * prevue de la mission - isole du refus "date deja passee" (RG-MIS-013)
     * en utilisant une mission dont le debut prevu est encore dans le futur.
     */
    @Test
    void dateAnterieureALaDateDeDebutEstRefusee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        CodeHN codeHNFutur = codeHNRepository.save(nouveauCodeHN("MISFUT" + suffixe, chantier));
        LocalDate debutFutur = LocalDate.now().plusDays(5);
        long missionFutureId = creerMission(tokenCa, codeHNFutur, debutFutur, debutFutur.plusDays(20));
        Utilisateur agentFutur = utilisateurRepository.save(nouvelAgentAvecCompte(
                "MATF" + suffixe, "Futur", "Agent", "agent_futur_" + suffixe, service));
        mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agentFutur.getId());
                            put("missionId", missionFutureId);
                            put("dateDebutAffectation", debutFutur.toString());
                        }})))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/missions/" + missionFutureId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", debutFutur.minusDays(1).toString()); // futur, mais avant le debut prevu
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-014"));
    }

    /** Une mission sans aucune affectation active n'a rien a prolonger/reduire. */
    @Test
    void missionSansAffectationActiveEstRefusee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        Utilisateur autreAgent = utilisateurRepository.save(nouvelAgentAvecCompte(
                "MAT2-" + suffixe, "Sans", "Affectation", "sans_affect_" + suffixe, service));
        CodeHN autreCodeHN = codeHNRepository.save(nouveauCodeHN("MISNA" + suffixe, chantier));
        long missionSansAffectationId = creerMission(tokenCa, autreCodeHN, debut, finInitiale);

        mockMvc.perform(patch("/api/missions/" + missionSansAffectationId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", finInitiale.plusDays(5).toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-012"));
        org.assertj.core.api.Assertions.assertThat(autreAgent).isNotNull(); // sanity : cree, non utilise par ce scenario
    }

    /** Un Charge d'Affaires d'un AUTRE service ne peut pas prolonger/reduire une mission hors de son perimetre. */
    @Test
    void modificationHorsPerimetreEstRefusee() throws Exception {
        String tokenCaAutreService = seConnecter(caAutreService.getIdentifiant());

        mockMvc.perform(patch("/api/missions/" + missionId + "/date-fin-prevue")
                        .header("Authorization", "Bearer " + tokenCaAutreService)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouvelleDateFinPrevue", finInitiale.plusDays(5).toString());
                        }})))
                .andExpect(status().isForbidden());
    }

    // --- Aides de scenario ---

    /** Cree directement, sans passer par un bon de sortie, une ligne de pointage AVEC des heures reellement saisies. */
    private void creerPointageAvecHeures(Utilisateur agentPointe, LocalDate date, BigDecimal heuresNormales) {
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
        pointage.setHeuresNormales(heuresNormales);
        pointageRepository.save(pointage);
    }

    private long creerMission(String token, CodeHN codeHN, LocalDate debutMission, LocalDate finMission) throws Exception {
        String reponse = mockMvc.perform(post("/api/missions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("codeChantier", chantier.getCodeAffaire());
                            put("libelleChantier", chantier.getLibelle());
                            put("codeMission", codeHN.getCode());
                            put("libelleCodeMission", codeHN.getLibelle());
                            put("dateDebutPrevue", debutMission.toString());
                            put("dateFinPrevue", finMission.toString());
                            put("missionPrecedenteId", null);
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

    /** Agent AVEC compte applicatif (necessaire pour verifier la notification de modification de mission). */
    private Utilisateur nouvelAgentAvecCompte(String matricule, String nom, String prenom, String identifiant, Service svc) {
        Utilisateur a = new Utilisateur();
        a.setMatricule(matricule);
        a.setNom(nom);
        a.setPrenom(prenom);
        a.setIdentifiant(identifiant);
        a.setEmail(identifiant + "@example.invalid");
        a.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        a.setStatutCompte(StatutCompte.ACTIF);
        a.setService(svc);
        return a;
    }
}
