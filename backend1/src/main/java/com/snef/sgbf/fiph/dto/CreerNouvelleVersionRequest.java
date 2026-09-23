package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** RG-VER-002 : motif obligatoire pour toute nouvelle version (correction post-validation). */
public record CreerNouvelleVersionRequest(

        @NotBlank(message = "Le motif de la nouvelle version est obligatoire.")
        @Size(max = 500)
        String motifModification
) {
}
