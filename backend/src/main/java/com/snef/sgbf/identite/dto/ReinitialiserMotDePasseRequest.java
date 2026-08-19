package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reinitialisation du mot de passe d'un compte par l'Administrateur (section
 * 7 de la mission d'evolution du 2026-08-18). Le mot de passe fourni en
 * clair ici n'est jamais journalise ni persiste tel quel - voir
 * {@code UtilisateurService.reinitialiserMotDePasse}, qui le hashe
 * immediatement, exactement comme a la creation d'un compte.
 */
public record ReinitialiserMotDePasseRequest(

        @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
        @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caracteres.")
        String nouveauMotDePasse
) {
}
