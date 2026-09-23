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
 * Evolution du 2026-08-27 (brief "Evolution du module Bon de Sortie et
 * integration avec les FIPH", section 5-6) : le "Code Mission" du bon de
 * sortie devient une vraie relation (BonSortie.mission), choisie
 * explicitement a la creation ou a une correction, plutot qu'un simple texte
 * libre - lorsqu'elle est renseignee, elle devient prioritaire sur la
 * resolution automatique par agent+date a la validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LienMissionBonSortieIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private MissionRepository missionRepository;
    @Autowired private AffectationMissionRepository affectationMissionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service littoral;
    private Utilisateur ca;
    private Utilisateur agent;
    private Mission missionChoisie;
    private Mission autreMission;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        littoral = serviceRepository.save(nouveauService("LIT" + suffixe, "Service Littoral"));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        agent = creerUtilisateur("agent_" + suffixe, littoral);

        Chantier chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        missionChoisie = missionRepository.save(nouvelleMission("MIS-CHOISIE-" + suffixe, chantier));
        autreMission = missionRepository.save(nouvelleMission("MIS-AUTRE-" + suffixe, chantier));
    }

    /** La mission choisie a la creation est bien enregistree et visible sur le bon de sortie. */
    @Test
    void creationAvecMissionId_missionEnregistreeEtVisible() throws Exception {
        String token = seConnecter(agent.getIdentifiant());
        JsonNode bon = creerBonDeSortie(token, missionChoisie.getId());
        assertThat(bon.get("missionSelectionneeId").asLong()).isEqualTo(missionChoisie.getId());
        assertThat(bon.get("missionSelectionneeCodeHN").asText()).isEqualTo(missionChoisie.getCodeHN().getCode());
    }

    /**
     * Si l'agent est effectivement affecte a la mission choisie a la date de
     * sortie, la validation resout correctement l'affectation vers cette
     * mission (aucun avertissement).
     */
    @Test
    void validation_agentAffecteALaMissionChoisie_affectationResolue() throws Exception {
        AffectationMission affectation = affecterAgent(agent, missionChoisie, LocalDate.now().minusDays(1));
        String tokenAgent = seConnecter(agent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, missionChoisie.getId());
        long bonId = bon.get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectationMissionId").value(affectation.getId()))
                .andExpect(jsonPath("$.avertissementAffectation").doesNotExist());
    }

    /**
     * Si l'agent est affecte a une AUTRE mission que celle choisie sur le bon
     * de sortie, l'affectation n'est PAS resolue (jamais acceptee par
     * coincidence de date) - avertissement actionnable, jamais bloquant.
     */
    @Test
    void validation_agentAffecteAUneAutreMission_avertissementJamaisSilencieux() throws Exception {
        affecterAgent(agent, autreMission, LocalDate.now().minusDays(1));
        String tokenAgent = seConnecter(agent.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());
        JsonNode bon = creerBonDeSortie(tokenAgent, missionChoisie.getId());
        long bonId = bon.get("id").asLong();

        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/viser").header("Authorization", "Bearer " + tokenAgent))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectationMissionId").doesNotExist())
                .andExpect(jsonPath("$.avertissementAffectation", org.hamcrest.Matchers.containsString(
                        missionChoisie.getCodeHN().getCode())));
    }

    /** Filtre "nom complet" (evolution du 2026-08-27, section 13-15) sur la liste des bons de sortie. */
    @Test
    void listerAvecFiltreNomComplet() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        String tokenAgent = seConnecter(agent.getIdentifiant());
        creerBonDeSortie(tokenAgent, null);

        String reponse = mockMvc.perform(get("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenCa)
                        .param("nomComplet", agent.getNom()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode liste = objectMapper.readTree(reponse);
        assertThat(liste.size()).isGreaterThan(0);
        for (JsonNode bs : liste) {
            assertThat(bs.get("agentNomComplet").asText().toLowerCase()).contains(agent.getNom().toLowerCase());
        }

        String reponseVide = mockMvc.perform(get("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenCa)
                        .param("nomComplet", "NomQuiNexistePas" + suffixe))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponseVide)).isEmpty();
    }

    // --- Aides ---

    private AffectationMission affecterAgent(Utilisateur agentAffecte, Mission mission, LocalDate debut) {
        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agentAffecte);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(debut);
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(agentAffecte);
        return affectationMissionRepository.save(affectation);
    }

    private JsonNode creerBonDeSortie(String token, Long missionId) throws Exception {
        String reponse = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("missionId", missionId);
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test lien mission");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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
        utilisateur.setNom("Nom" + suffixe);
        utilisateur.setPrenom("Prenom");
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

    private Chantier nouveauChantier(String code, String libelle) {
        Chantier c = new Chantier();
        c.setCodeAffaire(code);
        c.setLibelle(libelle);
        return c;
    }

    private Mission nouvelleMission(String codeMission, Chantier chantier) {
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN(codeMission, chantier));
        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(2));
        mission.setStatut(StatutMission.PLANIFIEE);
        return mission;
    }

    private CodeHN nouveauCodeHN(String code, Chantier chantier) {
        CodeHN codeHN = new CodeHN();
        codeHN.setCode(code);
        codeHN.setLibelle("Code mission de test");
        codeHN.setChantier(chantier);
        return codeHN;
    }
}
