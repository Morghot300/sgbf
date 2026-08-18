package com.snef.sgbf.bonsortie.controller;

import com.snef.sgbf.bonsortie.dto.BonSortieDto;
import com.snef.sgbf.bonsortie.dto.CreerBonSortieRequest;
import com.snef.sgbf.bonsortie.dto.ModifierRetourRequest;
import com.snef.sgbf.bonsortie.service.BonSortiePdfService;
import com.snef.sgbf.bonsortie.service.BonSortieService;
import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public BonSortieController(BonSortieService bonSortieService, BonSortiePdfService bonSortiePdfService) {
        this.bonSortieService = bonSortieService;
        this.bonSortiePdfService = bonSortiePdfService;
    }

    @GetMapping
    public List<BonSortieDto> lister(@AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.listerVisibles(principal.getUtilisateur());
    }

    @GetMapping("/{id}")
    public BonSortieDto obtenir(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.obtenirParId(id, principal.getUtilisateur());
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

    @PutMapping("/{id}/retour")
    public BonSortieDto renseignerRetour(@PathVariable Long id, @Valid @RequestBody ModifierRetourRequest requete,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortieService.renseignerRetour(id, requete, principal.getUtilisateur());
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
