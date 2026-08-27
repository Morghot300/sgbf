package com.snef.sgbf.mission;

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
 * Decoupage complet d'un chevauchement de missions (evolution du 2026-08-27,
 * brief "Evolution avancee du module Bon de Sortie, Missions et FIPH",
 * section 18-22 - decision confirmee explicitement) : exemple exact du
 * brief - Mission A du lundi au vendredi (5 jours), nouvelle Mission B
 * affectee du mercredi au jeudi (2 jours) -&gt; Mission A devient
 * lundi+mardi (avant) puis vendredi (reprise automatique), Mission B devient
 * mercredi+jeudi.
 *
 * <p>Necessite d'assouplir la contrainte "une seule affectation ACTIVE par
 * agent" (V16) - verifiee au passage par {@link #uneSeuleAffectationActiveParPeriodeReste}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScissionMissionsChevauchantesIT {

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
    private Service service;
    private Utilisateur ca;
    private Utilisateur agent;
    private Chantier chantier;
    private Mission missionA;
    private LocalDate lundi;
    private LocalDate mardi;
    private LocalDate mercredi;
    private LocalDate jeudi;
    private LocalDate vendredi;
    private long affectationAId;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = System.nanoTime();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        agent = creerUtilisateur("agent_" + suffixe, service);
        chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));

        lundi = LocalDate.now();
        mardi = lundi.plusDays(1);
        mercredi = lundi.plusDays(2);
        jeudi = lundi.plusDays(3);
        vendredi = lundi.plusDays(4);

        CodeHN codeA = codeHNRepository.save(nouveauCodeHN("MIS-A-" + suffixe, chantier));
        missionA = new Mission();
        missionA.setCodeHN(codeA);
        missionA.setChantier(chantier);
        missionA.setDateDebutPrevue(lundi);
        missionA.setDateFinPrevue(vendredi);
        missionA.setStatut(StatutMission.EN_COURS);
        missionA = missionRepository.save(missionA);

        // Affectation initiale de l'agent sur la Mission A, du lundi au vendredi (5 jours) -
        // inseree directement (l'endpoint de creation initiale ne permet pas de definir une date
        // de fin des la creation).
        AffectationMission affectationA = new AffectationMission();
        affectationA.setAgent(agent);
        affectationA.setMission(missionA);
        affectationA.setDateDebutAffectation(lundi);
        affectationA.setDateFinAffectation(vendredi);
        affectationA.setStatutAffectation(StatutAffectation.ACTIVE);
        affectationA.setCreePar(agent);
        affectationAId = affectationMissionRepository.save(affectationA).getId();
    }

    @Test
    void missionBAuMilieuDeMissionA_scinderEnLundiMardiPuisVendredi() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());

        String reponse = mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("codeChantier", chantier.getCodeAffaire());
                            put("libelleChantier", chantier.getLibelle());
                            put("codeMission", "MIS-B-" + suffixe);
                            put("libelleCodeMission", "Mission B");
                            put("dateDebutPrevueMission", mercredi.toString());
                            put("dateFinPrevueMission", jeudi.toString());
                            put("dateDebutAffectation", mercredi.toString());
                            put("dateFinAffectation", jeudi.toString());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAffectation").value("ACTIVE"))
                .andExpect(jsonPath("$.dateDebutAffectation").value(mercredi.toString()))
                .andExpect(jsonPath("$.dateFinAffectation").value(jeudi.toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode affectationB = objectMapper.readTree(reponse);
        long missionBId = affectationB.get("missionId").asLong();

        // Mission A (l'affectation d'origine) tronquee : lundi-mardi seulement, INTERROMPUE.
        mockMvc.perform(get("/api/affectations-mission/" + affectationAId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAffectation").value("INTERROMPUE"))
                .andExpect(jsonPath("$.dateDebutAffectation").value(lundi.toString()))
                .andExpect(jsonPath("$.dateFinAffectation").value(mardi.toString()));

        // Reprise automatique de Mission A le vendredi (jusqu'a son propre terme d'origine).
        List<AffectationMission> affectationsMissionA = affectationMissionRepository
                .findByMission_IdOrderByDateDebutAffectationAsc(missionA.getId());
        assertThat(affectationsMissionA).hasSize(2); // l'originale (lundi-mardi) + la reprise (vendredi)
        AffectationMission reprise = affectationsMissionA.stream()
                .filter(a -> a.getId() != affectationAId)
                .findFirst().orElseThrow();
        assertThat(reprise.getStatutAffectation()).isEqualTo(StatutAffectation.ACTIVE);
        assertThat(reprise.getDateDebutAffectation()).isEqualTo(vendredi);
        assertThat(reprise.getDateFinAffectation()).isEqualTo(vendredi);
        assertThat(reprise.getAffectationPrecedente().getId()).isEqualTo(affectationAId);

        // Mission A elle-meme redevient EN_COURS (la reprise l'a fait sortir d'INTERROMPUE).
        mockMvc.perform(get("/api/missions/" + missionA.getId()).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));

        // Mission B : mercredi-jeudi uniquement, agent bien affecte.
        mockMvc.perform(get("/api/missions/" + missionBId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));
    }

    /**
     * Verifie que la contrainte reelle - jamais deux affectations d'un meme
     * agent sur des jours qui se chevauchent - continue d'etre appliquee
     * malgre l'assouplissement de l'index unique "une seule ACTIVE" (V16) :
     * une nouvelle affectation dont la periode chevauche a la fois B ET la
     * reprise de A est refusee (conflit).
     */
    @Test
    void uneSeuleAffectationActiveParPeriodeReste() throws Exception {
        String tokenCa = seConnecter(ca.getIdentifiant());
        mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("codeChantier", chantier.getCodeAffaire());
                            put("libelleChantier", chantier.getLibelle());
                            put("codeMission", "MIS-B2-" + suffixe);
                            put("libelleCodeMission", "Mission B2");
                            put("dateDebutPrevueMission", mercredi.toString());
                            put("dateFinPrevueMission", jeudi.toString());
                            put("dateDebutAffectation", mercredi.toString());
                            put("dateFinAffectation", jeudi.toString());
                        }})))
                .andExpect(status().isCreated());

        // Tentative de creer une affectation initiale sur une AUTRE mission qui chevauche vendredi
        // (deja couvert par la reprise automatique de Mission A) -> refusee (conflit).
        Mission missionCBrouillon = new Mission();
        missionCBrouillon.setCodeHN(codeHNRepository.save(nouveauCodeHN("MIS-C-" + suffixe, chantier)));
        missionCBrouillon.setChantier(chantier);
        missionCBrouillon.setDateDebutPrevue(vendredi);
        missionCBrouillon.setDateFinPrevue(vendredi.plusDays(10));
        missionCBrouillon.setStatut(StatutMission.PLANIFIEE);
        final Mission missionC = missionRepository.save(missionCBrouillon);

        mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", missionC.getId());
                            put("dateDebutAffectation", vendredi.toString());
                        }})))
                .andExpect(status().isConflict());
    }

    // --- Aides ---

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

    private Chantier nouveauChantier(String code, String libelle) {
        Chantier c = new Chantier();
        c.setCodeAffaire(code);
        c.setLibelle(libelle);
        return c;
    }

    private CodeHN nouveauCodeHN(String code, Chantier chantier) {
        CodeHN codeHN = new CodeHN();
        codeHN.setCode(code);
        codeHN.setLibelle("Code mission de test");
        codeHN.setChantier(chantier);
        return codeHN;
    }
}
