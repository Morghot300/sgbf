package com.snef.sgbf.bonsortie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Evolution du 2026-08-27 (brief "Evolution avancee du module Bon de Sortie,
 * Missions et FIPH", section 3-4-30) : personnel d'un service, selectionnable
 * par cases a cocher AVANT MEME la creation d'un bon de sortie (personne
 * principale, personnes a bord) - remplace la simple saisie libre d'un
 * identifiant numerique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonnelServiceIT {

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
    private Utilisateur agentLittoral;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        littoral = serviceRepository.save(nouveauService("LIT" + suffixe, "Service Littoral"));
        centre = serviceRepository.save(nouveauService("CTR" + suffixe, "Service Centre"));
        caLittoral = creerUtilisateurAvecHabilitation("ca_lit_" + suffixe, littoral, CodeRoleMetier.CHARGE_AFFAIRES);
        caCentre = creerUtilisateurAvecHabilitation("ca_ctr_" + suffixe, centre, CodeRoleMetier.CHARGE_AFFAIRES);
        agentLittoral = creerUtilisateur("agent_lit_" + suffixe, littoral);
    }

    /** Un agent peut consulter le personnel de son PROPRE service. */
    @Test
    void agentPeutConsulterLePersonnelDeSonPropreService() throws Exception {
        String token = seConnecter(agentLittoral.getIdentifiant());
        String reponse = mockMvc.perform(get("/api/bons-sortie/personnel-service/" + littoral.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode liste = objectMapper.readTree(reponse);
        boolean caTrouve = false;
        for (JsonNode p : liste) {
            if (p.get("id").asLong() == caLittoral.getId()) {
                caTrouve = true;
            }
        }
        assertThat(caTrouve).isTrue();
    }

    /** Un Charge d'Affaires peut consulter le personnel de son service. */
    @Test
    void caPeutConsulterLePersonnelDeSonService() throws Exception {
        String token = seConnecter(caLittoral.getIdentifiant());
        mockMvc.perform(get("/api/bons-sortie/personnel-service/" + littoral.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** Un Charge d'Affaires d'un AUTRE service ne peut pas consulter ce personnel (RG-SEC-002). */
    @Test
    void caDunAutreServiceNePeutPasConsulter() throws Exception {
        String token = seConnecter(caCentre.getIdentifiant());
        mockMvc.perform(get("/api/bons-sortie/personnel-service/" + littoral.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** Un agent d'un AUTRE service ne peut pas non plus consulter (ni son propre service, ni gestionnaire). */
    @Test
    void agentDunAutreServiceNePeutPasConsulter() throws Exception {
        Utilisateur agentCentre = creerUtilisateur("agent_ctr_" + suffixe, centre);
        String token = seConnecter(agentCentre.getIdentifiant());
        mockMvc.perform(get("/api/bons-sortie/personnel-service/" + littoral.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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
}
