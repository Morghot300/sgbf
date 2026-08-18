package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Donnees necessaires a la creation d'un {@link com.snef.sgbf.identite.entity.Agent}
 * dans le referentiel RH. Reserve a l'Administrateur (section 14 - "Administre
 * les referentiels").
 *
 * <p>Toutes les contraintes ci-dessous sont revalidees cote serveur
 * (Bean Validation) independamment de toute validation deja effectuee cote
 * frontend - le frontend n'est jamais la seule barriere de controle (brief §11).
 */
public record CreerAgentRequest(

        @NotBlank(message = "Le matricule est obligatoire.")
        @Size(max = 20, message = "Le matricule ne peut pas depasser 20 caracteres.")
        String matricule,

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 100)
        String nom,

        @NotBlank(message = "Le prenom est obligatoire.")
        @Size(max = 100)
        String prenom,

        @NotNull(message = "Le service de rattachement est obligatoire.")
        Long serviceId
) {
}
