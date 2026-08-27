package com.snef.sgbf.fiph.controller;

import com.snef.sgbf.fiph.dto.CreerFiphManuelleRequest;
import com.snef.sgbf.fiph.dto.FiphDto;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import com.snef.sgbf.fiph.service.FiphService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Filtres optionnels combinables (evolution du 2026-08-18, section 2) - voir Javadoc de {@code FiphService.listerVisibles}. */
    @GetMapping
    public List<FiphDto> lister(@AuthenticationPrincipal CustomUserDetails principal,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateDebut,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFin,
                                 @RequestParam(required = false) @Nullable StatutFiphVersion statut,
                                 @RequestParam(required = false) @Nullable Long serviceId,
                                 @RequestParam(required = false) @Nullable String nomComplet) {
        return fiphService.listerVisibles(principal.getUtilisateur(), date, dateDebut, dateFin, statut, serviceId, nomComplet);
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
