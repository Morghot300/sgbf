package com.snef.sgbf.bonsortie.dto;

import com.snef.sgbf.identite.entity.StatutCompte;

/**
 * Une personne eligible a etre ajoutee comme personne a bord d'un bon de
 * sortie donne (evolution du 2026-08-19, Lot 4) - le perimetre (service du
 * titulaire du bon) est calcule cote serveur, jamais recu du client.
 *
 * @param dejaAffecteMemeCreneau signale (sans jamais bloquer) que cette
 *        personne est deja a bord d'un AUTRE bon de sortie a la meme date de
 *        sortie - decision laissee au Charge d'Affaires/personne habilitee.
 */
public record AgentEligibleDto(
        Long id,
        String nomComplet,
        String matricule,
        String serviceLibelle,
        StatutCompte statutCompte,
        boolean dejaAffecteMemeCreneau
) {
}
