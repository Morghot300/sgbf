package com.snef.sgbf.fiph.entity;

/**
 * Origine d'une {@link FIPH} (RG-FIPH-004, choix de conception documente
 * section 2 du document source - remplace la creation exhaustive et
 * uniforme du modele precedent) :
 * <ul>
 *   <li>{@code BON_SORTIE} - agent concerne par une mission ; creation
 *       automatique a la validation d'un bon de sortie, Code Mission
 *       (RG-BS-007, RG-FIPH-001) ;</li>
 *   <li>{@code MANUELLE} - agents du service non concernes par une mission
 *       durant la periode ; creation par le Charge d'Affaires ou la
 *       personne habilitee, Code Service.</li>
 * </ul>
 */
public enum OrigineFiph {
    BON_SORTIE,
    MANUELLE
}
