package com.snef.sgbf.mission.controller;

import com.snef.sgbf.mission.dto.AffectationMissionDto;
import com.snef.sgbf.mission.dto.AffecterAgentRequest;
import com.snef.sgbf.mission.dto.InterrompreAffectationRequest;
import com.snef.sgbf.mission.dto.ReaffecterMiMissionRequest;
import com.snef.sgbf.mission.dto.ReaffecterRequest;
import com.snef.sgbf.mission.service.AffectationMissionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API du cycle de vie des affectations (creation, interruption,
 * reaffectation - section 5 a 8, RG-MIS-001 a 008).
 *
 * <p>Toutes les operations d'ecriture sont reservees au Charge d'Affaires et
 * a la personne habilitee (section 10, 16) ; le controle de perimetre fin
 * (le service de l'agent concerne, pas seulement le role) est verifie dans
 * {@link AffectationMissionService}, pas ici - {@code @PreAuthorize} ne
 * fournit qu'un premier filtrage grossier par role (voir Javadoc de
 * {@code security.CustomUserDetails}).
 */
@RestController
@RequestMapping("/api/affectations-mission")
public class AffectationMissionController {

    private final AffectationMissionService affectationMissionService;

    public AffectationMissionController(AffectationMissionService affectationMissionService) {
        this.affectationMissionService = affectationMissionService;
    }

    @GetMapping
    public List<AffectationMissionDto> listerPourMission(@RequestParam Long missionId) {
        return affectationMissionService.listerPourMission(missionId);
    }

    @GetMapping("/{id}")
    public AffectationMissionDto obtenir(@PathVariable Long id) {
        return affectationMissionService.obtenirParId(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<AffectationMissionDto> affecter(@Valid @RequestBody AffecterAgentRequest requete,
                                                            @AuthenticationPrincipal CustomUserDetails principal) {
        AffectationMissionDto creee = affectationMissionService.affecter(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(creee);
    }

    @PostMapping("/{id}/interrompre")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public AffectationMissionDto interrompre(@PathVariable Long id,
                                              @Valid @RequestBody InterrompreAffectationRequest requete,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return affectationMissionService.interrompre(id, requete, principal.getUtilisateur());
    }

    @PostMapping("/{id}/reaffecter")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<AffectationMissionDto> reaffecter(@PathVariable Long id,
                                                              @Valid @RequestBody ReaffecterRequest requete,
                                                              @AuthenticationPrincipal CustomUserDetails principal) {
        AffectationMissionDto nouvelle = affectationMissionService.reaffecter(id, requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(nouvelle);
    }

    /** Reaffectation en une seule etape pendant qu'une mission est encore en cours (section 9-13, evolution du 2026-08-20). */
    @PostMapping("/reaffecter-mi-mission")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<AffectationMissionDto> reaffecterPendantMissionEnCours(
            @Valid @RequestBody ReaffecterMiMissionRequest requete,
            @AuthenticationPrincipal CustomUserDetails principal) {
        AffectationMissionDto nouvelle = affectationMissionService.reaffecterPendantMissionEnCours(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(nouvelle);
    }
}
