package com.snef.sgbf.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreerChantierRequest(
        @NotBlank @Size(max = 30) String codeAffaire,
        @NotBlank @Size(max = 150) String libelle
) {
}
