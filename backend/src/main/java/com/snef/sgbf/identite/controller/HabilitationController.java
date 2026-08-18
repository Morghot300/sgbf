package com.snef.sgbf.identite.controller;

import com.snef.sgbf.identite.dto.CreerHabilitationRequest;
import com.snef.sgbf.identite.dto.HabilitationDto;
import com.snef.sgbf.identite.service.HabilitationService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API d'attribution et de retrait des habilitations (RG-HAB-001 a 006).
 *
 * <p>Reservee a l'Administrateur. Toute la logique de validation des regles
 * (exclusivite RH, coherence du perimetre) est deleguee a
 * {@link HabilitationService} - ce controleur ne fait que traduire les
 * requetes HTTP.
 */
@RestController
@RequestMapping("/api/habilitations")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class HabilitationController {

    private final HabilitationService habilitationService;

    public HabilitationController(HabilitationService habilitationService) {
        this.habilitationService = habilitationService;
    }

    @GetMapping("/utilisateur/{utilisateurId}")
    public List<HabilitationDto> listerPourUtilisateur(@PathVariable Long utilisateurId) {
        return habilitationService.listerPourUtilisateur(utilisateurId);
    }

    @PostMapping
    public ResponseEntity<HabilitationDto> attribuer(@Valid @RequestBody CreerHabilitationRequest requete,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        HabilitationDto creee = habilitationService.attribuer(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(creee);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirer(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        habilitationService.retirer(id, principal.getUtilisateur());
        return ResponseEntity.noContent().build();
    }
}
