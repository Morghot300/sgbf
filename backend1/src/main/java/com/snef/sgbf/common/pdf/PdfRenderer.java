package com.snef.sgbf.common.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

/**
 * Rendu HTML -&gt; PDF, point de passage unique pour tous les documents
 * generes par l'application (section 13.5 du document source : "generation
 * dynamique, sans stockage" - chaque document est reconstruit a la demande,
 * jamais conserve ni mis en cache).
 *
 * <p>S'appuie sur openhtmltopdf (deja present en dependance, XHTML/CSS
 * strict) plutot que sur l'API bas niveau de PDFBox directement : les
 * services {@code *PdfService} du module bon de sortie et du module FIPH
 * construisent un simple document HTML (voir {@code gabarits/*.html}
 * generes en memoire) plutot que de positionner des elements PDF au pixel
 * pres, ce qui reste plus lisible et plus facile a faire evoluer que du
 * dessin bas niveau.
 */
@Component
public class PdfRenderer {

    /** Le HTML fourni doit etre du XHTML valide (balises fermees, attributs entre guillemets) - exigence d'openhtmltopdf. */
    public byte[] rendre(String xhtml) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(sortie);
            builder.run();
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new PdfGenerationException("Echec de la generation du document PDF.", e);
        }
    }
}
