package com.snef.sgbf.mission.mapper;

import com.snef.sgbf.mission.dto.AffectationMissionDto;
import com.snef.sgbf.mission.entity.AffectationMission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link AffectationMission} vers {@link AffectationMissionDto}. */
@Mapper(componentModel = "spring")
public interface AffectationMissionMapper {

    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentNomComplet", expression = "java(affectation.getAgent().getNomComplet())")
    @Mapping(target = "agentMatricule", source = "agent.matricule")
    @Mapping(target = "missionId", source = "mission.id")
    @Mapping(target = "missionCodeHN", source = "mission.codeHN.code")
    @Mapping(target = "motifInterruptionCode", source = "motifInterruption.code")
    @Mapping(target = "motifInterruptionLibelle", source = "motifInterruption.libelle")
    @Mapping(target = "affectationPrecedenteId", source = "affectationPrecedente.id")
    @Mapping(target = "creeParIdentifiant", source = "creePar.identifiant")
    AffectationMissionDto toDto(AffectationMission affectation);
}
