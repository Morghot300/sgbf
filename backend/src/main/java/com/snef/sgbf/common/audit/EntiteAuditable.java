package com.snef.sgbf.common.audit;

/**
 * Enumere les types d'entites pouvant faire l'objet d'un enregistrement dans
 * {@link EvenementAudit}. Sert de valeur pour la colonne polymorphe
 * {@code entite_type} (section 24 du document source).
 *
 * <p>Cette liste est volontairement fermee (contrairement au referentiel
 * {@code motif_interruption_mission}, par exemple) : ajouter un nouveau type
 * d'entite auditee est une decision de conception, pas une donnee de
 * parametrage metier.
 */
public enum EntiteAuditable {
    UTILISATEUR,
    HABILITATION,
    AGENT,
    MISSION,
    AFFECTATION_MISSION,
    BON_SORTIE,
    BON_SORTIE_PERSONNE,
    FIPH,
    FIPH_VERSION
}
