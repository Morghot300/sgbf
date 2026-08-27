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
 * Evolution du 2026-08-26 - "les mission et code mission ne seront pas des
 * liste deroulante mais une zone texte ou on ajoutera des mission au clavier" :
 * la creation d'une mission (et la reaffectation "vers une nouvelle mission")
 * saisit desormais le chantier et le code mission librement au clavier -
 * reutilisation d'un code deja existant, creation a la volee d'un code
 * inedit (chantier et/ou code mission), conflit si un code mission deja pris
 * est reutilise pour un AUTRE chantier (RG-MIS-015).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreationMissionSaisieLibreIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ChantierRepository chantierRepository;
    @Autowired private CodeHNRepository codeHNRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private HabilitationRepository habilitationRepository;
    @Autowired private RoleMetierRepository roleMetierRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private long suffixe;
    private Service service;
    private Utilisateur ca;
    private Utilisateur agent;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);
        agent = utilisateurRepository.save(nouvelAgent("MAT" + suffixe, "Test", "Agent", service));
    }

    /** Chantier ET code mission inedits : les deux sont crees a la volee, decouvrables ensuite via les referentiels. */
    @Test
    void creationMission_chantierEtCodeMissionInedits_creesALaVolee() throws Exception {
        String token = seConnecter(ca.getIdentifiant());
        String codeChantier = "CHT-NEUF-" + suffixe;
        String codeMission = "MIS-NEUF-" + suffixe;

        JsonNode mission = creerMissionParTexte(token, codeChantier, "Chantier tout neuf", codeMission, "Mission toute neuve",
                LocalDate.now(), LocalDate.now().plusMonths(1), status().isCreated());
        assertThat(mission.get("codeHN").asText()).isEqualTo(codeMission);
        assertThat(mission.get("chantierLibelle").asText()).isEqualTo("Chantier tout neuf");

        assertThat(chantierRepository.findByCodeAffaire(codeChantier)).isPresent();
        assertThat(codeHNRepository.findByCode(codeMission)).isPresent();

        String reponseChantiers = mockMvc.perform(get("/api/referentiels/chantiers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode chantiers = objectMapper.readTree(reponseChantiers);
        boolean chantierTrouve = false;
        for (JsonNode c : chantiers) {
            if (c.get("codeAffaire").asText().equals(codeChantier)) {
                chantierTrouve = true;
            }
        }
        assertThat(chantierTrouve).isTrue();
    }

    /** Un code chantier ET un code mission deja existants sont reutilises tels quels, sans creer de doublon. */
    @Test
    void creationMission_codesExistants_reutilisesSansDoublon() throws Exception {
        String token = seConnecter(ca.getIdentifiant());
        Chantier chantierExistant = chantierRepository.save(nouveauChantier("CHT-EXIST-" + suffixe, "Chantier existant"));
        CodeHN codeExistant = codeHNRepository.save(nouveauCodeHN("MIS-EXIST-" + suffixe, chantierExistant));
        long nbChantiersAvant = chantierRepository.count();
        long nbCodesAvant = codeHNRepository.count();

        JsonNode mission = creerMissionParTexte(token, chantierExistant.getCodeAffaire(), null, codeExistant.getCode(), null,
                LocalDate.now(), LocalDate.now().plusMonths(1), status().isCreated());
        assertThat(mission.get("chantierId").asLong()).isEqualTo(chantierExistant.getId());

        assertThat(chantierRepository.count()).isEqualTo(nbChantiersAvant); // aucun doublon
        assertThat(codeHNRepository.count()).isEqualTo(nbCodesAvant);
    }

    /** RG-MIS-015 : un code mission deja pris par un AUTRE chantier ne peut pas etre reutilise. */
    @Test
    void creationMission_codeMissionDejaPrisParAutreChantier_refusee() throws Exception {
        String token = seConnecter(ca.getIdentifiant());
        Chantier chantierA = chantierRepository.save(nouveauChantier("CHT-A-" + suffixe, "Chantier A"));
        Chantier chantierB = chantierRepository.save(nouveauChantier("CHT-B-" + suffixe, "Chantier B"));
        CodeHN codeSousA = codeHNRepository.save(nouveauCodeHN("MIS-PARTAGE-" + suffixe, chantierA));

        mockMvc.perform(post("/api/missions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("codeChantier", chantierB.getCodeAffaire());
                            put("libelleChantier", null);
                            put("codeMission", codeSousA.getCode());
                            put("libelleCodeMission", null);
                            put("dateDebutPrevue", LocalDate.now().toString());
                            put("dateFinPrevue", LocalDate.now().plusMonths(1).toString());
                            put("missionPrecedenteId", null);
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-MIS-015"));
    }

    /** La reaffectation "vers une nouvelle mission" cree, elle aussi, un code mission inedit a la volee. */
    @Test
    void reaffectationMiMission_codeMissionInedit_creeALaVolee() throws Exception {
        String token = seConnecter(ca.getIdentifiant());
        String codeChantier = "CHT-REA-" + suffixe;
        String codeMissionInitial = "MIS-REA-INIT-" + suffixe;
        JsonNode missionInitiale = creerMissionParTexte(token, codeChantier, "Chantier reaffectation", codeMissionInitial, null,
                LocalDate.now(), LocalDate.now().plusMonths(2), status().isCreated());
        long missionInitialeId = missionInitiale.get("id").asLong();

        mockMvc.perform(post("/api/affectations-mission")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("missionId", missionInitialeId);
                            put("dateDebutAffectation", LocalDate.now().minusDays(2).toString());
                        }})))
                .andExpect(status().isCreated());

        String codeMissionCible = "MIS-REA-CIBLE-" + suffixe;
        LocalDate dateReaffectation = LocalDate.now().plusDays(1);
        String reponse = mockMvc.perform(post("/api/affectations-mission/reaffecter-mi-mission")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", agent.getId());
                            put("codeChantier", codeChantier);
                            put("libelleChantier", null);
                            put("codeMission", codeMissionCible);
                            put("libelleCodeMission", "Mission cible toute neuve");
                            put("dateDebutPrevueMission", dateReaffectation.toString());
                            put("dateFinPrevueMission", dateReaffectation.plusMonths(1).toString());
                            put("dateDebutAffectation", dateReaffectation.toString());
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long missionCibleId = objectMapper.readTree(reponse).get("missionId").asLong();

        assertThat(codeHNRepository.findByCode(codeMissionCible)).isPresent();
        mockMvc.perform(get("/api/missions/" + missionCibleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeHN").value(codeMissionCible));
    }

    // --- Aides ---

    private JsonNode creerMissionParTexte(String token, String codeChantier, String libelleChantier, String codeMission,
                                           String libelleCodeMission, LocalDate debut, LocalDate fin,
                                           org.springframework.test.web.servlet.ResultMatcher statutAttendu) throws Exception {
        String reponse = mockMvc.perform(post("/api/missions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("codeChantier", codeChantier);
                            put("libelleChantier", libelleChantier);
                            put("codeMission", codeMission);
                            put("libelleCodeMission", libelleCodeMission);
                            put("dateDebutPrevue", debut.toString());
                            put("dateFinPrevue", fin.toString());
                            put("missionPrecedenteId", null);
                        }})))
                .andExpect(statutAttendu)
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

    private Utilisateur nouvelAgent(String matricule, String nom, String prenom, Service svc) {
        Utilisateur a = new Utilisateur();
        a.setMatricule(matricule);
        a.setNom(nom);
        a.setPrenom(prenom);
        a.setService(svc);
        return a;
    }
}
