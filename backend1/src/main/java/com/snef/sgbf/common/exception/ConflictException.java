package com.snef.sgbf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Levee en cas de conflit d'etat : doublon rejete par une contrainte
 * d'unicite metier (ex. RG-PAB-003, une personne deja associee a un bon de
 * sortie), ou conflit de verrouillage optimiste (RG-SEC-001) lorsque le
 * {@code lockVersion} soumis par le client diverge de celui persiste.
 *
 * <p>Traduite en HTTP 409 par {@link GlobalExceptionHandler}, avec un message
 * explicite invitant l'utilisateur a recharger la ressource avant de rejouer
 * sa modification (section 26.7 du document source) - jamais de fusion
 * automatique des deux saisies.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
