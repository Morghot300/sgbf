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
    FIPH_PRISE_EN_MAIN_SUPER_ADMIN
}
