/** Types miroir des DTO du module Bon de sortie (backend/.../bonsortie/*). */

export type StatutBonSortie = "BROUILLON" | "VISE" | "VALIDE";
export type MoyenUtilise = "OMNIUM_SERVICE" | "PERSONNEL" | "TAXI" | "AUTRE";
export type OrigineBonSortie = "PRINCIPALE" | "PERSONNE_A_BORD";
export type StatutAssociationPersonne = "ACTIVE" | "RETIREE";

export interface BonSortieDto {
  id: number;
  agentId: number;
  agentNomComplet: string;
  agentMatricule: string;
  vehiculeId: number | null;
  vehiculeImmatriculation: string | null;
  affectationMissionId: number | null;
  missionCodeHN: string | null;
  /** Mission choisie explicitement à la création/correction (évolution du 2026-08-27, "Code Mission") - distincte de missionCodeHN ci-dessus, qui reste la résolution automatique par agent+date à la validation. */
  missionSelectionneeId: number | null;
  missionSelectionneeCodeHN: string | null;
  missionSelectionneeChantierLibelle: string | null;
  moyenUtilise: MoyenUtilise;
  precisionVehicule: string | null;
  lt: string | null;
  kilometrage: number;
  dateSortie: string;
  heureSortie: string;
  heureRetour: string | null;
  lieu: string;
  codeAffaireSaisi: string;
  motifSortie: string;
  statut: StatutBonSortie;
  origine: OrigineBonSortie;
  bonSortiePrincipalId: number | null;
  viseParIdentifiant: string | null;
  dateVisa: string | null;
  valideParIdentifiant: string | null;
  dateValidation: string | null;
  lockVersion: number;
  /** Message actionnable si aucune affectation active n'est résolue pour l'agent à la date de sortie (évolution du 2026-08-19, Lot 2) - jamais bloquant, `null` dès qu'une affectation est résolue. */
  avertissementAffectation: string | null;
}

export interface CreerBonSortieRequest {
  /** Personne principale (évolution du 2026-08-27) : `null` pour la création en libre-service habituelle ; renseigné pour créer POUR LE COMPTE d'un tiers - réservé au CA/PH du service ou au Super Administrateur, vérifié côté serveur. */
  agentId?: number | null;
  /** Mission choisie explicitement (évolution du 2026-08-27, "Code Mission") - facultative. */
  missionId?: number | null;
  vehiculeId: number | null;
  moyenUtilise: MoyenUtilise;
  precisionVehicule: string | null;
  lt: string | null;
  kilometrage: number;
  dateSortie: string;
  heureSortie: string;
  lieu: string;
  codeAffaireSaisi: string;
  motifSortie: string;
}

export interface ModifierBonSortieRequest {
  /** Mission choisie explicitement (évolution du 2026-08-27, "Code Mission") - facultative. */
  missionId: number | null;
  vehiculeId: number | null;
  moyenUtilise: MoyenUtilise;
  precisionVehicule: string | null;
  lt: string | null;
  kilometrage: number;
  dateSortie: string;
  heureSortie: string;
  heureRetour: string | null;
  lieu: string;
  codeAffaireSaisi: string;
  motifSortie: string;
  lockVersion: number;
}

export interface BonSortiePersonneDto {
  id: number;
  bonSortiePrincipalId: number;
  agentId: number;
  agentNomComplet: string;
  agentMatricule: string;
  statutAssociation: StatutAssociationPersonne;
  dateAssociation: string;
  dateRetrait: string | null;
  bonSortieIndividuelId: number | null;
}

export interface AjouterPersonneABordRequest {
  agentId: number;
}

export interface AjouterPersonnesABordEnLotRequest {
  agentIds: number[];
}

export type StatutCompte = "ACTIF" | "VERROUILLE" | "DESACTIVE";

/** Personne éligible à être ajoutée comme personne à bord (évolution du 2026-08-19, Lot 4) - périmètre calculé côté serveur. */
export interface AgentEligibleDto {
  id: number;
  nomComplet: string;
  matricule: string | null;
  serviceLibelle: string | null;
  statutCompte: StatutCompte;
  /** Signale (sans jamais bloquer) que cette personne est déjà à bord d'un AUTRE bon à la même date de sortie. */
  dejaAffecteMemeCreneau: boolean;
}

export const LIBELLES_STATUT_BON_SORTIE: Record<StatutBonSortie, string> = {
  BROUILLON: "Brouillon",
  VISE: "Visé",
  VALIDE: "Validé",
};

export const LIBELLES_MOYEN_UTILISE: Record<MoyenUtilise, string> = {
  OMNIUM_SERVICE: "Omnium service",
  PERSONNEL: "Véhicule personnel",
  TAXI: "Taxi",
  AUTRE: "Autre",
};
