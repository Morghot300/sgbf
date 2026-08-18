/**
 * Types partages par toute la couche authentification du frontend.
 *
 * Ils reproduisent volontairement les DTO exposes par le backend
 * (`security.dto.*`, voir `backend/src/main/java/com/snef/sgbf/security/dto`)
 * afin que le contrat entre les deux cotes reste explicite et verifie par le
 * compilateur TypeScript plutot que par convention informelle.
 *
 * Depuis le 2026-08-17, l'authentification est simple : `POST /auth/login`
 * verifie l'identifiant (ou l'e-mail) et le mot de passe et renvoie
 * immediatement un jeton d'acces, sans aucune seconde etape.
 */

/** Reponse porteuse d'un jeton d'acces, emise directement par POST /api/auth/login ou lors d'un rafraichissement. */
export interface TokenResponse {
  jetonAcces: string;
  expiresInSecondes: number;
}

/**
 * Informations utilisateur telles qu'exposees par
 * `GET /api/auth/me` (`security.dto.UtilisateurCourantDto`) - ne contient
 * jamais de secret.
 */
export interface UtilisateurCourant {
  id: number;
  identifiant: string;
  email: string;
  serviceId: number | null;
  serviceLibelle: string | null;
  /** Codes de role actifs (ex. "CHARGE_AFFAIRES", "ADMINISTRATEUR"), utilises pour l'affichage conditionnel des menus - jamais pour decider seul d'une autorisation. */
  rolesActifs: string[];
}
