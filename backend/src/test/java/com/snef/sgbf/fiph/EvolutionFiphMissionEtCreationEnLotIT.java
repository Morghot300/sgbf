package com.snef.sgbf.fiph;

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
import com.snef.sgbf.support.IdentifiantsTest;
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
 * Verifie, au niveau HTTP, l'evolution du 2026-08-27 (brief "Evolution du
 * module FIPH : creation, consultation, mission, droits par service et
 * refonte ergonomique") :
 * <ul>
 *   <li>creation manuelle EN LOT par cases a cocher (section 2-3-4-14) : un
 *       echec metier pour un agent (periode chevauchante) n'empeche jamais la
 *       creation pour les autres agents du meme lot ;</li>
 *   <li>champ Mission explicite sur la FIPH (section 6-7-8) : Code Mission
 *       invalide refuse clairement, Code Mission valide expose le nom
 *       textuel, avertissement non bloquant si l'agent n'a aucune affectation
 *       connue sur cette mission ;</li>
 *   <li>liste du personnel d'un service pour la creation FIPH (section 2-3),
 *       avec le meme perimetre que la creation elle-meme (CA/PH du service,
 *       RH/Super Administrateur a perimetre global) ;</li>
 *   <li>confirmation que le Responsable d'Activite reste en LECTURE SEULE
 *       (aucune extension de RG-FIPH-010 - decision confirmee explicitement
 *       pour ce brief) ;</li>
 *   <li>filtres de regroupement par sous-menu ({@code statuts}, section 16-18)
 *       et par mission (section 25) sur la liste des FIPH.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionFiphMissionEtCreationEnLotIT {

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
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service littoral;
    private Service centre;
    private Chantier chantier;
    private CodeHN codeHN;

    private Utilisateur caLittoral;
    private Utilisateur raLittoral;
    private Utilisateur rh;
    private Utilisateur superAdmin;

    private Utilisateur agent1;
    private Utilisateur agent2;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();

        littoral = serviceRepository.save(nouveauService("LIT" + suffixe, "Littoral " + suffixe));
        centre = serviceRepository.save(nouveauService("CTR" + suffixe, "Centre " + suffixe));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        caLittoral = creerUtilisateurAvecHabilitation("ca_lit_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        raLittoral = creerUtilisateurAvecHabilitation("ra_lit_" + suffixe, littoral, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        rh = creerUtilisateurAvecHabilitation("rh_" + suffixe, null, CodeRoleMetier.RH);
        superAdmin = creerUtilisateurAvecHabilitation("sa_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);

        agent1 = utilisateurRepository.save(nouvelAgent("AG1-" + suffixe, "Ateba", "Alice", littoral));
        agent2 = utilisateurRepository.save(nouvelAgent("AG2-" + suffixe, "Bikoro", "Bruno", littoral));
    }

    // =========================================================================================
    // Creation manuelle en lot (section 2-3-4-14)
    // =========================================================================================

    @Test
    void creationEnLot_deuxAgents_reussitPourLesDeux() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId(), agent2.getId()));
                            put("dateDebut", LocalDate.of(2026, 5, 4).toString());
                            put("dateFin", LocalDate.of(2026, 5, 10).toString());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode racine = objectMapper.readTree(reponse);
        assertThat(racine.get("creees")).hasSize(2);
        assertThat(racine.get("echecs")).isEmpty();
    }

    @Test
    void creationEnLot_unAgentEnConflit_neBloquePasLesAutres() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        // agent1 a deja une FIPH sur cette periode (creee au prealable, hors du lot).
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 6, 1).toString());
                            put("dateFin", LocalDate.of(2026, 6, 7).toString());
                        }})))
                .andExpect(status().isCreated());

        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            // agent1 chevauche la FIPH deja creee ; agent2 est libre sur cette periode.
                            put("agentIds", List.of(agent1.getId(), agent2.getId()));
                            put("dateDebut", LocalDate.of(2026, 6, 3).toString());
                            put("dateFin", LocalDate.of(2026, 6, 9).toString());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode racine = objectMapper.readTree(reponse);
        assertThat(racine.get("creees")).hasSize(1);
        assertThat(racine.get("creees").get(0).get("agentId").asLong()).isEqualTo(agent2.getId());
        assertThat(racine.get("echecs")).hasSize(1);
        assertThat(racine.get("echecs").get(0).get("agentId").asLong()).isEqualTo(agent1.getId());
        assertThat(racine.get("echecs").get(0).get("motif").asText()).contains("RG-FIPH-002");
    }

    /**
     * Le client recoit un 403 global pour tout le lot des qu'un identifiant hors perimetre s'y
     * trouve - meme si d'autres agents du meme lot etaient legitimes (voir la Javadoc de
     * {@code FiphService#creerManuelle}, qui documente aussi le rollback transactionnel reel en
     * production). Ce rollback n'est pas re-verifiable ici par une lecture HTTP ulterieure dans le
     * MEME test : cette classe est elle-meme {@code @Transactional} (nettoyage automatique des
     * donnees de test), donc la lecture y verrait de toute facon les ecritures non "commitees" de
     * sa PROPRE transaction - une limite du harnais de test, pas du comportement reel de l'API.
     */
    @Test
    void creationEnLot_agentHorsPerimetre_refuseLaTotaliteDuLot() throws Exception {
        Utilisateur agentCentre = utilisateurRepository.save(nouvelAgent("AGC-" + suffixe, "Ekwalla", "Paul", centre));
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            // agent1 (Littoral, legitime) + agentCentre (hors perimetre du CA Littoral).
                            put("agentIds", List.of(agent1.getId(), agentCentre.getId()));
                            put("dateDebut", LocalDate.of(2026, 7, 6).toString());
                        }})))
                .andExpect(status().isForbidden());
    }

    // =========================================================================================
    // Champ Mission explicite (section 6-7-8, 15)
    // =========================================================================================

    @Test
    void creationAvecCodeMissionInexistant_refusee() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 5, 18).toString());
                            put("missionId", 9_999_999L);
                        }})))
                .andExpect(status().isNotFound());
    }

    @Test
    void creationAvecMissionValide_exposeCodeEtNomTextuel_etAvertitSiAucuneAffectation() throws Exception {
        Mission mission = nouvelleMission();
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 5, 25).toString());
                            put("missionId", mission.getId());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creees[0].missionId").value(mission.getId()))
                .andExpect(jsonPath("$.creees[0].missionCodeHN").value(codeHN.getCode()))
                .andExpect(jsonPath("$.creees[0].missionChantierLibelle").value(chantier.getLibelle()))
                .andReturn().getResponse().getContentAsString();
        // agent1 n'a ete affecte a AUCUNE mission dans ce test : avertissement non bloquant attendu.
        assertThat(objectMapper.readTree(reponse).get("creees").get(0).get("avertissementMission").asText())
                .contains(codeHN.getCode());
    }

    @Test
    void creationAvecMissionValideEtAffectationReelle_aucunAvertissement() throws Exception {
        Mission mission = nouvelleMission();
        affecterAgentAMission(agent1, mission, caLittoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 5, 25).toString());
                            put("missionId", mission.getId());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponse).get("creees").get(0).get("avertissementMission").isNull()).isTrue();
    }

    // =========================================================================================
    // Personnel du service pour la creation FIPH (section 2-3) - meme perimetre que la creation
    // =========================================================================================

    @Test
    void personnelDuService_caDuMemeService_reussit() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        // Le service Littoral compte 4 personnes dans ce jeu de donnees : caLittoral et raLittoral
        // (comptes applicatifs, egalement rattaches a ce service) + agent1 et agent2 (sans compte).
        mockMvc.perform(get("/api/fiph/personnel-service/" + littoral.getId()).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.id == " + agent1.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + agent2.getId() + ")]").exists());
    }

    @Test
    void personnelDuService_caDUnAutreService_refuse() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(get("/api/fiph/personnel-service/" + centre.getId()).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isForbidden());
    }

    @Test
    void personnelDuService_rhEtSuperAdmin_accedentAnimporteQuelService() throws Exception {
        String tokenRh = seConnecter(rh.getIdentifiant());
        mockMvc.perform(get("/api/fiph/personnel-service/" + littoral.getId()).header("Authorization", "Bearer " + tokenRh))
                .andExpect(status().isOk());
        String tokenSa = seConnecter(superAdmin.getIdentifiant());
        mockMvc.perform(get("/api/fiph/personnel-service/" + centre.getId()).header("Authorization", "Bearer " + tokenSa))
                .andExpect(status().isOk());
    }

    /** Le Responsable d'Activite reste en LECTURE SEULE (decision confirmee explicitement pour ce brief - aucune extension de RG-FIPH-010). */
    @Test
    void personnelDuService_responsableActivite_refuse() throws Exception {
        String tokenRa = seConnecter(raLittoral.getIdentifiant());
        mockMvc.perform(get("/api/fiph/personnel-service/" + littoral.getId()).header("Authorization", "Bearer " + tokenRa))
                .andExpect(status().isForbidden());
    }

    @Test
    void creationManuelle_responsableActivite_refuseeParLeControleDeRole() throws Exception {
        String tokenRa = seConnecter(raLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 5, 25).toString());
                        }})))
                .andExpect(status().isForbidden());
    }

    // =========================================================================================
    // Filtres de regroupement par sous-menu et par mission (section 16-18, 25)
    // =========================================================================================

    @Test
    void filtreStatuts_regroupePlusieursStatutsReellementAtteignables() throws Exception {
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 8, 3).toString());
                        }})))
                .andExpect(status().isCreated());

        // Categorie "Brouillons" (BROUILLON + EN_COMPLEMENT) : une FIPH fraichement creee est SIGNEE,
        // donc absente de ce regroupement, mais presente dans "Visees" (SIGNEE + SOUMISE).
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa)
                        .param("statuts", "BROUILLON", "EN_COMPLEMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa)
                        .param("statuts", "SIGNEE", "SOUMISE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void filtreMission_rechercheParCodeOuLibelle() throws Exception {
        Mission mission = nouvelleMission();
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent1.getId()));
                            put("dateDebut", LocalDate.of(2026, 8, 10).toString());
                            put("missionId", mission.getId());
                        }})))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa).param("mission", codeHN.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa).param("mission", "AUCUNE-CORRESPONDANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- Aides ---

    private void affecterAgentAMission(Utilisateur agent, Mission mission, Utilisateur creePar) {
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

    private Utilisateur creerUtilisateurAvecHabilitation(String identifiant, Service service, CodeRoleMetier role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        utilisateur.setService(service);
        utilisateur = utilisateurRepository.save(utilisateur);
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

    /** Personne du referentiel SANS compte applicatif (evolution du 2026-08-19, unification Agent/Utilisateur). */
    private Utilisateur nouvelAgent(String matricule, String nom, String prenom, Service service) {
        Utilisateur agent = new Utilisateur();
        agent.setMatricule(matricule);
        agent.setNom(nom);
        agent.setPrenom(prenom);
        agent.setService(service);
        return agent;
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
}
