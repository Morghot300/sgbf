package com.snef.sgbf.mission.dto;

import com.snef.sgbf.mission.entity.StatutMission;
import java.time.LocalDate;

/** Representation en lecture d'une {@link com.snef.sgbf.mission.entity.Mission}. */
public record MissionDto(
        Long id,
        String codeHN,
        String codeHNLibelle,
        Long chantierId,
        String chantierLibelle,
        LocalDate dateDebutPrevue,
        LocalDate dateFinPrevue,
        LocalDate dateFinReelle,
        StatutMission statut,
        Long missionPrecedenteId
) {
}
