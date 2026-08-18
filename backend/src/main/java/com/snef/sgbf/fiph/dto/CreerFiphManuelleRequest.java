package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Donnees necessaires a la creation manuelle d'une FIPH d'origine
 * {@code MANUELLE} (Code Service, section 2 : "agents non concernes par une
 * mission durant la periode"), reservee au Charge d'Affaires et a la
 * personne habilitee (RG-FIPH-004, RG-FIPH-010).
 */
public record CreerFiphManuelleRequest(

        @NotNull(message = "L'agent est obligatoire.")
        Long agentId,

        @NotNull(message = "L'annee est obligatoire.")
        @Min(2020) @Max(2100)
        Integer annee,

        @NotNull(message = "Le numero de semaine (ISO 8601, 1 a 53) est obligatoire.")
        @Min(1) @Max(53)
        Integer numeroSemaine
) {
}
