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
import com.snef.sgbf.support.IdentifiantsTest;
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
 * Evolution du calcul de la periode de la FIPH (2026-08-21, brief "Evolution
 * du calcul de la periode de la FIPH") : la date de debut vient
 * automatiquement du Bon de Sortie declencheur (jamais ressaisie, jamais
 * modifiable) ; la date de fin est definie librement par le Charge
 * d'Affaires/la personne habilitee, potentiellement sur plusieurs semaines ;
 * les jours de pointage sont generes automatiquement pour toute la periode,
 * uniquement la ou l'agent est reellement affecte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiphPeriodeIT {

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
    private Service autreService;
    private Chantier chantier;
    private CodeHN codeHN;
    private Utilisateur emetteurAgent;
    private Utilisateur ca;
    private Utilisateur personneHabilitee;
    private Utilisateur responsableActivite;
    private Utilisateur direction;
    private Utilisateur caAutreService;
    private LocalDate debut;

    @BeforeEach
    void construireJeuDeDonnees() throws Exception {
        suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        emetteurAgent = creerPersonneAvecCompte("MAT" + suffixe, "Test", "Emetteur", "emetteur" + suffixe, service);
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        personneHabilitee = creerUtilisateurAvecHabilitation("ph_" + suffixe, service, CodeRoleMetier.PERSONNE_HABILITEE);
        responsableActivite = creerUtilisateurAvecHabilitation("ra_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        direction = creerUtilisateurAvecHabilitation("direction_" + suffixe, service, CodeRoleMetier.DIRECTION);
        caAutreService = creerUtilisateurAvecHabilitation("ca_autre_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);

        // Affectation ouverte (jamais de date de fin) a partir d'il y a 5 jours : couvre
        // largement toute date utilisee dans ces tests, y compris plusieurs semaines a venir.
        affecterAgentAMission(emetteurAgent, ca);

        debut = LocalDate.now();
    }

    /** Test 1 : la date de debut de la FIPH est automatiquement celle du Bon de Sortie, jamais une autre. */
    @Test
    void dateDebutEstAutomatiquementCelleDuBonDeSortie() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);

        JsonNode fiph = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId());
        assertThat(fiph.get("dateDebutPeriode").asText()).isEqualTo(debut.toString());

        long versionId = fiph.get("versionCouranteId").asLong();
        JsonNode version = obtenirVersion(tokenCa, versionId);
        assertThat(version.get("dateDebutPeriode").asText()).isEqualTo(debut.toString());
        assertThat(version.get("dateFinPeriode").isNull()).isTrue(); // non encore definie (etape 3 du brief)
    }

    /** Test 2 : le Charge d'Affaires definit la date de fin, la periode et les jours sont calcules automatiquement. */
    @Test
    void caDefinitLaDateDeFinEtLesJoursSontCalcules() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        LocalDate fin = debut.plusDays(2); // exemple du brief : 3 jours (jeudi/vendredi/samedi ou equivalent)
        JsonNode version = definirDateFin(tokenCa, versionId, fin, null, status().isOk());

        assertThat(version.get("dateFinPeriode").asText()).isEqualTo(fin.toString());
        assertThat(version.get("pointages").size()).isEqualTo(3);
        assertThat(version.get("avertissementPeriode").isNull()).isTrue(); // agent affecte les 3 jours
    }

    /** Test 3 : meme scenario avec une Personne habilitee du meme service. */
    @Test
    void personneHabiliteePeutAussiDefinirLaDateDeFin() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenPh = seConnecter(personneHabilitee.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        LocalDate fin = debut.plusDays(1);
        JsonNode version = definirDateFin(tokenPh, versionId, fin, null, status().isOk());
        assertThat(version.get("pointages").size()).isEqualTo(2);
    }

    /** Test 4 : une date de fin anterieure a la date de debut est refusee. */
    @Test
    void dateFinAnterieureALaDateDeDebutEstRefusee() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", debut.minusDays(1).toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-FIPH-030"));
    }

    /**
     * Test 5 : aucun endpoint n'expose l'ecriture de la date de debut - elle
     * reste rigoureusement identique quel que soit le nombre d'operations
     * effectuees sur la periode (definition, extension, retrecissement).
     */
    @Test
    void dateDebutResteInchangeeMalgrePlusieursModificationsDeLaPeriode() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long fiphId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("id").asLong();
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        definirDateFin(tokenCa, versionId, debut.plusDays(2), null, status().isOk());
        definirDateFin(tokenCa, versionId, debut.plusDays(5), null, status().isOk());
        definirDateFin(tokenCa, versionId, debut.plusDays(1), null, status().isOk());

        String reponse = mockMvc.perform(get("/api/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponse).get("dateDebutPeriode").asText()).isEqualTo(debut.toString());
    }

    /** Test 6 : une periode couvrant plusieurs semaines ISO genere correctement tous les jours et les totaux. */
    @Test
    void periodeMultiSemainesGenereTousLesJoursEtLesTotaux() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        LocalDate fin = debut.plusDays(11); // 12 jours, deux semaines ISO ou plus selon le jour de depart
        JsonNode version = definirDateFin(tokenCa, versionId, fin, null, status().isOk());
        assertThat(version.get("pointages").size()).isEqualTo(12);

        // Completer quelques heures et verifier que les totaux se recalculent correctement.
        long premierPointageId = version.get("pointages").get(0).get("id").asLong();
        LocalDate premiereDate = LocalDate.parse(version.get("pointages").get(0).get("datePointage").asText());
        JsonNode versionApresCompletion = completerPointage(tokenCa, versionId, premiereDate, 8, 2);
        assertThat(versionApresCompletion.get("totalHN").asDouble()).isEqualTo(8.0);
        assertThat(versionApresCompletion.get("totalHS").asDouble()).isEqualTo(2.0);
        assertThat(premierPointageId).isPositive(); // sanity : l'id existe bien avant completion
    }

    /** Test 7 : modifier la date de fin recalcule automatiquement les jours (extension puis retrecissement). */
    @Test
    void modificationDeLaDateDeFinRecalculeLesJours() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        definirDateFin(tokenCa, versionId, debut.plusDays(2), null, status().isOk());

        // Extension : 6 jours au lieu de 3.
        JsonNode etendue = definirDateFin(tokenCa, versionId, debut.plusDays(5), null, status().isOk());
        assertThat(etendue.get("pointages").size()).isEqualTo(6);

        // Retrecissement vers 4 jours : les jours retires n'avaient jamais ete renseignes, autorise.
        JsonNode retrecie = definirDateFin(tokenCa, versionId, debut.plusDays(3), null, status().isOk());
        assertThat(retrecie.get("pointages").size()).isEqualTo(4);

        // Completer le dernier jour restant, puis tenter de retrecir en-deca : refuse (heures deja saisies).
        LocalDate dernierJour = debut.plusDays(3);
        completerPointage(tokenCa, versionId, dernierJour, 8, 0);
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", debut.plusDays(2).toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-FIPH-032"));
    }

    /**
     * Test 8 : modifier la date de fin d'une FIPH deja VALIDEE DEFINITIVEMENT
     * cree une nouvelle version tracable et redemarre le circuit de validation.
     */
    @Test
    void modificationApresValidationDefinitiveCreeUneNouvelleVersionTracable() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long fiphId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("id").asLong();
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        definirDateFin(tokenCa, versionId, debut.plusDays(1), null, status().isOk());

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));

        // Modification de la date de fin d'une version definitivement validee : nouvelle version, tracable.
        JsonNode nouvelleVersion = definirDateFin(tokenCa, versionId, debut.plusDays(3), "Extension necessaire (jours supplementaires reellement travailles)",
                status().isOk());
        assertThat(nouvelleVersion.get("numeroVersion").asInt()).isEqualTo(2);
        assertThat(nouvelleVersion.get("statutVersion").asText()).isEqualTo("BROUILLON"); // circuit redemarre (section 8)
        assertThat(nouvelleVersion.get("versionPrecedenteId").asLong()).isEqualTo(versionId);
        assertThat(nouvelleVersion.get("pointages").size()).isEqualTo(4);

        // Tracabilite : l'historique d'audit retrace la creation de cette nouvelle version ET la modification de la date de fin.
        String reponseHistorique = mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode historique = objectMapper.readTree(reponseHistorique);
        boolean creationVersionTracee = false;
        boolean modificationDateFinTracee = false;
        for (JsonNode e : historique) {
            if ("CREATION_VERSION".equals(e.get("action").asText())) {
                creationVersionTracee = true;
            }
            if ("MODIFICATION".equals(e.get("action").asText())) {
                modificationDateFinTracee = true;
            }
        }
        assertThat(creationVersionTracee).isTrue();
        assertThat(modificationDateFinTracee).isTrue();

        // L'ancienne version (1) reste intacte et toujours VALIDEE_DEFINITIVEMENT (RG-VER-003).
        JsonNode ancienneVersion = obtenirVersion(tokenCa, versionId);
        assertThat(ancienneVersion.get("statutVersion").asText()).isEqualTo("VALIDEE_DEFINITIVEMENT");
        assertThat(ancienneVersion.get("dateFinPeriode").asText()).isEqualTo(debut.plusDays(1).toString());
    }

    /** Test 10 : un Charge d'Affaires d'un AUTRE service ne peut pas definir la date de fin d'une FIPH hors de son perimetre. */
    @Test
    void controleDesDroitsParService() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenCaAutreService = seConnecter(caAutreService.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCaAutreService)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", debut.plusDays(2).toString());
                        }})))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 11 (correction du 2026-08-26) : re-confirmer une date de fin
     * strictement identique - sans qu'aucun jour ne soit ajoute ou retire -
     * ne doit rien modifier reellement : ni faire regresser le statut d'une
     * version deja validee au niveau 2, ni ajouter d'entree d'audit "sans
     * effet". Bug reel observe : un Charge d'Affaires qui reconfirmait la
     * meme date de fin (par exemple en esperant que des jours nouvellement
     * couverts par une affectation apparaissent) perdait a chaque fois sa
     * validation de niveau 2 deja acquise, sans aucun gain.
     */
    @Test
    void reconfirmerLaMemeDateDeFinNeFaitRienRegresser() throws Exception {
        String tokenEmetteur = seConnecter(emetteurAgent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteurAgent.getId()).get("versionCouranteId").asLong();

        LocalDate fin = debut.plusDays(2);
        definirDateFin(tokenCa, versionId, fin, null, status().isOk());

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        // Re-confirmation de la MEME date de fin, sans affectation nouvelle disponible entre-temps :
        // aucun jour ne peut etre ajoute ou retire -> aucun changement reel.
        JsonNode reconfirmee = definirDateFin(tokenCa, versionId, fin, null, status().isOk());
        assertThat(reconfirmee.get("statutVersion").asText()).isEqualTo("VALIDEE_NIVEAU_2");
        assertThat(reconfirmee.get("pointages").size()).isEqualTo(3);

        // A l'inverse, une VRAIE extension (jours supplementaires reellement ajoutes) doit, elle,
        // continuer a faire regresser le statut - la garde ne doit pas devenir trop permissive.
        JsonNode etendue = definirDateFin(tokenCa, versionId, debut.plusDays(3), null, status().isOk());
        assertThat(etendue.get("statutVersion").asText()).isEqualTo("EN_COMPLEMENT");
        assertThat(etendue.get("pointages").size()).isEqualTo(4);
    }

    // --- Aides de scenario ---

    private JsonNode definirDateFin(String token, long versionId, LocalDate dateFin, String motif,
                                     org.springframework.test.web.servlet.ResultMatcher statutAttendu) throws Exception {
        String reponse = mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", dateFin.toString());
                            put("motifModification", motif);
                        }})))
                .andExpect(statutAttendu)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private JsonNode completerPointage(String token, long versionId, LocalDate date, int heuresNormales, int heuresSup) throws Exception {
        String reponse = mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("datePointage", date.toString());
                            put("heuresNormales", heuresNormales);
                            put("heuresSup", heuresSup);
                        }})))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private JsonNode obtenirVersion(String token, long versionId) throws Exception {
        String reponse = mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private long creerViserEtValiderBonDeSortie(String tokenEmetteur, String tokenValidateur, LocalDate dateSortie) throws Exception {
        String corpsBonSortie = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("kilometrage", 30);
            put("dateSortie", dateSortie.toString());
            put("heureSortie", "08:00:00");
            put("lieu", "Chantier de test");
            put("codeAffaireSaisi", "CODE-TEST");
            put("motifSortie", "Test periode FIPH");
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
                .andExpect(status().isOk());
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
        mission.setDateFinPrevue(LocalDate.now().plusMonths(2));
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

    private Utilisateur creerPersonneAvecCompte(String matricule, String nom, String prenom, String identifiant, Service svc) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setService(svc);
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
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
        CodeHN codeHn = new CodeHN();
        codeHn.setCode(code);
        codeHn.setLibelle("Code mission de test");
        codeHn.setChantier(c);
        return codeHn;
    }
}
