import { httpClient } from "./httpClient";
import type {
  ChantierDto, CodeHNDto, CreerChantierRequest, CreerCodeHNRequest, CreerServiceRequest,
  CreerVehiculeRequest, MotifInterruptionDto, ServiceDto, VehiculeDto,
} from "../types/referentiel";

/** Referentiels partages (listes deroulantes) : lecture ouverte a tout utilisateur authentifie, ecriture reservee a l'Administrateur. */

export async function listerServices(): Promise<ServiceDto[]> {
  return (await httpClient.get<ServiceDto[]>("/referentiels/services")).data;
}
export async function creerService(requete: CreerServiceRequest): Promise<ServiceDto> {
  return (await httpClient.post<ServiceDto>("/referentiels/services", requete)).data;
}

export async function listerChantiers(): Promise<ChantierDto[]> {
  return (await httpClient.get<ChantierDto[]>("/referentiels/chantiers")).data;
}
export async function creerChantier(requete: CreerChantierRequest): Promise<ChantierDto> {
  return (await httpClient.post<ChantierDto>("/referentiels/chantiers", requete)).data;
}

export async function listerCodesHN(): Promise<CodeHNDto[]> {
  return (await httpClient.get<CodeHNDto[]>("/referentiels/codes-hn")).data;
}
export async function creerCodeHN(requete: CreerCodeHNRequest): Promise<CodeHNDto> {
  return (await httpClient.post<CodeHNDto>("/referentiels/codes-hn", requete)).data;
}

export async function listerVehicules(): Promise<VehiculeDto[]> {
  return (await httpClient.get<VehiculeDto[]>("/referentiels/vehicules")).data;
}
export async function creerVehicule(requete: CreerVehiculeRequest): Promise<VehiculeDto> {
  return (await httpClient.post<VehiculeDto>("/referentiels/vehicules", requete)).data;
}

export async function listerMotifsInterruption(): Promise<MotifInterruptionDto[]> {
  return (await httpClient.get<MotifInterruptionDto[]>("/referentiels/motifs-interruption")).data;
}
