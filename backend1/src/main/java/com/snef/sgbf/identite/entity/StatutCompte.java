package com.snef.sgbf.identite.entity;

/**
 * Statut d'un compte {@link Utilisateur}. Un compte {@code VERROUILLE} ou
 * {@code DESACTIVE} ne peut pas s'authentifier (voir
 * {@code security.UserDetailsServiceImpl}), quelles que soient ses
 * habilitations actives.
 */
public enum StatutCompte {
    ACTIF,
    VERROUILLE,
    DESACTIVE
}
