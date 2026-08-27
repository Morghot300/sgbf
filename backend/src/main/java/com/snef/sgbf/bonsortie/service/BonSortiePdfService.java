package com.snef.sgbf.bonsortie.service;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.common.pdf.PdfBranding;
import com.snef.sgbf.common.pdf.PdfRenderer;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.entity.AffectationMission;
import java.time.format.DateTimeFormatter;
import org.springframework.transaction.annotation.Transactional;

/**
 * Impression du bon de sortie au format PDF (section 13.2, RG-DOC-001 a 008).
 *
 * <p>Reservee aux documents au statut {@code VALIDE} (RG-DOC-001) : avant ce
 * statut, seule la consultation a l'ecran est proposee (section 13.7). Le
 * document est genere dynamiquement a chaque demande a partir des donnees
 * persistees, sans aucun champ nouveau ni saisie complementaire (RG-DOC-002).
 *
 * <p><strong>{@code @Transactional} indispensable ici</strong> (bug corrige
 * le 2026-08-18, repere uniquement via un test manuel reel - jamais par la
 * suite automatisee, dont les classes de test sont elles-memes annotees
 * {@code @Transactional} et maintenaient donc une session Hibernate ouverte
 * pendant tout le test, masquant completement le probleme) : {@link BonSortie#getVisePar()}
 * et {@link BonSortie#getValideParCA()} sont des associations paresseuses
 * ({@code FetchType.LAZY}) - sans transaction englobant tout {@link #genererPdf},
 * leur acces dans {@link #construireXhtml} (appele apres la fin de la
 * transaction de {@code BonSortieService.chargerPourImpression}) levait
 * {@code LazyInitializationException} ("no Session").
 *
 * <p><strong>Jamais {@code readOnly = true}</strong> ici (piege repere dans
 * la foulee du bug ci-dessus) : {@link #genererPdf} ecrit aussi un
 * evenement d'audit (RG-DOC-005) - une transaction en lecture seule fait
 * echouer cette ecriture avec {@code SQLException: Connection is read-only}.
 * La encore, la suite de tests ne pouvait pas reveler le probleme : ses
 * classes de test sont deja dans une transaction englobante non read-only,
 * et Spring n'applique le mode lecture seule d'une transaction imbriquee
 * qu'a l'ouverture d'une transaction reellement nouvelle.
 */
@org.springframework.stereotype.Service
@Transactional
public class BonSortiePdfService {

    private static final DateTimeFormatter FORMAT_NOM_FICHIER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final BonSortieService bonSortieService;
    private final PdfRenderer pdfRenderer;
    private final PdfBranding pdfBranding;
    private final AuditService auditService;

    public BonSortiePdfService(BonSortieService bonSortieService, PdfRenderer pdfRenderer,
                                PdfBranding pdfBranding, AuditService auditService) {
        this.bonSortieService = bonSortieService;
        this.pdfRenderer = pdfRenderer;
        this.pdfBranding = pdfBranding;
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
        Utilisateur agent = bs.getAgent();
        AffectationMission affectation = bs.getAffectationMission();
        // Evolution du 2026-08-27 ("Code Mission") : si aucune affectation n'a ete resolue mais
        // qu'une mission a ete choisie explicitement sur le bon, l'imprimer quand meme plutot que
        // "Non resolue" - avec une mention explicite que l'affectation elle-meme reste a confirmer.
        String mission;
        if (affectation != null) {
            mission = esc(affectation.getMission().getCodeHN().getCode()) + " &#8212; " + esc(affectation.getMission().getChantier().getLibelle());
        } else if (bs.getMission() != null) {
            mission = esc(bs.getMission().getCodeHN().getCode()) + " &#8212; " + esc(bs.getMission().getChantier().getLibelle())
                    + " (affectation non confirmee)";
        } else {
            mission = "Non resolue";
        }
        String vehicule = bs.getVehicule() != null ? esc(bs.getVehicule().getImmatriculation()) : "Non renseigne";
        String lt = bs.getLt() != null && !bs.getLt().isBlank() ? esc(bs.getLt()) : "Non renseigne";
        String moyenUtilise = esc(bs.getMoyenUtilise().name())
                + (bs.getMoyenUtilise() == MoyenUtilise.AUTRE && bs.getPrecisionVehicule() != null
                        ? " (" + esc(bs.getPrecisionVehicule()) + ")" : "");
        String heureRetour = bs.getHeureRetour() != null ? bs.getHeureRetour().format(FORMAT_HEURE) : "Non renseignee";
        String dateValidation = bs.getDateValidation() != null ? bs.getDateValidation().format(FORMAT_DATE_HEURE) : "-";
        String validateur = bs.getValideParCA() != null ? esc(bs.getValideParCA().getIdentifiant()) : "-";
        String visePar = bs.getVisePar() != null ? esc(bs.getVisePar().getIdentifiant()) : "-";

        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><style>
                    %s
                </style></head>
                <body>
                    %s
                    <div class="sous-titre">Reference n&#176; %d</div>
                    <table>
                        <tr><th class="fiche-champ">Agent</th><td>%s %s (matricule %s)</td></tr>
                        <tr><th class="fiche-champ">Moyen utilise</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Vehicule / immatriculation (LT)</th><td>%s / %s</td></tr>
                        <tr><th class="fiche-champ">Motif</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Destination</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Date, heure de sortie</th><td>%s a %s</td></tr>
                        <tr><th class="fiche-champ">Heure de retour</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Kilometrage</th><td>%d km</td></tr>
                        <tr><th class="fiche-champ">Mission / code mission / chantier</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Visa de l'agent (niveau 1)</th><td>%s</td></tr>
                        <tr><th class="fiche-champ">Validation Charge d'Affaires (niveau 2)</th><td>%s, le %s</td></tr>
                        <tr><th class="fiche-champ">Statut du document</th><td><span class="badge badge-succes">%s</span></td></tr>
                    </table>
                    <div class="pied">Document genere dynamiquement le %s a partir des donnees du systeme SGBF ;
                        conforme a RG-DOC-002 (aucune saisie ni modification n'intervient a l'impression).</div>
                </body>
                </html>
                """.formatted(
                pdfBranding.css(),
                pdfBranding.entete("BON DE SORTIE"),
                bs.getId(),
                esc(agent.getPrenom()), esc(agent.getNom()), esc(agent.getMatricule()),
                moyenUtilise,
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
