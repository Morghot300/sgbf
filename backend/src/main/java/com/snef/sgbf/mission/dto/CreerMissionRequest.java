package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires a la creation d'une {@link com.snef.sgbf.mission.entity.Mission}.
 *
 * <p>{@link #missionPrecedenteId} n'est renseigne que lorsque cette mission
 * nait d'une reaffectation consecutive a l'interruption d'une autre
 * (RG-MIS-005) - dans le cas courant (nouvelle mission independante), il
 * reste {@code null}.
 */
public record CreerMissionRequest(

        @NotNull(message = "Le code mission (Code HN) est obligatoire.")
        Long codeHNId,

        @NotNull(message = "Le chantier est obligatoire.")
        Long chantierId,

        @NotNull(message = "La date de debut prevue est obligatoire.")
        LocalDate dateDebutPrevue,

        @NotNull(message = "La date de fin prevue est obligatoire.")
        @FutureOrPresent(message = "La date de fin prevue ne peut pas etre dans le passe.")
        LocalDate dateFinPrevue,

        Long missionPrecedenteId
) {
}
