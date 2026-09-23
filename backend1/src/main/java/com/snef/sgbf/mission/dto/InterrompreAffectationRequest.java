package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Donnees necessaires pour declarer l'interruption d'une affectation
 * (RG-MIS-001, RG-MIS-002). {@link #commentaire} est obligatoire lorsque
 * {@link #motifCode} vaut {@code AUTRE} (verifie en service, voir
 * {@code AffectationMissionService}) - limite de longueur explicite en
 * cohesion avec RG-SEC-003 (assainissement des champs texte libre).
 */
public record InterrompreAffectationRequest(

        @NotBlank(message = "Le motif d'interruption est obligatoire.")
        String motifCode,

        @NotNull(message = "La date d'interruption est obligatoire.")
        LocalDate dateInterruption,

        @Size(max = 500, message = "Le commentaire ne peut pas depasser 500 caracteres.")
        String commentaire
) {
}
