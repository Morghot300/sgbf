package com.snef.sgbf.fiph.dto;

/**
 * Modifie (ou retire, si {@code missionId} est {@code null}) la mission
 * associée à une FIPH déjà créée (évolution du 2026-08-27, brief "Evolution
 * du module FIPH", section 6-9 : "lors de la création OU DE LA MODIFICATION
 * d'une FIPH" - la première évolution n'avait couvert que la création).
 *
 * <p>Comme à la création, un {@code missionId} qui ne correspond à aucune
 * mission existante est refusé (404, section 7) plutôt que d'accepter une
 * association incohérente.
 */
public record ModifierMissionFiphRequest(Long missionId) {
}
