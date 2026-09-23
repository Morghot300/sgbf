package com.snef.sgbf.security;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.security.dto.LoginRequest;
import com.snef.sgbf.security.dto.TokenResponse;
import com.snef.sgbf.security.dto.UtilisateurCourantDto;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entree unique de l'authentification (section 16 : "S'authentifier -
 * point d'entree commun a tous les acteurs").
 *
 * <p><strong>Authentification simple</strong> (decision du 2026-08-17,
 * annule et remplace la double authentification par code e-mail mise en
 * place plus tot le meme jour - voir section K de l'analyse fonctionnelle) :
 * <pre>
 *   POST /login     identifiant/e-mail + mot de passe -&gt; jeton d'acces + cookie de rafraichissement
 *   POST /refresh    renouvelle le jeton d'acces via le cookie de rafraichissement
 *   POST /logout     revoque la session
 * </pre>
 * Un mot de passe correct suffit desormais, seul, a authentifier un
 * utilisateur - aucune seconde etape n'est plus demandee, pour aucun role.
 * Le jeton de rafraichissement reste pose en cookie {@code HttpOnly}/
 * {@code Secure}/{@code SameSite=Strict}, jamais expose au JavaScript cote
 * client. Les comptes desactives ou verrouilles ({@link CustomUserDetails})
 * restent refuses au meme titre qu'un mot de passe incorrect - la
 * suppression du second facteur ne touche a aucun controle d'autorisation
 * (roles, habilitations, perimetre par service), tous appliques en aval,
 * inchanges.
 *
 * <p><strong>Expiration reelle a la fermeture du navigateur</strong>
 * (evolution du 2026-08-27, brief "Evolution avancee du module Bon de
 * Sortie, Missions et FIPH", section 24-26 - decision confirmee
 * explicitement, remplace l'evolution du 2026-08-26 ci-dessous qui faisait
 * deliberement persister la session au-dela) : le cookie de rafraichissement
 * est desormais un cookie de SESSION (aucun {@code Max-Age}/{@code Expires})
 * - le navigateur le supprime lui-meme des que TOUTES ses fenetres/processus
 * se ferment reellement (pas seulement un onglet), sans action serveur
 * necessaire. Une deconnexion explicite ({@code /logout}) ou une
 * suspension/desactivation du compte revoquent en plus, immediatement,
 * tous les jetons cote serveur ({@code UtilisateurService.changerStatut}) -
 * un jeton de rafraichissement presente apres l'un ou l'autre de ces
 * evenements est donc toujours refuse par {@code /refresh}. Voir
 * {@code RefreshTokenService} pour le detail du mecanisme.
 *
 * <p><em>Historique</em> : l'evolution du 2026-08-26 avait introduit un
 * cookie a duree fixe de 400 jours ("session sans expiration d'inactivite"),
 * decision explicitement inversee ci-dessus.
 *
 * <p>Toutes les tentatives (reussies ou non) sont journalisees dans le
 * journal d'audit (section 26.1, 26.4).
 */
@RestController
@org.springframework.web.bind.annotation.RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_REFRESH = "refreshToken";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final boolean cookieSecurise;

    public AuthController(AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService,
                           AuditService auditService,
                           @Value("${app.security.cookie-secure:true}") boolean cookieSecurise) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.cookieSecurise = cookieSecurise;
    }

    /**
     * Verifie l'identifiant et le mot de passe et, en cas de succes, emet
     * immediatement un jeton d'acces et un cookie de rafraichissement -
     * "identifiants corrects = authentification reussie", sans etape
     * intermediaire. Un mot de passe incorrect ({@link BadCredentialsException})
     * ou un compte desactive/verrouille ({@link AccountStatusException}) sont
     * tous deux refuses et journalises comme echec de connexion.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest requete) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requete.identifiant(), requete.motDePasse()));
        } catch (BadCredentialsException | AccountStatusException e) {
            auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, requete.identifiant(), null,
                    TypeActionAudit.ECHEC_CONNEXION);
            throw e; // traduit en 401 par GlobalExceptionHandler - aucun jeton emis
        }

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        Utilisateur utilisateur = principal.getUtilisateur();

        auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, utilisateur.getId(), utilisateur,
                TypeActionAudit.CONNEXION_REUSSIE);

        return ResponseEntity.ok()
                .headers(headers -> headers.add(HttpHeaders.SET_COOKIE, construireCookieRefresh(utilisateur).toString()))
                .body(new TokenResponse(jwtService.genererJetonAcces(utilisateur.getId()),
                        jwtService.getDureeAccesMinutes() * 60));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> rafraichir(
            @CookieValue(name = COOKIE_REFRESH, required = false) String jetonRafraichissement) {
        if (jetonRafraichissement == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Utilisateur> utilisateurOpt = refreshTokenService.verifierEtRevoquer(jetonRafraichissement);
        if (utilisateurOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Utilisateur utilisateur = utilisateurOpt.get();

        return ResponseEntity.ok()
                .headers(headers -> headers.add(HttpHeaders.SET_COOKIE, construireCookieRefresh(utilisateur).toString()))
                .body(new TokenResponse(jwtService.genererJetonAcces(utilisateur.getId()),
                        jwtService.getDureeAccesMinutes() * 60));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> deconnexion(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal != null) {
            refreshTokenService.revoquerTousPourUtilisateur(principal.getUtilisateurId());
            auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, principal.getUtilisateurId(),
                    principal.getUtilisateur(), TypeActionAudit.DECONNEXION);
        }
        ResponseCookie cookieExpire = ResponseCookie.from(COOKIE_REFRESH, "")
                .httpOnly(true).secure(cookieSecurise).sameSite("Strict").path("/api/auth").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieExpire.toString())
                .build();
    }

    /**
     * Identite et roles actifs de l'utilisateur porteur du jeton d'acces
     * presente. Consommee par le frontend au demarrage de l'application pour
     * reconstruire son etat d'authentification (voir {@code frontend/src/auth/AuthContext.tsx}) -
     * jamais pour decider seule d'une autorisation, qui reste toujours
     * revalidee cote serveur a chaque appel sensible.
     */
    @GetMapping("/me")
    public UtilisateurCourantDto moi(@AuthenticationPrincipal CustomUserDetails principal) {
        Utilisateur utilisateur = principal.getUtilisateur();
        return new UtilisateurCourantDto(
                utilisateur.getId(),
                utilisateur.getIdentifiant(),
                utilisateur.getEmail(),
                utilisateur.getService() != null ? utilisateur.getService().getId() : null,
                utilisateur.getService() != null ? utilisateur.getService().getLibelle() : null,
                principal.getCodesRolesActifs());
    }

    /**
     * Cookie {@code HttpOnly}/{@code Secure}/{@code SameSite=Strict} portant
     * le jeton de rafraichissement - jamais accessible en JavaScript (protection
     * XSS) et jamais rejoue lors d'une requete inter-site (protection CSRF,
     * voir la justification dans {@code config.SecurityConfig}). Restreint au
     * chemin {@code /api/auth} : inutile de l'envoyer sur tous les autres appels API.
     *
     * <p>Deliberement SANS {@code maxAge} (evolution du 2026-08-27, section
     * 24-26 - decision confirmee) : un cookie sans {@code Max-Age}/{@code Expires}
     * est un cookie de SESSION, que le navigateur supprime lui-meme des que
     * toutes ses fenetres/tous ses processus se ferment reellement - c'est ce
     * mecanisme, natif et fiable, qui porte l'expiration a la fermeture du
     * navigateur, sans necessiter la moindre logique cote serveur.
     */
    private ResponseCookie construireCookieRefresh(Utilisateur utilisateur) {
        String jeton = refreshTokenService.emettre(utilisateur);
        return ResponseCookie.from(COOKIE_REFRESH, jeton)
                .httpOnly(true)
                .secure(cookieSecurise)
                .sameSite("Strict")
                .path("/api/auth")
                .build();
    }
}
