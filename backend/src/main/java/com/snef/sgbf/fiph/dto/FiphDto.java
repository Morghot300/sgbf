package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.OrigineFiph;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import java.time.LocalDate;

/** Representation en lecture d'une {@link com.snef.sgbf.fiph.entity.FIPH} (identite stable, sans le detail de la version courante - voir {@link FiphVersionDto}). */
public record FiphDto(
        Long id,
        Long agentId,
        String agentNomComplet,
        String agentMatricule,
        Long serviceId,
        String serviceLibelle,
        OrigineFiph origine,
        Long bonSortieId,
        int annee,
        int mois,
        int numeroSemaine,
        LocalDate dateDebutPeriode,
        LocalDate dateFinPeriode,
        StatutFiphVersion statut,
        Long versionCouranteId,
        Integer versionCouranteNumero
) {
}
