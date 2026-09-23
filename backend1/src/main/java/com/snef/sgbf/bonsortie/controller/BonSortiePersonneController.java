package com.snef.sgbf.bonsortie.controller;

import com.snef.sgbf.bonsortie.dto.AjouterPersonneABordRequest;
import com.snef.sgbf.bonsortie.dto.AjouterPersonnesABordEnLotRequest;
import com.snef.sgbf.bonsortie.dto.BonSortiePersonneDto;
import com.snef.sgbf.bonsortie.service.BonSortiePersonneService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API des personnes a bord d'un bon de sortie principal (section 9.2,
 * RG-PAB-001 a 009). Ouverte a l'agent emetteur lui-meme et au gestionnaire
 * de son service (controle fait en service, voir
 * {@code BonSortieService.verifierAutoServiceOuGestionnaire}) - pas de
 * restriction de role au niveau du controleur.
 */
@RestController
@RequestMapping("/api/bons-sortie/{bonSortiePrincipalId}/personnes-a-bord")
public class BonSortiePersonneController {

    private final BonSortiePersonneService bonSortiePersonneService;

    public BonSortiePersonneController(BonSortiePersonneService bonSortiePersonneService) {
        this.bonSortiePersonneService = bonSortiePersonneService;
    }

    @GetMapping
    public List<BonSortiePersonneDto> lister(@PathVariable Long bonSortiePrincipalId) {
        return bonSortiePersonneService.listerPourBonSortie(bonSortiePrincipalId);
    }

    @PostMapping
    public ResponseEntity<BonSortiePersonneDto> ajouter(@PathVariable Long bonSortiePrincipalId,
                                                          @Valid @RequestBody AjouterPersonneABordRequest requete,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        BonSortiePersonneDto creee = bonSortiePersonneService.ajouter(bonSortiePrincipalId, requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(creee);
    }

    /**
     * Ajout groupe (evolution du 2026-08-19, Lot 4) : un seul appel pour
     * l'ensemble de la selection, transactionnel (tout ou rien pour les
     * echecs reels) et idempotent (les personnes deja associees sont
     * silencieusement ignorees, jamais une erreur).
     */
    @PostMapping("/lot")
    public ResponseEntity<List<BonSortiePersonneDto>> ajouterEnLot(@PathVariable Long bonSortiePrincipalId,
                                                                     @Valid @RequestBody AjouterPersonnesABordEnLotRequest requete,
                                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        List<BonSortiePersonneDto> creees = bonSortiePersonneService.ajouterEnLot(bonSortiePrincipalId, requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(creees);
    }

    @DeleteMapping("/{associationId}")
    public BonSortiePersonneDto retirer(@PathVariable Long bonSortiePrincipalId, @PathVariable Long associationId,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        return bonSortiePersonneService.retirer(associationId, principal.getUtilisateur());
    }
}
