/** Types miroir des DTO du module Mission (backend/.../mission/*). */

export type StatutMission = "PLANIFIEE" | "EN_COURS" | "INTERROMPUE" | "TERMINEE";
export type StatutAffectation = "ACTIVE" | "INTERROMPUE" | "TERMINEE" | "TRANSFEREE";

export interface MissionDto {
  id: number;
  codeHN: string;
  codeHNLibelle: string;
  chantierId: number;
  chantierLibelle: string;
  dateDebutPrevue: string;
  dateFinPrevue: string;
  dateFinReelle: string | null;
  statut: StatutMission;
  missionPrecedenteId: number | null;
}

export interface CreerMissionRequest {
  codeHNId: number;
  chantierId: number;
  dateDebutPrevue: string;
  dateFinPrevue: string;
  missionPrecedenteId: number | null;
}

export interface AffectationMissionDto {
  id: number;
  agentId: number;
  agentNomComplet: string;
  agentMatricule: string;
  missionId: number;
  missionCodeHN: string;
  dateDebutAffectation: string;
  dateFinAffectation: string | null;
  statutAffectation: StatutAffectation;
  motifInterruptionCode: string | null;
  motifInterruptionLibelle: string | null;
  commentaireInterruption: string | null;
  affectationPrecedenteId: number | null;
  creeParIdentifiant: string;
  dateCreation: string;
}

export interface AffecterAgentRequest {
  agentId: number;
  missionId: number;
  dateDebutAffectation: string;
}

export interface InterrompreAffectationRequest {
  motifCode: string;
  dateInterruption: string;
  commentaire: string | null;
}

export interface ReaffecterRequest {
  missionCibleId: number;
  dateDebutAffectation: string;
}

export const LIBELLES_STATUT_MISSION: Record<StatutMission, string> = {
  PLANIFIEE: "Planifiée",
  EN_COURS: "En cours",
  INTERROMPUE: "Interrompue",
  TERMINEE: "Terminée",
};

export const LIBELLES_STATUT_AFFECTATION: Record<StatutAffectation, string> = {
  ACTIVE: "Active",
  INTERROMPUE: "Interrompue",
  TERMINEE: "Terminée",
  TRANSFEREE: "Transférée",
};
