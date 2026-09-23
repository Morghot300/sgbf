package com.snef.sgbf.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.identite.service.UtilisateurService;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import com.snef.sgbf.security.entity.RefreshToken;
import com.snef.sgbf.security.entity.RefreshTokenRepository;
import com.snef.sgbf.support.IdentifiantsTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie, au niveau HTTP reel, l'evolution du 2026-08-26 : le jeton de
 * rafraichissement lui-meme (cote serveur, {@code RefreshToken.dateExpiration})
 * ne porte plus aucune expiration temporelle - seules une deconnexion
 * explicite ou une suspension/desactivation de compte le revoquent
 * (verifications inchangees par l'evolution du 2026-08-27 ci-dessous).
 *
 * <p>Le COOKIE HTTP qui porte ce jeton, lui, redevient un cookie de session
 * depuis l'evolution du 2026-08-27 (section 24-26, decision confirmee) - le
 * navigateur le supprime de lui-meme a la fermeture reelle de toutes ses
 * fenetres, un comportement natif que MockMvc ne peut pas exercer ici (voir
 * {@code AuthController#construireCookieRefresh}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessionSansExpirationIT {

    private static final String MOT_DE_PASSE = "MotDePasseTest123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UtilisateurService utilisateurService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Utilisateur compte;

    @BeforeEach
    void construireJeuDeDonnees() {
        long suffixe = IdentifiantsTest.prochainSuffixe();
        Service service = new Service();
        service.setCodeService("SVC" + suffixe);
        service.setLibelle("Service de test");
        service = serviceRepository.save(service);

        compte = new Utilisateur();
        compte.setIdentifiant("session_" + suffixe);
        compte.setEmail("session_" + suffixe + "@example.invalid");
        compte.setMotDePasseHash(passwordEncoder.encode(MOT_DE_PASSE));
        compte.setStatutCompte(StatutCompte.ACTIF);
        compte.setService(service);
        compte = utilisateurRepository.save(compte);
    }

    /** Le jeton de rafraichissement emis a la connexion ne porte plus aucune date d'expiration. */
    @Test
    void jetonDeRafraichissementEmisSansExpiration() throws Exception {
        String jetonComplet = seConnecter();
        String id = jetonComplet.split("\\.", 2)[0];

        RefreshToken jeton = refreshTokenRepository.findById(id).orElseThrow();
        assertThat(jeton.getDateExpiration()).isNull();
        assertThat(jeton.estValide()).isTrue();
    }

    /** Le rafraichissement peut se repeter indefiniment (rotation), sans jamais echouer pour cause d'expiration. */
    @Test
    void rafraichissementRepeteFonctionneSansExpirer() throws Exception {
        String jeton1 = seConnecter();
        String jeton2 = rafraichir(jeton1);
        String jeton3 = rafraichir(jeton2);
        rafraichir(jeton3);

        // Rotation a usage unique : le premier jeton, deja consomme, ne peut plus jamais resservir.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", jeton1)))
                .andExpect(status().isUnauthorized());
    }

    /** Une deconnexion explicite revoque le jeton : tout rafraichissement ulterieur avec l'ancien cookie est refuse. */
    @Test
    void deconnexionExpliciteBloqueToutRafraichissementUlterieur() throws Exception {
        MvcResult resultatLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json").content(corpsLogin()))
                .andExpect(status().isOk())
                .andReturn();
        String jetonAcces = objectMapper.readTree(resultatLogin.getResponse().getContentAsString())
                .get("jetonAcces").asText();
        String jetonRafraichissement = extraireValeurCookie(setCookie(resultatLogin));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jetonAcces))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", jetonRafraichissement)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Une session sans expiration temporelle ne doit jamais devenir une session "eternelle
     * et incontrolable" : suspendre ou desactiver un compte revoque immediatement tous ses
     * jetons de rafraichissement (UtilisateurService.changerStatut), independamment de toute
     * deconnexion explicite prealable.
     */
    @Test
    void suspensionDuCompteRevoqueLaSessionSansDeconnexionExplicite() throws Exception {
        String jetonRafraichissement = seConnecter();

        utilisateurService.changerStatut(compte.getId(), StatutCompte.DESACTIVE, compte);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", jetonRafraichissement)))
                .andExpect(status().isUnauthorized());
    }

    private String seConnecter() throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json").content(corpsLogin()))
                .andExpect(status().isOk())
                .andReturn();
        return extraireValeurCookie(setCookie(resultat));
    }

    private String rafraichir(String jetonCourant) throws Exception {
        MvcResult resultat = mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", jetonCourant)))
                .andExpect(status().isOk())
                .andReturn();
        return extraireValeurCookie(setCookie(resultat));
    }

    private static String setCookie(MvcResult resultat) {
        return resultat.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    private String corpsLogin() throws Exception {
        return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("identifiant", compte.getIdentifiant());
            put("motDePasse", MOT_DE_PASSE);
        }});
    }

    private static String extraireValeurCookie(String setCookieHeader) {
        String prefixe = "refreshToken=";
        int debut = setCookieHeader.indexOf(prefixe) + prefixe.length();
        int fin = setCookieHeader.indexOf(';', debut);
        return fin >= 0 ? setCookieHeader.substring(debut, fin) : setCookieHeader.substring(debut);
    }
}
