package com.snef.sgbf.fiph.controller;

import com.snef.sgbf.fiph.dto.CreerFiphManuelleRequest;
import com.snef.sgbf.fiph.dto.FiphDto;
import com.snef.sgbf.fiph.service.FiphService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de l'identite FIPH (section 4, 17). La generation automatique
 * (RG-BS-007, RG-FIPH-001) n'est jamais exposee directement : elle decoule
 * uniquement de la validation d'un bon de sortie (voir
 * {@code BonSortieService.valider}). Seule la creation MANUELLE (Code
 * Service) passe par un appel API explicite.
 */
@RestController
@RequestMapping("/api/fiph")
public class FiphController {

    private final FiphService fiphService;

    public FiphController(FiphService fiphService) {
        this.fiphService = fiphService;
    }

    @GetMapping
    public List<FiphDto> lister(@AuthenticationPrincipal CustomUserDetails principal) {
        return fiphService.listerVisibles(principal.getUtilisateur());
    }

    @GetMapping("/{id}")
    public FiphDto obtenir(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphService.obtenirParId(id, principal.getUtilisateur());
    }

    @PostMapping("/manuelle")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<FiphDto> creerManuelle(@Valid @RequestBody CreerFiphManuelleRequest requete,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        FiphDto creee = fiphService.creerManuelle(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(creee);
    }
}
