package com.snef.sgbf.fiph.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires a la creation directe ("manuelle") d'une FIPH d'origine
 * {@code MANUELLE} (Code Service), reservee au Charge d'Affaires et a la
 * personne habilitee (RG-FIPH-004, RG-FIPH-010).
 *
 * <p>Evolution du 2026-08-26 (section 7-9) : meme modele de periode flexible
 * que les FIPH issues d'un Bon de Sortie - {@link #dateDebut} est saisie
 * librement par le createur (jamais deduite d'une semaine ISO), et
 * {@link #dateFin} reste optionnelle a la creation (periode "ouverte" tant
 * que non definie, exactement comme {@code FIPH.dateFinPeriode} pour une
 * FIPH issue d'un bon de sortie) - definissable/ajustable ensuite via
 * {@code FiphVersionService#definirDateFin}, avec la meme obligation
 * (RG-FIPH-033) de la renseigner avant toute soumission au circuit de
 * validation.
 */
public record CreerFiphManuelleRequest(

        @NotNull(message = "L'agent est obligatoire.")
        Long agentId,

        @NotNull(message = "La date de debut est obligatoire.")
        LocalDate dateDebut,

        LocalDate dateFin
) {
}
