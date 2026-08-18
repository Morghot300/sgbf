package com.snef.sgbf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Levee lorsqu'une operation est syntaxiquement valide mais viole une regle
 * de gestion (RG-*) du document source : par exemple tenter de modifier une
 * {@code FIPHVersion} au statut {@code VALIDEE_DEFINITIVEMENT} (RG-VER-001),
 * ou soumettre une FIPH non encore signee (RG-FIPH-019).
 *
 * <p>Traduite en HTTP 422 (Unprocessable Entity) par
 * {@link GlobalExceptionHandler} : la requete est comprise, mais l'etat
 * metier ne permet pas de l'honorer.
 */
public class BusinessRuleViolationException extends ApiException {

    /** Code de la regle violee (ex. "RG-VER-001"), reporte dans la reponse d'erreur pour tracabilite. */
    private final String codeRegle;

    public BusinessRuleViolationException(String codeRegle, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
        this.codeRegle = codeRegle;
    }

    public String getCodeRegle() {
        return codeRegle;
    }
}
