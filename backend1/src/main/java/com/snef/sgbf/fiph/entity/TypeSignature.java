package com.snef.sgbf.fiph.entity;

/**
 * Nature de la preuve technique portee par une {@link Signature} (section 22,
 * 23.1 du document source). Le niveau de preuve effectivement requis pour la
 * validation de niveau 4 (Direction) reste un point a arbitrer avec la
 * Direction juridique (section 29.1, point I.5 de l'analyse fonctionnelle) :
 * {@link #VISA_APPLICATIF} est retenu par defaut pour toutes les signatures
 * en phase 1, l'architecture restant prete a accueillir
 * {@link #SIGNATURE_ELECTRONIQUE} sans migration de schema.
 */
public enum TypeSignature {
    VISA_APPLICATIF,
    SIGNATURE_ELECTRONIQUE
}
