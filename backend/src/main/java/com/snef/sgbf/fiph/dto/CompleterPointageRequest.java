package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Complete les heures d'une ligne de pointage existante (RG-FIPH-009/010).
 *
 * <p>Le document source note explicitement (section 4.2, point non tranche
 * §29.1) que "les heures d'un seul bon de sortie ne suffisent pas a
 * determiner heures normales / heures supplementaires" : ce calcul n'est
 * donc jamais automatique dans ce systeme (voir {@code FiphVersionService}) -
 * une ligne generee automatiquement porte des heures a zero jusqu'a ce
 * qu'une personne habilitee les complete explicitement ici.
 */
public record CompleterPointageRequest(

        @NotNull(message = "La date de la ligne de pointage a completer est obligatoire.")
        LocalDate datePointage,

        @NotNull(message = "Les heures normales sont obligatoires (0 si non travaillees).")
        @PositiveOrZero
        BigDecimal heuresNormales,

        @NotNull(message = "Les heures supplementaires sont obligatoires (0 si aucune).")
        @PositiveOrZero
        BigDecimal heuresSup
) {
}
