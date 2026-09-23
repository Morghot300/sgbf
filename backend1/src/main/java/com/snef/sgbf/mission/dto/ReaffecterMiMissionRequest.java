package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Donnees necessaires pour reaffecter un agent vers une nouvelle mission
 * alors que sa mission actuelle est encore en cours (evolution du
 * 2026-08-20, section 9-13) : contrairement a {@link ReaffecterRequest},
 * qui suppose une affectation deja interrompue au prealable, cette requete
 * agit directement sur l'affectation ACTIVE de l'agent - la fermeture de
 * celle-ci (a la veille de {@link #dateDebutAffectation}) est calculee et
 * appliquee automatiquement par le service, en une seule operation
 * atomique.
 *
 * <p>Evolution du 2026-08-26 - "les mission et code mission ne seront pas
 * des liste deroulante mais une zone texte" : la mission cible n'est plus
 * choisie dans une liste deroulante de missions existantes ({@code
 * missionCibleId}) mais nait toujours d'une creation a la volee a partir de
 * {@link #codeChantier}/{@link #codeMission} saisis librement (chantier et
 * code mission reutilises s'ils existent deja, crees sinon) - coherent avec
 * le nom meme de l'action ("reaffecter vers une NOUVELLE mission").
 *
 * <p>Evolution du 2026-08-27 (brief "Evolution avancee du module Bon de
 * Sortie, Missions et FIPH", section 18-22) : {@link #dateFinAffectation}
 * (facultative) borne desormais la nouvelle affectation dans le temps -
 * "definir une date de fin" signifie "diversion temporaire" : la mission
 * precedente reprend AUTOMATIQUEMENT le jour suivant, jusqu'a son propre
 * terme d'origine (ou indefiniment si elle etait ouverte), sans aucune
 * action manuelle supplementaire. Laisser ce champ vide reproduit
 * exactement le comportement d'origine (bascule permanente, jamais de
 * reprise).
 */
public record ReaffecterMiMissionRequest(

        @NotNull(message = "L'agent est obligatoire.")
        Long agentId,

        @NotBlank(message = "Le code chantier est obligatoire.")
        @Size(max = 30)
        String codeChantier,

        @Size(max = 150)
        String libelleChantier,

        @NotBlank(message = "Le code mission est obligatoire.")
        @Size(max = 30)
        String codeMission,

        @Size(max = 150)
        String libelleCodeMission,

        @NotNull(message = "La date de debut prevue de la nouvelle mission est obligatoire.")
        LocalDate dateDebutPrevueMission,

        @NotNull(message = "La date de fin prevue de la nouvelle mission est obligatoire.")
        @FutureOrPresent(message = "La date de fin prevue ne peut pas etre dans le passe.")
        LocalDate dateFinPrevueMission,

        @NotNull(message = "La date de debut de la nouvelle affectation est obligatoire.")
        LocalDate dateDebutAffectation,

        LocalDate dateFinAffectation
) {
}
