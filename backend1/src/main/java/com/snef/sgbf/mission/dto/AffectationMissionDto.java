package com.snef.sgbf.mission.dto;

import com.snef.sgbf.mission.entity.StatutAffectation;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Representation en lecture d'une {@link com.snef.sgbf.mission.entity.AffectationMission}. */
public record AffectationMissionDto(
        Long id,
        Long agentId,
        String agentNomComplet,
        String agentMatricule,
        Long missionId,
        String missionCodeHN,
        LocalDate dateDebutAffectation,
        LocalDate dateFinAffectation,
        StatutAffectation statutAffectation,
        String motifInterruptionCode,
        String motifInterruptionLibelle,
        String commentaireInterruption,
        Long affectationPrecedenteId,
        String creeParIdentifiant,
        LocalDateTime dateCreation
) {
}
