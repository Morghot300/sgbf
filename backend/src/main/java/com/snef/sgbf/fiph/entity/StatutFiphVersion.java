package com.snef.sgbf.fiph.entity;

/**
 * Statuts d'une {@link FIPHVersion} (machine d'etats, section 12.3 du
 * document source).
 *
 * <p>Les sept premiers statuts sont confirmes par le document source. Les
 * quatre derniers ({@link #REJETEE}, {@link #RETOUR_POUR_CORRECTION},
 * {@link #ANNULEE}, {@link #EN_REVISION}) y sont explicitement marques
 * "proposes, a valider avant implementation" (section 12.3) - implementes
 * ici tels quels, comme deja explique au point I.1 de l'analyse
 * fonctionnelle : sans eux, le circuit de validation n'aurait aucune
 * porte de sortie pour une FIPH refusee, ce qui n'est pas une hypothese
 * mais une reprise fidele d'une proposition deja motivee par le document.
 */
public enum StatutFiphVersion {
    BROUILLON,
    EN_COMPLEMENT,
    SIGNEE,
    SOUMISE,
    VALIDEE_NIVEAU_2,
    VALIDEE_NIVEAU_3,
    VALIDEE_DEFINITIVEMENT,

    /** Proposee (section 12.3) : un valideur rejette la FIPH avec motif obligatoire (RG-FIPH-026). */
    REJETEE,
    /** Proposee (section 12.3) : renvoyee vers la signature ou le complement (RG-FIPH-027). */
    RETOUR_POUR_CORRECTION,
    /** Proposee (section 12.3) : annulation avant signature de l'emetteur, ou par l'administrateur (RG-FIPH-028). */
    ANNULEE,
    /** Proposee (section 12.3) : modification demandee apres validation definitive, ouvre une nouvelle version. */
    EN_REVISION;

    public boolean estModifiable() {
        return this == BROUILLON || this == EN_COMPLEMENT;
    }

    public boolean estFigee() {
        return this == VALIDEE_DEFINITIVEMENT;
    }
}
