import { ouvrirBlobPdf } from "./bonSortieApi";
import { httpClient } from "./httpClient";
import type { EvenementAuditDto } from "../types/audit";

export async function historiqueAuditFiph(fiphId: number): Promise<EvenementAuditDto[]> {
  return (await httpClient.get<EvenementAuditDto[]>(`/audit/fiph/${fiphId}`)).data;
}

/** Reserve a Direction/RH/Administrateur cote backend (@PreAuthorize). */
export async function telechargerAuditCsv(fiphId: number): Promise<void> {
  const reponse = await httpClient.get(`/audit/fiph/${fiphId}/export/csv`, { responseType: "blob" });
  declencherTelechargement(reponse.data as Blob, "text/csv", `audit-fiph-${fiphId}.csv`);
}
export async function telechargerAuditPdf(fiphId: number): Promise<void> {
  const reponse = await httpClient.get(`/audit/fiph/${fiphId}/export/pdf`, { responseType: "blob" });
  ouvrirBlobPdf(reponse.data as Blob);
}

function declencherTelechargement(blob: Blob, contentType: string, nomFichier: string): void {
  const url = URL.createObjectURL(new Blob([blob], { type: contentType }));
  const lien = document.createElement("a");
  lien.href = url;
  lien.download = nomFichier;
  lien.click();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
