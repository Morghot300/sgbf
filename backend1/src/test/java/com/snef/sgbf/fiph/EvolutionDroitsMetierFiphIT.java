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
import com.snef.sgbf.notification.entity.Notification;
import com.snef.sgbf.notification.entity.TypeNotification;
import com.snef.sgbf.notification.repository.NotificationRepository;
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
 * Evolution du 2026-08-26 ("Evolution complete des droits metier et du
 * workflow FIPH") : trois exigences non encore couvertes ailleurs -
 * <ul>
 *   <li>RG-FIPH-033 (section 9) : une FIPH ne peut etre soumise sans date de
 *       fin definie ;</li>
 *   <li>section 17 : une notification distincte informe les niveau-2 du
 *       service des qu'une FIPH deja validee est modifiee (nouvelle version) -
 *       pas seulement au moment de la soumission ulterieure ;</li>
 *   <li>section 13-14 : une validation de niveau effectuee par un Super
 *       Administrateur hors de son perimetre habituel est journalisee
 *       distinctement (VALIDATION_PAR_SUPER_ADMIN), en plus de la VALIDATION
 *       normale - jamais confondue avec elle ni avec la prise en main.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvolutionDroitsMetierFiphIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Service service;
    private Utilisateur emetteur;
    private Utilisateur ca;
    private Utilisateur ca2;
    private Utilisateur responsableActivite;
    private Utilisateur direction;
    private Utilisateur superAdmin;
    private LocalDate debut;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        emetteur = creerPersonneAvecCompte("EMT" + (suffixe % 100_000L), "Test", "Emetteur", "emetteur_edm_" + suffixe, service);
        ca = creerUtilisateurAvecHabilitation("ca_edm_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        ca2 = creerUtilisateurAvecHabilitation("ca2_edm_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        responsableActivite = creerUtilisateurAvecHabilitation("ra_edm_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        direction = creerUtilisateurAvecHabilitation("direction_edm_" + suffixe, service, CodeRoleMetier.DIRECTION);
        superAdmin = creerUtilisateurAvecHabilitation("superadmin_edm_" + suffixe, null, CodeRoleMetier.SUPER_ADMINISTRATEUR);
        debut = LocalDate.now();
    }

    /** RG-FIPH-033 (section 9) : une FIPH sans date de fin ne peut pas etre soumise au circuit de validation. */
    @Test
    void soumissionRefuseeSansDateDeFin() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        long bonSortieId = creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long versionId = trouverFiphDeLAgent(tokenCa, emetteur.getId()).get("versionCouranteId").asLong();

        // Aucun appel a /date-fin : la periode reste "ouverte" (dateFinPeriode == null).
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-FIPH-033"));

        assertThat(bonSortieId).isPositive();
    }

    /** Section 17 : une notification distincte est envoyee des la modification d'une FIPH deja validee - pas seulement a la soumission ulterieure. */
    @Test
    void notificationEnvoyeeDesLaModificationDUneFiphDejaValideeDefinitivement() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long fiphId = trouverFiphDeLAgent(tokenCa, emetteur.getId()).get("id").asLong();
        long versionId = trouverFiphDeLAgent(tokenCa, emetteur.getId()).get("versionCouranteId").asLong();

        definirDateFin(tokenCa, versionId, debut, null);
        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());
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

        // Modification post-validation-definitive par un SECOND Charge d'Affaires du service (ca2) :
        // cree une nouvelle version - ca2 (niveau 2 du service) doit en etre notifie des maintenant.
        String tokenCa2 = seConnecter(ca2.getIdentifiant());
        definirDateFin(tokenCa2, versionId, debut.plusDays(1), "Jour supplementaire reellement travaille");

        List<Notification> notificationsCa2 = notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(ca2.getId());
        assertThat(notificationsCa2).anyMatch(n -> n.getType() == TypeNotification.FIPH_A_VALIDER
                && n.getMessage() != null && n.getMessage().contains("modifiee") && n.getMessage().contains("nouvelle validation"));
    }

    /** Section 13-14 : une validation de niveau par un Super Administrateur hors perimetre est journalisee distinctement. */
    @Test
    void validationParSuperAdministrateurJournaliseeDistinctement() throws Exception {
        String tokenEmetteur = seConnecter(emetteur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenSuperAdmin = seConnecter(superAdmin.getIdentifiant());
        creerViserEtValiderBonDeSortie(tokenEmetteur, tokenCa, debut);
        long fiphId = trouverFiphDeLAgent(tokenCa, emetteur.getId()).get("id").asLong();
        long versionId = trouverFiphDeLAgent(tokenCa, emetteur.getId()).get("versionCouranteId").asLong();

        definirDateFin(tokenCa, versionId, debut, null);
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk());

        String decisionValidee = objectMapper.writeValueAsString(new LinkedHashMap<>() {{ put("decision", "VALIDEE"); }});
        // Le Super Administrateur valide le niveau 2 - il n'a jamais "saisi" cette FIPH (le CA
        // du service a prepare la date de fin), RG-HAB-004 ne s'y oppose donc pas.
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenSuperAdmin).contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_NIVEAU_2"));

        String reponseHistorique = mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean validationParSuperAdminTracee = false;
        boolean validationNormaleTracee = false;
        for (JsonNode e : objectMapper.readTree(reponseHistorique)) {
            String action = e.get("action").asText();
            if ("VALIDATION_PAR_SUPER_ADMIN".equals(action)) {
                validationParSuperAdminTracee = true;
            }
            if ("VALIDATION".equals(action)) {
                validationNormaleTracee = true;
            }
        }
        assertThat(validationParSuperAdminTracee).isTrue();
        assertThat(validationNormaleTracee).isTrue(); // la VALIDATION normale reste aussi ecrite, jamais remplacee.
    }

    // --- Aides ---

    private JsonNode trouverFiphDeLAgent(String token, long agentId) throws Exception {
        String reponse = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode n : objectMapper.readTree(reponse)) {
            if (n.get("agentId").asLong() == agentId) {
                return n;
            }
        }
        throw new IllegalStateException("Aucune FIPH trouvee pour l'agent " + agentId);
    }

    private void definirDateFin(String token, long versionId, LocalDate dateFin, String motif) throws Exception {
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("dateFin", dateFin.toString());
                            put("motifModification", motif);
                        }})))
                .andExpect(status().isOk());
    }

    private long creerViserEtValiderBonDeSortie(String tokenEmetteur, String tokenValidateur, LocalDate dateSortie) throws Exception {
        String corpsBonSortie = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
            put("kilometrage", 30);
            put("dateSortie", dateSortie.toString());
            put("heureSortie", "08:00:00");
            put("lieu", "Chantier de test");
            put("codeAffaireSaisi", "CODE-TEST");
            put("motifSortie", "Test evolution droits metier FIPH");
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
        Utilisateur utilisateur = creerUtilisateur(identifiant, svc);
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        return utilisateurRepository.save(utilisateur);
    }

    private Service nouveauService(String code, String libelle) {
        Service svc = new Service();
        svc.setCodeService(code);
        svc.setLibelle(libelle);
        return svc;
    }
}
