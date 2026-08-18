/**
 * Types miroir des DTO exposes par `ReferentielController`
 * (backend/.../referentiel/controller). Referentiels partages par les
 * modules Mission, Bon de sortie et FIPH (listes deroulantes de saisie).
 */

export interface ServiceDto {
  id: number;
  codeService: string;
  libelle: string;
  actif: boolean;
}

export interface ChantierDto {
  id: number;
  codeAffaire: string;
  libelle: string;
  actif: boolean;
}

export interface CodeHNDto {
  id: number;
  code: string;
  libelle: string;
  chantierId: number;
  chantierLibelle: string;
}

/** Distinct de `MoyenUtilise` (bon de sortie) : ne porte pas TAXI. */
export type TypeVehicule = "OMNIUM_SERVICE" | "PERSONNEL";

export interface VehiculeDto {
  id: number;
  immatriculation: string;
  type: TypeVehicule;
}

export interface MotifInterruptionDto {
  id: number;
  code: string;
  libelle: string;
  actif: boolean;
}

export interface CreerServiceRequest {
  codeService: string;
  libelle: string;
}

export interface CreerChantierRequest {
  codeAffaire: string;
  libelle: string;
}

export interface CreerCodeHNRequest {
  code: string;
  libelle: string;
  chantierId: number;
}

export interface CreerVehiculeRequest {
  immatriculation: string;
  type: TypeVehicule;
}
