package com.snef.sgbf.fiph.entity;

/**
 * Decision portee par une {@link Validation}. {@link #REJETEE} et
 * {@link #RETOUR_POUR_CORRECTION} correspondent aux regles proposees
 * RG-FIPH-026/027 (section 11.9) - voir {@link StatutFiphVersion} pour la
 * meme reserve documentaire.
 */
public enum DecisionValidation {
    VALIDEE,
    REJETEE,
    RETOUR_POUR_CORRECTION
}
