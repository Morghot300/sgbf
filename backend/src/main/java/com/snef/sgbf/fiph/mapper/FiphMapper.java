package com.snef.sgbf.fiph.mapper;

import com.snef.sgbf.fiph.dto.FiphDto;
import com.snef.sgbf.fiph.entity.FIPH;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link FIPH} vers {@link FiphDto}. */
@Mapper(componentModel = "spring")
public interface FiphMapper {

    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentNomComplet", expression = "java(fiph.getAgent().getNomComplet())")
    @Mapping(target = "agentMatricule", source = "agent.matricule")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceLibelle", source = "service.libelle")
    @Mapping(target = "bonSortieId", source = "bonSortie.id")
    @Mapping(target = "versionCouranteId", source = "versionCourante.id")
    @Mapping(target = "versionCouranteNumero", source = "versionCourante.numeroVersion")
    FiphDto toDto(FIPH fiph);
}
