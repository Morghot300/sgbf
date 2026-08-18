import type { ReactNode } from "react";
import { extraireMessageErreur } from "../api/httpClient";

/**
 * Enveloppe generique d'un etat `useQuery` : evite de repeter le meme
 * if/else (chargement / erreur / donnees) dans chaque page. `children`
 * (fonction de rendu, passee comme enfant JSX) ne recoit le controle qu'une
 * fois `donnees` non-nul (garde de type).
 */
export function EtatAsync<T>({
  chargement, erreur, donnees, children,
}: {
  chargement: boolean;
  erreur: unknown;
  donnees: T | undefined;
  children: (donnees: T) => ReactNode;
}) {
  if (chargement) {
    return <p className="etat-chargement">Chargement...</p>;
  }
  if (erreur) {
    return <p role="alert">{extraireMessageErreur(erreur, "Une erreur est survenue lors du chargement.")}</p>;
  }
  if (donnees === undefined) {
    return null;
  }
  return <>{children(donnees)}</>;
}
