package com.snef.sgbf.common.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Entete stylise commun a tous les documents PDF generes par l'application
 * (bon de sortie, FIPH, export d'audit) - demande explicite apres les
 * premiers tests reels : "la presentation des impressions avec le logo SNEF
 * doit etre une version stylisee de celle qui se faisait sur papier".
 *
 * <p>Le logo est charge une seule fois depuis {@code static/brand/logo-email.png}
 * (meme fichier que celui prevu pour les futurs e-mails transactionnels, deja
 * genere par {@code tools/generate_brand_assets.py}) et encode en base64 -
 * seule methode fiable pour embarquer une image dans un PDF genere par
 * openhtmltopdf sans dependre d'un chemin de fichier resolu au moment du rendu.
 *
 * <p>Les couleurs utilisees ci-dessous (bandeau, textes) sont EXACTEMENT
 * celles extraites du logo par {@code tools/brand_extract.py} et auditees
 * par {@code tools/contrast_audit.py} (voir {@code frontend/src/styles/tokens.css}
 * pour les memes valeurs cote frontend) - jamais choisies a l'oeil. A tenir
 * synchronise manuellement avec {@code tokens.css} tant qu'openhtmltopdf ne
 * partage pas de source de tokens avec le frontend.
 */
@Component
public class PdfBranding {

    private static final String CHEMIN_LOGO = "static/brand/logo-email.png";

    public static final String BRAND_1 = "#1D1D1B";       // noir d'encre du logo
    public static final String BRAND_2 = "#0095A9";       // teal du logo (aplats/bordures, jamais du texte < 18px)
    public static final String BRAND_2_TEXT = "#008294";  // variante teal conforme AA (texte)
    public static final String GRIS_SECONDAIRE = "#56646D";
    public static final String GRIS_BORDURE = "#DEE3E7";
    public static final String GRIS_FOND = "#F7F8F9";

    private final String logoDataUri;

    public PdfBranding() {
        this.logoDataUri = chargerLogoEnBase64();
    }

    private String chargerLogoEnBase64() {
        try (InputStream flux = new ClassPathResource(CHEMIN_LOGO).getInputStream()) {
            byte[] octets = flux.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(octets);
        } catch (IOException e) {
            // Un logo manquant ne doit jamais empecher la generation du document
            // (RG-DOC : l'impression reste une operation de lecture) - le document
            // est simplement genere sans logo plutot que de faire echouer l'impression.
            return "";
        }
    }

    /** Bloc CSS partage (entete, tableau, badge de statut) a inserer dans le &lt;style&gt; de chaque gabarit. */
    public String css() {
        return """
                body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: %s; margin: 24px; }
                .entete { display: flex; align-items: center; gap: 16px; padding-bottom: 10px; border-bottom: 3px solid %s; margin-bottom: 6px; }
                .entete img { height: 40px; width: auto; }
                .entete-organisation { font-size: 9px; color: %s; text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 2px; }
                .entete-document { font-size: 20px; font-weight: 700; color: %s; }
                .sous-titre { color: %s; margin-bottom: 16px; font-size: 11px; }
                table { width: 100%%; border-collapse: collapse; margin-bottom: 10px; }
                td, th { border: 1px solid %s; padding: 5px 8px; text-align: left; vertical-align: top; }
                th { background-color: %s; font-weight: bold; }
                .fiche-champ { width: 32%%; }
                .badge { display: inline-block; padding: 2px 10px; border-radius: 999px; font-weight: bold; }
                .badge-succes { background-color: #E6F4E9; color: #1E7A34; }
                .badge-neutre { background-color: %s; color: %s; }
                .pied { margin-top: 18px; font-size: 9px; color: %s; }
                """.formatted(BRAND_1, BRAND_2, GRIS_SECONDAIRE, BRAND_1, GRIS_SECONDAIRE,
                GRIS_BORDURE, GRIS_FOND, GRIS_FOND, GRIS_SECONDAIRE, GRIS_SECONDAIRE);
    }

    /** Bloc d'entete HTML (logo + nom de l'organisation + titre du document) partage par tous les gabarits. */
    public String entete(String titreDocument) {
        return """
                <div class="entete">
                    <img src="%s" alt="SNEF" />
                    <div>
                        <div class="entete-organisation">SNEF Cameroun SA &#8212; Groupe SNEF</div>
                        <div class="entete-document">%s</div>
                    </div>
                </div>
                """.formatted(logoDataUri, HtmlUtils.echapper(titreDocument));
    }
}
