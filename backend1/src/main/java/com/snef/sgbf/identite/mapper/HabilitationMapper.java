package com.snef.sgbf.identite.mapper;

import com.snef.sgbf.identite.dto.HabilitationDto;
import com.snef.sgbf.identite.entity.Habilitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link Habilitation} vers {@link HabilitationDto}. */
@Mapper(componentModel = "spring")
public interface HabilitationMapper {

    @Mapping(target = "utilisateurId", source = "utilisateur.id")
    @Mapping(target = "utilisateurIdentifiant", source = "utilisateur.identifiant")
    @Mapping(target = "roleMetierCode", source = "roleMetier.code")
    @Mapping(target = "roleMetierLibelle", source = "roleMetier.libelle")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceLibelle", source = "service.libelle")
    HabilitationDto toDto(Habilitation habilitation);
}
