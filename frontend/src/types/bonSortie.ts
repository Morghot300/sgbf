/** Types miroir des DTO du module Bon de sortie (backend/.../bonsortie/*). */

export type StatutBonSortie = "BROUILLON" | "VISE" | "VALIDE";
export type MoyenUtilise = "OMNIUM_SERVICE" | "PERSONNEL" | "TAXI";
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
  moyenUtilise: MoyenUtilise;
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
}

export interface CreerBonSortieRequest {
  vehiculeId: number | null;
  moyenUtilise: MoyenUtilise;
  lt: string | null;
  kilometrage: number;
  dateSortie: string;
  heureSortie: string;
  lieu: string;
  codeAffaireSaisi: string;
  motifSortie: string;
}

export interface ModifierRetourRequest {
  heureRetour: string;
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

export const LIBELLES_STATUT_BON_SORTIE: Record<StatutBonSortie, string> = {
  BROUILLON: "Brouillon",
  VISE: "Visé",
  VALIDE: "Validé",
};

export const LIBELLES_MOYEN_UTILISE: Record<MoyenUtilise, string> = {
  OMNIUM_SERVICE: "Omnium service",
  PERSONNEL: "Véhicule personnel",
  TAXI: "Taxi",
};
