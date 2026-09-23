package com.snef.sgbf.bonsortie.dto;

import com.snef.sgbf.bonsortie.entity.StatutAssociationPersonne;
import java.time.LocalDateTime;

/** Representation en lecture d'une {@link com.snef.sgbf.bonsortie.entity.BonSortiePersonne}. */
public record BonSortiePersonneDto(
        Long id,
        Long bonSortiePrincipalId,
        Long agentId,
        String agentNomComplet,
        String agentMatricule,
        StatutAssociationPersonne statutAssociation,
        LocalDateTime dateAssociation,
        LocalDateTime dateRetrait,
        Long bonSortieIndividuelId
) {
}
