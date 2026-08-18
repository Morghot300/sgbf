import { httpClient } from "./httpClient";
import type {
  AgentDto, CreerAgentRequest, CreerHabilitationRequest, CreerUtilisateurRequest,
  HabilitationDto, StatutCompte, UtilisateurDto,
} from "../types/identite";

/** Reserve a l'Administrateur cote backend (@PreAuthorize classe entiere sur UtilisateurController/HabilitationController). */

export async function listerUtilisateurs(): Promise<UtilisateurDto[]> {
  return (await httpClient.get<UtilisateurDto[]>("/utilisateurs")).data;
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

export async function listerAgents(): Promise<AgentDto[]> {
  return (await httpClient.get<AgentDto[]>("/agents")).data;
}
export async function rechercherAgents(terme: string): Promise<AgentDto[]> {
  return (await httpClient.get<AgentDto[]>("/agents/recherche", { params: { terme } })).data;
}
export async function creerAgent(requete: CreerAgentRequest): Promise<AgentDto> {
  return (await httpClient.post<AgentDto>("/agents", requete)).data;
}
export async function lierUtilisateurAgent(agentId: number, utilisateurId: number): Promise<AgentDto> {
  return (await httpClient.put<AgentDto>(`/agents/${agentId}/utilisateur/${utilisateurId}`)).data;
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
