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
        StatutFiphVersion statut,
        Long versionCouranteId,
        Integer versionCouranteNumero,
        /** Mission choisie a la creation (evolution du 2026-08-27, section 6-8) - {@code null} si non renseignee. */
        Long missionId,
        String missionCodeHN,
        String missionChantierLibelle,
        /** Calcule a chaque lecture (jamais persiste, meme principe que BonSortieDto.avertissementAffectation) : signale, sans jamais bloquer, que l'agent ne dispose d'aucune affectation connue sur la mission choisie. */
        String avertissementMission
) {
    /** Reconstruit ce DTO avec l'avertissement de coherence mission calcule (voir FiphService). */
    public FiphDto avecAvertissementMission(String avertissement) {
        return new FiphDto(id, agentId, agentNomComplet, agentMatricule, serviceId, serviceLibelle, origine,
                bonSortieId, annee, mois, numeroSemaine, dateDebutPeriode, statut, versionCouranteId,
                versionCouranteNumero, missionId, missionCodeHN, missionChantierLibelle, avertissement);
    }
}
