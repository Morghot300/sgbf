package com.snef.sgbf.identite.dto;

import com.snef.sgbf.identite.entity.StatutCompte;

/** Representation en lecture d'un {@link com.snef.sgbf.identite.entity.Utilisateur} - ne porte jamais le hash du mot de passe. */
public record UtilisateurDto(
        Long id,
        String identifiant,
        String email,
        StatutCompte statutCompte,
        Long serviceId,
        String serviceLibelle
) {
}
