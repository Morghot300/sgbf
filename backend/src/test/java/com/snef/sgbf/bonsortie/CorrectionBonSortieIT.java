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
 * Evolution du 2026-08-26 ("ajoute la correction des bon de sortie") :
 * correction des champs d'un bon de sortie deja cree (remplace l'ancien
 * endpoint {@code /retour}, jamais expose cote frontend et donc inutilisable
 * en pratique). Meme perimetre que le visa/la validation (titulaire ou
 * gestionnaire - Charge d'Affaires/personne habilitee - du meme service),
 * bloquee des que le bon est {@code VALIDE} (RG-VER-001).
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

    /** RG-VER-001 : plus aucune correction possible une fois le bon VALIDE. */
    @Test
    void correctionRefuseeUneFoisValide_RG_VER_001() throws Exception {
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

        mockMvc.perform(put("/api/bons-sortie/" + bonId)
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(corpsCorrection("MS-004", 10, valide.get("lockVersion").asInt())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-VER-001"));
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
