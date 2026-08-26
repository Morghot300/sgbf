package com.snef.sgbf.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.entity.Habilitation;
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
 * Verifie, au niveau HTTP, les trois exigences transverses du module
 * "Audit, impression, export PDF, droits RH lecture seule" (section 13, 14,
 * 24, 25, 26.5 du document source) :
 * <ul>
 *   <li>anti-IDOR (RG-SEC-002, section 26.5) : un utilisateur totalement hors
 *       perimetre ne peut consulter ni un bon de sortie, ni une FIPH, ni une
 *       FIPHVersion par identifiant, quand bien meme celui-ci est
 *       syntaxiquement valide ;</li>
 *   <li>droits RH (section 25 ; ecriture etendue le 2026-08-26, section 7) :
 *       la RH consulte globalement, ET peut desormais corriger le pointage
 *       d'une FIPH de n'importe quel service, y compris apres validation
 *       definitive - meme mecanisme que CA/PH (nouvelle version tracable),
 *       perimetre global plutot que par service ;</li>
 *   <li>impression/export PDF conditionnes au statut (RG-DOC-001/003) et
 *       journal d'audit d'une FIPH, dont l'export est reserve a la
 *       Direction/RH/Administrateur (section 24).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditPdfDroitsRhIT {

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

    private Utilisateur emetteurUtilisateur;
    private Utilisateur ca1;
    private Utilisateur ca2;
    private Utilisateur responsableActivite;
    private Utilisateur direction;
    private Utilisateur rh;
    private Utilisateur horsPerimetre;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = System.nanoTime();
        Service service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        Service autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        Chantier chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        emetteurUtilisateur = creerPersonneAvecCompte("MAT" + suffixe, "Test", "Emetteur", "emetteur" + suffixe, service);
        Utilisateur emetteurAgent = emetteurUtilisateur;

        ca1 = creerUtilisateurAvecHabilitation("ca1_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        ca2 = creerUtilisateurAvecHabilitation("ca2_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        responsableActivite = creerUtilisateurAvecHabilitation("ra_" + suffixe, service, CodeRoleMetier.RESPONSABLE_ACTIVITE);
        direction = creerUtilisateurAvecHabilitation("direction_" + suffixe, service, CodeRoleMetier.DIRECTION);
        rh = creerUtilisateurAvecHabilitation("rh_" + suffixe, null, CodeRoleMetier.RH);
        // Hors perimetre : habilite, mais sur un AUTRE service - ne doit rien pouvoir consulter ici (RG-SEC-002).
        horsPerimetre = creerUtilisateurAvecHabilitation("outsider_" + suffixe, autreService, CodeRoleMetier.CHARGE_AFFAIRES);

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        mission = missionRepository.save(mission);

        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(emetteurAgent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(ca1);
        affectationMissionRepository.save(affectation);
    }

    @Test
    void idorPdfAuditEtDroitsRh() throws Exception {
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCa1 = seConnecter(ca1.getIdentifiant());
        String tokenCa2 = seConnecter(ca2.getIdentifiant());
        String tokenRa = seConnecter(responsableActivite.getIdentifiant());
        String tokenDirection = seConnecter(direction.getIdentifiant());
        String tokenRh = seConnecter(rh.getIdentifiant());
        String tokenHorsPerimetre = seConnecter(horsPerimetre.getIdentifiant());

        // --- 1. Creation du bon de sortie (statut BROUILLON) : impression refusee avant validation (RG-DOC-001). ---
        String corpsBonSortie = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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

        mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/pdf").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isUnprocessableEntity());

        // --- 2. IDOR : un utilisateur totalement hors perimetre ne peut consulter ce bon de sortie (RG-SEC-002). ---
        mockMvc.perform(get("/api/bons-sortie/" + bonSortieId).header("Authorization", "Bearer " + tokenHorsPerimetre))
                .andExpect(status().isForbidden());

        // --- 3. Visa + validation -> BS VALIDE -> impression desormais disponible. ---
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser").header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/pdf").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        // --- 4. FIPH generee automatiquement ; recuperation de son identifiant. ---
        String reponseFiphListe = mockMvc.perform(get("/api/fiph").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fiphs = objectMapper.readTree(reponseFiphListe);
        JsonNode fiphGeneree = fiphs.get(fiphs.size() - 1);
        long fiphId = fiphGeneree.get("id").asLong();
        long versionId = fiphGeneree.get("versionCouranteId").asLong();

        // --- 5. IDOR sur la FIPH et sa version : refuse pour l'utilisateur hors perimetre. ---
        mockMvc.perform(get("/api/fiph/" + fiphId).header("Authorization", "Bearer " + tokenHorsPerimetre))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenHorsPerimetre))
                .andExpect(status().isForbidden());

        // --- 6. RH : lecture globale accordee des maintenant (vision globale), meme hors service. ---
        mockMvc.perform(get("/api/fiph/" + fiphId).header("Authorization", "Bearer " + tokenRh))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/fiph-versions/" + versionId).header("Authorization", "Bearer " + tokenRh))
                .andExpect(status().isOk());
        // Evolution du 2026-08-26 (section 7) : la RH PEUT desormais corriger le pointage d'une
        // FIPH de n'importe quel service - meme mecanisme d'ecriture que CA/PH, perimetre global.
        String corpsCompletionRh = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("datePointage", LocalDate.now().toString());
            put("heuresNormales", 8);
            put("heuresSup", 0);
        }});
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenRh)
                        .contentType("application/json").content(corpsCompletionRh))
                .andExpect(status().isOk());
        // Un utilisateur hors perimetre (habilite, mais sur un AUTRE service, sans role RH/Super
        // Administrateur) reste, lui, toujours refuse - l'exception ne beneficie qu'a RH/Super Admin.
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenHorsPerimetre)
                        .contentType("application/json").content(corpsCompletionRh))
                .andExpect(status().isForbidden());

        // --- 7. Previsualisation PDF de la FIPH disponible avant VALIDEE_DEFINITIVEMENT (evolution du
        // 2026-08-21) : le Charge d'Affaires peut desormais previsualiser une FIPH non encore terminee,
        // borne par le meme perimetre de lecture que la consultation en ligne - jamais par le statut.
        mockMvc.perform(get("/api/fiph-versions/" + versionId + "/pdf").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        // Toujours borne par le perimetre : un utilisateur hors service ne peut pas previsualiser non plus.
        mockMvc.perform(get("/api/fiph-versions/" + versionId + "/pdf").header("Authorization", "Bearer " + tokenHorsPerimetre))
                .andExpect(status().isForbidden());

        // --- 8. Deroule le circuit complet (complement -> soumission -> validations 2/3/4). Plus
        // d'etape de signature explicite : le visa de l'agent titulaire est deja acquis d'office
        // des la precreation de cette FIPH issue d'un bon de sortie (evolution du workflow FIPH,
        // 2026-08-18) - la version est deja SIGNEE a ce stade. ---
        String corpsCompletion = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("datePointage", LocalDate.now().toString());
            put("heuresNormales", 8);
            put("heuresSup", 0);
        }});
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/pointage")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json").content(corpsCompletion))
                .andExpect(status().isOk());
        // RG-FIPH-033 : date de fin obligatoire avant soumission (evolution du 2026-08-26, section 9).
        mockMvc.perform(put("/api/fiph-versions/" + versionId + "/date-fin")
                        .header("Authorization", "Bearer " + tokenCa1)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("dateFin", LocalDate.now().toString());
                        }})))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/soumettre").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk());

        String decisionValidee = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("decision", "VALIDEE");
        }});
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/2")
                        .header("Authorization", "Bearer " + tokenCa2)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/3")
                        .header("Authorization", "Bearer " + tokenRa)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/fiph-versions/" + versionId + "/valider/4")
                        .header("Authorization", "Bearer " + tokenDirection)
                        .contentType("application/json").content(decisionValidee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVersion").value("VALIDEE_DEFINITIVEMENT"));

        // --- 9. Desormais terminee : le telechargement PDF fonctionne et produit un document non vide. ---
        byte[] pdf = mockMvc.perform(get("/api/fiph-versions/" + versionId + "/pdf").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf).isNotEmpty();
        // Un PDF valide commence toujours par la signature "%PDF-".
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // --- 10. Historique d'audit de la FIPH : consultable par quiconque peut deja lire le document. ---
        String reponseHistorique = mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode historique = objectMapper.readTree(reponseHistorique);
        assertThat(historique.isArray()).isTrue();
        assertThat(historique.size()).isGreaterThan(0);
        mockMvc.perform(get("/api/audit/fiph/" + fiphId).header("Authorization", "Bearer " + tokenHorsPerimetre))
                .andExpect(status().isForbidden());

        // --- 11. Export d'audit (CSV/PDF) : reserve a la Direction, la RH et l'Administrateur (section 24). ---
        mockMvc.perform(get("/api/audit/fiph/" + fiphId + "/export/csv").header("Authorization", "Bearer " + tokenCa1))
                .andExpect(status().isForbidden());
        byte[] csv = mockMvc.perform(get("/api/audit/fiph/" + fiphId + "/export/csv").header("Authorization", "Bearer " + tokenRh))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(csv).isNotEmpty();
        byte[] pdfAudit = mockMvc.perform(get("/api/audit/fiph/" + fiphId + "/export/pdf").header("Authorization", "Bearer " + tokenDirection))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdfAudit, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    /** Authentification simple (identifiant + mot de passe, sans seconde etape - decision du 2026-08-17). */
    private String seConnecter(String identifiant) throws Exception {
        String corps = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
