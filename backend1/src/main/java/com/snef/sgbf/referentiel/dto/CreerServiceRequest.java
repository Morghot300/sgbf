package com.snef.sgbf.referentiel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** RG-FIPH-006 : {@code codeService} doit rester unique et invariant une fois attribue. */
public record CreerServiceRequest(
        @NotBlank @Size(max = 20) String codeService,
        @NotBlank @Size(max = 150) String libelle
) {
}
