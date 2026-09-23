package com.snef.sgbf.common.pdf;

/**
 * Document PDF genere a la demande, avec le nom de fichier a restituer au
 * client (section 13.6 - convention de nommage). Jamais persiste (section
 * 13.5) : simple porteur entre le service de generation et le controleur.
 */
public record DocumentPdf(byte[] contenu, String nomFichier) {
}
