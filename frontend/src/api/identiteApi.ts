import { httpClient } from "./httpClient";
import type {
  CreerHabilitationRequest, CreerUtilisateurRequest, ModifierIdentiteRequest,
  HabilitationDto, StatutCompte, UtilisateurDto,
} from "../types/identite";

/** Reserve a l'Administrateur cote backend (@PreAuthorize classe entiere sur UtilisateurController/HabilitationController). */

export async function listerUtilisateurs(): Promise<UtilisateurDto[]> {
  return (await httpClient.get<UtilisateurDto[]>("/utilisateurs")).data;
}
/** Recherche/filtrage combinable (identifiant, e-mail, nom d'agent lié, service, rôle, statut) - évolution du 2026-08-18. */
export async function rechercherUtilisateurs(filtres: {
  terme?: string; serviceId?: number; role?: string; statut?: StatutCompte;
}): Promise<UtilisateurDto[]> {
  return (await httpClient.get<UtilisateurDto[]>("/utilisateurs", { params: filtres })).data;
}
export async function obtenirUtilisateur(id: number): Promise<UtilisateurDto> {
  return (await httpClient.get<UtilisateurDto>(`/utilisateurs/${id}`)).data;
}
export async function creerUtilisateur(requete: CreerUtilisateurRequest): Promise<UtilisateurDto> {
  return (await httpClient.post<UtilisateurDto>("/utilisateurs", requete)).data;
}
export async function changerStatutCompte(id: number, statut: StatutCompte): Promise<void> {
  await httpClient.put(`/utilisateurs/${id}/statut/${statut}`);
}
export async function modifierIdentifiant(id: number, identifiant: string): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/identifiant`, { identifiant })).data;
}
export async function modifierEmail(id: number, email: string): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/email`, { email })).data;
}
export async function modifierServiceUtilisateur(id: number, serviceId: number | null): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/service`, { serviceId })).data;
}
export async function reinitialiserMotDePasse(id: number, nouveauMotDePasse: string): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/mot-de-passe`, { nouveauMotDePasse })).data;
}
/** Correction du nom/prénom/matricule d'une personne (évolution du 2026-08-19). */
export async function modifierIdentite(id: number, requete: ModifierIdentiteRequest): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/identite`, requete)).data;
}
/** Ajoute un compte applicatif à une personne du référentiel qui n'en avait pas encore (évolution du 2026-08-19). */
export async function ajouterCompteApplicatif(id: number, requete: CreerUtilisateurRequest): Promise<UtilisateurDto> {
  return (await httpClient.put<UtilisateurDto>(`/utilisateurs/${id}/compte`, requete)).data;
}

export async function listerHabilitationsUtilisateur(utilisateurId: number): Promise<HabilitationDto[]> {
  return (await httpClient.get<HabilitationDto[]>(`/habilitations/utilisateur/${utilisateurId}`)).data;
}
export async function attribuerHabilitation(requete: CreerHabilitationRequest): Promise<HabilitationDto> {
  return (await httpClient.post<HabilitationDto>("/habilitations", requete)).data;
}
export async function retirerHabilitation(id: number): Promise<void> {
  await httpClient.delete(`/habilitations/${id}`);
}
/** Reaffectation vers un autre service (évolution du 2026-08-19) - action dédiée et tracée, à utiliser plutôt qu'un retrait suivi d'une nouvelle attribution. */
export async function changerServiceHabilitation(id: number, nouveauServiceId: number): Promise<HabilitationDto> {
  return (await httpClient.put<HabilitationDto>(`/habilitations/${id}/service`, { nouveauServiceId })).data;
}
