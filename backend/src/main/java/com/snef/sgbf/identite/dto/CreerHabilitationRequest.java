package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires a l'attribution d'une habilitation (RG-HAB-001) par
 * l'Administrateur. {@link #serviceId} peut etre {@code null} uniquement pour
 * les roles a perimetre global (RH, ADMINISTRATEUR) - {@code HabilitationService}
 * rejette toute autre combinaison.
 */
public record CreerHabilitationRequest(

        @NotNull(message = "L'utilisateur beneficiaire est obligatoire.")
        Long utilisateurId,

        @NotNull(message = "Le role metier est obligatoire.")
        String roleMetierCode,

        Long serviceId,

        @NotNull(message = "La date de debut est obligatoire.")
        LocalDate dateDebut,

        LocalDate dateFin
) {
}
