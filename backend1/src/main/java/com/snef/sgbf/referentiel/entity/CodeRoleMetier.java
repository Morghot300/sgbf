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
    ADMINISTRATEUR,
    /**
     * Supervision globale de l'application (evolution du 2026-08-18) :
     * herite de tous les droits de {@link #ADMINISTRATEUR} (voir la
     * hierarchie de roles Spring Security dans {@code SecurityConfig}) et
     * de droits supplementaires reserves (attribution de l'habilitation
     * SUPER_ADMINISTRATEUR elle-meme - voir {@code HabilitationService}).
     * Un Administrateur standard ne peut jamais s'auto-attribuer ni
     * attribuer a un tiers ce role : seul un titulaire actif de
     * SUPER_ADMINISTRATEUR le peut.
     */
    SUPER_ADMINISTRATEUR;

    /**
     * Roles a perimetre global (aucun service associe) : RH par construction
     * (RG-HAB-005, "consultation globale"), Administrateur par extension
     * technique raisonnable puisqu'il administre des referentiels transverses
     * a tous les services (hypothese documentee, voir Javadoc de
     * {@link com.snef.sgbf.identite.entity.Habilitation}), et Super
     * Administrateur pour la meme raison, a fortiori.
     */
    private static final Set<CodeRoleMetier> ROLES_PERIMETRE_GLOBAL = EnumSet.of(RH, ADMINISTRATEUR, SUPER_ADMINISTRATEUR);

    /**
     * Roles pour lesquels un meme utilisateur ne peut jamais detenir deux
     * habilitations actives simultanees sur deux services differents
     * (evolution du 2026-08-19, "un acteur metier appartient a un seul
     * service") : Charge d'Affaires, Personne habilitee, Responsable
     * d'Activite. Volontairement EXCLU : DIRECTION reste multi-service /
     * transverse par construction (le document source demande explicitement
     * de ne pas lui imposer artificiellement une restriction par service -
     * voir aussi {@code FiphService#estRoleVisionGlobale}, qui traite deja
     * DIRECTION comme a vision globale en lecture). AGENT est deja garanti
     * a un seul service par {@code Agent.service} (colonne NOT NULL), sans
     * passer par ce mecanisme d'habilitation.
     */
    private static final Set<CodeRoleMetier> ROLES_SERVICE_EXCLUSIF =
            EnumSet.of(CHARGE_AFFAIRES, PERSONNE_HABILITEE, RESPONSABLE_ACTIVITE);

    public boolean estPerimetreGlobal() {
        return ROLES_PERIMETRE_GLOBAL.contains(this);
    }

    /** Vrai si ce role ne peut etre detenu, activement, que sur un seul service a la fois pour un meme utilisateur. */
    public boolean estServiceExclusif() {
        return ROLES_SERVICE_EXCLUSIF.contains(this);
    }
}
