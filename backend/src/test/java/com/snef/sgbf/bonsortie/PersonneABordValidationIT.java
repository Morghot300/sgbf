package com.snef.sgbf.bonsortie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.BonSortiePersonne;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.bonsortie.repository.BonSortiePersonneRepository;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.repository.FiphRepository;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.mission.entity.StatutAffectation;
import com.snef.sgbf.mission.entity.StatutMission;
import com.snef.sgbf.mission.repository.AffectationMissionRepository;
import com.snef.sgbf.mission.repository.MissionRepository;
import com.snef.sgbf.notification.entity.Notification;
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

/**
 * Verifie de bout en bout, avec de VRAIS commits (voir absence deliberee de
 * {@code @Transactional} ci-dessous), l'evolution du 2026-08-26 (section 12-15) :
 * le bon de sortie individuel d'une personne a bord doit rester "en attente de
 * validation du Charge d'Affaires" (statut {@code VISE}) - jamais directement
 * {@code VALIDE} - et la FIPH de cette personne ne doit s'initialiser qu'a
 * la validation explicite de ce bon individuel, jamais avant.
 *
 * <p>Cette classe n'est PAS annotee {@code @Transactional}, contrairement aux
 * autres IT de ce module : la generation du bon individuel s'execute dans
 * {@code PersonneABordGenerationService#genererPourAssociation}, annotee
 * {@code @Transactional(propagation = REQUIRES_NEW)} pour l'atomicite par
 * personne (section 9.6) - une transaction de test englobante en rollback-only
 * rendrait cette generation invisible aux lectures suivantes de ce meme test
 * (voir la note detaillee dans {@code PersonneABordIT}). Les identifiants sont
 * rendus uniques via {@code System.nanoTime()} : les donnees reellement
 * commitees par ce test n'entrent jamais en collision avec celles d'un autre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonneABordValidationIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private AffectationMissionRepository affectationMissionRepository;
    @Autowired private BonSortiePersonneRepository bonSortiePersonneRepository;
    @Autowired private FiphRepository fiphRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Service service;
    private Utilisateur emetteur;
    private Utilisateur ca;
    private Utilisateur personneA;
    private Utilisateur personneB;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test validation BS individuel"));
        Chantier chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        emetteur = creerPersonneAvecCompte("EMT" + (suffixe % 100_000L), "Test", "Emetteur", "emetteur_val_" + suffixe, service);
        long court = suffixe % 100_000L;
        personneA = utilisateurRepository.save(nouvelAgent("PXV" + court, "Ateba", "Alice", service));
        personneB = utilisateurRepository.save(nouvelAgent("PXW" + court, "Bikoro", "Bruno", service));
        ca = creerUtilisateurAvecHabilitation("ca_val_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        mission = missionRepository.save(mission);

        affecterSurMission(emetteur, mission);
        affecterSurMission(personneA, mission);
        affecterSurMission(personneB, mission);
    }

    private void affecterSurMission(Utilisateur agent, Mission mission) {
        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(ca);
        affectationMissionRepository.save(affectation);
    }

    @Test
    void bonIndividuelResteEnAttenteJusquaValidationCA_puisInitialiseLaFiph() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());

        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test validation BS individuel");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortiePrincipalId = objectMapper.readTree(reponseBs).get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneA.getId());
                        }})))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/viser")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/valider")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        // Cette fois, de vrais commits : la generation REQUIRES_NEW est bien observable ici.
        BonSortiePersonne association = bonSortiePersonneRepository
                .findByBonSortiePrincipal_IdAndAgent_Id(bonSortiePrincipalId, personneA.getId())
                .orElseThrow();
        assertThat(association.getBonSortieIndividuel()).isNotNull();
        long bonIndividuelId = association.getBonSortieIndividuel().getId();

        // Le bon individuel est en attente de validation (VISE), jamais directement VALIDE.
        mockMvc.perform(get("/api/bons-sortie/" + bonIndividuelId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VISE"));

        // Le CA le retrouve dans sa liste de travail habituelle (filtre statut=VISE).
        String reponseListeAValider = mockMvc.perform(get("/api/bons-sortie")
                        .param("statut", "VISE")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean individuelDansLaListe = false;
        for (JsonNode n : objectMapper.readTree(reponseListeAValider)) {
            if (n.get("id").asLong() == bonIndividuelId) {
                individuelDansLaListe = true;
            }
        }
        assertThat(individuelDansLaListe).isTrue();

        // Une notification "a valider" a bien ete envoyee au CA pour ce bon individuel precis.
        List<Notification> notificationsCa = notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(ca.getId());
        assertThat(notificationsCa).anyMatch(n -> n.getType() == TypeNotification.BON_SORTIE_A_VALIDER
                && bonIndividuelId == n.getEntiteId());

        // Aucune FIPH n'est encore initialisee pour cette personne : elle n'est pas encore validee.
        assertThat(fiphRepository.findByAgent_Id(personneA.getId())).isEmpty();

        // Le CA valide explicitement le bon individuel, comme n'importe quel autre bon de sortie.
        mockMvc.perform(post("/api/bons-sortie/" + bonIndividuelId + "/valider")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        // C'est SEULEMENT maintenant que la FIPH de cette personne est initialisee.
        List<FIPH> fiphsPersonneA = fiphRepository.findByAgent_Id(personneA.getId());
        assertThat(fiphsPersonneA).hasSize(1);
    }

    /**
     * Bug reel corrige le 2026-08-26 (observe en conditions reelles, pas
     * seulement suppose par un commentaire de test) : ajouter une personne a
     * bord APRES que le bon principal soit deja VALIDE (RG-PAB-006, "ajout
     * tardif") declenchait la generation dans la MEME transaction que
     * l'insertion de l'association, avant que celle-ci ne soit commitee - la
     * transaction REQUIRES_NEW de generation ne la voyait donc jamais, et
     * echouait silencieusement (rattrapee par l'atomicite-par-personne, sans
     * erreur visible, mais sans jamais generer le bon individuel ni la FIPH).
     * Necessite, comme le premier test de cette classe, de vrais commits
     * (pas de {@code @Transactional}) pour observer le comportement reel.
     */
    @Test
    void ajoutTardifApresValidationGenereReellementLeBonIndividuelEtLaFiph() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());

        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test ajout tardif personne a bord");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortiePrincipalId = objectMapper.readTree(reponseBs).get("id").asLong();

        // Le bon principal est valide AVANT que personneB ne soit ajoutee - c'est precisement
        // le chemin "ajout tardif" (RG-PAB-006) qui declenchait le bug.
        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/viser")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/valider")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        mockMvc.perform(post("/api/bons-sortie/" + bonSortiePrincipalId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneB.getId());
                        }})))
                .andExpect(status().isCreated());

        // Avant le correctif, l'association restait indefiniment sans bon individuel (echec
        // silencieux de la generation) - ici, la generation differee jusqu'apres le commit doit
        // avoir reellement eu lieu.
        BonSortiePersonne association = bonSortiePersonneRepository
                .findByBonSortiePrincipal_IdAndAgent_Id(bonSortiePrincipalId, personneB.getId())
                .orElseThrow();
        assertThat(association.getBonSortieIndividuel()).isNotNull();
        long bonIndividuelId = association.getBonSortieIndividuel().getId();

        mockMvc.perform(get("/api/bons-sortie/" + bonIndividuelId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VISE"));

        mockMvc.perform(post("/api/bons-sortie/" + bonIndividuelId + "/valider")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        assertThat(fiphRepository.findByAgent_Id(personneB.getId())).hasSize(1);
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

    private Utilisateur creerPersonneAvecCompte(String matricule, String nom, String prenom, String identifiant, Service service) {
        Utilisateur utilisateur = nouvelAgent(matricule, nom, prenom, service);
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        return utilisateurRepository.save(utilisateur);
    }
}
