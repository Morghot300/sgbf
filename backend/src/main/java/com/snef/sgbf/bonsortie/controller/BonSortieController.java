package com.snef.sgbf.bonsortie.controller;

import com.snef.sgbf.bonsortie.dto.AgentEligibleDto;
import com.snef.sgbf.bonsortie.dto.BonSortieDto;
import com.snef.sgbf.bonsortie.dto.CreerBonSortieRequest;
import com.snef.sgbf.bonsortie.dto.ModifierBonSortieRequest;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.bonsortie.service.BonSortiePdfService;
import com.snef.sgbf.bonsortie.service.BonSortiePersonneService;
import com.snef.sgbf.bonsortie.service.BonSortieService;
import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API du bon de sortie (section 3, RG-BS-001 a 007).
 *
 * <p>La creation et le visa sont en libre-service (l'agent agit pour
 * lui-meme - controle d'identite fait en service, pas de restriction de
 * role ici) ; la validation reste reservee au Charge d'Affaires et a la
 * personne habilitee, avec controle de perimetre fin en service.
 */
@RestController
@RequestMapping("/api/bons-sortie")
public class BonSortieController {

    private final BonSortieService bonSortieService;
    private final BonSortiePdfService bonSortiePdfService;
    private final BonSortiePersonneService bonSortiePersonneService;

    public BonSortieController(BonSortieService bonSortieService, BonSortiePdfService bonSortiePdfService,
                                BonSortiePersonneService bonSortiePersonneService) {
        this.bonSortieService = bonSortieService;
        this.bonSortiePdfService = bonSortiePdfService;
        this.bonSortiePersonneService = bonSortiePersonneService;
    }

    /**
     * Liste des bons de sortie visibles, avec filtres optionnels combinables
     * (section 1 de l'evolution du 2026-08-18) : {@code date} (jour exact),
     * {@code dateDebut}/{@code dateFin} (periode, bornes incluses),
     * {@code statut} (parmi les statuts reellement definis - BROUILLON, VISE,
     * VALIDE) et {@code serviceId}. Toujours appliques APRES le filtrage de
     * perimetre (RG-SEC-002) : un filtre ne peut jamais elargir ce qu'un
     * utilisateur est habilite a voir, seulement restreindre l'affichage
     * parmi ce qui lui est deja visible.
     */
    @GetMapping
    public List<BonSortieDto> lister(@AuthenticationPrincipal CustomUserDetails principal,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateDebut,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFin,
                                      @RequestParam(required = false) @Nullable StatutBonSortie statut,
                                      @RequestParam(required = false) @Nullable Long serviceId,
                                      @RequestParam(required = false) @Nullable String nomComplet) {
        return bonSortieService.listerVisibles(principal.getUtilisateur(), date, dateDebut, dateFin, statut, serviceId, nomComplet);
    }

    @GetMapping("/{id}")
    public BonSortieDto obtenir(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.obtenirParId(id, principal.getUtilisateur());
    }

    /**
     * Personnel eligible a etre ajoute comme personne a bord de ce bon
     * (evolution du 2026-08-19, Lot 4) - perimetre (service du titulaire du
     * bon) calcule cote serveur uniquement, voir
     * {@code BonSortiePersonneService.listerAgentsEligibles}.
     */
    @GetMapping("/{id}/agents-eligibles")
    public List<AgentEligibleDto> agentsEligibles(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortiePersonneService.listerAgentsEligibles(id, principal.getUtilisateur());
    }

    /**
     * Personnel d'un service, selectionnable comme personne principale ou
     * personnes a bord AVANT MEME la creation du bon (evolution du
     * 2026-08-27, section 3-4-30) - voir
     * {@code BonSortiePersonneService.listerPersonnelDuService}.
     */
    @GetMapping("/personnel-service/{serviceId}")
    public List<AgentEligibleDto> personnelDuService(@PathVariable Long serviceId, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortiePersonneService.listerPersonnelDuService(serviceId, principal.getUtilisateur());
    }

    /** Impression du document valide au format PDF (section 13.2, RG-DOC-001 a 007). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> imprimer(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        DocumentPdf document = bonSortiePdfService.genererPdf(id, principal.getUtilisateur());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(document.contenu());
    }

    @PostMapping
    public ResponseEntity<BonSortieDto> creer(@Valid @RequestBody CreerBonSortieRequest requete,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        BonSortieDto cree = bonSortieService.creer(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }

    /**
     * Correction des champs du bon de sortie (evolution du 2026-08-26) -
     * remplace l'ancien endpoint {@code /retour}, jamais expose cote
     * frontend. Reservee au titulaire ou a un gestionnaire de son service tant
     * que le bon n'est pas {@code VALIDE} ; une fois valide, reservee au seul
     * gestionnaire du service (ou au Super Administrateur) - evolution du
     * 2026-08-27, RG-VER-001 desormais inversee sur decision explicite.
     */
    @PutMapping("/{id}")
    public BonSortieDto modifier(@PathVariable Long id, @Valid @RequestBody ModifierBonSortieRequest requete,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.modifier(id, requete, principal.getUtilisateur());
    }

    /** Visa de l'agent (niveau 1) - reserve au titulaire, verifie en service. */
    @PostMapping("/{id}/viser")
    public BonSortieDto viser(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.viser(id, principal.getUtilisateur());
    }

    /** Validation du Charge d'Affaires (niveau 2, RG-BS-003) - declenche la generation pour les personnes a bord (RG-PAB-002). */
    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public BonSortieDto valider(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.valider(id, principal.getUtilisateur());
    }
}
