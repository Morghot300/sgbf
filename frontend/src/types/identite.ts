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

/**
 * Une personne du personnel (evolution du 2026-08-19, "un utilisateur est
 * obligatoirement un agent") - identite RH (matricule/nom/prenom) et,
 * lorsqu'elle existe, identite de connexion (identifiant/email) dans une
 * seule et meme ligne. `identifiant`/`email` sont `null` si cette personne
 * ne dispose pas d'un compte applicatif (voir `possedeCompteApplicatif`).
 */
export interface UtilisateurDto {
  id: number;
  matricule: string | null;
  nom: string | null;
  prenom: string | null;
  nomComplet: string | null;
  identifiant: string | null;
  email: string | null;
  possedeCompteApplicatif: boolean;
  statutCompte: StatutCompte;
  serviceId: number | null;
  serviceLibelle: string | null;
}

/**
 * `identifiant`/`email`/`motDePasse` forment un groupe : soit les trois sont
 * fournis (compte applicatif immediat), soit aucun ne l'est (personne du
 * referentiel sans acces direct - voir `AjouterCompteApplicatifRequest` pour
 * lui en ajouter un plus tard).
 */
export interface CreerUtilisateurRequest {
  nom: string;
  prenom: string;
  matricule: string | null;
  identifiant: string | null;
  email: string | null;
  motDePasse: string | null;
  serviceId: number | null;
}

export interface ModifierIdentiteRequest {
  nom: string;
  prenom: string;
  matricule: string | null;
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

/** Roles pour lesquels un même utilisateur ne peut détenir qu'une seule habilitation active à la fois (évolution du 2026-08-19) - voir CodeRoleMetier#estServiceExclusif côté backend, seule source de vérité réelle. */
export const ROLES_SERVICE_EXCLUSIF: ReadonlySet<CodeRoleMetier> = new Set(["CHARGE_AFFAIRES", "PERSONNE_HABILITEE", "RESPONSABLE_ACTIVITE"]);

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
