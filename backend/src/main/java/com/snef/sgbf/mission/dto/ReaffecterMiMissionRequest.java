package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires pour reaffecter un agent vers une nouvelle mission
 * alors que sa mission actuelle est encore en cours (evolution du
 * 2026-08-20, section 9-13) : contrairement a {@link ReaffecterRequest},
 * qui suppose une affectation deja interrompue au prealable, cette requete
 * agit directement sur l'affectation ACTIVE de l'agent - la fermeture de
 * celle-ci (a la veille de {@link #dateDebutAffectation}) est calculee et
 * appliquee automatiquement par le service, en une seule operation
 * atomique.
 */
public record ReaffecterMiMissionRequest(

        @NotNull(message = "L'agent est obligatoire.")
        Long agentId,

        @NotNull(message = "La mission cible est obligatoire.")
        Long missionCibleId,

        @NotNull(message = "La date de debut de la nouvelle affectation est obligatoire.")
        LocalDate dateDebutAffectation
) {
}
