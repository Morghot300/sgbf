package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correction de l'adresse e-mail par l'Administrateur (section 8 de la mission d'evolution du 2026-08-18). */
public record ModifierEmailRequest(

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail doit etre valide.")
        @Size(max = 150)
        String email
) {
}
