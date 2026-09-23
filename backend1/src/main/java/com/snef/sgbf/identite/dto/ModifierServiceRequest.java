package com.snef.sgbf.identite.dto;

/** Correction du service de rattachement d'un compte (section 7-9 de la mission d'evolution du 2026-08-18). {@code serviceId} peut etre {@code null} (rattachement retire - roles a perimetre global). */
public record ModifierServiceRequest(
        Long serviceId
) {
}
