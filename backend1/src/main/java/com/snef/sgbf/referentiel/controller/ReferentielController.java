package com.snef.sgbf.referentiel.controller;

import com.snef.sgbf.referentiel.dto.ChantierDto;
import com.snef.sgbf.referentiel.dto.CodeHNDto;
import com.snef.sgbf.referentiel.dto.CreerChantierRequest;
import com.snef.sgbf.referentiel.dto.CreerCodeHNRequest;
import com.snef.sgbf.referentiel.dto.CreerServiceRequest;
import com.snef.sgbf.referentiel.dto.CreerVehiculeRequest;
import com.snef.sgbf.referentiel.dto.MotifInterruptionDto;
import com.snef.sgbf.referentiel.dto.ServiceDto;
import com.snef.sgbf.referentiel.dto.VehiculeDto;
import com.snef.sgbf.referentiel.service.ReferentielService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API des referentiels de base (service, chantier, code mission, vehicule,
 * motif d'interruption).
 *
 * <p>Lecture ouverte a tout utilisateur authentifie (ces listes alimentent
 * les listes deroulantes des formulaires bon de sortie / FIPH / missions) ;
 * ecriture reservee a l'Administrateur.
 */
@RestController
@RequestMapping("/api/referentiels")
public class ReferentielController {

    private final ReferentielService referentielService;

    public ReferentielController(ReferentielService referentielService) {
        this.referentielService = referentielService;
    }

    @GetMapping("/services")
    public List<ServiceDto> listerServices() {
        return referentielService.listerServices();
    }

    @PostMapping("/services")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<ServiceDto> creerService(@Valid @RequestBody CreerServiceRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referentielService.creerService(requete));
    }

    @GetMapping("/chantiers")
    public List<ChantierDto> listerChantiers() {
        return referentielService.listerChantiers();
    }

    @PostMapping("/chantiers")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<ChantierDto> creerChantier(@Valid @RequestBody CreerChantierRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referentielService.creerChantier(requete));
    }

    @GetMapping("/codes-hn")
    public List<CodeHNDto> listerCodesHN() {
        return referentielService.listerCodesHN();
    }

    @PostMapping("/codes-hn")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<CodeHNDto> creerCodeHN(@Valid @RequestBody CreerCodeHNRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referentielService.creerCodeHN(requete));
    }

    @GetMapping("/vehicules")
    public List<VehiculeDto> listerVehicules() {
        return referentielService.listerVehicules();
    }

    @PostMapping("/vehicules")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<VehiculeDto> creerVehicule(@Valid @RequestBody CreerVehiculeRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referentielService.creerVehicule(requete));
    }

    @GetMapping("/motifs-interruption")
    public List<MotifInterruptionDto> listerMotifsInterruption() {
        return referentielService.listerMotifsInterruption();
    }
}
