package com.snef.sgbf.bonsortie.service;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.common.pdf.PdfRenderer;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.entity.AffectationMission;
import java.time.format.DateTimeFormatter;

/**
 * Impression du bon de sortie au format PDF (section 13.2, RG-DOC-001 a 008).
 *
 * <p>Reservee aux documents au statut {@code VALIDE} (RG-DOC-001) : avant ce
 * statut, seule la consultation a l'ecran est proposee (section 13.7). Le
 * document est genere dynamiquement a chaque demande a partir des donnees
 * persistees, sans aucun champ nouveau ni saisie complementaire (RG-DOC-002).
 */
@org.springframework.stereotype.Service
public class BonSortiePdfService {

    private static final DateTimeFormatter FORMAT_NOM_FICHIER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final BonSortieService bonSortieService;
    private final PdfRenderer pdfRenderer;
    private final AuditService auditService;

    public BonSortiePdfService(BonSortieService bonSortieService, PdfRenderer pdfRenderer, AuditService auditService) {
        this.bonSortieService = bonSortieService;
        this.pdfRenderer = pdfRenderer;
        this.auditService = auditService;
    }

    /**
     * Genere le PDF d'un bon de sortie valide. Le controle de perimetre
     * (memes droits que la consultation - RG-DOC-007) et le controle de
     * statut (RG-DOC-001) sont executes avant toute generation.
     */
    public DocumentPdf genererPdf(Long bonSortieId, Utilisateur auteur) {
        BonSortie bonSortie = bonSortieService.chargerPourImpression(bonSortieId, auteur);

        if (bonSortie.getStatut() != StatutBonSortie.VALIDE) {
            throw new BusinessRuleViolationException("RG-DOC-001",
                    "Seul un bon de sortie au statut VALIDE peut etre imprime comme document valide "
                            + "(statut actuel : " + bonSortie.getStatut() + ").");
        }

        byte[] contenu = pdfRenderer.rendre(construireXhtml(bonSortie));
        String nomFichier = "BON_SORTIE_" + bonSortie.getId() + "_" + bonSortie.getDateSortie().format(FORMAT_NOM_FICHIER) + ".pdf";

        // RG-DOC-005 : traçabilite de l'impression ; RG-DOC-006 : purement une lecture, aucun etat modifie.
        auditService.enregistrerAction(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur, TypeActionAudit.IMPRESSION_BON_SORTIE);

        return new DocumentPdf(contenu, nomFichier);
    }

    private String construireXhtml(BonSortie bs) {
        Agent agent = bs.getAgent();
        AffectationMission affectation = bs.getAffectationMission();
        String mission = affectation != null
                ? esc(affectation.getMission().getCodeHN().getCode()) + " &#8212; " + esc(affectation.getMission().getChantier().getLibelle())
                : "Non resolue";
        String vehicule = bs.getVehicule() != null ? esc(bs.getVehicule().getImmatriculation()) : "Non renseigne";
        String lt = bs.getLt() != null && !bs.getLt().isBlank() ? esc(bs.getLt()) : "Non renseigne";
        String heureRetour = bs.getHeureRetour() != null ? bs.getHeureRetour().format(FORMAT_HEURE) : "Non renseignee";
        String dateValidation = bs.getDateValidation() != null ? bs.getDateValidation().format(FORMAT_DATE_HEURE) : "-";
        String validateur = bs.getValideParCA() != null ? esc(bs.getValideParCA().getIdentifiant()) : "-";
        String visePar = bs.getVisePar() != null ? esc(bs.getVisePar().getIdentifiant()) : "-";

        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><style>
                    body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #1a1a1a; }
                    h1 { font-size: 16px; margin-bottom: 2px; }
                    .sous-titre { color: #555; margin-bottom: 16px; }
                    table { width: 100%%; border-collapse: collapse; margin-bottom: 10px; }
                    td, th { border: 1px solid #ccc; padding: 5px 8px; text-align: left; vertical-align: top; }
                    th { background-color: #f0f0f0; width: 32%%; font-weight: bold; }
                    .statut { font-weight: bold; color: #1a6e1a; }
                    .pied { margin-top: 18px; font-size: 9px; color: #777; }
                </style></head>
                <body>
                    <h1>SNEF Cameroun SA &#8212; Bon de sortie</h1>
                    <div class="sous-titre">Reference n&#176; %d</div>
                    <table>
                        <tr><th>Agent</th><td>%s %s (matricule %s)</td></tr>
                        <tr><th>Vehicule / immatriculation (LT)</th><td>%s / %s</td></tr>
                        <tr><th>Motif</th><td>%s</td></tr>
                        <tr><th>Destination</th><td>%s</td></tr>
                        <tr><th>Date, heure de sortie</th><td>%s a %s</td></tr>
                        <tr><th>Heure de retour</th><td>%s</td></tr>
                        <tr><th>Kilometrage</th><td>%d km</td></tr>
                        <tr><th>Mission / code mission / chantier</th><td>%s</td></tr>
                        <tr><th>Visa de l'agent (niveau 1)</th><td>%s</td></tr>
                        <tr><th>Validation Charge d'Affaires (niveau 2)</th><td>%s, le %s</td></tr>
                        <tr><th>Statut du document</th><td class="statut">%s</td></tr>
                    </table>
                    <div class="pied">Document genere dynamiquement le %s a partir des donnees du systeme SGBF ;
                        conforme a RG-DOC-002 (aucune saisie ni modification n'intervient a l'impression).</div>
                </body>
                </html>
                """.formatted(
                bs.getId(),
                esc(agent.getPrenom()), esc(agent.getNom()), esc(agent.getMatricule()),
                vehicule, lt,
                esc(bs.getMotifSortie()),
                esc(bs.getLieu()),
                bs.getDateSortie().format(FORMAT_DATE), bs.getHeureSortie().format(FORMAT_HEURE),
                heureRetour,
                bs.getKilometrage(),
                mission,
                visePar,
                validateur, dateValidation,
                bs.getStatut().name(),
                java.time.LocalDateTime.now().format(FORMAT_DATE_HEURE));
    }

    private static String esc(Object valeur) {
        return com.snef.sgbf.common.pdf.HtmlUtils.echapper(valeur);
    }
}
