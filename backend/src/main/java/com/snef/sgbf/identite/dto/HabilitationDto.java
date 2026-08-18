package com.snef.sgbf.identite.dto;

import java.time.LocalDate;

/** Representation en lecture d'une {@link com.snef.sgbf.identite.entity.Habilitation}. */
public record HabilitationDto(
        Long id,
        Long utilisateurId,
        String utilisateurIdentifiant,
        String roleMetierCode,
        String roleMetierLibelle,
        Long serviceId,
        String serviceLibelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean actif
) {
}
