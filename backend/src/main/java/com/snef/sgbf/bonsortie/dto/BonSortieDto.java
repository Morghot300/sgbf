package com.snef.sgbf.bonsortie.dto;

import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.bonsortie.entity.OrigineBonSortie;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Representation en lecture d'un {@link com.snef.sgbf.bonsortie.entity.BonSortie}. */
public record BonSortieDto(
        Long id,
        Long agentId,
        String agentNomComplet,
        String agentMatricule,
        Long vehiculeId,
        String vehiculeImmatriculation,
        Long affectationMissionId,
        String missionCodeHN,
        MoyenUtilise moyenUtilise,
        String precisionVehicule,
        String lt,
        Integer kilometrage,
        LocalDate dateSortie,
        LocalTime heureSortie,
        LocalTime heureRetour,
        String lieu,
        String codeAffaireSaisi,
        String motifSortie,
        StatutBonSortie statut,
        OrigineBonSortie origine,
        Long bonSortiePrincipalId,
        String viseParIdentifiant,
        LocalDateTime dateVisa,
        String valideParIdentifiant,
        LocalDateTime dateValidation,
        Integer lockVersion
) {
}
