package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correction du nom/prenom/matricule d'une personne (evolution du 2026-08-19). */
public record ModifierIdentiteRequest(

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 100)
        String nom,

        @NotBlank(message = "Le prenom est obligatoire.")
        @Size(max = 100)
        String prenom,

        @Size(max = 20, message = "Le matricule ne peut pas depasser 20 caracteres.")
        String matricule
) {
}
