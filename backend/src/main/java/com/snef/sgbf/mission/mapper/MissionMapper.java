package com.snef.sgbf.mission.mapper;

import com.snef.sgbf.mission.dto.MissionDto;
import com.snef.sgbf.mission.entity.Mission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link Mission} vers {@link MissionDto}. */
@Mapper(componentModel = "spring")
public interface MissionMapper {

    @Mapping(target = "codeHN", source = "codeHN.code")
    @Mapping(target = "codeHNLibelle", source = "codeHN.libelle")
    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierLibelle", source = "chantier.libelle")
    @Mapping(target = "missionPrecedenteId", source = "missionPrecedente.id")
    MissionDto toDto(Mission mission);
}
