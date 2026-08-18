package com.snef.sgbf.identite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Donnees necessaires a la creation d'un compte applicatif par
 * l'Administrateur. Le mot de passe fourni ici est immediatement hashe
 * (BCrypt) par {@code UtilisateurService} - jamais conserve ni journalise en
 * clair, y compris dans le journal d'audit.
 *
 * <p>{@link #email} identifie simplement le compte (authentification par
 * identifiant/e-mail + mot de passe uniquement, section K de l'analyse
 * fonctionnelle) : aucun champ relatif a un second facteur n'existe ici.
 */
public record CreerUtilisateurRequest(

        @NotBlank(message = "L'identifiant de connexion est obligatoire.")
        @Size(max = 60)
        String identifiant,

        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail doit etre valide.")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Le mot de passe initial est obligatoire.")
        @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caracteres.")
        String motDePasse,

        Long serviceId
) {
}
