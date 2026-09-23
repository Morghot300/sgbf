package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires pour que le Charge d'Affaires/la personne habilitee
 * definisse ou modifie la date de fin d'une FIPH (evolution du 2026-08-21,
 * section 2/7). {@link #motifModification} n'est utilise que lorsque la
 * modification porte sur une version deja {@code VALIDEE_DEFINITIVEMENT}
 * (section 8) - obligatoire dans ce cas precis (RG-VER-002), ignore sinon ;
 * si absent alors qu'il est requis, un motif explicatif est genere
 * automatiquement a partir des anciennes/nouvelles dates.
 */
public record DefinirDateFinRequest(

        @NotNull(message = "La date de fin est obligatoire.")
        LocalDate dateFin,

        String motifModification
) {
}
