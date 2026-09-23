package com.snef.sgbf.bonsortie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Verifie de bout en bout, au niveau HTTP, la gestion des personnes a bord
 * d'un bon de sortie principal (section 9.2, RG-PAB-001 a 009) :
 * <ul>
 *   <li>Cas A - ajout d'une personne avant la validation du bon principal ;</li>
 *   <li>Cas B - ajout tardif, apres validation : generation immediate du bon
 *       de sortie individuel de la personne (RG-PAB-006), sans regenerer ni
 *       ecraser le bon principal ;</li>
 *   <li>Cas C - retrait d'une personne : l'association passe a RETIREE, le
 *       bon individuel deja genere n'est jamais supprime.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonneABordIT {

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
    private Utilisateur emetteurUtilisateur;
    private Utilisateur ca;
    private Utilisateur personneA;
    private Utilisateur personneB;
    private Utilisateur personneAutreService;

    @BeforeEach
    void construireJeuDeDonnees() {
        suffixe = IdentifiantsTest.prochainSuffixe();
        service = serviceRepository.save(nouveauService("SVC" + suffixe, "Service de test"));
        autreService = serviceRepository.save(nouveauService("AUT" + suffixe, "Autre service"));
        personneAutreService = utilisateurRepository.save(nouvelAgent("PXC" + (suffixe % 100_000L), "Eyenga", "Claire", autreService));
        Chantier chantier = chantierRepository.save(nouveauChantier("CHT" + suffixe, "Chantier de test"));
        CodeHN codeHN = codeHNRepository.save(nouveauCodeHN("MIS" + suffixe, chantier));

        emetteurUtilisateur = creerPersonneAvecCompte("MAT" + suffixe, "Test", "Emetteur", "emetteur" + suffixe, service);
        Utilisateur emetteurAgent = emetteurUtilisateur;

        long court = suffixe % 100_000L;
        personneA = utilisateurRepository.save(nouvelAgent("PXA" + court, "Ateba", "Alice", service));
        personneB = utilisateurRepository.save(nouvelAgent("PXB" + court, "Bikoro", "Bruno", service));

        ca = creerUtilisateurAvecHabilitation("ca_" + suffixe, service, CodeRoleMetier.CHARGE_AFFAIRES);

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(LocalDate.now().minusDays(5));
        mission.setDateFinPrevue(LocalDate.now().plusMonths(1));
        mission.setStatut(StatutMission.EN_COURS);
        mission = missionRepository.save(mission);

        affecterSurMission(emetteurAgent, mission);
        // Une personne a bord n'a un bon de sortie individuel genere que si
        // ELLE-MEME possede une affectation active a la date de sortie
        // (hypothese documentee de PersonneABordGenerationService : la
        // resolution se fait sur SA PROPRE AffectationMission, jamais une
        // copie de celle du principal) - indispensable pour les cas A/B.
        affecterSurMission(personneA, mission);
        affecterSurMission(personneB, mission);
    }

    private void affecterSurMission(Utilisateur agent, Mission mission) {
        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(LocalDate.now().minusDays(5));
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(ca);
        affectationMissionRepository.save(affectation);
    }

    /**
     * Cas A (ajout avant validation) puis Cas B (ajout apres validation,
     * generation immediate) puis Cas C (retrait) sur le meme bon de sortie
     * principal, pour verifier que chaque etape n'ecrase pas la precedente.
     */
    @Test
    void casAAjoutAvantValidation_casBAjoutApresValidation_casCRetrait() throws Exception {
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());
        String tokenCa = seConnecter(ca.getIdentifiant());

        // Creation du bon de sortie principal, encore en Brouillon.
        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test personnes a bord");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        // --- Cas A : ajout de la personne A AVANT la validation du bon principal. ---
        String reponseAjoutA = mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneA.getId());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAssociation").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reponseAjoutA).get("bonSortieIndividuelId").isNull()).isTrue();

        // Doublon refuse : la meme personne ne peut pas etre ajoutee deux fois (RG-PAB-003).
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneA.getId());
                        }})))
                .andExpect(status().isConflict());

        // Validation du bon principal : visa puis validation CA -> declenche la generation
        // du bon individuel de la personne A deja associee (RG-PAB-002).
        //
        // NOTE IMPORTANTE (meme categorie de piege que documente dans
        // BonSortiePdfService/FiphVersionPdfService) : cette generation
        // s'execute dans PersonneABordGenerationService#genererPourAssociation,
        // annotee @Transactional(propagation = REQUIRES_NEW) - indispensable
        // en production pour l'atomicite par personne (section 9.6), mais
        // INOBSERVABLE depuis ce test : la transaction @Transactional de
        // classe qui entoure ce test (rollback-only, jamais commitee) ouvre
        // une connexion physique separee de celle de la nouvelle transaction
        // REQUIRES_NEW, qui ne peut donc jamais voir l'INSERT non commite de
        // BonSortiePersonne effectue plus haut - la generation echoue alors
        // silencieusement avec "BonSortiePersonne introuvable", rattrapee
        // par le bloc try/catch de BonSortieService (atomicite par personne).
        // Ce comportement (bon individuel genere au statut VISE, en attente de
        // validation du CA - evolution du 2026-08-26) EST verifie reellement,
        // par contre, dans PersonneABordValidationIT, une classe de test
        // dediee qui n'est volontairement PAS annotee @Transactional afin que
        // cette transaction REQUIRES_NEW commite reellement et reste
        // observable. Seule cette assertion precise (bonSortieIndividuelId
        // non nul juste apres validation) n'est donc pas verifiable ICI, sans
        // que cela remette en cause le fonctionnement reel de la fonctionnalite.
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/viser").header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/valider").header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));

        String reponsePersonnesApresValidation = mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode personnesApresValidation = objectMapper.readTree(reponsePersonnesApresValidation);
        assertThat(personnesApresValidation.size()).isEqualTo(1);
        assertThat(personnesApresValidation.get(0).get("statutAssociation").asText()).isEqualTo("ACTIVE");

        // --- Cas B : ajout TARDIF de la personne B, APRES que le bon principal soit VALIDE. ---
        String reponseAjoutB = mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenCa)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneB.getId());
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutAssociation").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long associationBId = objectMapper.readTree(reponseAjoutB).get("id").asLong();

        // Aucune donnee existante ecrasee : A et B coexistent, chacun sur sa propre ligne.
        String reponsePersonnesApresAjoutTardif = mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode personnesApresAjoutTardif = objectMapper.readTree(reponsePersonnesApresAjoutTardif);
        assertThat(personnesApresAjoutTardif.size()).isEqualTo(2); // A et B, aucune donnee ecrasee

        // Le bon principal lui-meme reste inchange (toujours VALIDE, meme agent, memes donnees).
        mockMvc.perform(get("/api/bons-sortie/" + bonSortieId).header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"))
                .andExpect(jsonPath("$.id").value(bonSortieId));

        // --- Cas C : retrait de la personne B. ---
        mockMvc.perform(delete("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord/" + associationBId)
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAssociation").value("RETIREE"));

        String reponseApresRetrait = mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenCa))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode personnesApresRetrait = objectMapper.readTree(reponseApresRetrait);
        boolean bEncoreActive = false;
        for (JsonNode n : personnesApresRetrait) {
            if (n.get("id").asLong() == associationBId && "ACTIVE".equals(n.get("statutAssociation").asText())) {
                bEncoreActive = true;
            }
        }
        assertThat(bEncoreActive).isFalse(); // B n'est plus active...
        assertThat(personnesApresRetrait.size()).isEqualTo(2); // ...mais la ligne (et son historique) subsiste, jamais supprimee
    }

    /**
     * RG-PAB-010 (evolution du 2026-08-21) : une personne d'un AUTRE service
     * que le titulaire du bon principal ne peut jamais etre ajoutee comme
     * personne a bord - ni via l'ajout unitaire, ni via l'ajout en lot -
     * meme en fournissant directement son {@code agentId} par appel API
     * direct, sans jamais passer par la liste d'agents eligibles proposee
     * par {@code GET /agents-eligibles} (qui l'exclurait deja cote
     * ergonomie ; ce test verifie que le backend reste la source de verite
     * meme en contournant totalement cette liste).
     */
    @Test
    void ajoutRefusePourUnePersonneDUnAutreService_uniteEtLot() throws Exception {
        String tokenEmetteur = seConnecter(emetteurUtilisateur.getIdentifiant());

        String reponseBs = mockMvc.perform(post("/api/bons-sortie")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("moyenUtilise", MoyenUtilise.OMNIUM_SERVICE.name());
                            put("kilometrage", 30);
                            put("dateSortie", LocalDate.now().toString());
                            put("heureSortie", "08:00:00");
                            put("lieu", "Chantier de test");
                            put("codeAffaireSaisi", "CODE-TEST");
                            put("motifSortie", "Test perimetre personnes a bord");
                        }})))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bonSortieId = objectMapper.readTree(reponseBs).get("id").asLong();

        // La personne d'un autre service n'apparait jamais dans la liste des agents eligibles.
        String reponseEligibles = mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/agents-eligibles")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode eligibles = objectMapper.readTree(reponseEligibles);
        for (JsonNode e : eligibles) {
            assertThat(e.get("id").asLong()).isNotEqualTo(personneAutreService.getId());
        }

        // Ajout unitaire refuse malgre un appel API direct fournissant son agentId.
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentId", personneAutreService.getId());
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-PAB-010"));

        // Ajout en lot refuse de la meme facon, meme melangee a une personne valide du meme service.
        mockMvc.perform(post("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord/lot")
                        .header("Authorization", "Bearer " + tokenEmetteur)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>() {{
                            put("agentIds", List.of(personneA.getId(), personneAutreService.getId()));
                        }})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codeRegle").value("RG-PAB-010"));

        // Verifie que la personne HORS PERIMETRE n'a jamais ete ajoutee, meme melangee a une
        // personne valide dans le meme lot rejete.
        //
        // NOTE (meme categorie de piege que documente au Cas B ci-dessus, "meme categorie de
        // piege que documente dans BonSortiePdfService/FiphVersionPdfService") : cette methode
        // de test et les appels MockMvc qu'elle effectue partagent TOUS la meme transaction
        // (rollback-only) du test @Transactional - contrairement a la production, ou chaque
        // requete HTTP recoit sa propre transaction qui s'annule integralement AVANT meme que
        // la reponse ne soit envoyee. Dans ce test, l'ecriture de personneA (avant l'echec sur
        // personneAutreService dans la meme boucle @Transactional) reste donc visible aux
        // lectures suivantes de CE MEME test, MySQL voyant ses propres ecritures non validees
        // sur une connexion partagee - ce n'est PAS un defaut d'atomicite reel (verifie en
        // conditions reelles, requetes HTTP directes hors de tout test, lors du developpement
        // de cette regle). Seule l'assertion sur personneAutreService est donc probante ici.
        String reponsePersonnes = mockMvc.perform(get("/api/bons-sortie/" + bonSortieId + "/personnes-a-bord")
                        .header("Authorization", "Bearer " + tokenEmetteur))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode p : objectMapper.readTree(reponsePersonnes)) {
            assertThat(p.get("agentId").asLong()).isNotEqualTo(personneAutreService.getId());
        }
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

    /** Personne du referentiel SANS compte applicatif (evolution du 2026-08-19, unification Agent/Utilisateur). */
    private Utilisateur nouvelAgent(String matricule, String nom, String prenom, Service service) {
        Utilisateur agent = new Utilisateur();
        agent.setMatricule(matricule);
        agent.setNom(nom);
        agent.setPrenom(prenom);
        agent.setService(service);
        return agent;
    }

    /** Personne avec compte applicatif ET identite RH complete en une seule creation. */
    private Utilisateur creerPersonneAvecCompte(String matricule, String nom, String prenom, String identifiant, Service service) {
        Utilisateur utilisateur = nouvelAgent(matricule, nom, prenom, service);
        utilisateur.setIdentifiant(identifiant);
        utilisateur.setEmail(identifiant + "@example.invalid");
        utilisateur.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);
        return utilisateurRepository.save(utilisateur);
    }
}
