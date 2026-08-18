package com.snef.sgbf.bonsortie.entity;

/**
 * Statuts possibles d'un {@link BonSortie} (RG-BS-003, RG-BS-004).
 * L'ordre de la chaine est fixe : {@code BROUILLON} → {@code VISE}
 * (visa de l'agent, niveau 1) → {@code VALIDE} (validation du Charge
 * d'Affaires, niveau 2) - jamais d'etape sautee.
 */
public enum StatutBonSortie {
    BROUILLON,
    VISE,
    VALIDE
}
