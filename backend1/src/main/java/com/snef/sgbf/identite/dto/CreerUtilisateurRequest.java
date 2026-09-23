package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Donnees necessaires a la creation d'une personne (evolution du 2026-08-19,
 * "un utilisateur est obligatoirement un agent") - une seule operation, qu'elle
 * dispose ou non d'un compte applicatif.
 *
 * <p>{@link #nom}, {@link #prenom} sont toujours obligatoires (identite d'une
 * personne reelle). {@link #matricule} reste optionnel a ce niveau (les
 * comptes purement administratifs preexistants n'en ont jamais eu) mais reste
 * unique lorsqu'il est fourni.
 *
 * <p>{@link #identifiant}, {@link #email}, {@link #motDePasse} forment un
 * GROUPE : soit les trois sont fournis (la personne dispose immediatement
 * d'un compte applicatif), soit aucun ne l'est (personne du referentiel
 * uniquement, geree pour son compte par un tiers habilite) - controle
 * explicite en service ({@code UtilisateurService#creer}), une combinaison
 * partielle etant refusee comme une erreur metier claire plutot qu'un etat
 * incoherent silencieusement accepte. Le mot de passe, lorsqu'il est fourni,
 * est immediatement hashe (BCrypt) - jamais conserve ni journalise en clair.
 */
public record CreerUtilisateurRequest(

        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 100)
        String nom,

        @NotBlank(message = "Le prenom est obligatoire.")
        @Size(max = 100)
        String prenom,

        @Size(max = 20, message = "Le matricule ne peut pas depasser 20 caracteres.")
        String matricule,

        @Size(max = 60)
        String identifiant,

        @Size(max = 150)
        String email,

        @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caracteres.")
        String motDePasse,

        Long serviceId
) {
}
