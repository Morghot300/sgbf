package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Donnees necessaires pour affecter un agent a une mission (creation de l'{@code AffectationMission} initiale). */
public record AffecterAgentRequest(

        @NotNull(message = "L'agent est obligatoire.")
        Long agentId,

        @NotNull(message = "La mission est obligatoire.")
        Long missionId,

        @NotNull(message = "La date de debut d'affectation est obligatoire.")
        LocalDate dateDebutAffectation
) {
}
