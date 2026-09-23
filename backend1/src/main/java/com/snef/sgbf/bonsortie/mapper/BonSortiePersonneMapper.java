package com.snef.sgbf.bonsortie.mapper;

import com.snef.sgbf.bonsortie.dto.BonSortiePersonneDto;
import com.snef.sgbf.bonsortie.entity.BonSortiePersonne;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link BonSortiePersonne} vers {@link BonSortiePersonneDto}. */
@Mapper(componentModel = "spring")
public interface BonSortiePersonneMapper {

    @Mapping(target = "bonSortiePrincipalId", source = "bonSortiePrincipal.id")
    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentNomComplet", expression = "java(association.getAgent().getNomComplet())")
    @Mapping(target = "agentMatricule", source = "agent.matricule")
    @Mapping(target = "bonSortieIndividuelId", source = "bonSortieIndividuel.id")
    BonSortiePersonneDto toDto(BonSortiePersonne association);
}
