package com.snef.sgbf.fiph.mapper;

import com.snef.sgbf.fiph.dto.PointageDto;
import com.snef.sgbf.fiph.entity.Pointage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link Pointage} vers {@link PointageDto}. */
@Mapper(componentModel = "spring")
public interface PointageMapper {

    @Mapping(target = "affectationMissionId", source = "affectationMission.id")
    @Mapping(target = "codeMission", source = "affectationMission.mission.codeHN.code")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "codeService", source = "service.codeService")
    PointageDto toDto(Pointage pointage);
}
