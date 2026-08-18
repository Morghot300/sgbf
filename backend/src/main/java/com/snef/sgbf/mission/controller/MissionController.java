package com.snef.sgbf.mission.controller;

import com.snef.sgbf.mission.dto.CreerMissionRequest;
import com.snef.sgbf.mission.dto.MissionDto;
import com.snef.sgbf.mission.service.MissionService;
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
 * API des missions (section 5.1). La lecture reste ouverte a tout
 * utilisateur authentifie (consultation par la Direction, la RH, les
 * valideurs...) ; la creation est reservee au Charge d'Affaires et a la
 * personne habilitee (section 16).
 */
@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    public List<MissionDto> lister() {
        return missionService.listerToutes();
    }

    @GetMapping("/{id}")
    public MissionDto obtenir(@PathVariable Long id) {
        return missionService.obtenirParId(id);
    }

    /** Cas d'utilisation "Consulter l'historique d'une mission" (section 16) : chaine complete via missionPrecedente. */
    @GetMapping("/{id}/historique")
    public List<MissionDto> consulterHistorique(@PathVariable Long id) {
        return missionService.consulterChaineHistorique(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<MissionDto> creer(@Valid @RequestBody CreerMissionRequest requete,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        MissionDto cree = missionService.creer(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }
}
