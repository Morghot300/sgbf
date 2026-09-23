package com.snef.sgbf;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Evolution du 2026-08-26 (brief "Evolution des droits du Charge d'Affaires
 * et de la Personne habilitee sur les Bons de Sortie et les FIPH") :
 * <ul>
 *   <li>section 7 - "Oui, elargir aux CA/PH du service" : le visa (niveau 1)
 *       d'un bon de sortie n'est plus strictement reserve au titulaire, un
 *       Charge d'Affaires/une personne habilitee du meme service peut
 *       desormais viser le bon d'un autre agent de son service, avec le
 *       meme perimetre que la validation (niveau 2) - jamais hors service ;</li>
 *   <li>section 8-9 - "Oui, meme modele flexible (recommande)" : la creation
 *       manuelle d'une FIPH (Code Service) abandonne le couple annee/semaine
 *       ISO rigide au profit du meme modele de periode flexible que les FIPH
 *       issues d'un bon de sortie - date de debut libre, date de fin
 *       optionnelle a la creation, ajustable ensuite, obligatoire avant toute
 *       soumission (RG-FIPH-033).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionDroitsCaPhBonSortieFiphIT {

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
    private Utilisateur phLittoral;
    private Utilisateur caCentre;
    private Utilisateur responsableActiviteLittoral;
    private Utilisateur directionLittoral;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        littoral = serviceRepository.save(nouveauService("LIT" + suffixe, "Service Littoral"));
        centre = serviceRepository.save(nouveauService("CTR" + suffixe, "Service Centre"));

        caLittoral = creerUtilisateurAvecHabilitation("ca_lit_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        phLittoral = creerUtilisateurAvecHabilitation("ph_lit_" + suffixe, littoral, CodeRoleMetier.PERSONNE_HABILITEE);
        responsableActiviteLittoral = creerUtilisateurAvecHabilitation("ra_lit_" + suffixe, littoral, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        directionLittoral = creerUtilisateurAvecHabilitation("dir_lit_" + suffixe, littoral, CodeRoleMetier.DIRECTION);
        caCentre = creerUtilisateurAvecHabilitation("ca_ctr_" + suffixe, centre, CodeRoleMetier.CHARGE_AFFAIRES);
    }

    // =========================================================================================
    // Visa (niveau 1) elargi aux CA/PH du service - section 7
    // =========================================================================================

    /** Positif : un Charge d'Affaires, NON titulaire, peut viser le bon d'un agent de son propre service. */
    @Test
    void viserAutoriseParUnChargeAffairesDuMemeServiceNonTitulaire() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_lit_" + suffixe, littoral);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-VISER-CA-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VISE"))
                .andExpect(jsonPath("$.viseParIdentifiant").value(caLittoral.getIdentifiant()));
    }

    /** Positif : meme scenario pour une Personne habilitee du meme service. */
    @Test
    void viserAutoriseParUnePersonneHabiliteeDuMemeServiceNonTitulaire() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_lit2_" + suffixe, littoral);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        String tokenPh = seConnecter(phLittoral.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-VISER-PH-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenPh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParIdentifiant").value(phLittoral.getIdentifiant()));
    }

    /** Le titulaire lui-meme peut toujours viser son propre bon (non-regression). */
    @Test
    void viserResteAutoriseParLeTitulaireLuiMeme() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_lit3_" + suffixe, littoral);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-VISER-TIT-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParIdentifiant").value(agent.getIdentifiant()));
    }

    /** Negatif (RG-SEC-002) : un Charge d'Affaires d'un AUTRE service ne peut pas viser ce bon. */
    @Test
    void viserRefusePourUnChargeAffairesDunAutreService() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_lit4_" + suffixe, littoral);
        String tokenAgent = seConnecter(agent.getIdentifiant());
        String tokenCaCentre = seConnecter(caCentre.getIdentifiant());
        long bonId = creerBonDeSortie(tokenAgent, "MS-VISER-HORS-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenCaCentre))
                .andExpect(status().isForbidden());
    }

    /** Negatif : un simple agent sans habilitation de gestion, autre que le titulaire, ne peut pas viser. */
    @Test
    void viserRefusePourUnAgentQuelconqueSansHabilitationDeGestion() throws Exception {
        Utilisateur titulaire = creerUtilisateur("agent_lit5_" + suffixe, littoral);
        Utilisateur autreAgent = creerUtilisateur("agent_lit6_" + suffixe, littoral);
        String tokenTitulaire = seConnecter(titulaire.getIdentifiant());
        String tokenAutreAgent = seConnecter(autreAgent.getIdentifiant());
        long bonId = creerBonDeSortie(tokenTitulaire, "MS-VISER-SIMPLE-" + (suffixe % 100_000L));

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAutreAgent))
                .andExpect(status().isForbidden());
    }

    // =========================================================================================
    // FIPH manuelle - modele de periode flexible - section 8-9
    // =========================================================================================

    /** La creation manuelle sans date de fin laisse la periode "ouverte" (dateFinPeriode null), exactement comme une FIPH issue d'un bon de sortie. */
    @Test
    void creationManuelleSansDateFin_periodeOuverte() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_manuel1_" + suffixe, littoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());

        JsonNode fiph = creerFiphManuelle(tokenCa, agent.getId(), LocalDate.of(2026, 6, 1), null, status().isCreated());
        assertThat(fiph.get("origine").asText()).isEqualTo("MANUELLE");
        assertThat(fiph.get("statut").asText()).isEqualTo("SIGNEE");
        assertThat(fiph.get("dateDebutPeriode").asText()).isEqualTo("2026-06-01");

        long versionId = fiph.get("versionCouranteId").asLong();
        JsonNode version = obtenirVersion(tokenCa, versionId);
        assertThat(version.get("dateFinPeriode").isNull()).isTrue();
    }

    /** RG-FIPH-033 : une FIPH manuelle sans date de fin ne peut pas etre soumise au circuit de validation. */
    @Test
    void soumissionRefuseeSansDateFin_RG_FIPH_033() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_manuel2_" + suffixe, littoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        JsonNode fiph = creerFiphManuelle(tokenCa, agent.getId(), LocalDate.of(2026, 6, 8), null, status().isCreated());
        long versionId = fiph.get("versionCouranteId").asLong();

        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-FIPH-033"));
    }

    /** Une fois la date de fin definie, la soumission reussit et le circuit complet de validation aboutit normalement. */
    @Test
    void dateFinDefinieEnsuite_soumissionEtWorkflowCompletReussissent() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_manuel3_" + suffixe, littoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        String tokenRa = seConnecter(responsableActiviteLittoral.getIdentifiant());
        String tokenDirection = seConnecter(directionLittoral.getIdentifiant());

        JsonNode fiph = creerFiphManuelle(tokenCa, agent.getId(), LocalDate.of(2026, 6, 15), null, status().isCreated());
        long versionId = fiph.get("versionCouranteId").asLong();

        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", LocalDate.of(2026, 6, 16).toString());
                        }})))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("SOUMISE"));

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
    }

    /** RG-FIPH-030 : la date de fin fournie a la creation ne peut pas etre anterieure a la date de debut. */
    @Test
    void creationManuelleAvecDateFinAnterieureADateDebut_refusee() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_manuel4_" + suffixe, littoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());

        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent.getId()));
                            put("dateDebut", LocalDate.of(2026, 7, 10).toString());
                            put("dateFin", LocalDate.of(2026, 7, 5).toString());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-FIPH-030"));
    }

    /** RG-FIPH-002 : une FIPH manuelle ouverte (sans date de fin) bloque bien toute FIPH chevauchante ulterieure, meme lointaine. */
    @Test
    void periodeOuverteBloqueToutChevauchementUlterieur_RG_FIPH_002() throws Exception {
        Utilisateur agent = creerUtilisateur("agent_manuel5_" + suffixe, littoral);
        String tokenCa = seConnecter(caLittoral.getIdentifiant());
        creerFiphManuelle(tokenCa, agent.getId(), LocalDate.of(2026, 8, 1), null, status().isCreated());

        mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agent.getId()));
                            put("dateDebut", LocalDate.of(2027, 1, 1).toString());
                        }})))
                .andExpect(status().isConflict());
    }

    // --- Aides ---

    /** Cree une FIPH manuelle pour un seul agent et retourne son FiphDto (deballe de "creees[0]" - reponse en lot depuis l'evolution du 2026-08-27). */
    private JsonNode creerFiphManuelle(String token, Long agentId, LocalDate dateDebut, LocalDate dateFin,
                                        org.springframework.test.web.servlet.ResultMatcher statutAttendu) throws Exception {
        String reponse = mockMvc.perform(post("/api/fiph/manuelle")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(agentId));
                            put("dateDebut", dateDebut.toString());
                            put("dateFin", dateFin != null ? dateFin.toString() : null);
                        }})))
                .andExpect(statutAttendu)
                .andReturn().getResponse().getContentAsString();
        JsonNode racine = objectMapper.readTree(reponse);
        return racine.has("creees") ? racine.get("creees").get(0) : racine;
    }

    private JsonNode obtenirVersion(String token, long versionId) throws Exception {
        String reponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse);
    }

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
                            put("motifSortie", "Test evolution droits CA/PH");
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
}
