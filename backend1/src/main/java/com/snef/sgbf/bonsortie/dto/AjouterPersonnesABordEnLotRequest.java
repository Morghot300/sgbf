package com.snef.sgbf.bonsortie.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Ajout groupe de personnes a bord, transactionnel et idempotent (evolution du 2026-08-19, Lot 4). */
public record AjouterPersonnesABordEnLotRequest(

        @NotEmpty(message = "Selectionnez au moins une personne a ajouter.")
        List<Long> agentIds
) {
}
