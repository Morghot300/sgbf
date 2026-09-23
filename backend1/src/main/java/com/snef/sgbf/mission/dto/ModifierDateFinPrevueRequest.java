package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Prolongation ou reduction de la date de fin prevue d'une mission en cours
 * (evolution du 2026-08-26). {@link #motif} n'est pas obligatoire mais
 * recommande, notamment pour une reduction.
 */
public record ModifierDateFinPrevueRequest(

        @NotNull(message = "La nouvelle date de fin prevue est obligatoire.")
        LocalDate nouvelleDateFinPrevue,

        String motif
) {
}
