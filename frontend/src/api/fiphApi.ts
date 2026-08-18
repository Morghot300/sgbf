import { ouvrirBlobPdf } from "./bonSortieApi";
import { httpClient } from "./httpClient";
import type {
  CompleterPointageRequest, CreerFiphManuelleRequest, CreerNouvelleVersionRequest,
  FiphDto, FiphVersionDto, ValiderFiphRequest, ValidationDto,
} from "../types/fiph";

export async function listerFiph(): Promise<FiphDto[]> {
  return (await httpClient.get<FiphDto[]>("/fiph")).data;
}
export async function obtenirFiph(id: number): Promise<FiphDto> {
  return (await httpClient.get<FiphDto>(`/fiph/${id}`)).data;
}
export async function creerFiphManuelle(requete: CreerFiphManuelleRequest): Promise<FiphDto> {
  return (await httpClient.post<FiphDto>("/fiph/manuelle", requete)).data;
}

export async function obtenirFiphVersion(id: number): Promise<FiphVersionDto> {
  return (await httpClient.get<FiphVersionDto>(`/fiph-versions/${id}`)).data;
}
export async function listerVersionsFiph(fiphId: number): Promise<FiphVersionDto[]> {
  return (await httpClient.get<FiphVersionDto[]>(`/fiph-versions/fiph/${fiphId}`)).data;
}
export async function listerValidations(fiphVersionId: number): Promise<ValidationDto[]> {
  return (await httpClient.get<ValidationDto[]>(`/fiph-versions/${fiphVersionId}/validations`)).data;
}
export async function completerPointage(fiphVersionId: number, requete: CompleterPointageRequest): Promise<FiphVersionDto> {
  return (await httpClient.put<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/pointage`, requete)).data;
}
export async function signerFiph(fiphVersionId: number): Promise<FiphVersionDto> {
  return (await httpClient.post<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/signer`)).data;
}
export async function soumettreFiph(fiphVersionId: number): Promise<FiphVersionDto> {
  return (await httpClient.post<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/soumettre`)).data;
}
export async function validerFiph(fiphVersionId: number, niveau: number, requete: ValiderFiphRequest): Promise<FiphVersionDto> {
  return (await httpClient.post<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/valider/${niveau}`, requete)).data;
}
export async function creerNouvelleVersionFiph(fiphId: number, requete: CreerNouvelleVersionRequest): Promise<FiphVersionDto> {
  return (await httpClient.post<FiphVersionDto>(`/fiph-versions/fiph/${fiphId}/nouvelle-version`, requete)).data;
}
/** Ouvre le PDF de la FIPH (RG-DOC-003 : disponible uniquement une fois VALIDEE_DEFINITIVEMENT). */
export async function ouvrirPdfFiphVersion(fiphVersionId: number): Promise<void> {
  const reponse = await httpClient.get(`/fiph-versions/${fiphVersionId}/pdf`, { responseType: "blob" });
  ouvrirBlobPdf(reponse.data as Blob);
}
