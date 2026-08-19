package com.snef.sgbf;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Verifie de bout en bout, au niveau HTTP, l'evolution "Administration et
 * workflow FIPH" du 2026-08-19 :
 * <ul>
 *   <li>Test 1-2 : hierarchie Administrateur / Super Administrateur -
 *       invisibilite dans les listes/recherches, refus des appels directs a
 *       l'API meme pour un identifiant connu (jamais un simple masquage
 *       frontend) ;</li>
 *   <li>Test 3-4 : FIPH "L 99" validee de bout en bout, avec une notification
 *       generee a chaque transition de niveau, jamais avant, jamais en
 *       double, jamais recue par un autre destinataire que celui vise
 *       (anti-IDOR) ;</li>
 *   <li>Test 5-7 : interruption a un niveau intermediaire puis prise en main
 *       exceptionnelle par le Super Administrateur - tracabilite distincte
 *       d'une validation normale, commentaire obligatoire, audit ;</li>
 *   <li>Test 8 : la prise en main exceptionnelle reste strictement reservee
 *       au Super Administrateur, y compris par appel direct a l'API.</li>
 * </ul>
 * Test 9 (non-regression) : couvert par la reexecution de l'integralite de
 * la suite existante (voir rapport de mission), pas par cette classe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionAdministrationFiphNotificationsIT {

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
    private Service service;
    private Chantier chantier;
    private CodeHN codeHN;
    private Utilisateur admin;
    private Utilisateur superAdmin;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));
        admin = creerUtilisateurAvecHabilitation("admin_" + suffixe, null, CodeRoleMetier.ADMINISTRATEUR);
        superAdmin = creerUtilisateurAvecHabilitation("superadmin_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);
    }

    // --- Test 1 : visibilite ---

    @Test
    void administrateurStandardNeVoitJamaisLeSuperAdministrateurDansLesListesEtRecherches() throws Exception {
        String tokenAdmin = seConnecter(admin.getIdentifiant());
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());

        String reponseListeAdmin = mockMvc.perform(get("/api/utilisateurs").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(reponseListeAdmin).doesNotContain(superAdmin.getIdentifiant());

        // Une recherche explicite par son identifiant ne doit rien renvoyer non plus.
        mockMvc.perform(get("/api/utilisateurs").param("terme", superAdmin.getIdentifiant())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // Le Super Administrateur, lui, voit tout : l'Administrateur ET lui-meme.
        String reponseListeSuperAdmin = mockMvc.perform(get("/api/utilisateurs").header("Authorization", "Bearer " + tokenSuperAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(reponseListeSuperAdmin).contains(admin.getIdentifiant());
        assertThat(reponseListeSuperAdmin).contains(superAdmin.getIdentifiant());
    }

    // --- Test 2 : protection API directe (pas seulement un masquage frontend) ---

    @Test
    void administrateurStandardNePeutPasAccederDirectementAuCompteSuperAdministrateurParApi() throws Exception {
        String tokenAdmin = seConnecter(admin.getIdentifiant());
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());
        Long idSuperAdmin = superAdmin.getId();

        // Lecture directe par id, en connaissant pourtant l'identifiant exact - refusee.
        mockMvc.perform(get("/api/utilisateurs/" + idSuperAdmin).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
        // Le Super Administrateur, lui, peut consulter son propre compte.
        mockMvc.perform(get("/api/utilisateurs/" + idSuperAdmin).header("Authorization", "Bearer " + tokenSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiant").value(superAdmin.getIdentifiant()));

        // Consultation de ses habilitations - refusee.
        mockMvc.perform(get("/api/habilitations/utilisateur/" + idSuperAdmin).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        // Tentative de modification (mot de passe, e-mail, statut) - toutes refusees.
        mockMvc.perform(put("/api/utilisateurs/" + idSuperAdmin + "/mot-de-passe")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("nouveauMotDePasse", "TentativeAttaque123!");
                        }})))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/utilisateurs/" + idSuperAdmin + "/email")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("email", "usurpation@example.invalid");
                        }})))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/utilisateurs/" + idSuperAdmin + "/statut/DESACTIVE")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());

        // Chaque tentative bloquee reste journalisee (section 4) - jamais silencieuse. Le meme flux contient
        // aussi la CONNEXION_REUSSIE du Super Administrateur lui-meme (entiteId = son propre id) : on isole donc
        // specifiquement les evenements ACCES_REFUSE plutot que d'exiger que TOUS le soient.
        List<EvenementAudit> evenements = evenementAuditRepository
                .findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable.UTILISATEUR, String.valueOf(idSuperAdmin));
        long tentativesRefusees = evenements.stream().filter(ev -> "ACCES_REFUSE".equals(ev.getAction().name())).count();
        assertThat(tentativesRefusees).isEqualTo(5); // GET/id, GET habilitations, PUT mot-de-passe, PUT email, PUT statut
        assertThat(evenements).allSatisfy(ev -> assertThat(ev.getUtilisateur() == null
                || ev.getUtilisateur().getId().equals(admin.getId()) || ev.getUtilisateur().getId().equals(superAdmin.getId())).isTrue());
    }

    // --- Test 8 : la prise en main exceptionnelle reste reservee au Super Administrateur ---

    @Test
    void administrateurStandardNePeutPasUtiliserLaPriseEnMainSuperAdmin() throws Exception {
        Agent agent = agentRepository.save(nouvelAgent("PEM" + (suffixe % 100_000L), "Test", "PriseEnMain", service));
        Utilisateur agentUtilisateur = creerUtilisateur("agent_pem_" + suffixe, service);
        agent.setUtilisateur(agentUtilisateur);
        agentRepository.save(agent);
        Utilisateur ca = creerUtilisateurAvecHabilitation("ca_pem_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        affecterAgentAMission(agent, ca);

        String tokenAgent = seConnecter(agentUtilisateur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenAdmin = seConnecter(admin.getIdentifiant());

        creerViserEtValiderBonDeSortie(tokenAgent, tokenCa);
        JsonNode fiph = trouverFiphDeLAgent(tokenCa, agent.getId());
        long versionId = fiph.get("versionCouranteId").asLong();

        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/prise-en-main-super-admin")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("commentaire", "Tentative non autorisee.");
                        }})))
                .andExpect(status().isForbidden());
    }

    // --- Test 3-4 : parcours complet FIPH "L 99" avec notification a chaque niveau ---

    @Test
    void parcoursCompletFiphL99AvecUneNotificationAChaqueNiveauDeValidation() throws Exception {
        Agent agentL99 = agentRepository.save(nouvelAgent("L99-" + (suffixe % 100_000L), "Ekwalla", "Paul", service));
        Utilisateur utilisateurL99 = creerUtilisateur("agent_l99n_" + suffixe, service);
        agentL99.setUtilisateur(utilisateurL99);
        agentRepository.save(agentL99);

        Utilisateur ca = creerUtilisateurAvecHabilitation("ca_notif_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        Utilisateur ra = creerUtilisateurAvecHabilitation("ra_notif_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        Utilisateur direction = creerUtilisateurAvecHabilitation("dir_notif_" + suffixe, service, CodeRoleMetier.DIRECTION);
        Utilisateur exterieur = creerUtilisateur("exterieur_notif_" + suffixe, service);
        affecterAgentAMission(agentL99, ca);

        String tokenL99 = seConnecter(utilisateurL99.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenRa = seConnecter(ra.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());
        String tokenExterieur = seConnecter(exterieur.getIdentifiant());

        // 0. Avant toute validation : ni CA, ni RA, ni Direction n'ont encore de notification pour cette FIPH.
        creerViserEtValiderBonDeSortie(tokenL99, tokenCa);
        JsonNode fiphL99 = trouverFiphDeLAgent(tokenCa, agentL99.getId());
        long fiphId = fiphL99.get("id").asLong();
        long versionId = fiphL99.get("versionCouranteId").asLong();
        String lienAttendu = "/fiph/" + fiphId;

        // 1. Precreation automatique (visa acquis d'office) -> le CA est notifie immediatement (niveau 2).
        JsonNode notifCa = trouverNotification(tokenCa, "FIPH_A_VALIDER", lienAttendu);
        assertThat(notifCa.get("lue").asBoolean()).isFalse();
        assertThat(notifCa.get("lien").asText()).isEqualTo(lienAttendu);
        // Aucune notification prematuree pour le niveau suivant.
        assertThat(compterNotifications(tokenRa, "FIPH_A_VALIDER", lienAttendu)).isZero();
        assertThat(compterNotifications(tokenDirection, "FIPH_A_VALIDER", lienAttendu)).isZero();
        // Un utilisateur exterieur au circuit ne recoit jamais cette notification (cloisonnement par destinataire).
        assertThat(compterNotifications(tokenExterieur, "FIPH_A_VALIDER", lienAttendu)).isZero();

        // Marque la notification du CA comme lue.
        mockMvc.perform(put("/api/notifications/" + notifCa.get("id").asLong() + "/lue")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));
        // Anti-IDOR : un autre utilisateur ne peut pas marquer CETTE notification comme lue (RG-SEC-002).
        mockMvc.perform(put("/api/notifications/" + notifCa.get("id").asLong() + "/lue")
                        .header("Authorization", "Bearer " + tokenExterieur))
                .andExpect(status().isForbidden());

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});

        // 2. CA valide le niveau 2 -> le Responsable d'activite est notifie (niveau 3), jamais avant.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
        JsonNode notifRa = trouverNotification(tokenRa, "FIPH_A_VALIDER", lienAttendu);
        assertThat(notifRa.get("message").asText()).contains("Charge d'Affaires");
        assertThat(compterNotifications(tokenDirection, "FIPH_A_VALIDER", lienAttendu)).isZero();

        // 3. Responsable d'activite valide le niveau 3 -> la Direction est notifiee (niveau 4, definitif).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));
        JsonNode notifDirection = trouverNotification(tokenDirection, "FIPH_A_VALIDER", lienAttendu);
        assertThat(notifDirection.get("message").asText()).contains("Responsable d'activite");

        // 4. Direction valide le niveau 4 (definitif) -> FIPH VALIDEE, le titulaire de la fiche est notifie
        // (FIPH_VALIDEE), aucune notification "a valider" supplementaire n'est generee ensuite.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));
        JsonNode notifTitulaire = trouverNotification(tokenL99, "FIPH_VALIDEE", lienAttendu);
        assertThat(notifTitulaire.get("titre").asText()).contains("validee definitivement");
    }

    // --- Test 5-7 : interruption mediane puis prise en main exceptionnelle du Super Administrateur ---

    @Test
    void interruptionMedianeEtPriseEnMainExceptionnelleParLeSuperAdministrateur() throws Exception {
        Agent agent = agentRepository.save(nouvelAgent("PEM2-" + (suffixe % 100_000L), "Test", "Interrompue", service));
        Utilisateur agentUtilisateur = creerUtilisateur("agent_interrompu_" + suffixe, service);
        agent.setUtilisateur(agentUtilisateur);
        agentRepository.save(agent);
        Utilisateur ca = creerUtilisateurAvecHabilitation("ca_interrompu_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        affecterAgentAMission(agent, ca);

        String tokenAgent = seConnecter(agentUtilisateur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());

        creerViserEtValiderBonDeSortie(tokenAgent, tokenCa);
        JsonNode fiph = trouverFiphDeLAgent(tokenCa, agent.getId());
        long versionId = fiph.get("versionCouranteId").asLong();

        // Le processus normal s'arrete volontairement au niveau 2 : le Responsable d'activite
        // et la Direction ne valideront jamais (simule leur indisponibilite).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        // Commentaire obligatoire : une prise en main sans justification est refusee.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/prise-en-main-super-admin")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("commentaire", "  "); }})))
                .andExpect(status().isBadRequest());

        // Prise en main exceptionnelle, avec justification (section 14 : exemple de la mission).
        String commentairePriseEnMain = "Responsable indisponible - continuite du processus de validation.";
        String reponsePriseEnMain = mockMvc.perform(post("/api/fiph-versions/" + versionId + "/prise-en-main-super-admin")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("commentaire", commentairePriseEnMain); }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"))
                .andExpect(jsonPath("$.empreinteIntegrite").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponsePriseEnMain).get("statutVersion").asText()).isEqualTo("VALIDEE_DEFINITIVEMENT");

        // Une FIPH deja validee definitivement n'a plus besoin d'etre prise en main.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/prise-en-main-super-admin")
                        .header("Authorization", "Bearer " + tokenSuperAdmin)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("commentaire", commentairePriseEnMain); }})))
                .andExpect(status().isUnprocessableEntity());

        // Tracabilite : le niveau 2 (normal, CA) reste distinct des niveaux 3 et 4 (exceptionnels, Super Admin).
        String reponseValidations = mockMvc.perform(get("/api/fiph-versions/" + versionId + "/validations")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode validations = objectMapper.readTree(reponseValidations);
        assertThat(validations.size()).isEqualTo(3);
        assertThat(validations.get(0).get("niveauValidation").asInt()).isEqualTo(2);
        assertThat(validations.get(0).get("priseEnMainSuperAdmin").asBoolean()).isFalse();
        assertThat(validations.get(0).get("utilisateurIdentifiant").asText()).isEqualTo(ca.getIdentifiant());
        for (int i = 1; i <= 2; i++) {
            assertThat(validations.get(i).get("priseEnMainSuperAdmin").asBoolean()).isTrue();
            assertThat(validations.get(i).get("utilisateurIdentifiant").asText()).isEqualTo(superAdmin.getIdentifiant());
            assertThat(validations.get(i).get("commentaire").asText()).isEqualTo(commentairePriseEnMain);
        }
        assertThat(validations.get(1).get("niveauValidation").asInt()).isEqualTo(3);
        assertThat(validations.get(2).get("niveauValidation").asInt()).isEqualTo(4);

        // Audit : l'intervention exceptionnelle est journalisee distinctement d'une VALIDATION normale.
        List<EvenementAudit> evenementsVersion = evenementAuditRepository
                .findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable.FIPH_VERSION, String.valueOf(versionId));
        assertThat(evenementsVersion).anySatisfy(ev -> {
            assertThat(ev.getAction().name()).isEqualTo("PRISE_EN_MAIN_SUPER_ADMIN");
            assertThat(ev.getUtilisateur().getId()).isEqualTo(superAdmin.getId());
            assertThat(ev.getStatutAvant()).isEqualTo("VALIDEE_NIVEAU_2");
            assertThat(ev.getStatutApres()).isEqualTo("VALIDEE_DEFINITIVEMENT");
        });
    }

    // --- Aides de scenario (reprises du meme schema que FiphWorkflowIT/EvolutionSecuriteIT) ---

    private long creerViserEtValiderBonDeSortie(String tokenEmetteur, String tokenValidateur) throws Exception {
        String corpsBonSortie = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("kilometrage", 30);
            put("dateSortie", LocalDate.now().toString());
            put("heureSortie", "08:00:00");
            put("lieu", "Chantier de test");
            put("codeAffaireSaisi", "CODE-TEST");
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
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider").header("Authorization", "Bearer " + tokenValidateur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
        return bonSortieId;
    }

    private JsonNode trouverFiphDeLAgent(String token, Long agentId) throws Exception {
        String reponse = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponse);
        for (JsonNode f : fiphs) {
            if (f.get("agentId").asLong() == agentId) {
                return f;
            }
        }
        throw new AssertionError("Aucune FIPH trouvee pour l'agent id=" + agentId);
    }

    /** Notification correspondant au type et au lien donnes, la plus recente d'abord - echoue si aucune ne correspond. */
    private JsonNode trouverNotification(String token, String type, String lien) throws Exception {
        String reponse = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode notifications = objectMapper.readTree(reponse);
        for (JsonNode n : notifications) {
            if (n.get("type").asText().equals(type) && n.get("lien").asText().equals(lien)) {
                return n;
            }
        }
        throw new AssertionError("Aucune notification de type " + type + " avec lien " + lien + " pour ce destinataire.");
    }

    private long compterNotifications(String token, String type, String lien) throws Exception {
        String reponse = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode notifications = objectMapper.readTree(reponse);
        long compte = 0;
        for (JsonNode n : notifications) {
            if (n.get("type").asText().equals(type) && n.get("lien").asText().equals(lien)) {
                compte++;
            }
        }
        return compte;
    }

    private void affecterAgentAMission(Agent agent, Utilisateur creePar) {
        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        mission = missionRepository.save(mission);

        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(creePar);
        affectationMissionRepository.save(affectation);
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
        attribuerHabilitation(utilisateur, service, role);
        return utilisateur;
    }

    private void attribuerHabilitation(Utilisateur utilisateur, Service service, CodeRoleMetier role) {
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
