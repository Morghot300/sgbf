package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correction du login de connexion par l'Administrateur (section 8 de la mission d'evolution du 2026-08-18). */
public record ModifierIdentifiantRequest(

        @NotBlank(message = "L'identifiant est obligatoire.")
        @Size(max = 60)
        String identifiant
) {
}
