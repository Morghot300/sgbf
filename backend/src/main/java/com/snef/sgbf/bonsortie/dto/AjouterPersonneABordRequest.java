package com.snef.sgbf.bonsortie.dto;

import jakarta.validation.constraints.NotNull;

/** RG-PAB-001 : la personne a bord est toujours identifiee par son {@code Agent} (matricule), jamais par une saisie libre. */
public record AjouterPersonneABordRequest(

        @NotNull(message = "L'agent (personne a bord) est obligatoire.")
        Long agentId
) {
}
