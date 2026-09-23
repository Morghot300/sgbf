package com.snef.sgbf.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gestionnaire centralise des exceptions, traduisant chaque erreur en un
 * corps de reponse conforme a RFC 7807 (Problem Details for HTTP APIs) via
 * {@link ProblemDetail} - support natif de Spring 6 / Boot 3.
 *
 * <p>Objectifs (brief §9, §11) :
 * <ul>
 *   <li>des codes HTTP et des messages coherents sur toute l'API ;</li>
 *   <li>aucune fuite d'information technique (trace, SQL, nom de classe) vers
 *       le client - {@link #handleUnexpected(Exception)} ne renvoie jamais le
 *       message brut d'une exception non prevue ;</li>
 *   <li>un traitement uniforme des violations de validation Bean Validation,
 *       champ par champ, pour que le frontend puisse afficher une erreur par
 *       champ de formulaire.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus().getReasonPhrase());
        if (ex instanceof BusinessRuleViolationException brve) {
            problem.setProperty("codeRegle", brve.getCodeRegle());
        }
        if (ex instanceof ForbiddenOperationException) {
            // RG-SEC-002 / section 26.5 : tout refus d'acces "hors perimetre" (identifiant syntaxiquement
            // valide mais hors habilitation) est une tentative a journaliser (section 26.4).
            journaliserAccesRefuse(request, ex.getMessage());
        }
        return problem;
    }

    /**
     * Violations Bean Validation (@Valid sur un DTO de requete) : restituees
     * champ par champ pour permettre au formulaire frontend de localiser
     * l'erreur exactement la ou elle se trouve (brief §10 : "gestion
     * professionnelle des erreurs" cote formulaire).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Un ou plusieurs champs sont invalides.");
        problem.setTitle("Validation echouee");

        Map<String, String> erreursParChamp = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                erreursParChamp.put(fieldError.getField(), fieldError.getDefaultMessage()));
        problem.setProperty("erreurs", erreursParChamp);
        return problem;
    }

    /**
     * Conflit de verrouillage optimiste (RG-SEC-001) : le {@code lockVersion}
     * soumis par le client ne correspond plus a celui persiste en base. Le
     * client doit recharger la ressource avant de rejouer sa modification -
     * aucune fusion automatique n'est tentee (section 26.7 du document source).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Cette ressource a ete modifiee entre-temps par un autre utilisateur. "
                        + "Veuillez recharger la version courante avant de reessayer.");
        problem.setTitle("Conflit de modification concurrente");
        return problem;
    }

    /**
     * Refus d'autorisation Spring Security (@PreAuthorize, filtre securite).
     * Toujours 403, jamais 404 - voir {@link ForbiddenOperationException} pour
     * la justification anti-IDOR (RG-SEC-002).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        journaliserAccesRefuse(request, ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Vous n'etes pas habilite a effectuer cette operation.");
        problem.setTitle("Acces refuse");
        return problem;
    }

    /**
     * Journalisation applicative de toute tentative d'acces refusee
     * (habilitation insuffisante ou perimetre non autorise - section 26.4),
     * qu'elle provienne d'un controle grossier par role
     * ({@link AccessDeniedException}, filtre Spring Security /
     * {@code @PreAuthorize}) ou d'un controle fin de perimetre en service
     * ({@link ForbiddenOperationException}, RG-SEC-002).
     *
     * <p><strong>Distincte du journal fonctionnel {@code evenement_audit}</strong>
     * (exigence explicite de la section 26.4) : il s'agit ici d'un journal
     * technique/securite (flux de logs applicatifs), pas d'un enregistrement
     * en base dans la table polymorphe utilisee pour la tracabilite metier -
     * les deux repondent a des besoins differents (voir section 23.1,
     * distinction traçabilite/auditabilite).
     */
    private void journaliserAccesRefuse(HttpServletRequest request, String motif) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        String utilisateur = authentification != null ? authentification.getName() : "anonyme";
        log.warn("Acces refuse (section 26.4) : utilisateur={} methode={} uri={} motif={}",
                utilisateur, request.getMethod(), request.getRequestURI(), motif);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        // Message volontairement generique : ne jamais reveler si c'est
        // l'identifiant ou le mot de passe qui est errone (enumeration de
        // comptes), conformement aux bonnes pratiques d'authentification.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Identifiant ou mot de passe incorrect.");
        problem.setTitle("Authentification echouee");
        return problem;
    }

    /**
     * Compte desactive ou verrouille (levee par
     * {@link org.springframework.security.authentication.DaoAuthenticationProvider}
     * a partir de {@link CustomUserDetails#isEnabled()}/{@code isAccountNonLocked()}
     * lorsque le mot de passe est pourtant correct). Un identifiant/mot de
     * passe valides ne suffisent jamais a eux seuls : le statut du compte
     * reste verifie a chaque connexion, meme apres la suppression du second
     * facteur.
     */
    @ExceptionHandler(org.springframework.security.authentication.AccountStatusException.class)
    public ProblemDetail handleAccountStatus(org.springframework.security.authentication.AccountStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Ce compte est desactive ou verrouille. Contactez votre administrateur.");
        problem.setTitle("Authentification echouee");
        return problem;
    }

    /**
     * Filet de securite final : toute exception non anticipee est journalisee
     * en integralite cote serveur (diagnostic) mais restituee au client sous
     * une forme generique, sans detail technique (brief §11 : "exposition
     * d'informations sensibles").
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erreur non geree", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur inattendue est survenue. L'incident a ete journalise.");
        problem.setTitle("Erreur interne");
        return problem;
    }
}
