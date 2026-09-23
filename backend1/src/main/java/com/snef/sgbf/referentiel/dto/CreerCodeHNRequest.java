package com.snef.sgbf.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Point non tranche par le document source, section 29.1 : "definition exacte du Code HN et de sa relation precise avec le code affaire". Modelise ici comme rattache a un {@code Chantier}, conformement a la section 5.1. */
public record CreerCodeHNRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String libelle,
        @NotNull Long chantierId
) {
}
