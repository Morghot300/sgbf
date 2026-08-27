package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Donnees necessaires a la creation directe ("manuelle") d'une ou plusieurs
 * FIPH d'origine {@code MANUELLE} (Code Service), reservee au Charge
 * d'Affaires et a la personne habilitee (RG-FIPH-004, RG-FIPH-010).
 *
 * <p>Evolution du 2026-08-27 (brief "Evolution du module FIPH", section 2-3-4-14) :
 * {@link #agentIds} remplace l'ancien identifiant unique - le personnel du
 * service est desormais selectionne par cases a cocher (jamais une saisie
 * libre d'identifiant numerique), une FIPH etant creee independamment pour
 * chaque agent selectionne (voir {@code FiphService#creerManuelleEnLot} :
 * l'echec de l'une n'empeche jamais la creation des autres - meme
 * philosophie de tolerance que {@code PersonneABordGenerationService}).
 *
 * <p>{@link #missionId} (section 6-7-8) est optionnel : rattache
 * explicitement la FIPH creee a une mission existante (Code Mission), sans
 * jamais se substituer a la resolution par-jour deja portee par chaque ligne
 * de pointage.
 */
public record CreerFiphManuelleRequest(

        @NotEmpty(message = "Au moins un agent doit etre selectionne.")
        List<Long> agentIds,

        @NotNull(message = "La date de debut est obligatoire.")
        LocalDate dateDebut,

        LocalDate dateFin,

        Long missionId
) {
}
