package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.JourSemaine;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Representation en lecture d'une {@link com.snef.sgbf.fiph.entity.Pointage}. */
public record PointageDto(
        Long id,
        JourSemaine jourSemaine,
        LocalDate datePointage,
        BigDecimal heuresNormales,
        BigDecimal heuresSup,
        Long affectationMissionId,
        String codeMission,
        Long serviceId,
        String codeService
) {
}
