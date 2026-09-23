package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Donnees necessaires a la creation d'une {@link com.snef.sgbf.mission.entity.Mission}.
 *
 * <p>Evolution du 2026-08-26 - "les mission et code mission ne seront pas des
 * liste deroulante mais une zone texte ou on ajoutera des mission au clavier" :
 * {@link #codeChantier} et {@link #codeMission} sont desormais saisis
 * librement au clavier plutot que choisis dans une liste deroulante
 * alimentee par un referentiel gere exclusivement par l'Administrateur.
 * S'ils correspondent a un chantier/code mission (Code HN) deja existant,
 * celui-ci est reutilise tel quel ; sinon, un nouveau est cree a la volee
 * (voir {@code MissionService#resoudreOuCreerChantier}/{@code resoudreOuCreerCodeHN}),
 * avec {@link #libelleChantier}/{@link #libelleCodeMission} comme libelle
 * (repris du code lui-meme si laisse vide) - le Charge d'Affaires/la
 * personne habilitee n'a donc plus besoin de passer par l'Administrateur
 * pour enrichir ces referentiels au fil de l'eau.
 *
 * <p>{@link #missionPrecedenteId} n'est renseigne que lorsque cette mission
 * nait d'une reaffectation consecutive a l'interruption d'une autre
 * (RG-MIS-005) - dans le cas courant (nouvelle mission independante), il
 * reste {@code null}.
 */
public record CreerMissionRequest(

        @NotBlank(message = "Le code chantier est obligatoire.")
        @Size(max = 30)
        String codeChantier,

        @Size(max = 150)
        String libelleChantier,

        @NotBlank(message = "Le code mission (Code HN) est obligatoire.")
        @Size(max = 30)
        String codeMission,

        @Size(max = 150)
        String libelleCodeMission,

        @NotNull(message = "La date de debut prevue est obligatoire.")
        LocalDate dateDebutPrevue,

        @NotNull(message = "La date de fin prevue est obligatoire.")
        @FutureOrPresent(message = "La date de fin prevue ne peut pas etre dans le passe.")
        LocalDate dateFinPrevue,

        Long missionPrecedenteId
) {
}
