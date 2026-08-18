import { httpClient } from "./httpClient";
import type { TokenResponse, UtilisateurCourant } from "../types/auth";

/**
 * Appels API du module d'authentification. Simples wrappers types autour de
 * `httpClient` - toute la logique (gestion du jeton, rafraichissement
 * automatique) reste centralisee dans `httpClient.ts` et `AuthContext.tsx`.
 *
 * Authentification simple (2026-08-17) : `seConnecter` renvoie directement
 * un jeton d'acces des que l'identifiant (ou l'e-mail) et le mot de passe
 * sont corrects, sans aucune seconde etape.
 */

export async function seConnecter(identifiant: string, motDePasse: string): Promise<TokenResponse> {
  const reponse = await httpClient.post<TokenResponse>("/auth/login", { identifiant, motDePasse });
  return reponse.data;
}

export async function seDeconnecter(): Promise<void> {
  await httpClient.post("/auth/logout");
}

export async function recupererUtilisateurCourant(): Promise<UtilisateurCourant> {
  const reponse = await httpClient.get<UtilisateurCourant>("/auth/me");
  return reponse.data;
}
