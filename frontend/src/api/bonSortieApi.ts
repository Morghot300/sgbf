import { httpClient } from "./httpClient";
import type {
  AjouterPersonneABordRequest, BonSortieDto, BonSortiePersonneDto, CreerBonSortieRequest,
  ModifierRetourRequest,
} from "../types/bonSortie";

export async function listerBonsSortie(): Promise<BonSortieDto[]> {
  return (await httpClient.get<BonSortieDto[]>("/bons-sortie")).data;
}
export async function obtenirBonSortie(id: number): Promise<BonSortieDto> {
  return (await httpClient.get<BonSortieDto>(`/bons-sortie/${id}`)).data;
}
export async function creerBonSortie(requete: CreerBonSortieRequest): Promise<BonSortieDto> {
  return (await httpClient.post<BonSortieDto>("/bons-sortie", requete)).data;
}
export async function renseignerRetour(id: number, requete: ModifierRetourRequest): Promise<BonSortieDto> {
  return (await httpClient.put<BonSortieDto>(`/bons-sortie/${id}/retour`, requete)).data;
}
export async function viserBonSortie(id: number): Promise<BonSortieDto> {
  return (await httpClient.post<BonSortieDto>(`/bons-sortie/${id}/viser`)).data;
}
export async function validerBonSortie(id: number): Promise<BonSortieDto> {
  return (await httpClient.post<BonSortieDto>(`/bons-sortie/${id}/valider`)).data;
}
/** Ouvre l'aperçu PDF (RG-DOC-001 : disponible uniquement au statut VALIDE) dans un nouvel onglet. */
export async function ouvrirPdfBonSortie(id: number): Promise<void> {
  const reponse = await httpClient.get(`/bons-sortie/${id}/pdf`, { responseType: "blob" });
  ouvrirBlobPdf(reponse.data as Blob);
}

export async function listerPersonnesABord(bonSortiePrincipalId: number): Promise<BonSortiePersonneDto[]> {
  return (await httpClient.get<BonSortiePersonneDto[]>(`/bons-sortie/${bonSortiePrincipalId}/personnes-a-bord`)).data;
}
export async function ajouterPersonneABord(bonSortiePrincipalId: number, requete: AjouterPersonneABordRequest): Promise<BonSortiePersonneDto> {
  return (await httpClient.post<BonSortiePersonneDto>(`/bons-sortie/${bonSortiePrincipalId}/personnes-a-bord`, requete)).data;
}
export async function retirerPersonneABord(bonSortiePrincipalId: number, associationId: number): Promise<BonSortiePersonneDto> {
  return (await httpClient.delete<BonSortiePersonneDto>(`/bons-sortie/${bonSortiePrincipalId}/personnes-a-bord/${associationId}`)).data;
}

/** Ouvre un blob PDF dans un nouvel onglet - utilitaire partage avec le module FIPH. */
export function ouvrirBlobPdf(blob: Blob): void {
  const url = URL.createObjectURL(new Blob([blob], { type: "application/pdf" }));
  window.open(url, "_blank", "noopener,noreferrer");
  // Liberation differee : le temps que l'onglet/le lecteur PDF ait charge le contenu.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
