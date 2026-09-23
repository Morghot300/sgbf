package com.snef.sgbf.bonsortie.entity;

/**
 * Origine d'un {@link BonSortie} (section 9.1) : {@code PRINCIPALE} pour le
 * bon cree par l'agent emetteur lui-meme, {@code PERSONNE_A_BORD} pour un
 * bon genere automatiquement pour une personne accompagnant l'emetteur -
 * meme entite, memes champs, memes regles, seule l'origine et la reference
 * {@link BonSortie#getBonSortiePrincipal()} les distinguent.
 */
public enum OrigineBonSortie {
    PRINCIPALE,
    PERSONNE_A_BORD
}
