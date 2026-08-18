/** Types miroir des DTO du journal d'audit (backend/.../common/audit/*). */

export type EntiteAuditable =
  | "UTILISATEUR"
  | "HABILITATION"
  | "AGENT"
  | "MISSION"
  | "AFFECTATION_MISSION"
  | "BON_SORTIE"
  | "BON_SORTIE_PERSONNE"
  | "FIPH"
  | "FIPH_VERSION";

export interface EvenementAuditDto {
  id: number;
  entiteType: EntiteAuditable;
  entiteId: string;
  utilisateur: string | null;
  action: string;
  valeurAvant: string | null;
  valeurApres: string | null;
  statutAvant: string | null;
  statutApres: string | null;
  dateAction: string;
}
