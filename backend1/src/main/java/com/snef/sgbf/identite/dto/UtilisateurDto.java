package com.snef.sgbf.identite.dto;

import com.snef.sgbf.identite.entity.StatutCompte;

/** Representation en lecture d'un {@link com.snef.sgbf.identite.entity.Utilisateur} - ne porte jamais le hash du mot de passe. */
public record UtilisateurDto(
        Long id,
        String matricule,
        String nom,
        String prenom,
        String nomComplet,
        String identifiant,
        String email,
        boolean possedeCompteApplicatif,
        StatutCompte statutCompte,
        Long serviceId,
        String serviceLibelle
) {
}
