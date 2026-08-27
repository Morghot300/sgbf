package com.snef.sgbf.bonsortie;

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
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
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
 * Evolution du 2026-08-26 ("ajoute la correction des bon de sortie"), puis du
 * 2026-08-27 (brief "Evolution du module Bon de Sortie", section 10-12,
 * RG-VER-001 inversee sur decision explicite ; brief "Evolution avancee du
 * module Bon de Sortie, Missions et FIPH", section 15-17 - le bon repasse
 * desormais a VISE) : correction des champs d'un bon de sortie deja cree
 * (remplace l'ancien endpoint {@code /retour}, jamais expose cote frontend
 * et donc inutilisable en pratique).
 *
 * <p>Perimetre selon le statut : tant que le bon n'est pas {@code VALIDE},
 * le titulaire ou un gestionnaire (Charge d'Affaires/personne habilitee) de
 * son service peuvent corriger. Une fois {@code VALIDE}, la correction reste
 * possible mais se restreint au seul gestionnaire du service (le simple
 * titulaire, s'il n'est pas lui-meme gestionnaire, ne peut plus corriger son
 * propre bon) - sauf si une FIPH couvrant cette date pour cet agent est deja
 * {@code VALIDEE_DEFINITIVEMENT} (RG-BS-011, jours de pointage scelles).
 * Corriger un bon {@code VALIDE} le fait repasser a {@code VISE} : une
 * nouvelle validation (niveau 2) est desormais exigee (retour dans le
 * circuit de validation, jamais un simple ecrasement silencieux).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CorrectionBonSortieIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service littoral;
    private Service centre;
    private Utilisateur caLittoral;
    private Utilisateur caCentre;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        littoral = serviceRepository.save(nouveauService("LIT" + suffixe, "Service Littoral"));
        centre = serviceRepository.save(nouveauService("CTR" + suffixe, "Service Centre"));
        caLittoral = creerUtilisateurAvecHabilitation("ca_lit_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        caCentre = creerUtilisateurAvecHabilitation("ca_ctr_" + suffixe, centre, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    /** Le titulaire lui-meme peut corriger son bon de sortie tant qu'il n'est pas VALIDE. */
    @Test
    void correctionParLeTitulaireEnBrouillon_reussit() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc1_" + suffixe, littoral);
        String token = seConnecter(titulaire.getIdentifiant());
        JsonNode bon = creerBonDeSortie(token, "CODE-ERRONE");

        JsonNode corrige = corriger(token, bon.get("id").asLong(), "CODE-CORRIGE", 999, bon.get("lockVersion").asInt(), status().isOk());
        assertThat(corrige.get("codeAffaireSaisi").asText()).isEqualTo("CODE-CORRIGE");
        assertThat(corrige.get("kilometrage").asInt()).isEqualTo(999);
    }

    /** Un Charge d'Affaires du meme service peut corriger apres le visa (meme perimetre que viser/valider). */
    @Test
    void correctionParUnCaDuServiceApresVisa_reussit() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc2_" + suffixe, littoral);
        String tokenAgent = seConnecter(titulaire.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, "MS-001");
        long bonId = bon.get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        JsonNode vise = obtenir(tokenCa, bonId);

        JsonNode corrige = corriger(tokenCa, bonId, "MS-002", 50, vise.get("lockVersion").asInt(), status().isOk());
        assertThat(corrige.get("codeAffaireSaisi").asText()).isEqualTo("MS-002");
        assertThat(corrige.get("statut").asText()).isEqualTo("VISE");
    }

    /**
     * Evolution du 2026-08-27 (brief "Evolution avancee...", section 15-17) :
     * un Charge d'Affaires/une personne habilitee du service PEUT desormais
     * corriger un bon de sortie meme deja VALIDE, mais cela le fait repasser
     * a VISE - une nouvelle validation (niveau 2) est exigee avant de le
     * considerer de nouveau VALIDE (retour dans le circuit de validation).
     */
    @Test
    void correctionParCaDuServiceApresValidation_repasseAViseEtExigeUneNouvelleValidation() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc3_" + suffixe, littoral);
        String tokenAgent = seConnecter(titulaire.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, "MS-003");
        long bonId = bon.get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());
        JsonNode valide = obtenir(tokenCa, bonId);

        JsonNode corrige = corriger(tokenCa, bonId, "MS-004", 10, valide.get("lockVersion").asInt(), status().isOk());
        assertThat(corrige.get("codeAffaireSaisi").asText()).isEqualTo("MS-004");
        assertThat(corrige.get("statut").asText()).isEqualTo("VISE"); // retour dans le circuit de validation
        assertThat(corrige.get("valideParIdentifiant").isNull()).isTrue(); // l'ancienne validation est effacee
        assertThat(corrige.get("dateValidation").isNull()).isTrue();

        // Une nouvelle validation (niveau 2) est desormais necessaire et fonctionne normalement.
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
    }

    /**
     * A l'inverse, le simple titulaire (qui n'est pas lui-meme gestionnaire
     * du service) ne peut plus corriger son propre bon une fois VALIDE -
     * seul un CA/PH/Super Administrateur le peut desormais.
     */
    @Test
    void correctionRefuseeAuTitulaireSeulUneFoisValide() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc3b_" + suffixe, littoral);
        String tokenAgent = seConnecter(titulaire.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, "MS-003B");
        long bonId = bon.get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());
        JsonNode valide = obtenir(tokenAgent, bonId);

        mockMvc.perform(put("/api/bons-sortie/" + bonId)
                        .header("Authorization", "Bearer " + tokenAgent)
                        .contentType("application/json")
                        .content(corpsCorrection("MS-004B", 10, valide.get("lockVersion").asInt())))
                .andExpect(status().isForbidden());
    }

    /**
     * RG-BS-011 : des qu'une FIPH couvrant la date de sortie de l'agent est
     * deja VALIDEE_DEFINITIVEMENT, la correction du bon de sortie source est
     * refusee - ses jours de pointage sont scelles.
     */
    @Test
    void correctionRefuseeSiFiphDejaValideeDefinitivement() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc3c_" + suffixe, littoral);
        Utilisateur ra = creerUtilisateurAvecHabilitation("ra_" + suffixe, littoral, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        Utilisateur direction = creerUtilisateurAvecHabilitation("dir_" + suffixe, littoral, CodeRoleMetier.DIRECTION);
        String tokenAgent = seConnecter(titulaire.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        String tokenRa = seConnecter(ra.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());

        JsonNode bon = creerBonDeSortie(tokenAgent, "MS-003C");
        long bonId = bon.get("id").asLong();
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());

        // FIPH generee automatiquement - amenee jusqu'a VALIDEE_DEFINITIVEMENT (niveaux 2/3/4).
        String reponseFiph = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponseFiph);
        long versionId = -1;
        for (JsonNode f : fiphs) {
            if (f.get("agentId").asLong() == titulaire.getId()) {
                versionId = f.get("versionCouranteId").asLong();
            }
        }
        assertThat(versionId).isPositive();

        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", LocalDate.now().toString());
                        }})))
                .andExpect(status().isOk());
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

        JsonNode valide = obtenir(tokenCa, bonId);
        mockMvc.perform(put("/api/bons-sortie/" + bonId)
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsCorrection("MS-004C", 10, valide.get("lockVersion").asInt())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-BS-011"));
    }

    /** Un Charge d'Affaires d'un AUTRE service ne peut pas corriger (RG-SEC-002). */
    @Test
    void correctionRefuseeHorsPerimetre() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc4_" + suffixe, littoral);
        String tokenAgent = seConnecter(titulaire.getIdentifiant());
        String tokenCaCentre = seConnecter(caCentre.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, "MS-005");

        mockMvc.perform(put("/api/bons-sortie/" + bon.get("id").asLong())
                        .header("Authorization", "Bearer " + tokenCaCentre)
                        .contentType("application/json")
                        .content(corpsCorrection("MS-006", 10, bon.get("lockVersion").asInt())))
                .andExpect(status().isForbidden());
    }

    /** Verrouillage optimiste : un lockVersion perime est rejete comme conflit (409), pas silencieusement ecrase. */
    @Test
    void correctionAvecLockVersionPerime_conflit409() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc5_" + suffixe, littoral);
        String token = seConnecter(titulaire.getIdentifiant());
        JsonNode bon = creerBonDeSortie(token, "MS-007");
        long bonId = bon.get("id").asLong();
        int lockVersionInitial = bon.get("lockVersion").asInt();

        corriger(token, bonId, "MS-008", 20, lockVersionInitial, status().isOk());

        mockMvc.perform(put("/api/bons-sortie/" + bonId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(corpsCorrection("MS-009", 30, lockVersionInitial)))
                .andExpect(status().isConflict());
    }

    /** RG-BS-VEHICULE : la precision du vehicule reste obligatoire quand le moyen est "Autre", meme a la correction. */
    @Test
    void correctionExigePrecisionVehiculeSiAutre() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_correc6_" + suffixe, littoral);
        String token = seConnecter(titulaire.getIdentifiant());
        JsonNode bon = creerBonDeSortie(token, "MS-010");

        mockMvc.perform(put("/api/bons-sortie/" + bon.get("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("vehiculeId", null);
                            put("moyenUtilise", MoyenUtilise.AUTRE.name());
                            put("precisionVehicule", null);
                            put("lt", null);
                            put("kilometrage", 10);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("heureRetour", null);
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "MS-010");
                            put("motifSortie", "Test correction");
                            put("lockVersion", bon.get("lockVersion").asInt());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-BS-VEHICULE"));
    }

    // --- Aides ---

    private JsonNode corriger(String token, long bonId, String codeAffaire, int kilometrage, int lockVersion,
                               org.springframework.test.web.servlet.ResultMatcher statutAttendu) throws Exception {
        String reponse = mockMvc.perform(put("/api/bons-sortie/" + bonId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(corpsCorrection(codeAffaire, kilometrage, lockVersion)))
                .andExpect(statutAttendu)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private String corpsCorrection(String codeAffaire, int kilometrage, int lockVersion) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("vehiculeId", null);
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("precisionVehicule", null);
            put("lt", null);
            put("kilometrage", kilometrage);
            put("dateSortie", LocalDate.now().toString());
            put("heureSortie", "08:00:00");
            put("heureRetour", null);
            put("lieu", "Chantier de test corrige");
            put("codeAffaireSaisi", codeAffaire);
            put("motifSortie", "Test correction");
            put("lockVersion", lockVersion);
        }});
    }

    private JsonNode creerBonDeSortie(String token, String codeAffaire) throws Exception {
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
                            put("motifSortie", "Test correction bon de sortie");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

    private JsonNode obtenir(String token, long bonId) throws Exception {
        String reponse = mockMvc.perform(get("/api/bons-sortie/" + bonId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
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
}
