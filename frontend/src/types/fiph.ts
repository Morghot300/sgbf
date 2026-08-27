/** Types miroir des DTO du module FIPH (backend/.../fiph/*). */

export type StatutFiphVersion =
  | "BROUILLON"
  | "EN_COMPLEMENT"
  | "SIGNEE"
  | "SOUMISE"
  | "VALIDEE_NIVEAU_2"
  | "VALIDEE_NIVEAU_3"
  | "VALIDEE_DEFINITIVEMENT"
  | "REJETEE"
  | "RETOUR_POUR_CORRECTION"
  | "ANNULEE"
  | "EN_REVISION";

export type OrigineFiph = "BON_SORTIE" | "MANUELLE";
export type DecisionValidation = "VALIDEE" | "REJETEE" | "RETOUR_POUR_CORRECTION";
export type JourSemaine = "LUNDI" | "MARDI" | "MERCREDI" | "JEUDI" | "VENDREDI" | "SAMEDI" | "DIMANCHE";

export interface FiphDto {
  id: number;
  agentId: number;
  agentNomComplet: string;
  agentMatricule: string;
  serviceId: number;
  serviceLibelle: string;
  origine: OrigineFiph;
  bonSortieId: number | null;
  annee: number;
  mois: number;
  numeroSemaine: number;
  /** Automatiquement issue du Bon de Sortie declencheur (evolution du 2026-08-21) — jamais modifiable. */
  dateDebutPeriode: string;
  statut: StatutFiphVersion;
  versionCouranteId: number;
  versionCouranteNumero: number;
  /** Mission choisie à la création (évolution du 2026-08-27, section 6-8) — null si non renseignée. */
  missionId: number | null;
  missionCodeHN: string | null;
  missionChantierLibelle: string | null;
  /** Non bloquant : signale qu'aucune affectation connue de l'agent sur cette mission n'a été trouvée. */
  avertissementMission: string | null;
}

/**
 * Création manuelle EN LOT (évolution du 2026-08-27, section 2-3-4-14) : un
 * agent par case cochée, jamais une saisie libre d'identifiant. L'échec pour
 * l'un d'eux (période chevauchante, RG-FIPH-002) n'empêche jamais la
 * création pour les autres — voir {@link ResultatCreationFiphDto}.
 */
export interface CreerFiphManuelleRequest {
  agentIds: number[];
  dateDebut: string;
  /** Optionnelle : periode "ouverte", definissable/ajustable ensuite via /date-fin (RG-FIPH-033 avant soumission). */
  dateFin?: string | null;
  /** Mission choisie (Code Mission, section 6-7) — optionnelle, purement associative/descriptive. */
  missionId?: number | null;
}

export interface EchecCreationFiphDto {
  agentId: number;
  agentNomComplet: string | null;
  motif: string;
}

export interface ResultatCreationFiphDto {
  creees: FiphDto[];
  echecs: EchecCreationFiphDto[];
}

export interface PointageDto {
  id: number;
  jourSemaine: JourSemaine;
  datePointage: string;
  heuresNormales: number;
  heuresSup: number;
  affectationMissionId: number | null;
  codeMission: string | null;
  serviceId: number | null;
  codeService: string | null;
}

export interface FiphVersionDto {
  id: number;
  fiphId: number;
  numeroVersion: number;
  dateCreation: string;
  creeParIdentifiant: string;
  motifModification: string | null;
  versionPrecedenteId: number | null;
  /** Celle de la FIPH parente (automatique, issue du Bon de Sortie). */
  dateDebutPeriode: string;
  /** Definie par le Charge d'Affaires/la personne habilitee — null tant que non encore renseignee (evolution du 2026-08-21). */
  dateFinPeriode: string | null;
  /** Recalcule a chaque lecture : jours de la periode exclus du tableau faute d'affectation reelle. */
  avertissementPeriode: string | null;
  totalHN: number;
  totalHS: number;
  statutVersion: StatutFiphVersion;
  empreinteIntegrite: string | null;
  lockVersion: number;
  pointages: PointageDto[];
}

/** Definit/modifie la date de fin de la periode (evolution du 2026-08-21). */
export interface DefinirDateFinRequest {
  dateFin: string;
  motifModification: string | null;
}

export interface CompleterPointageRequest {
  datePointage: string;
  heuresNormales: number;
  heuresSup: number;
}

export interface ValiderFiphRequest {
  decision: DecisionValidation;
  commentaire: string | null;
}

export interface CreerNouvelleVersionRequest {
  motifModification: string;
}

export interface ValidationDto {
  id: number;
  utilisateurIdentifiant: string;
  niveauValidation: number;
  decision: DecisionValidation;
  dateValidation: string;
  commentaire: string | null;
  statutAvant: string;
  statutApres: string;
  /** Vrai si cette decision provient d'une prise en main exceptionnelle du Super Administrateur (evolution du 2026-08-19), jamais une validation normale. */
  priseEnMainSuperAdmin: boolean;
}

/** Justification obligatoire d'une prise en main exceptionnelle par le Super Administrateur (evolution du 2026-08-19). */
export interface PriseEnMainSuperAdminRequest {
  commentaire: string;
}

