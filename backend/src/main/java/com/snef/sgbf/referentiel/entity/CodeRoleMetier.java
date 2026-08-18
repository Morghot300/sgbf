package com.snef.sgbf.referentiel.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Codes de role metier stables, tels que definis section 10 et seedes dans
 * {@code V1__referentiels.sql}. Utilise cote code (controles d'autorisation,
 * regles de separation des responsabilites RG-HAB-004/005) pour eviter toute
 * comparaison de chaine "magique" eparpillee dans le code.
 *
 * <p>Ne pas confondre avec {@link RoleMetier}, qui est l'entite JPA
 * correspondant a la ligne en base : ce enum donne un nom de compilation sur
 * dont la valeur en base ({@link RoleMetier#getCode()}) doit rester
 * synchronisee.
 */
public enum CodeRoleMetier {
    AGENT,
    CHARGE_AFFAIRES,
    PERSONNE_HABILITEE,
    RESPONSABLE_ACTIVITE,
    DIRECTION,
    RH,
    ADMINISTRATEUR;

    /**
     * Roles a perimetre global (aucun service associe) : RH par construction
     * (RG-HAB-005, "consultation globale"), et Administrateur par extension
     * technique raisonnable puisqu'il administre des referentiels transverses
     * a tous les services (hypothese documentee, voir Javadoc de
     * {@link com.snef.sgbf.identite.entity.Habilitation}).
     */
    private static final Set<CodeRoleMetier> ROLES_PERIMETRE_GLOBAL = EnumSet.of(RH, ADMINISTRATEUR);

    public boolean estPerimetreGlobal() {
        return ROLES_PERIMETRE_GLOBAL.contains(this);
    }
}
