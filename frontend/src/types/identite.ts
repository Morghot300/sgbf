/**
 * Types miroir des DTO d'identite/habilitation (backend/.../identite/*).
 * `UtilisateurController` et `HabilitationController` sont reserves a
 * l'Administrateur cote backend (@PreAuthorize classe entiere) - les appels
 * correspondants ne doivent etre proposes dans l'interface qu'a ce role.
 */

/** Les 8 roles metier stables (referentiel/entity/CodeRoleMetier.java). */
export type CodeRoleMetier =
  | "AGENT"
  | "CHARGE_AFFAIRES"
  | "PERSONNE_HABILITEE"
  | "RESPONSABLE_ACTIVITE"
  | "DIRECTION"
  | "RH"
  | "ADMINISTRATEUR"
  | "SUPER_ADMINISTRATEUR";

export type StatutCompte = "ACTIF" | "VERROUILLE" | "DESACTIVE";

export interface UtilisateurDto {
  id: number;
  identifiant: string;
  email: string;
  statutCompte: StatutCompte;
  serviceId: number | null;
  serviceLibelle: string | null;
}

export interface CreerUtilisateurRequest {
  identifiant: string;
  email: string;
  motDePasse: string;
  serviceId: number | null;
}

export interface AgentDto {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  nomComplet: string;
  serviceId: number;
  serviceLibelle: string;
  actif: boolean;
  possedeCompte: boolean;
}

export interface CreerAgentRequest {
  matricule: string;
  nom: string;
  prenom: string;
  serviceId: number;
}

export interface HabilitationDto {
  id: number;
  utilisateurId: number;
  utilisateurIdentifiant: string;
  roleMetierCode: CodeRoleMetier;
  roleMetierLibelle: string;
  serviceId: number | null;
  serviceLibelle: string | null;
  dateDebut: string;
  dateFin: string | null;
  actif: boolean;
}

export interface CreerHabilitationRequest {
  utilisateurId: number;
  /** Code role (CodeRoleMetier) - null uniquement autorise pour RH/ADMINISTRATEUR (perimetre global). */
  roleMetierCode: CodeRoleMetier;
  serviceId: number | null;
  dateDebut: string;
  dateFin: string | null;
}

/** Roles a perimetre global (RG-HAB-005) : aucun service a demander a la creation d'habilitation. */
export const ROLES_PERIMETRE_GLOBAL: ReadonlySet<CodeRoleMetier> = new Set(["RH", "ADMINISTRATEUR", "SUPER_ADMINISTRATEUR"]);

export const LIBELLES_ROLE: Record<CodeRoleMetier, string> = {
  AGENT: "Agent",
  CHARGE_AFFAIRES: "Chargé d'Affaires",
  PERSONNE_HABILITEE: "Personne habilitée",
  RESPONSABLE_ACTIVITE: "Responsable d'activité",
  DIRECTION: "Direction",
  RH: "Ressources Humaines",
  ADMINISTRATEUR: "Administrateur",
  SUPER_ADMINISTRATEUR: "Super Administrateur",
};
