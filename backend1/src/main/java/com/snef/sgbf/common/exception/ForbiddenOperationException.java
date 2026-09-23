package com.snef.sgbf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Levee lorsqu'un utilisateur authentifie tente une action pour laquelle son
 * habilitation ne couvre pas la ressource ou l'operation demandee.
 *
 * <p>C'est l'exception a utiliser pour toute ressource "hors perimetre" au
 * sens de RG-SEC-002 (controle anti-IDOR) : un identifiant syntaxiquement
 * valide mais hors habilitation de l'utilisateur produit un refus d'acces
 * (403), jamais un "introuvable" (404) - voir section 26.5 du document
 * source. Chaque levee de cette exception est journalisee comme tentative
 * d'acces refusee (section 26.4) par
 * {@code GlobalExceptionHandler#journaliserAccesRefuse}.
 */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
