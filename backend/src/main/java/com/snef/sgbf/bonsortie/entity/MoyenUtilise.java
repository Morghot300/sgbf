package com.snef.sgbf.bonsortie.entity;

/**
 * Moyen utilise pour la sortie (section 3.1 du formulaire d'origine). Non
 * present dans le tableau synthetique des attributs de la section 18 du
 * document source mais explicitement liste sur le formulaire papier -
 * ajoute par hypothese documentee (voir analyse fonctionnelle, section D).
 * {@code TAXI} ne porte jamais de {@link BonSortie#getVehicule()} (aucun
 * vehicule de l'entreprise concerne).
 */
public enum MoyenUtilise {
    OMNIUM_SERVICE,
    PERSONNEL,
    TAXI
}
