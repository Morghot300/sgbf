package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Representation en lecture d'une {@link com.snef.sgbf.fiph.entity.FIPHVersion},
 * avec le detail de ses lignes de pointage.
 *
 * <p>{@link #dateDebutPeriode} est celle de la {@link com.snef.sgbf.fiph.entity.FIPH}
 * parente (immuable, issue du Bon de Sortie) ; {@link #dateFinPeriode} est
 * propre a CETTE version, {@code null} tant qu'elle n'a pas encore ete
 * definie par le Charge d'Affaires/la personne habilitee (evolution du
 * 2026-08-21). {@link #avertissementPeriode}, recalcule a chaque lecture
 * (jamais persiste - meme principe que {@code BonSortieDto.avertissementAffectation}),
 * signale les jours de la periode qui restent exclus du tableau de pointage
 * faute d'affectation reelle de l'agent ce jour-la.
 */
public record FiphVersionDto(
        Long id,
        Long fiphId,
        int numeroVersion,
        LocalDateTime dateCreation,
        String creeParIdentifiant,
        String motifModification,
        Long versionPrecedenteId,
        LocalDate dateDebutPeriode,
        LocalDate dateFinPeriode,
        String avertissementPeriode,
        BigDecimal totalHN,
        BigDecimal totalHS,
        StatutFiphVersion statutVersion,
        String empreinteIntegrite,
        Integer lockVersion,
        List<PointageDto> pointages
) {
}
