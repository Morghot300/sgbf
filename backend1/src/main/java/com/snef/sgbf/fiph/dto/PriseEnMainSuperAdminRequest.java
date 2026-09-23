package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Justification obligatoire d'une prise en main exceptionnelle d'une FIPH
 * par le Super Administrateur (evolution du 2026-08-19, section 14) - ex.
 * "Responsable indisponible - continuite du processus de validation."
 */
public record PriseEnMainSuperAdminRequest(

        @NotBlank(message = "Un commentaire justifiant la prise en main est obligatoire.")
        @Size(max = 500, message = "Le commentaire ne peut pas depasser 500 caracteres.")
        String commentaire
) {
}
