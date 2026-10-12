import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import {
  listerPersonnesABord, obtenirBonSortie, ouvrirPdfBonSortie, retirerPersonneABord,
  validerBonSortie, viserBonSortie,
} from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutBonSortie } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_MOYEN_UTILISE, LIBELLES_STATUT_BON_SORTIE } from "../../types/bonSortie";
import { CorrectionBonSortie } from "./CorrectionBonSortie";
import { SelectionPersonnesABord } from "./SelectionPersonnesABord";

/** Detail d'un bon de sortie : consultation, visa (titulaire), validation (CA/personne habilitee), personnes a bord, impression. */
export default function BonSortieDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const bonSortieId = Number(id);
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  const [erreurAction, setErreurAction] = useState<string | null>(null);

  const bonSortie = useQuery({ queryKey: ["bon-sortie", bonSortieId], queryFn: () => obtenirBonSortie(bonSortieId) });
  const personnesABord = useQuery({ queryKey: ["personnes-a-bord", bonSortieId], queryFn: () => listerPersonnesABord(bonSortieId) });

  function invalider() {
    void queryClient.invalidateQueries({ queryKey: ["bon-sortie", bonSortieId] });
    void queryClient.invalidateQueries({ queryKey: ["bons-sortie"] });
  }
  function invaliderPersonnesABord() {
    void queryClient.invalidateQueries({ queryKey: ["personnes-a-bord", bonSortieId] });
  }

  const viser = useMutation({
    mutationFn: () => viserBonSortie(bonSortieId),
    onSuccess: invalider,
    onError: (e) => setErreurAction(extraireMessageErreur(e, t("Impossible de viser ce bon de sortie."))),
  });
  const valider = useMutation({
    mutationFn: () => validerBonSortie(bonSortieId),
    onSuccess: invalider,
    onError: (e) => setErreurAction(extraireMessageErreur(e, t("Impossible de valider ce bon de sortie."))),
  });
  const retirer = useMutation({
    mutationFn: (associationId: number) => retirerPersonneABord(bonSortieId, associationId),
    onSuccess: invaliderPersonnesABord,
    onError: (e) => setErreurAction(extraireMessageErreur(e, t("Impossible de retirer cette personne à bord."))),
  });

  const peutValider = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE");

  return (
    <div>
      <h1>{t("Bon de sortie")} #{bonSortieId}</h1>
      <EtatAsync chargement={bonSortie.isLoading} erreur={bonSortie.error} donnees={bonSortie.data}>
        {(bs) => (
          <>
            <table className="fiche">
              <tbody>
                <tr><th>{t("Statut")}</th><td><BadgeStatutBonSortie statut={bs.statut} libelle={t(LIBELLES_STATUT_BON_SORTIE[bs.statut])} /></td></tr>
                <tr><th>{t("Agent")}</th><td>{bs.agentNomComplet} ({bs.agentMatricule})</td></tr>
                <tr><th>{t("Moyen utilisé")}</th><td>{t(LIBELLES_MOYEN_UTILISE[bs.moyenUtilise])}{bs.precisionVehicule ? ` — ${bs.precisionVehicule}` : ""}{bs.vehiculeImmatriculation ? ` — ${bs.vehiculeImmatriculation}` : ""}{bs.lt ? ` (LT ${bs.lt})` : ""}</td></tr>
                <tr><th>{t("Destination")}</th><td>{bs.lieu}</td></tr>
                <tr><th>{t("Motif")}</th><td>{bs.motifSortie}</td></tr>
                <tr><th>{t("Date / heure de sortie")}</th><td>{bs.dateSortie} {t("à")} {bs.heureSortie}</td></tr>
                <tr><th>{t("Heure de retour")}</th><td>{bs.heureRetour ?? t("Non renseignée")}</td></tr>
                <tr><th>{t("Kilométrage")}</th><td>{bs.kilometrage} km</td></tr>
                <tr>
                  <th>{t("Code Mission")}</th>
                  <td>
                    {bs.missionSelectionneeCodeHN
                      ? `${bs.missionSelectionneeCodeHN} — ${bs.missionSelectionneeChantierLibelle}`
                      : t("Non renseigné")}
                  </td>
                </tr>
                <tr><th>{t("Mission résolue (affectation)")}</th><td>{bs.missionCodeHN ?? t("Non résolue")}</td></tr>
                <tr><th>{t("Visa")}</th><td>{bs.viseParIdentifiant ? `${bs.viseParIdentifiant} ${t("le")} ${bs.dateVisa}` : t("Non visé")}</td></tr>
                <tr><th>{t("Validation")}</th><td>{bs.valideParIdentifiant ? `${bs.valideParIdentifiant} ${t("le")} ${bs.dateValidation}` : t("Non validé")}</td></tr>
              </tbody>
            </table>

            {bs.avertissementAffectation && (
              <p role="alert" className="avertissement">{bs.avertissementAffectation}</p>
            )}

            {erreurAction && <p role="alert">{erreurAction}</p>}

            <CorrectionBonSortie bonSortie={bs} onCorrigeAvecSucces={invalider} />

            <div className="barre-actions">
              {bs.statut === "BROUILLON" && (
                <button type="button" onClick={() => viser.mutate()} disabled={viser.isPending}>
                  {viser.isPending ? t("Visa en cours...") : t("Viser (je suis l'agent titulaire)")}
                </button>
              )}
              {bs.statut === "VISE" && peutValider && (
                <button type="button" onClick={() => valider.mutate()} disabled={valider.isPending}>
                  {valider.isPending ? t("Validation en cours...") : t("Valider (niveau 2)")}
                </button>
              )}
              {bs.statut === "VALIDE" && (
                <button type="button" onClick={() => void ouvrirPdfBonSortie(bonSortieId)}>{t("Imprimer (PDF)")}</button>
              )}
            </div>

            {bs.origine === "PRINCIPALE" && (
              <section>
                <h2>{t("Personnes à bord")}</h2>
                <EtatAsync chargement={personnesABord.isLoading} erreur={personnesABord.error} donnees={personnesABord.data}>
                  {(liste) => (
                    liste.length === 0
                      ? <p>{t("Aucune personne à bord renseignée.")}</p>
                      : (
                        <table className="tableau">
                          <thead><tr><th>{t("Agent")}</th><th>{t("Statut")}</th><th>{t("Bon individuel")}</th><th></th></tr></thead>
                          <tbody>
                            {liste.map((p) => (
                              <tr key={p.id}>
                                <td>{p.agentNomComplet} ({p.agentMatricule})</td>
                                <td>{p.statutAssociation === "ACTIVE" ? t("Active") : t("Retirée")}</td>
                                <td>
                                  {p.bonSortieIndividuelId
                                    ? <a href={`/bons-sortie/${p.bonSortieIndividuelId}`}>#{p.bonSortieIndividuelId}</a>
                                    : bs.statut === "VALIDE"
                                      ? t("Non générée — signalé, voir avertissement ci-dessus")
                                      : t("Générée à la validation du bon principal")}
                                </td>
                                <td>
                                  {p.statutAssociation === "ACTIVE" && (
                                    <button type="button" onClick={() => retirer.mutate(p.id)} disabled={retirer.isPending}>
                                      {t("Retirer")}
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      )
                  )}
                </EtatAsync>
                <h3>{t("Ajouter des personnes à bord")}</h3>
                <SelectionPersonnesABord bonSortieId={bonSortieId} onAjoutReussi={invaliderPersonnesABord} />
              </section>
            )}
          </>
        )}
      </EtatAsync>
    </div>
  );
}
