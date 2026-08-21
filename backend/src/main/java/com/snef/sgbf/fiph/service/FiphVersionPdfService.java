package com.snef.sgbf.fiph.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.common.pdf.HtmlUtils;
import com.snef.sgbf.common.pdf.PdfBranding;
import com.snef.sgbf.common.pdf.PdfRenderer;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.entity.FIPHVersion;
import com.snef.sgbf.fiph.entity.Pointage;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import com.snef.sgbf.fiph.entity.Validation;
import com.snef.sgbf.fiph.repository.FiphVersionRepository;
import com.snef.sgbf.fiph.repository.PointageRepository;
import com.snef.sgbf.fiph.repository.ValidationRepository;
import com.snef.sgbf.identite.entity.Utilisateur;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generation de la FIPH au format PDF, en previsualisation ou en document
 * final (section 13.3, RG-DOC-003/004).
 *
 * <p><strong>Disponible a tout moment, quel que soit le statut de la
 * version (evolution du 2026-08-21)</strong> : jusqu'a cette evolution, seule
 * une version {@link StatutFiphVersion#VALIDEE_DEFINITIVEMENT} ("terminee")
 * pouvait etre generee (RG-DOC-003). Le Charge d'Affaires/la personne
 * habilitee doit desormais pouvoir previsualiser la FIPH avant meme d'y
 * apporter une correction, et le Responsable d'Activite comme la Direction
 * doivent pouvoir la previsualiser a leur tour, sans pouvoir la modifier -
 * ce dernier point est deja garanti par construction : cette methode ne fait
 * que LIRE la version (aucune ecriture de contenu), et le perimetre de
 * lecture ({@link FiphService#verifierPerimetreLecture}) reste, lui,
 * pleinement applique. Le PDF est toujours regenere a la demande a partir de
 * la version courante, jamais d'une copie conservee (section 13.5) ; une
 * fois {@code VALIDEE_DEFINITIVEMENT}, sa fidelite est en outre garantie par
 * l'empreinte d'integrite deja associee a la version (RG-VER-006,
 * RG-DOC-004) - avant ce statut, le document reste une previsualisation du
 * contenu courant, encore susceptible d'evoluer.
 */
@org.springframework.stereotype.Service
public class FiphVersionPdfService {

    // NOTE (bug corrige le 2026-08-18, voir Javadoc equivalente dans
    // BonSortiePdfService pour le detail complet) : genererPdf() ecrit un
    // evenement d'audit (RG-DOC-005) en plus de lire la version - jamais
    // @Transactional(readOnly = true) sur cette methode, sous peine de faire
    // echouer cette ecriture ("Connection is read-only").

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final FiphVersionRepository fiphVersionRepository;
    private final PointageRepository pointageRepository;
    private final ValidationRepository validationRepository;
    private final FiphService fiphService;
    private final PdfRenderer pdfRenderer;
    private final PdfBranding pdfBranding;
    private final AuditService auditService;

    public FiphVersionPdfService(FiphVersionRepository fiphVersionRepository, PointageRepository pointageRepository,
                                  ValidationRepository validationRepository, FiphService fiphService,
                                  PdfRenderer pdfRenderer, PdfBranding pdfBranding, AuditService auditService) {
        this.fiphVersionRepository = fiphVersionRepository;
        this.pointageRepository = pointageRepository;
        this.validationRepository = validationRepository;
        this.fiphService = fiphService;
        this.pdfRenderer = pdfRenderer;
        this.pdfBranding = pdfBranding;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentPdf genererPdf(Long fiphVersionId, Utilisateur auteur) {
        FIPHVersion version = fiphVersionRepository.findById(fiphVersionId)
                .orElseThrow(() -> ResourceNotFoundException.of("FIPHVersion", fiphVersionId));
        FIPH fiph = version.getFiph();
        fiphService.verifierPerimetreLecture(auteur, fiph);

        boolean definitive = version.getStatutVersion() == StatutFiphVersion.VALIDEE_DEFINITIVEMENT;
        List<Pointage> pointages = pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(version.getId());
        List<Validation> validations = validationRepository.findByFiphVersion_IdOrderByDateValidationAsc(version.getId());

        byte[] contenu = pdfRenderer.rendre(construireXhtml(fiph, version, pointages, validations, definitive));
        String prefixeFichier = definitive ? "FIPH_" : "FIPH_APERCU_";
        String nomFichier = prefixeFichier + fiph.getAgent().getMatricule() + "_" + fiph.getAnnee() + "_S" + fiph.getNumeroSemaine() + ".pdf";

        // RG-DOC-005 : entiteType cible FIPHVersion, et non FIPH, pour conserver la relation exacte
        // avec la version precisement generee (section 13.5) - le meme type d'evenement couvre
        // desormais aussi bien une previsualisation qu'un telechargement final, le statut au moment
        // de l'action restant de toute facon reconstituable depuis l'historique de la version elle-meme.
        auditService.enregistrerAction(EntiteAuditable.FIPH_VERSION, version.getId(), auteur, TypeActionAudit.TELECHARGEMENT_PDF_FIPH);

        return new DocumentPdf(contenu, nomFichier);
    }

    private String construireXhtml(FIPH fiph, FIPHVersion version, List<Pointage> pointages, List<Validation> validations,
                                    boolean definitive) {
        Utilisateur agent = fiph.getAgent();

        String lignesPointage = pointages.stream().map(p -> {
            String codeMission = p.getAffectationMission() != null
                    ? esc(p.getAffectationMission().getMission().getCodeHN().getCode())
                    : (p.getService() != null ? "Service : " + esc(p.getService().getLibelle()) : "-");
            return """
                    <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>
                    """.formatted(esc(p.getJourSemaine().name()), p.getDatePointage().format(FORMAT_DATE),
                    p.getHeuresNormales(), p.getHeuresSup(), codeMission);
        }).collect(Collectors.joining());

        String signatureEmetteur = version.getSignatureEmetteur() != null
                ? "Signee (type " + esc(version.getSignatureEmetteur().getType().name()) + ")" : "Non signee";

        String lignesValidation = validations.isEmpty() ? "<tr><td colspan=\"4\">Aucune</td></tr>" : validations.stream().map(v -> """
                <tr><td>Niveau %d</td><td>%s</td><td>%s</td><td>%s</td></tr>
                """.formatted(v.getNiveauValidation(), esc(v.getUtilisateur().getIdentifiant()),
                esc(v.getDecision().name()), v.getDateValidation().format(FORMAT_DATE_HEURE))
        ).collect(Collectors.joining());

        // Style en ligne, plutot qu'une classe CSS partagee dans PdfBranding : les couleurs de
        // PdfBranding.css() sont extraites/auditees depuis le logo (voir sa Javadoc), jamais choisies
        // a l'oeil - un avertissement ponctuel comme celui-ci n'a pas vocation a devenir une couleur
        // de marque partagee entre tous les documents PDF de l'application.
        String banniereApercu = definitive ? "" : """
                <div style="margin-bottom: 10px; padding: 6px 10px; border: 1px solid #B7472A; \
                background-color: #FBEAE7; color: #B7472A; font-weight: bold;">PREVISUALISATION &#8212; \
                ce document n'est pas definitif : le contenu peut encore evoluer avant la validation \
                complete du circuit (niveaux 2 a 4).</div>
                """;
        String libelleStatut = definitive ? "Statut final" : "Statut actuel";
        String ligneEmpreinte = definitive ? """
                <tr><th class="fiche-champ">Empreinte d'integrite (SHA-256)</th><td class="empreinte">%s</td></tr>
                """.formatted(esc(version.getEmpreinteIntegrite())) : "";
        String piedDePage = definitive
                ? "Document genere dynamiquement le %s a partir de la version validee definitivement du systeme SGBF ; "
                        + "sa coherence avec cette version est garantie par l'empreinte d'integrite ci-dessus (RG-VER-006, RG-DOC-004)."
                : "Previsualisation generee dynamiquement le %s a partir du contenu actuel de cette version, non encore validee definitivement.";

        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><style>
                    %s
                    .empreinte { font-family: monospace; font-size: 9px; word-break: break-all; }
                </style></head>
                <body>
                    %s
                    %s
                    <div class="sous-titre">Version n&#176; %d</div>
                    <table>
                        <tr><th class="fiche-champ">Agent</th><td>%s %s (matricule %s)</td></tr>
                        <tr><th class="fiche-champ">Service</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Periode</th><td>Semaine %d de %d &#8212; du %s au %s</td></tr>
                        <tr><th class="fiche-champ">Signature de l'emetteur</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Total heures normales / heures supplementaires</th><td>%s HN / %s HS</td></tr>
                        <tr><th class="fiche-champ">%s</th><td><span class="badge badge-succes">%s</span></td></tr>
                        %s
                    </table>
                    <table>
                        <tr><th>Jour</th><th>Date</th><th>Heures normales</th><th>Heures sup.</th><th>Code mission / service</th></tr>
                        %s
                    </table>
                    <table>
                        <tr><th>Niveau</th><th>Validateur</th><th>Decision</th><th>Date</th></tr>
                        %s
                    </table>
                    <div class="pied">%s</div>
                </body>
                </html>
                """.formatted(
                pdfBranding.css(),
                pdfBranding.entete("FICHE INDIVIDUELLE DE POINTAGE HEBDOMADAIRE"),
                banniereApercu,
                version.getNumeroVersion(),
                esc(agent.getPrenom()), esc(agent.getNom()), esc(agent.getMatricule()),
                esc(fiph.getService().getLibelle()),
                fiph.getNumeroSemaine(), fiph.getAnnee(), fiph.getDateDebutPeriode().format(FORMAT_DATE), fiph.getDateFinPeriode().format(FORMAT_DATE),
                signatureEmetteur,
                version.getTotalHN(), version.getTotalHS(),
                libelleStatut, version.getStatutVersion().name(),
                ligneEmpreinte,
                lignesPointage,
                lignesValidation,
                piedDePage.formatted(java.time.LocalDateTime.now().format(FORMAT_DATE_HEURE)));
    }

    private static String esc(Object valeur) {
        return HtmlUtils.echapper(valeur);
    }
}
