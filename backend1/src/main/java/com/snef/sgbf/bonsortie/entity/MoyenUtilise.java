package com.snef.sgbf.bonsortie.entity;

/**
 * Moyen utilise pour la sortie (section 3.1 du formulaire d'origine). Non
 * present dans le tableau synthetique des attributs de la section 18 du
 * document source mais explicitement liste sur le formulaire papier -
 * ajoute par hypothese documentee (voir analyse fonctionnelle, section D).
 * {@code TAXI} ne porte jamais de {@link BonSortie#getVehicule()} (aucun
 * vehicule de l'entreprise concerne).
 *
 * <p>{@code AUTRE} (evolution du 2026-08-18) couvre tout moyen non repertorie
 * ci-dessus (ex. vehicule de location) : {@link BonSortie#getPrecisionVehicule()}
 * devient alors obligatoire (RG-BS, controle applicatif dans
 * {@code BonSortieService} ET contrainte {@code CHECK} en base - defense en
 * profondeur), pour ne jamais laisser un bon de sortie ambigu sur le vehicule
 * reellement utilise.
 */
public enum MoyenUtilise {
    OMNIUM_SERVICE,
    PERSONNEL,
    TAXI,
    AUTRE
}
