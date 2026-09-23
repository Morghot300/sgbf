package com.snef.sgbf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Racine de la hierarchie des exceptions metier de l'application.
 *
 * <p>Toute exception applicative destinee a etre renvoyee au client via
 * {@link com.snef.sgbf.common.exception.GlobalExceptionHandler} doit etendre
 * cette classe et porter le code HTTP approprie a sa nature (voir sous-classes
 * {@link ResourceNotFoundException}, {@link ForbiddenOperationException},
 * {@link ConflictException}, {@link BusinessRuleViolationException}).
 *
 * <p>Ce mecanisme garantit une reponse d'erreur coherente (§9 du brief :
 * "reponses HTTP coherentes") sans jamais laisser fuiter de detail technique
 * (trace, nom de classe interne, requete SQL) vers le client.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
