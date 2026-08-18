package com.snef.sgbf.identite.mapper;

import com.snef.sgbf.identite.dto.AgentDto;
import com.snef.sgbf.identite.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversion {@link Agent} (entite JPA) vers {@link AgentDto} (contrat API).
 * Genere a la compilation par MapStruct - aucune implementation manuelle a
 * maintenir (voir choix technique justifie en architecture technique §3).
 */
@Mapper(componentModel = "spring")
public interface AgentMapper {

    @Mapping(target = "nomComplet", expression = "java(agent.getNomComplet())")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceLibelle", source = "service.libelle")
    @Mapping(target = "possedeCompte", expression = "java(agent.getUtilisateur() != null)")
    AgentDto toDto(Agent agent);
}
