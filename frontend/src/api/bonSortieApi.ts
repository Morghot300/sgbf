import { httpClient } from "./httpClient";
import type {
  AgentEligibleDto, AjouterPersonneABordRequest, AjouterPersonnesABordEnLotRequest,
  BonSortieDto, BonSortiePersonneDto, CreerBonSortieRequest, ModifierBonSortieRequest,
} from "../types/bonSortie";

export interface FiltresBonSortie {
  date?: string;
  dateDebut?: string;
  dateFin?: string;
  statut?: string;
  serviceId?: number;
}
/** Filtres combinables (date exacte, période, statut, service) - évolution du 2026-08-18. */
export async function listerBonsSortie(filtres: FiltresBonSortie = {}): Promise<BonSortieDto[]> {
  return (await httpClient.get<BonSortieDto[]>("/bons-sortie", { params: filtres })).data;
}
export async function obtenirBonSortie(id: number): Promise<BonSortieDto> {
  return (await httpClient.get<BonSortieDto>(`/bons-sortie/${id}`)).data;
}
export async function creerBonSortie(requete: CreerBonSortieRequest): Promise<BonSortieDto> {
  return (await httpClient.post<BonSortieDto>("/bons-sortie", requete)).data;
}
export async function modifierBonSortie(id: number, requete: ModifierBonSortieRequest): Promise<BonSortieDto> {
  return (await httpClient.put<BonSortieDto>(`/bons-sortie/${id}`, requete)).data;
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
/** Personnel du service du titulaire éligible à être ajouté à ce bon (évolution du 2026-08-19, Lot 4) - périmètre calculé côté serveur. */
export async function listerAgentsEligibles(bonSortieId: number): Promise<AgentEligibleDto[]> {
  return (await httpClient.get<AgentEligibleDto[]>(`/bons-sortie/${bonSortieId}/agents-eligibles`)).data;
}
/** Ajout groupé, transactionnel et idempotent. */
export async function ajouterPersonnesABordEnLot(bonSortiePrincipalId: number, requete: AjouterPersonnesABordEnLotRequest): Promise<BonSortiePersonneDto[]> {
  return (await httpClient.post<BonSortiePersonneDto[]>(`/bons-sortie/${bonSortiePrincipalId}/personnes-a-bord/lot`, requete)).data;
}

/**
 * Ouvre un blob PDF dans un nouvel onglet et declenche directement la
 * boite de dialogue d'impression du navigateur - utilitaire partage avec le
 * module FIPH. L'utilisateur y choisit lui-meme la destination (fichier PDF
 * sur le poste, ou une imprimante reseau, l'imprimante par defaut etant
 * deja preselectionnee) : c'est le comportement natif du navigateur, pas
 * une fonctionnalite reimplementee ici.
 *
 * Pas de `noopener` : contrairement a un lien externe, ce contenu est
 * genere par notre propre backend (jamais une URL tierce) - `noopener`
 * empecherait de garder une reference vers la fenetre et donc d'y
 * declencher l'impression.
 */
export function ouvrirBlobPdf(blob: Blob): void {
  const url = URL.createObjectURL(new Blob([blob], { type: "application/pdf" }));
  const fenetre = window.open(url, "_blank");
  if (fenetre) {
    // Delai pragmatique : le lecteur PDF integre du navigateur a besoin d'un
    // court instant pour s'initialiser avant de pouvoir repondre a print().
    // Sans effet bloquant si le navigateur restreint cet appel : l'utilisateur
    // garde de toute facon acces au bouton d'impression du lecteur PDF lui-meme.
    setTimeout(() => {
      try {
        fenetre.print();
      } catch {
        // Restriction navigateur sur l'appel programmatique - non bloquant.
      }
    }, 800);
  }
  // Liberation differee : le temps que l'onglet/le lecteur PDF ait charge le contenu.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
