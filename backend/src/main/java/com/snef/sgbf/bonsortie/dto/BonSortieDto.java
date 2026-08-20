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
        Integer lockVersion,
        /**
         * Message actionnable si aucune affectation active n'est resolue pour
         * l'agent a la date de sortie (evolution du 2026-08-19, Lot 2) : ni
         * bloquant ni silencieux - visible avant meme la validation (pour que
         * le Charge d'Affaires puisse corriger en amont) et apres, si le bon a
         * ete valide malgre cette absence (decision confirmee : avertissement,
         * pas blocage). {@code null} des qu'une affectation est resolue.
         */
        String avertissementAffectation
) {
    /** Reconstruit ce DTO avec l'avertissement d'affectation calcule (voir BonSortieService). */
    public BonSortieDto avecAvertissementAffectation(String avertissement) {
        return new BonSortieDto(id, agentId, agentNomComplet, agentMatricule, vehiculeId, vehiculeImmatriculation,
                affectationMissionId, missionCodeHN, moyenUtilise, precisionVehicule, lt, kilometrage, dateSortie,
                heureSortie, heureRetour, lieu, codeAffaireSaisi, motifSortie, statut, origine, bonSortiePrincipalId,
                viseParIdentifiant, dateVisa, valideParIdentifiant, dateValidation, lockVersion, avertissement);
    }
}
