import { ouvrirBlobPdf } from "./bonSortieApi";
import { httpClient } from "./httpClient";
import type { AgentEligibleDto } from "../types/bonSortie";
import type {
  CompleterPointageRequest, CreerFiphManuelleRequest, CreerNouvelleVersionRequest, DefinirDateFinRequest,
  FiphDto, FiphVersionDto, PriseEnMainSuperAdminRequest, ResultatCreationFiphDto, StatutFiphVersion,
  ValiderFiphRequest, ValidationDto,
} from "../types/fiph";

export interface FiltresFiph {
  date?: string;
  dateDebut?: string;
  dateFin?: string;
  statut?: string;
  /** Regroupement par sous-menu (section 16-18) — plusieurs statuts combinés en un seul filtre. */
  statuts?: StatutFiphVersion[];
  serviceId?: number;
  nomComplet?: string;
  /** Recherche par Code Mission ou nom textuel de la mission (section 25). */
  mission?: string;
}
/** Filtres combinables (date exacte, période, statut(s), service, nom, mission) - évolution du 2026-08-18/2026-08-27. */
export async function listerFiph(filtres: FiltresFiph = {}): Promise<FiphDto[]> {
  // `statuts` est envoyé comme une SEULE valeur separee par des virgules plutot que de compter sur
  // la serialisation de tableau d'axios (ambigue selon la version) : Spring lie nativement une
  // chaine unique separee par des virgules a un `List<Enum>` (conversion standard), sans ambiguite.
  const { statuts, ...reste } = filtres;
  return (await httpClient.get<FiphDto[]>("/fiph", {
    params: { ...reste, statuts: statuts && statuts.length > 0 ? statuts.join(",") : undefined },
  })).data;
}
export async function obtenirFiph(id: number): Promise<FiphDto> {
  return (await httpClient.get<FiphDto>(`/fiph/${id}`)).data;
}
/** Création manuelle en lot (évolution du 2026-08-27) — un FiphDto par agent réussi, un motif d'échec par agent refusé. */
export async function creerFiphManuelle(requete: CreerFiphManuelleRequest): Promise<ResultatCreationFiphDto> {
  return (await httpClient.post<ResultatCreationFiphDto>("/fiph/manuelle", requete)).data;
}
/** Personnel d'un service proposé pour la création manuelle d'une FIPH (section 2-3) — périmètre propre à la FIPH, vérifié côté serveur. */
export async function listerPersonnelDuServicePourFiph(serviceId: number): Promise<AgentEligibleDto[]> {
  return (await httpClient.get<AgentEligibleDto[]>(`/fiph/personnel-service/${serviceId}`)).data;
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
/** Definit/modifie la date de fin de la periode (evolution du 2026-08-21) - date de debut jamais modifiable. */
export async function definirDateFinFiph(fiphVersionId: number, requete: DefinirDateFinRequest): Promise<FiphVersionDto> {
  return (await httpClient.put<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/date-fin`, requete)).data;
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
/** Prise en main exceptionnelle (evolution du 2026-08-19) - reservee au Super Administrateur, revalide en base cote serveur quel que soit le role affiche cote client. */
export async function priseEnMainSuperAdminFiph(fiphVersionId: number, requete: PriseEnMainSuperAdminRequest): Promise<FiphVersionDto> {
  return (await httpClient.post<FiphVersionDto>(`/fiph-versions/${fiphVersionId}/prise-en-main-super-admin`, requete)).data;
}
/** Ouvre le PDF de la FIPH - previsualisation a tout statut, document final une fois VALIDEE_DEFINITIVEMENT (evolution du 2026-08-21). */
export async function ouvrirPdfFiphVersion(fiphVersionId: number): Promise<void> {
  const reponse = await httpClient.get(`/fiph-versions/${fiphVersionId}/pdf`, { responseType: "blob" });
  ouvrirBlobPdf(reponse.data as Blob);
}
