package com.snef.sgbf.notification.entity;

/**
 * Nature de l'evenement a l'origine d'une notification (evolution du
 * 2026-08-19, section 5-6). Determine le libelle et l'icone cote frontend,
 * jamais un texte libre non structure.
 */
public enum TypeNotification {
    /** FIPH transmise a un niveau pour validation (agent -&gt; CA/PH, CA/PH -&gt; RA, RA -&gt; Direction). */
    FIPH_A_VALIDER,
    /** FIPH definitivement validee (informe le titulaire). */
    FIPH_VALIDEE,
    /** Intervention exceptionnelle du Super Administrateur sur une FIPH bloquee/en attente. */
    FIPH_PRISE_EN_MAIN_SUPER_ADMIN,
    /** Bon de sortie vise (niveau 1), transmis au Charge d'Affaires/personne habilitee pour validation (niveau 2, evolution du 2026-08-19, Lot 3). */
    BON_SORTIE_A_VALIDER,
    /** Bon de sortie definitivement valide (informe l'agent titulaire). */
    BON_SORTIE_VALIDE,
    /** Agent ajoute comme personne a bord d'un bon de sortie (informe l'agent concerne, s'il dispose d'un compte). */
    PERSONNE_A_BORD_AJOUTEE,
    /** Bon de sortie valide malgre l'absence d'affectation active resolue pour l'agent (Lot 2 : avertissement, pas blocage - informe le valideur pour suivi). */
    ANOMALIE_AFFECTATION
}
