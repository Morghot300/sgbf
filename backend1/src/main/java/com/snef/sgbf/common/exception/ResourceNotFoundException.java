package com.snef.sgbf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Levee lorsqu'une ressource identifiee (par cle primaire ou reference
 * fonctionnelle) est introuvable.
 *
 * <p><strong>Attention (anti-IDOR, RG-SEC-002) :</strong> cette exception ne
 * doit JAMAIS etre utilisee pour signaler qu'une ressource existe mais est
 * hors du perimetre de l'utilisateur authentifie - ce cas doit produire un
 * {@link ForbiddenOperationException} (403), afin de ne pas laisser deviner
 * l'existence d'une ressource par un identifiant syntaxiquement valide
 * (section 26.5 du document source : "jamais une absence de ressource (404)
 * qui laisserait deviner son existence").
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String entite, Object id) {
        return new ResourceNotFoundException(entite + " introuvable (id=" + id + ")");
    }
}
