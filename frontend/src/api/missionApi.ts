import { httpClient } from "./httpClient";
import type {
  AffecterAgentRequest, AffectationMissionDto, CreerMissionRequest, InterrompreAffectationRequest,
  MissionDto, ReaffecterMiMissionRequest, ReaffecterRequest,
} from "../types/mission";

export async function listerMissions(): Promise<MissionDto[]> {
  return (await httpClient.get<MissionDto[]>("/missions")).data;
}
export async function obtenirMission(id: number): Promise<MissionDto> {
  return (await httpClient.get<MissionDto>(`/missions/${id}`)).data;
}
export async function historiqueMission(id: number): Promise<MissionDto[]> {
  return (await httpClient.get<MissionDto[]>(`/missions/${id}/historique`)).data;
}
export async function creerMission(requete: CreerMissionRequest): Promise<MissionDto> {
  return (await httpClient.post<MissionDto>("/missions", requete)).data;
}

export async function listerAffectations(missionId: number): Promise<AffectationMissionDto[]> {
  return (await httpClient.get<AffectationMissionDto[]>("/affectations-mission", { params: { missionId } })).data;
}
export async function obtenirAffectation(id: number): Promise<AffectationMissionDto> {
  return (await httpClient.get<AffectationMissionDto>(`/affectations-mission/${id}`)).data;
}
export async function affecterAgent(requete: AffecterAgentRequest): Promise<AffectationMissionDto> {
  return (await httpClient.post<AffectationMissionDto>("/affectations-mission", requete)).data;
}
export async function interrompreAffectation(id: number, requete: InterrompreAffectationRequest): Promise<AffectationMissionDto> {
  return (await httpClient.post<AffectationMissionDto>(`/affectations-mission/${id}/interrompre`, requete)).data;
}
export async function reaffecterAgent(id: number, requete: ReaffecterRequest): Promise<AffectationMissionDto> {
  return (await httpClient.post<AffectationMissionDto>(`/affectations-mission/${id}/reaffecter`, requete)).data;
}
export async function reaffecterPendantMissionEnCours(requete: ReaffecterMiMissionRequest): Promise<AffectationMissionDto> {
  return (await httpClient.post<AffectationMissionDto>("/affectations-mission/reaffecter-mi-mission", requete)).data;
}
