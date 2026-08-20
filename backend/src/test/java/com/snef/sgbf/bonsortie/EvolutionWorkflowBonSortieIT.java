package com.snef.sgbf.bonsortie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.snef.sgbf.notification.entity.TypeNotification;
import com.snef.sgbf.notification.repository.NotificationRepository;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
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
 * Verifie de bout en bout, au niveau HTTP, l'evolution "Correctifs workflow
 * visa/validation, notifications, selection des personnes a bord" du
 * 2026-08-19 :
 * <ul>
 *   <li>Lot 1 - auto-validation autorisee (decision confirmee) ;</li>
 *   <li>Lot 2 - absence d'affectation : avertissement, jamais un blocage
 *       (decision confirmee), reproduisant precisement le scenario reel du
 *       bon de sortie "#8" (agent sans aucune affectation) qui a motive
 *       cette evolution ;</li>
 *   <li>Lot 3 - notifications generees a chaque etape du workflow, jamais
 *       avant l'evenement reel, jamais accessibles a un autre destinataire ;</li>
 *   <li>Lot 4 - selection multiple des personnes a bord : perimetre calcule
 *       cote serveur, exclusions, ajout en lot transactionnel et idempotent.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionWorkflowBonSortieIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Service autreService;
    private Utilisateur ca;
    private Utilisateur caAutreService;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));

        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    /** Lot 1 : un Charge d'Affaires qui a lui-meme vise son propre bon (car aussi agent titulaire) peut le valider. */
    @Test
    void lot1_autoValidationAutorisee() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        long bonId = creerBonDeSortie(tokenCa, "MS-AUTO-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParIdentifiant").value(ca.getIdentifiant()));

        // Le meme utilisateur, en tant que Charge d'Affaires, valide le bon qu'il a lui-meme vise en tant qu'agent.
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"))
                .andExpect(jsonPath("$.valideParIdentifiant").value(ca.getIdentifiant()));
    }

    /**
     * Lot 2 : reproduit precisement le scenario reel du bon de sortie "#8" -
     * un agent SANS AUCUNE affectation valide malgre tout, avec un
     * avertissement actionnable (jamais un blocage, decision confirmee),
     * visible avant meme la validation.
     */
    @Test
    void lot2_agentSansAffectation_validationReussieAvecAvertissement() throws Exception {
        Utilisateur agentSansAffectation = creerUtilisateur("jean_jores_" + suffixe, service);
        String tokenAgent = seConnecter(agentSansAffectation.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());

        long bonId = creerBonDeSortie(tokenAgent, "MS-004");

        // L'avertissement est deja visible AVANT la validation (message actionnable, Lot 2 point 3).
        mockMvc.perform(get("/api/bons-sortie/" + bonId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avertissementAffectation").isNotEmpty());

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());

        // Plus jamais 422/RG-FIPH-020 : la validation reussit malgre l'absence d'affectation.
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"))
                .andExpect(jsonPath("$.affectationMissionId").doesNotExist())
                .andExpect(jsonPath("$.avertissementAffectation").isNotEmpty());

        // La FIPH generee automatiquement existe malgre tout - la ligne de pointage est retombee sur le
        // service de l'agent plutot que de violer la contrainte CHECK (ni affectation, ni service nuls a la fois).
        String reponseFiph = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponseFiph);
        boolean fiphTrouvee = false;
        for (JsonNode f : fiphs) {
            if (f.get("agentId").asLong() == agentSansAffectation.getId()) {
                fiphTrouvee = true;
            }
        }
        assertThat(fiphTrouvee).isTrue();
    }

    /** Lot 3 : le Charge d'Affaires du service est notifie des qu'un bon est vise, jamais avant. */
    @Test
    void lot3_notificationAValiderDeclencheeUniquementApresLeVisa() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_notif_" + suffixe, service);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-NOTIF-" + (suffixe % 100_000L));

        assertThat(notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(ca.getId())).isEmpty();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());

        var notifsCa = notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(ca.getId());
        assertThat(notifsCa).hasSize(1);
        assertThat(notifsCa.get(0).getType()).isEqualTo(TypeNotification.BON_SORTIE_A_VALIDER);
        assertThat(notifsCa.get(0).getLien()).isEqualTo("/bons-sortie/" + bonId);

        // La validation notifie a son tour le titulaire.
        String tokenCa = seConnecter(ca.getIdentifiant());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());
        var notifsAgent = notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(agent.getId());
        assertThat(notifsAgent).anyMatch(n -> n.getType() == TypeNotification.BON_SORTIE_VALIDE);
    }

    /** Lot 3 : un Charge d'Affaires d'un AUTRE service n'est jamais notifie (anti-IDOR, cloisonnement par destinataire). */
    @Test
    void lot3_aucuneNotificationCroisee() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_cloison_" + suffixe, service);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-CLOISON-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(caAutreService.getId())).isEmpty();
    }

    /** Lot 4 : perimetre des agents eligibles calcule cote serveur (service du titulaire), exclusions correctes. */
    @Test
    void lot4_agentsEligibles_perimetreEtExclusions() throws Exception {
        Utilisateur titulaire = creerUtilisateur("titulaire_" + suffixe, service);
        Utilisateur eligible = creerUtilisateur("eligible_" + suffixe, service);
        Utilisateur autreServiceAgent = creerUtilisateur("horsservice_" + suffixe, autreService);
        Utilisateur desactive = creerUtilisateur("desactive_" + suffixe, service);
        desactive.setStatutCompte(StatutCompte.DESACTIVE);
        utilisateurRepository.save(desactive);

        String tokenTitulaire = seConnecter(titulaire.getIdentifiant());
        long bonId = creerBonDeSortie(tokenTitulaire, "MS-ELIG-" + (suffixe % 100_000L));

        String reponse = mockMvc.perform(get("/api/bons-sortie/" + bonId + "/agents-eligibles")
                        .header("Authorization", "Bearer " + tokenTitulaire))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode eligibles = objectMapper.readTree(reponse);

        List<Long> idsEligibles = eligibles.findValuesAsText("id").stream().map(Long::parseLong).toList();
        assertThat(idsEligibles).contains(eligible.getId());
        assertThat(idsEligibles).doesNotContain(titulaire.getId()); // jamais le titulaire lui-meme
        assertThat(idsEligibles).doesNotContain(autreServiceAgent.getId()); // jamais hors service (RG-SEC-002)
        assertThat(idsEligibles).doesNotContain(desactive.getId()); // jamais un compte desactive
    }

    /** Lot 4 : tentative de contournement du perimetre par manipulation - un bon d'un AUTRE service reste inaccessible (IDOR). */
    @Test
    void lot4_agentsEligibles_refuseHorsPerimetre() throws Exception {
        Utilisateur titulaireAutreService = creerUtilisateur("titulaire_autre_" + suffixe, autreService);
        String tokenTitulaireAutre = seConnecter(titulaireAutreService.getIdentifiant());
        long bonAutreServiceId = creerBonDeSortie(tokenTitulaireAutre, "MS-IDOR-" + (suffixe % 100_000L));

        String tokenCa = seConnecter(ca.getIdentifiant());
        mockMvc.perform(get("/api/bons-sortie/" + bonAutreServiceId + "/agents-eligibles")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isForbidden());
    }

    /** Lot 4 : ajout en lot transactionnel et idempotent, contrainte d'unicite respectee. */
    @Test
    void lot4_ajoutEnLot_transactionnelEtIdempotent() throws Exception {
        Utilisateur titulaire = creerUtilisateur("titulaire_lot_" + suffixe, service);
        Utilisateur passagerA = creerUtilisateur("passagerA_" + suffixe, service);
        Utilisateur passagerB = creerUtilisateur("passagerB_" + suffixe, service);
        String tokenTitulaire = seConnecter(titulaire.getIdentifiant());
        long bonId = creerBonDeSortie(tokenTitulaire, "MS-LOT-" + (suffixe % 100_000L));

        String corpsLot = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("agentIds", List.of(passagerA.getId(), passagerB.getId()));
        }});
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/personnes-a-bord/lot")
                        .header("Authorization", "Bearer " + tokenTitulaire)
                        .contentType("application/json").content(corpsLot))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        // Rejeu exact du meme lot : idempotent, aucun doublon, aucune erreur (silencieusement ignore).
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/personnes-a-bord/lot")
                        .header("Authorization", "Bearer " + tokenTitulaire)
                        .contentType("application/json").content(corpsLot))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(0));

        String reponsePersonnes = mockMvc.perform(get("/api/bons-sortie/" + bonId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenTitulaire))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponsePersonnes)).hasSize(2); // toujours 2, jamais 4
    }

    // --- Aides ---

    private long creerBonDeSortie(String token, String codeAffaire) throws Exception {
        String reponse = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", codeAffaire);
                            put("motifSortie", "Test evolution workflow bon de sortie");
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
}
