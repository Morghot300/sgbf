package com.snef.sgbf.common.pdf;

/**
 * Echec technique (non metier) de generation d'un document PDF.
 *
 * <p>Volontairement une simple {@link RuntimeException} et non une
 * {@link com.snef.sgbf.common.exception.ApiException} : le tableau "gestion
 * des erreurs" de la section 13.7 du document source demande, pour ce cas
 * precis, un message generique cote client et une journalisation technique
 * complete cote serveur - exactement le comportement deja fourni par le
 * gestionnaire d'exceptions generique
 * ({@code GlobalExceptionHandler#handleUnexpected}), sans avoir besoin d'un
 * traitement special.
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
