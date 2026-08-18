package com.snef.sgbf.identite.mapper;

import com.snef.sgbf.identite.dto.UtilisateurDto;
import com.snef.sgbf.identite.entity.Utilisateur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversion {@link Utilisateur} vers {@link UtilisateurDto}.
 *
 * <p>Aucun champ sensible (hash de mot de passe) n'existe dans le DTO cible :
 * il n'y a donc rien a exclure explicitement, l'absence de mapping suffit a
 * garantir qu'il ne fuira jamais vers l'API - c'est le DTO lui-meme qui porte
 * cette garantie, pas une exclusion au cas par cas.
 */
@Mapper(componentModel = "spring")
public interface UtilisateurMapper {

    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceLibelle", source = "service.libelle")
    UtilisateurDto toDto(Utilisateur utilisateur);
}
