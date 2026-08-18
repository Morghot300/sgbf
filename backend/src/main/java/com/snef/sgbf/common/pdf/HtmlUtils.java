package com.snef.sgbf.common.pdf;

/**
 * Echappement minimal des valeurs metier interpolees dans les gabarits XHTML
 * des documents generes (section 13 du document source). Necessaire car
 * plusieurs champs restitues sont du texte libre saisi par un utilisateur
 * (motif de sortie, lieu, observations) : sans echappement, une valeur
 * contenant {@code <} ou {@code &} produirait soit un document PDF corrompu
 * (XHTML mal forme), soit, dans le pire cas, une injection de balises dans le
 * document genere.
 */
public final class HtmlUtils {

    private HtmlUtils() {
    }

    public static String echapper(Object valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
