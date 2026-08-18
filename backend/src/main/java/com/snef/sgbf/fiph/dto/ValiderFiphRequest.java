package com.snef.sgbf.fiph.dto;

import com.snef.sgbf.fiph.entity.DecisionValidation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decision d'un valideur (niveau 2, 3 ou 4). {@link #commentaire} est
 * obligatoire pour {@link DecisionValidation#REJETEE} et
 * {@link DecisionValidation#RETOUR_POUR_CORRECTION} (regles proposees
 * RG-FIPH-026/027, verifie en service).
 */
public record ValiderFiphRequest(

        @NotNull(message = "La decision est obligatoire.")
        DecisionValidation decision,

        @Size(max = 500)
        String commentaire
) {
}
