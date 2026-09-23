package com.snef.sgbf.security.dto;

import jakarta.validation.constraints.NotBlank;

/** Corps de requete de {@code POST /api/auth/login} : identifiant + mot de passe (premier facteur). */
public record LoginRequest(

        @NotBlank(message = "L'identifiant est obligatoire.")
        String identifiant,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String motDePasse
) {
}
