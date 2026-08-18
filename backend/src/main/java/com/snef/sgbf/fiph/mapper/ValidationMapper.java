package com.snef.sgbf.fiph.mapper;

import com.snef.sgbf.fiph.dto.ValidationDto;
import com.snef.sgbf.fiph.entity.Validation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link Validation} vers {@link ValidationDto}. */
@Mapper(componentModel = "spring")
public interface ValidationMapper {

    @Mapping(target = "utilisateurIdentifiant", source = "utilisateur.identifiant")
    ValidationDto toDto(Validation validation);
}
