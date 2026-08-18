package com.snef.sgbf.common.audit;

import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.common.pdf.HtmlUtils;
import com.snef.sgbf.common.pdf.PdfRenderer;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.entity.FIPHVersion;
import com.snef.sgbf.fiph.repository.FiphVersionRepository;
import com.snef.sgbf.fiph.service.FiphService;
import com.snef.sgbf.identite.entity.Utilisateur;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historique complet et export d'une FIPH (section 24 du document source :
 * "qui a cree cette FIPH... qui l'a validee a chaque niveau, et quelles
 * versions se sont succede ?").
 *
 * <p>L'historique reunit les evenements portes par la FIPH elle-meme
 * (creation) et par chacune de ses versions successives (complement,
 * signature, soumission, validation, creation de version) - reconstitution
 * en memoire plutot qu'une requete SQL unique, {@link EvenementAudit} etant
 * volontairement polymorphe sans jointure declarative possible entre
 * {@code entite_type = FIPH} et {@code entite_type = FIPH_VERSION} (choix de
 * conception documente section 24).
 */
@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AuditHistoryService {

    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final EvenementAuditRepository evenementAuditRepository;
    private final FiphVersionRepository fiphVersionRepository;
    private final FiphService fiphService;
    private final PdfRenderer pdfRenderer;

    public AuditHistoryService(EvenementAuditRepository evenementAuditRepository, FiphVersionRepository fiphVersionRepository,
                                FiphService fiphService, PdfRenderer pdfRenderer) {
        this.evenementAuditRepository = evenementAuditRepository;
        this.fiphVersionRepository = fiphVersionRepository;
        this.fiphService = fiphService;
        this.pdfRenderer = pdfRenderer;
    }

    /**
     * Historique complet, du plus ancien au plus recent. Ouvert a quiconque
     * peut deja consulter la FIPH elle-meme (section 14, colonne
     * "Historique" : "Oui" pour tous les roles habilites a consulter) - meme
     * controle de perimetre que {@link FiphService#obtenirParId}.
     */
    public List<EvenementAuditDto> historiqueFiph(Long fiphId, Utilisateur courant) {
        FIPH fiph = fiphService.chargerFiph(fiphId);
        fiphService.verifierPerimetreLecture(courant, fiph);
        return chargerEvenements(fiph).stream().map(this::versDto).toList();
    }

    /** Export CSV de l'historique complet - reserve a la Direction, la RH et l'Administrateur (section 24). */
    public DocumentExport exporterCsv(Long fiphId, Utilisateur courant) {
        FIPH fiph = fiphService.chargerFiph(fiphId);
        fiphService.verifierPerimetreLecture(courant, fiph);
        List<EvenementAudit> evenements = chargerEvenements(fiph);

        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        try (PrintWriter ecrivain = new PrintWriter(tampon, false, StandardCharsets.UTF_8)) {
            tampon.write(0xEF); tampon.write(0xBB); tampon.write(0xBF); // BOM UTF-8 : ouverture correcte des accents sous Excel
            ecrivain.println("Date;Entite;Identifiant;Action;Utilisateur;Statut avant;Statut apres");
            for (EvenementAudit e : evenements) {
                ecrivain.println(String.join(";",
                        champCsv(e.getDateAction().format(FORMAT_DATE_HEURE)),
                        champCsv(e.getEntiteType().name()),
                        champCsv(e.getEntiteId()),
                        champCsv(e.getAction().name()),
                        champCsv(e.getUtilisateur() != null ? e.getUtilisateur().getIdentifiant() : "systeme"),
                        champCsv(e.getStatutAvant()),
                        champCsv(e.getStatutApres())));
            }
        }
        String nomFichier = "AUDIT_FIPH_" + fiph.getAgent().getMatricule() + "_" + fiph.getAnnee() + "_S" + fiph.getNumeroSemaine() + ".csv";
        return new DocumentExport(tampon.toByteArray(), nomFichier, "text/csv");
    }

    /** Export PDF de l'historique complet - meme restriction que l'export CSV. */
    public DocumentExport exporterPdf(Long fiphId, Utilisateur courant) {
        FIPH fiph = fiphService.chargerFiph(fiphId);
        fiphService.verifierPerimetreLecture(courant, fiph);
        List<EvenementAudit> evenements = chargerEvenements(fiph);

        String lignes = evenements.stream().map(e -> """
                <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>
                """.formatted(
                        e.getDateAction().format(FORMAT_DATE_HEURE),
                        HtmlUtils.echapper(e.getEntiteType().name() + " #" + e.getEntiteId()),
                        HtmlUtils.echapper(e.getAction().name()),
                        HtmlUtils.echapper(e.getUtilisateur() != null ? e.getUtilisateur().getIdentifiant() : "systeme"),
                        HtmlUtils.echapper(e.getStatutAvant()),
                        HtmlUtils.echapper(e.getStatutApres()))
        ).collect(Collectors.joining());

        String xhtml = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><style>
                    body { font-family: Helvetica, Arial, sans-serif; font-size: 10px; color: #1a1a1a; }
                    h1 { font-size: 15px; margin-bottom: 2px; }
                    .sous-titre { color: #555; margin-bottom: 16px; }
                    table { width: 100%%; border-collapse: collapse; }
                    td, th { border: 1px solid #ccc; padding: 4px 6px; text-align: left; }
                    th { background-color: #f0f0f0; font-weight: bold; }
                </style></head>
                <body>
                    <h1>SNEF Cameroun SA &#8212; Journal d'audit FIPH</h1>
                    <div class="sous-titre">Agent %s (matricule %s) &#8212; Semaine %d de %d</div>
                    <table>
                        <tr><th>Date</th><th>Entite</th><th>Action</th><th>Utilisateur</th><th>Statut avant</th><th>Statut apres</th></tr>
                        %s
                    </table>
                </body>
                </html>
                """.formatted(HtmlUtils.echapper(fiph.getAgent().getPrenom()) + " " + HtmlUtils.echapper(fiph.getAgent().getNom()),
                HtmlUtils.echapper(fiph.getAgent().getMatricule()), fiph.getNumeroSemaine(), fiph.getAnnee(), lignes);

        byte[] contenu = pdfRenderer.rendre(xhtml);
        String nomFichier = "AUDIT_FIPH_" + fiph.getAgent().getMatricule() + "_" + fiph.getAnnee() + "_S" + fiph.getNumeroSemaine() + ".pdf";
        return new DocumentExport(contenu, nomFichier, "application/pdf");
    }

    private List<EvenementAudit> chargerEvenements(FIPH fiph) {
        List<EvenementAudit> evenements = new ArrayList<>(
                evenementAuditRepository.findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable.FIPH, String.valueOf(fiph.getId())));

        List<FIPHVersion> versions = fiphVersionRepository.findByFiph_IdOrderByNumeroVersionAsc(fiph.getId());
        for (FIPHVersion version : versions) {
            evenements.addAll(evenementAuditRepository
                    .findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable.FIPH_VERSION, String.valueOf(version.getId())));
        }
        evenements.sort(Comparator.comparing(EvenementAudit::getDateAction));
        return evenements;
    }

    private EvenementAuditDto versDto(EvenementAudit e) {
        return new EvenementAuditDto(e.getId(), e.getEntiteType(), e.getEntiteId(),
                e.getUtilisateur() != null ? e.getUtilisateur().getIdentifiant() : null,
                e.getAction(), e.getValeurAvant(), e.getValeurApres(), e.getStatutAvant(), e.getStatutApres(), e.getDateAction());
    }

    /** Echappe un champ pour insertion dans une ligne CSV separee par ";" (convention Excel FR). */
    private static String champCsv(String valeur) {
        if (valeur == null) {
            return "";
        }
        String echappe = valeur.replace("\"", "\"\"");
        boolean necessiteGuillemets = echappe.contains(";") || echappe.contains("\"") || echappe.contains("\n");
        return necessiteGuillemets ? "\"" + echappe + "\"" : echappe;
    }
}
