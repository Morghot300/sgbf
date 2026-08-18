package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.DecisionValidation;
import java.time.LocalDateTime;

/** Representation en lecture d'une {@link com.snef.sgbf.fiph.entity.Validation}. */
public record ValidationDto(
        Long id,
        String utilisateurIdentifiant,
        int niveauValidation,
        DecisionValidation decision,
        LocalDateTime dateValidation,
        String commentaire,
        String statutAvant,
        String statutApres
) {
}
