/** Types miroir des DTO du module notifications (backend/.../notification/*). */

export type TypeNotification =
  | "FIPH_A_VALIDER" | "FIPH_VALIDEE" | "FIPH_PRISE_EN_MAIN_SUPER_ADMIN"
  | "BON_SORTIE_A_VALIDER" | "BON_SORTIE_VALIDE" | "PERSONNE_A_BORD_AJOUTEE" | "ANOMALIE_AFFECTATION";

export interface NotificationDto {
  id: number;
  type: TypeNotification;
  titre: string;
  message: string;
  entiteType: string;
  entiteId: number;
  /** Chemin frontend relatif (ex. "/fiph/42") permettant d'ouvrir directement l'entite concernee. */
  lien: string;
  /** Identifiant de l'utilisateur ayant declenche l'evenement, ou null pour une action systeme. */
  declencheParIdentifiant: string | null;
  lue: boolean;
  dateCreation: string;
  dateLecture: string | null;
}
