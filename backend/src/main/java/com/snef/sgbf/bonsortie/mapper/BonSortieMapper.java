package com.snef.sgbf.bonsortie.mapper;

import com.snef.sgbf.bonsortie.dto.BonSortieDto;
import com.snef.sgbf.bonsortie.entity.BonSortie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Conversion {@link BonSortie} vers {@link BonSortieDto}. */
@Mapper(componentModel = "spring")
public interface BonSortieMapper {

    @Mapping(target = "agentId", source = "agent.id")
    @Mapping(target = "agentNomComplet", expression = "java(bonSortie.getAgent().getNomComplet())")
    @Mapping(target = "agentMatricule", source = "agent.matricule")
    @Mapping(target = "vehiculeId", source = "vehicule.id")
    @Mapping(target = "vehiculeImmatriculation", source = "vehicule.immatriculation")
    @Mapping(target = "affectationMissionId", source = "affectationMission.id")
    @Mapping(target = "missionCodeHN", source = "affectationMission.mission.codeHN.code")
    @Mapping(target = "bonSortiePrincipalId", source = "bonSortiePrincipal.id")
    @Mapping(target = "viseParIdentifiant", source = "visePar.identifiant")
    @Mapping(target = "valideParIdentifiant", source = "valideParCA.identifiant")
    // Calcule separement dans BonSortieService (necessite une resolution live de l'affectation, hors de portee d'un mapper pur).
    @Mapping(target = "avertissementAffectation", ignore = true)
    BonSortieDto toDto(BonSortie bonSortie);
}
