package com.snef.sgbf.fiph.dto;

import java.util.List;

/**
 * Resultat d'une creation manuelle en lot (evolution du 2026-08-27, section
 * 2-3-4-14) : l'echec de la creation pour un agent (ex. periode deja
 * couverte par une autre FIPH, RG-FIPH-002) n'empeche jamais la creation des
 * autres - chaque agent selectionne est traite independamment, exactement
 * comme {@code PersonneABordGenerationService} le fait deja pour les
 * personnes a bord d'un bon de sortie.
 */
public record ResultatCreationFiphDto(
        List<FiphDto> creees,
        List<EchecCreationFiphDto> echecs
) {
    public record EchecCreationFiphDto(Long agentId, String agentNomComplet, String motif) {
    }
}
