package com.snef.sgbf.security.dto;

import java.util.Set;

/**
 * Reponse de {@code GET /api/auth/me} : identite de l'utilisateur authentifie
 * et roles actuellement actifs (utilises par le frontend pour l'affichage
 * conditionnel des menus et actions, en complement - jamais en remplacement -
 * des controles serveur reels sur chaque endpoint).
 */
public record UtilisateurCourantDto(
        Long id,
        String identifiant,
        String email,
        Long serviceId,
        String serviceLibelle,
        Set<String> rolesActifs
) {
}
