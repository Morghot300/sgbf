package com.snef.sgbf.identite.dto;

/**
 * Representation en lecture d'un {@link com.snef.sgbf.identite.entity.Agent},
 * exposee par l'API REST. Un DTO dedie (plutot que l'entite JPA elle-meme)
 * evite de fuiter des details de mapping/persistance vers le client et
 * decouple le contrat d'API des colonnes de la table.
 */
public record AgentDto(
        Long id,
        String matricule,
        String nom,
        String prenom,
        String nomComplet,
        Long serviceId,
        String serviceLibelle,
        boolean actif,
        boolean possedeCompte
) {
}
