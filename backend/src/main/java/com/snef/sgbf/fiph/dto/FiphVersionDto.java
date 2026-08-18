package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Representation en lecture d'une {@link com.snef.sgbf.fiph.entity.FIPHVersion}, avec le detail de ses lignes de pointage. */
public record FiphVersionDto(
        Long id,
        Long fiphId,
        int numeroVersion,
        LocalDateTime dateCreation,
        String creeParIdentifiant,
        String motifModification,
        Long versionPrecedenteId,
        BigDecimal totalHN,
        BigDecimal totalHS,
        StatutFiphVersion statutVersion,
        String empreinteIntegrite,
        Integer lockVersion,
        List<PointageDto> pointages
) {
}
