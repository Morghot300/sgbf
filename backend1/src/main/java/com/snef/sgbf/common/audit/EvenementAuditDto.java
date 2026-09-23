package com.snef.sgbf.common.audit;

import java.time.LocalDateTime;

/**
 * Vue en lecture d'un {@link EvenementAudit} (section 24 du document source).
 * {@code utilisateur} peut etre {@code null} : une action peut etre
 * declenchee par le systeme lui-meme (ex. FIPH_AUTO_GENEREE).
 */
public record EvenementAuditDto(
        Long id,
        EntiteAuditable entiteType,
        String entiteId,
        String utilisateur,
        TypeActionAudit action,
        String valeurAvant,
        String valeurApres,
        String statutAvant,
        String statutApres,
        LocalDateTime dateAction) {
}