export const LIBELLES_STATUT_FIPH: Record<StatutFiphVersion, string> = {
  BROUILLON: "Brouillon",
  EN_COMPLEMENT: "En complément",
  SIGNEE: "Signée",
  SOUMISE: "Soumise",
  VALIDEE_NIVEAU_2: "Validée niveau 2",
  VALIDEE_NIVEAU_3: "Validée niveau 3",
  VALIDEE_DEFINITIVEMENT: "Validée définitivement",
  REJETEE: "Rejetée",
  RETOUR_POUR_CORRECTION: "Retour pour correction",
  ANNULEE: "Annulée",
  EN_REVISION: "En révision",
};

export const LIBELLES_JOUR_SEMAINE: Record<JourSemaine, string> = {
  LUNDI: "Lundi",
  MARDI: "Mardi",
  MERCREDI: "Mercredi",
  JEUDI: "Jeudi",
  VENDREDI: "Vendredi",
  SAMEDI: "Samedi",
  DIMANCHE: "Dimanche",
};

/**
 * Niveau de validation attendu selon le statut courant (section 12 du
 * document source). SIGNEE est inclus depuis l'evolution du workflow FIPH
 * (2026-08-18) : une FIPH issue d'un bon de sortie demarre directement a
 * SIGNEE (visa de l'agent titulaire acquis automatiquement), et le Charge
 * d'Affaires / la personne habilitee peut la valider au niveau 2 sans
 * attendre une soumission explicite.
 */
export const NIVEAU_VALIDATION_ATTENDU: Partial<Record<StatutFiphVersion, number>> = {
  BROUILLON: 2,
  EN_COMPLEMENT: 2,
  SIGNEE: 2,
  SOUMISE: 2,
  VALIDEE_NIVEAU_2: 3,
  VALIDEE_NIVEAU_3: 4,
};

/** Etats ou seul l'agent titulaire peut encore signer la FIPH (bouton "Signer"). Ne concerne plus les FIPH issues d'un bon de sortie (visa deja acquis). */
export function estFiphModifiable(statut: StatutFiphVersion): boolean {
  return statut === "BROUILLON" || statut === "EN_COMPLEMENT";
}

/** Etats ou le pointage reste modifiable par le Charge d'Affaires / la personne habilitee — plus large que estFiphModifiable, voir StatutFiphVersion#estPointageModifiable côté backend. */
export function estPointageModifiable(statut: StatutFiphVersion): boolean {
  return statut === "BROUILLON" || statut === "EN_COMPLEMENT" || statut === "SIGNEE";
}

export function estFiphFigee(statut: StatutFiphVersion): boolean {
  return statut === "VALIDEE_DEFINITIVEMENT";
}

/**
 * Sous-menus FIPH par catégorie (évolution du 2026-08-27, section 16-18) —
 * construits à partir des statuts RÉELLEMENT atteignables par le circuit de
 * validation (voir {@code FiphVersionService}), et non d'une liste
 * arbitraire : {@code RETOUR_POUR_CORRECTION}, {@code ANNULEE} et
 * {@code EN_REVISION} existent dans l'énumération mais ne sont jamais
 * effectivement produits par aucune transition du circuit actuel (le
 * document source les marque lui-même "proposés, à valider" — voir la
 * Javadoc de {@code StatutFiphVersion}) ; ils sont donc volontairement
 * absents de ce regroupement plutôt que de promettre une catégorie qui ne se
 * peuplera jamais.
 *
 * <p>Chaque catégorie correspond à un regroupement de statuts partageant la
 * même signification pour l'utilisateur métier :
 * <ul>
 *   <li>{@code BROUILLONS} : contenu encore en cours de saisie/complément ;</li>
 *   <li>{@code VISEES} : visa déjà acquis (automatique dans ce système),
 *       en attente de la décision du Chargé d'Affaires/de la Personne
 *       habilitée (niveau 2) ;</li>
 *   <li>{@code VALIDEES_NIVEAU_2} : en attente du Responsable d'Activité
 *       (niveau 3) ;</li>
 *   <li>{@code VALIDEES_NIVEAU_3} : en attente de la Direction (niveau 4) ;</li>
 *   <li>{@code VALIDEES_DEFINITIVEMENT} : circuit complet, document figé ;</li>
 *   <li>{@code REJETEES} : décision de rejet à un niveau quelconque.</li>
 * </ul>
 */
export type CategorieFiph = "BROUILLONS" | "VISEES" | "VALIDEES_NIVEAU_2" | "VALIDEES_NIVEAU_3"
  | "VALIDEES_DEFINITIVEMENT" | "REJETEES";

export const STATUTS_PAR_CATEGORIE: Record<CategorieFiph, StatutFiphVersion[]> = {
  BROUILLONS: ["BROUILLON", "EN_COMPLEMENT"],
  VISEES: ["SIGNEE", "SOUMISE"],
  VALIDEES_NIVEAU_2: ["VALIDEE_NIVEAU_2"],
  VALIDEES_NIVEAU_3: ["VALIDEE_NIVEAU_3"],
  VALIDEES_DEFINITIVEMENT: ["VALIDEE_DEFINITIVEMENT"],
  REJETEES: ["REJETEE"],
};

export const LIBELLES_CATEGORIE_FIPH: Record<CategorieFiph, string> = {
  BROUILLONS: "Brouillons",
  VISEES: "Visées (à valider niv. 2)",
  VALIDEES_NIVEAU_2: "Validées niv. 2 (à valider niv. 3)",
  VALIDEES_NIVEAU_3: "Validées niv. 3 (à valider niv. 4)",
  VALIDEES_DEFINITIVEMENT: "Validées définitivement",
  REJETEES: "Rejetées",
};
