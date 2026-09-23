package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Reaffectation d'une habilitation vers un autre service (evolution du
 * 2026-08-19, section 10) - {@code nouveauServiceId} est toujours obligatoire
 * ici : cette action ne concerne que les roles a service exclusif
 * (Charge d'Affaires, Personne habilitee, Responsable d'Activite), qui
 * exigent tous un service (voir {@code CodeRoleMetier#estServiceExclusif}).
 */
public record ChangerServiceHabilitationRequest(
        @NotNull(message = "Le nouveau service est obligatoire.")
        Long nouveauServiceId
) {
}
