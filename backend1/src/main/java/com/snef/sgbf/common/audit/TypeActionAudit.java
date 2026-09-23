package com.snef.sgbf.common.audit;

/**
 * Enumere les actions journalisables dans {@link EvenementAudit}, toutes
 * modules confondus. Definie des le socle (plutot que module par module) afin
 * que la table {@code evenement_audit}, commune a toute l'application,
 * dispose d'un vocabulaire d'actions stable des sa creation.
 *
 * <p>Chaque valeur est reliee, en commentaire, a la regle de gestion ou a la
 * section du document source qui impose sa tracabilite.
 */
public enum TypeActionAudit {

    // --- Authentification et securite (section 26.1, 26.4) ---
    CONNEXION_REUSSIE,
    ECHEC_CONNEXION,
    DECONNEXION,
    ACCES_REFUSE,

    // --- Identite et habilitations (RG-HAB-006) ---
    CREATION,
    MODIFICATION,
    DESACTIVATION,
    ATTRIBUTION_HABILITATION,
    MODIFICATION_HABILITATION,
    RETRAIT_HABILITATION,
    /**
     * Reaffectation d'un Charge d'Affaires / Personne habilitee / Responsable
     * d'Activite vers un autre service (evolution du 2026-08-19, "un acteur
     * metier appartient a un seul service") : action unique et tracee,
     * distincte d'un simple RETRAIT_HABILITATION suivi d'une
     * ATTRIBUTION_HABILITATION, qui rendrait le changement de service moins
     * explicite dans l'historique d'audit.
     */
    CHANGEMENT_SERVICE_HABILITATION,
    /** Correction du login de connexion par l'Administrateur (evolution du 2026-08-18, section 9). */
    MODIFICATION_IDENTIFIANT,
    /** Correction de l'adresse e-mail par l'Administrateur (evolution du 2026-08-18, section 9). */
    MODIFICATION_EMAIL,
    /** Jamais accompagnee du mot de passe lui-meme, ni en clair ni hashe (evolution du 2026-08-18, section 9). */
    REINITIALISATION_MOT_DE_PASSE,

    // --- Missions et affectations (RG-MIS-008) ---
    AFFECTATION,
    INTERRUPTION,
    REAFFECTATION,

    // --- Bon de sortie (RG-BS-005) ---
    VISA,
    VALIDATION,

    // --- FIPH (RG-FIPH-016) ---
    COMPLEMENT,
    SIGNATURE,
    SOUMISSION,
    REJET,
    RETOUR_POUR_CORRECTION,
    ANNULATION,
    CREATION_VERSION,

    // --- Personnes a bord (RG-PAB-008) ---
    PERSONNE_A_BORD_AJOUTEE,
    PERSONNE_A_BORD_RETIREE,
    BS_INDIVIDUEL_AUTO_GENERE,
    FIPH_AUTO_GENEREE,

    // --- Impression et export (RG-DOC-005) ---
    IMPRESSION_BON_SORTIE,
    GENERATION_PDF_FIPH,
    TELECHARGEMENT_PDF_FIPH,

    /**
     * Intervention exceptionnelle du Super Administrateur sur une FIPH
     * (evolution du 2026-08-19, section 12-16) : fait progresser une
     * FIPHVersion jusqu'a VALIDEE_DEFINITIVEMENT en franchissant les niveaux
     * restants, hors du circuit normal. Toujours accompagnee d'une
     * justification obligatoire (voir {@code Validation#getCommentaire()}
     * des lignes marquees {@code priseEnMainSuperAdmin = true}) - jamais
     * confondue avec une {@link #VALIDATION} normale.
     */
    PRISE_EN_MAIN_SUPER_ADMIN,

    /**
     * Validation normale, niveau par niveau (evolution du 2026-08-26, section
     * 13-14), effectuee par un Super Administrateur qui ne detient AUCUNE
     * habilitation de service correspondante - distincte a la fois d'une
     * {@link #VALIDATION} ordinaire (effectuee par un acteur metier habilite
     * sur le service) et de {@link #PRISE_EN_MAIN_SUPER_ADMIN} (qui saute
     * directement plusieurs niveaux en une seule operation) : ici, chaque
     * niveau reste franchi explicitement, un a la fois, exactement comme le
     * processus metier normal - seule l'identite du validateur sort du
     * perimetre habituel. Journalisee EN PLUS de la {@link #VALIDATION}
     * normale (jamais a sa place), pour que l'historique distingue clairement
     * les deux categories d'intervention.
     */
    VALIDATION_PAR_SUPER_ADMIN,

    /**
     * Bon de sortie valide malgre l'absence d'affectation active resolue
     * pour l'agent a la date de sortie (evolution du 2026-08-19, Lot 2 :
     * avertissement, jamais un blocage) - trace separement de
     * {@link #VALIDATION} pour rester repérable dans l'historique meme si
     * la validation elle-meme a reussi.
     */
    ANOMALIE_AFFECTATION
}
