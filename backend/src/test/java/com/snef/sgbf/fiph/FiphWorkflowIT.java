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
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Verifie de bout en bout, au niveau HTTP, la chaine complete du workflow
 * FIPH tel qu'evolue le 2026-08-18 : validation d'un bon de sortie -&gt;
 * precreation automatique de la FIPH avec le visa de l'agent titulaire deja
 * acquis (aucune seconde signature ne lui est demandee) -&gt; validation par
 * le Charge d'Affaires ou la personne habilitee ("Responsable designe",
 * niveau 2) -&gt; validation par le Responsable d'activite (niveau 3) -&gt;
 * validation finale par le Directeur (DG, niveau 4) -&gt; FIPH definitivement
 * validee (empreinte d'integrite, RG-VER-006) -&gt; correction post-validation
 * via une nouvelle version entierement re-validee (RG-VER-001 a 007).
 *
 * <p>Chaque acteur s'authentifie reellement via {@code POST /api/auth/login}
 * (authentification simple, voir {@code AuthentificationSimpleIT}).
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
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private AffectationMissionRepository affectationMissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Chantier chantier;
    private CodeHN codeHN;
    private Utilisateur emetteurAgent;
    private Utilisateur emetteurUtilisateur;
    private Utilisateur ca1;
    private Utilisateur ca2;
    private Utilisateur responsableActivite;
    private Utilisateur direction;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        emetteurAgent = creerPersonneAvecCompte("MAT" + suffixe, "Test", "Emetteur", "emetteur" + suffixe, service);
        emetteurUtilisateur = emetteurAgent;

        ca1 = creerUtilisateurAvecHabilitation("ca1_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        ca2 = creerUtilisateurAvecHabilitation("ca2_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        responsableActivite = creerUtilisateurAvecHabilitation("ra_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        direction = creerUtilisateurAvecHabilitation("direction_" + suffixe, service, CodeRoleMetier.DIRECTION);

        affecterAgentAMission(emetteurAgent, ca1);
    }

    /**
     * Parcours complet, utilisant comme exemple metier la fiche de l'agent
     * "L99" (Tests 1, 2, 3, 6, 7 et 8 de la mission d'evolution du workflow
     * FIPH) :
     * <ol>
     *   <li>bon de sortie cree, vise, valide par CA1 ;</li>
     *   <li>FIPH L99 precreee automatiquement, deja au statut SIGNEE - le
     *       visa de l'agent est acquis d'office ; une seconde signature est
     *       refusee par le serveur ;</li>
     *   <li>CA2 complete le pointage (heures) ;</li>
     *   <li>CA2 - ayant complete le pointage - ne peut pas valider le
     *       niveau 2 lui-meme (RG-HAB-004 toujours appliquee) ;</li>
     *   <li>CA1 - qui a pourtant valide le bon de sortie declencheur - PEUT
     *       valider le niveau 2 (nouvelle exemption RG-HAB-004 pour une
     *       precreation automatique, evolution du 2026-08-18) ;</li>
     *   <li>Responsable d'activite valide le niveau 3, puis Direction (DG)
     *       valide le niveau 4 - FIPH definitivement validee, empreinte
     *       calculee, pointage fige ;</li>
     *   <li>l'historique d'audit retrace chaque etape individuellement ;</li>
     *   <li>une correction post-validation cree une nouvelle version,
     *       entierement re-validee, sans alterer la version 1 ni son
     *       empreinte.</li>
     * </ol>
     */
    @Test
    void parcoursCompletFipheL99AvecVisaAutomatiqueEtCorrectionPostValidation() throws Exception {
        Utilisateur agentL99 = creerPersonneAvecCompte("L99", "Ekwalla", "Paul", "agent_l99_" + suffixe, service);
        Utilisateur utilisateurL99 = agentL99;
        affecterAgentAMission(agentL99, ca1);

        String tokenL99 = seConnecter(utilisateurL99.getIdentifiant());
        String tokenCa1 = seConnecter(ca1.getIdentifiant());
        String tokenCa2 = seConnecter(ca2.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());

        // 1. Bon de sortie de l'agent L99 : cree, vise, valide par CA1.
        creerViserEtValiderBonDeSortie(tokenL99, tokenCa1);

        // 2. La FIPH L99 doit avoir ete precreee automatiquement, DEJA SIGNEE
        // (visa de l'agent acquis d'office).
        JsonNode fiphL99 = trouverFiphDeLAgent(tokenCa1, agentL99.getId());
        long fiphId = fiphL99.get("id").asLong();
        assertThat(fiphL99.get("origine").asText()).isEqualTo("BON_SORTIE");
        assertThat(fiphL99.get("statut").asText()).isEqualTo("SIGNEE");
        long versionId = fiphL99.get("versionCouranteId").asLong();

        // Aucune seconde signature ne doit etre demandee a l'agent : le
        // serveur refuse l'appel (la version n'est plus dans un etat
        // "signable", RG-VER-001).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/signer").header("Authorization", "Bearer " + tokenL99))
                .andExpect(status().isUnprocessableEntity());

        // 3. CA2 complete le pointage (heures normales/supplementaires -
        // jamais deduites automatiquement d'un bon de sortie).
        String corpsCompletion = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("datePointage", LocalDate.now().toString());
            put("heuresNormales", 8);
            put("heuresSup", 0);
        }});
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa2)
                        .contentType("application/json").content(corpsCompletion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("SIGNEE")) // pas de transition automatique depuis SIGNEE
                .andExpect(jsonPath("$.totalHN").value(8));

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});

        // 4. CA2 a complete le pointage : il ne peut pas valider lui-meme (RG-HAB-004).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa2)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isForbidden());

        // 5. CA1 - qui a valide le bon de sortie declencheur mais n'a rien
        // complete lui-meme - PEUT valider directement le niveau 2, sans
        // etape de signature ni de soumission prealable.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        // 6. Validation niveau 3 (Responsable d'activite).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));

        // 7. Validation niveau 4 (Direction/DG) - definitive, empreinte calculee (RG-VER-006).
        String reponseV4 = mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"))
                .andExpect(jsonPath("$.empreinteIntegrite").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String empreinteV1 = objectMapper.readTree(reponseV4).get("empreinteIntegrite").asText();

        // 8. RG-VER-001 : une version definitivement validee est figee.
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsCompletion))
                .andExpect(status().isUnprocessableEntity());

        // 9. Historique : chaque etape reste tracee individuellement
        // (precreation, visa automatique, complement, 3 validations).
        String reponseHistorique = mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode historique = objectMapper.readTree(reponseHistorique);
        assertThat(historique.isArray()).isTrue();
        List<String> actions = new ArrayList<>();
        historique.forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).contains("FIPH_AUTO_GENEREE", "SIGNATURE", "COMPLEMENT", "VALIDATION");
        assertThat(actions.stream().filter("VALIDATION"::equals).count()).isEqualTo(3);

        // 10. Correction post-validation (RG-VER-001 a 007) : nouvelle
        // version, motif obligatoire, contenu copie, entierement re-validee.
        String corpsNouvelleVersion = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("motifModification", "Test L99 : correction post-validation (heures manquantes)");
        }});
        String reponseNouvelleVersion = mockMvc.perform(post("/api/fiph-versions/fiph/" + fiphId + "/nouvelle-version")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsNouvelleVersion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroVersion").value(2))
                .andExpect(jsonPath("$.statutVersion").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        JsonNode nouvelleVersionJson = objectMapper.readTree(reponseNouvelleVersion);
        long versionId2 = nouvelleVersionJson.get("id").asLong();
        assertThat(nouvelleVersionJson.get("pointages").size()).isEqualTo(1); // RG-VER-003 : contenu copie

        String corpsCorrection = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("datePointage", LocalDate.now().toString());
            put("heuresNormales", 6);
            put("heuresSup", 2);
        }});
        mockMvc.perform(put("/api/fiph-versions/" + versionId2 + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa2)
                        .contentType("application/json").content(corpsCorrection))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHS").value(2));

        mockMvc.perform(post("/api/fiph-versions/" + versionId2 + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
        mockMvc.perform(post("/api/fiph-versions/" + versionId2 + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));
        String reponseV2Finale = mockMvc.perform(post("/api/fiph-versions/" + versionId2 + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"))
                .andExpect(jsonPath("$.empreinteIntegrite").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String empreinteV2 = objectMapper.readTree(reponseV2Finale).get("empreinteIntegrite").asText();
        assertThat(empreinteV2).isNotEqualTo(empreinteV1); // contenu different -> empreinte differente

        // La version 1 reste consultable, intacte, avec sa propre empreinte (RG-VER-003).
        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"))
                .andExpect(jsonPath("$.empreinteIntegrite").value(empreinteV1));
    }

    /**
     * Test 4 (mission d'evolution du workflow) : la personne habilitee
     * ("Responsable designe") peut valider le niveau 2 au meme titre que le
     * Charge d'Affaires.
     */
    @Test
    void personneHabiliteePeutValiderLeNiveau2AuLieuDuChargeDAffaires() throws Exception {
        Utilisateur personneHabilitee = creerUtilisateurAvecHabilitation("ph_" + suffixe, service, CodeRoleMetier.PERSONNE_HABILITEE);
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCa1 = seConnecter(ca1.getIdentifiant());
        String tokenPh = seConnecter(personneHabilitee.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());

        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa1);
        JsonNode fiph = trouverFiphDeLAgent(tokenCa1, emetteurAgent.getId());
        long versionId = fiph.get("versionCouranteId").asLong();

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});
        // La personne habilitee (et non le Charge d'Affaires) valide le niveau 2 directement.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenPh)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));
    }

    /**
     * Test 5 (mission d'evolution du workflow) : lorsque le Charge
     * d'Affaires et le Responsable d'activite sont la meme personne, elle
     * peut effectuer les deux validations successives (niveau 2 puis niveau
     * 3) sans blocage artificiel, chacune restant tracee individuellement.
     */
    @Test
    void chargeAffairesEgalResponsableActiviteValideLesDeuxNiveauxSansBlocage() throws Exception {
        Utilisateur caEtRa = creerUtilisateur("ca_ra_" + suffixe, service);
        attribuerHabilitation(caEtRa, service, CodeRoleMetier.CHARGE_AFFAIRES);
        attribuerHabilitation(caEtRa, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);

        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCaRa = seConnecter(caEtRa.getIdentifiant());

        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCaRa);
        JsonNode fiph = trouverFiphDeLAgent(tokenCaRa, emetteurAgent.getId());
        long versionId = fiph.get("versionCouranteId").asLong();

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCaRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));
        // La MEME personne valide ensuite le niveau 3 : aucun blocage.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenCaRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_3"));

        // Les deux validations restent tracees individuellement (2 lignes distinctes).
        String reponseValidations = mockMvc.perform(get("/api/fiph-versions/" + versionId + "/validations")
                        .header("Authorization", "Bearer " + tokenCaRa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode validations = objectMapper.readTree(reponseValidations);
        assertThat(validations.size()).isEqualTo(2);
        assertThat(validations.get(0).get("niveauValidation").asInt()).isEqualTo(2);
        assertThat(validations.get(1).get("niveauValidation").asInt()).isEqualTo(3);
        assertThat(validations.get(0).get("utilisateurIdentifiant").asText())
                .isEqualTo(validations.get(1).get("utilisateurIdentifiant").asText());
    }

    /** Test 9 (mission d'evolution du workflow) : controle des droits - un utilisateur sans habilitation sur le service ne peut pas valider. */
    @Test
    void utilisateurNonHabiliteNePeutPasValiderLaFiph() throws Exception {
        Utilisateur exterieur = creerUtilisateur("exterieur_" + suffixe, service);
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCa1 = seConnecter(ca1.getIdentifiant());
        String tokenExterieur = seConnecter(exterieur.getIdentifiant());

        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa1);
        JsonNode fiph = trouverFiphDeLAgent(tokenCa1, emetteurAgent.getId());
        long versionId = fiph.get("versionCouranteId").asLong();

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenExterieur)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isForbidden());
    }

    // --- Aides de scenario ---

    private long creerViserEtValiderBonDeSortie(String tokenEmetteur, String tokenValidateur) throws Exception {
        String corpsBonSortie = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
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

    private void affecterAgentAMission(Utilisateur agent, Utilisateur creePar) {
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

    /** Authentification simple (identifiant + mot de passe, sans seconde etape - decision du 2026-08-17). */
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
        habilitation.setCreePar(utilisateur); // auto-reference acceptable en test (bootstrap)
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

    /** Personne avec compte applicatif ET identite RH complete en une seule creation (evolution du 2026-08-19, unification Agent/Utilisateur). */
    private Utilisateur creerPersonneAvecCompte(String matricule, String nom, String prenom, String identifiant, Service service) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        utilisateur.setService(service);
        return utilisateurRepository.save(utilisateur);
    }
}
