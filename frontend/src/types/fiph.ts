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
  dateDebutPeriode: string;
  dateFinPeriode: string;
  statut: StatutFiphVersion;
  versionCouranteId: number;
  versionCouranteNumero: number;
}

export interface CreerFiphManuelleRequest {
  agentId: number;
  annee: number;
  numeroSemaine: number;
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
  totalHN: number;
  totalHS: number;
  statutVersion: StatutFiphVersion;
  empreinteIntegrite: string | null;
  lockVersion: number;
  pointages: PointageDto[];
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

/** Niveau de validation attendu selon le statut courant (section 12 du document source). */
export const NIVEAU_VALIDATION_ATTENDU: Partial<Record<StatutFiphVersion, number>> = {
  SOUMISE: 2,
  VALIDEE_NIVEAU_2: 3,
  VALIDEE_NIVEAU_3: 4,
};

export function estFiphModifiable(statut: StatutFiphVersion): boolean {
  return statut === "BROUILLON" || statut === "EN_COMPLEMENT";
}

export function estFiphFigee(statut: StatutFiphVersion): boolean {
  return statut === "VALIDEE_DEFINITIVEMENT";
}
