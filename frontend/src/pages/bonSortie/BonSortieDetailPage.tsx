import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useParams } from "react-router-dom";
import {
  ajouterPersonneABord, listerPersonnesABord, obtenirBonSortie, ouvrirPdfBonSortie,
  validerBonSortie, viserBonSortie,
} from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutBonSortie } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_MOYEN_UTILISE, LIBELLES_STATUT_BON_SORTIE } from "../../types/bonSortie";

/** Detail d'un bon de sortie : consultation, visa (titulaire), validation (CA/personne habilitee), personnes a bord, impression. */
export default function BonSortieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const bonSortieId = Number(id);
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  const [erreurAction, setErreurAction] = useState<string | null>(null);
  const [nouvelAgentId, setNouvelAgentId] = useState("");

  const bonSortie = useQuery({ queryKey: ["bon-sortie", bonSortieId], queryFn: () => obtenirBonSortie(bonSortieId) });
  const personnesABord = useQuery({ queryKey: ["personnes-a-bord", bonSortieId], queryFn: () => listerPersonnesABord(bonSortieId) });

  function invalider() {
    void queryClient.invalidateQueries({ queryKey: ["bon-sortie", bonSortieId] });
    void queryClient.invalidateQueries({ queryKey: ["bons-sortie"] });
  }

  const viser = useMutation({
    mutationFn: () => viserBonSortie(bonSortieId),
    onSuccess: invalider,
    onError: (e) => setErreurAction(extraireMessageErreur(e, "Impossible de viser ce bon de sortie.")),
  });
  const valider = useMutation({
    mutationFn: () => validerBonSortie(bonSortieId),
    onSuccess: invalider,
    onError: (e) => setErreurAction(extraireMessageErreur(e, "Impossible de valider ce bon de sortie.")),
  });
  const ajouterPersonne = useMutation({
    mutationFn: () => ajouterPersonneABord(bonSortieId, { agentId: Number(nouvelAgentId) }),
    onSuccess: () => {
      setNouvelAgentId("");
      void queryClient.invalidateQueries({ queryKey: ["personnes-a-bord", bonSortieId] });
    },
    onError: (e) => setErreurAction(extraireMessageErreur(e, "Impossible d'ajouter cette personne à bord.")),
  });

  const peutValider = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE");

  return (
    <div>
      <h1>Bon de sortie #{bonSortieId}</h1>
      <EtatAsync chargement={bonSortie.isLoading} erreur={bonSortie.error} donnees={bonSortie.data}>
        {(bs) => (
          <>
            <table className="fiche">
              <tbody>
                <tr><th>Statut</th><td><BadgeStatutBonSortie statut={bs.statut} libelle={LIBELLES_STATUT_BON_SORTIE[bs.statut]} /></td></tr>
                <tr><th>Agent</th><td>{bs.agentNomComplet} ({bs.agentMatricule})</td></tr>
                <tr><th>Moyen utilisé</th><td>{LIBELLES_MOYEN_UTILISE[bs.moyenUtilise]}{bs.precisionVehicule ? ` — ${bs.precisionVehicule}` : ""}{bs.vehiculeImmatriculation ? ` — ${bs.vehiculeImmatriculation}` : ""}{bs.lt ? ` (LT ${bs.lt})` : ""}</td></tr>
                <tr><th>Destination</th><td>{bs.lieu}</td></tr>
                <tr><th>Motif</th><td>{bs.motifSortie}</td></tr>
                <tr><th>Date / heure de sortie</th><td>{bs.dateSortie} à {bs.heureSortie}</td></tr>
                <tr><th>Heure de retour</th><td>{bs.heureRetour ?? "Non renseignée"}</td></tr>
                <tr><th>Kilométrage</th><td>{bs.kilometrage} km</td></tr>
                <tr><th>Mission</th><td>{bs.missionCodeHN ?? "Non résolue"}</td></tr>
                <tr><th>Visa</th><td>{bs.viseParIdentifiant ? `${bs.viseParIdentifiant} le ${bs.dateVisa}` : "Non visé"}</td></tr>
                <tr><th>Validation</th><td>{bs.valideParIdentifiant ? `${bs.valideParIdentifiant} le ${bs.dateValidation}` : "Non validé"}</td></tr>
              </tbody>
            </table>

            {erreurAction && <p role="alert">{erreurAction}</p>}

            <div className="barre-actions">
              {bs.statut === "BROUILLON" && (
                <button type="button" onClick={() => viser.mutate()} disabled={viser.isPending}>
                  {viser.isPending ? "Visa en cours..." : "Viser (je suis l'agent titulaire)"}
                </button>
              )}
              {bs.statut === "VISE" && peutValider && (
                <button type="button" onClick={() => valider.mutate()} disabled={valider.isPending}>
                  {valider.isPending ? "Validation en cours..." : "Valider (niveau 2)"}
                </button>
              )}
              {bs.statut === "VALIDE" && (
                <button type="button" onClick={() => void ouvrirPdfBonSortie(bonSortieId)}>Imprimer (PDF)</button>
              )}
            </div>

            {bs.origine === "PRINCIPALE" && (
              <section>
                <h2>Personnes à bord</h2>
                <EtatAsync chargement={personnesABord.isLoading} erreur={personnesABord.error} donnees={personnesABord.data}>
                  {(liste) => (
                    liste.length === 0
                      ? <p>Aucune personne à bord renseignée.</p>
                      : (
                        <table className="tableau">
                          <thead><tr><th>Agent</th><th>Statut</th><th>Bon individuel</th></tr></thead>
                          <tbody>
                            {liste.map((p) => (
                              <tr key={p.id}>
                                <td>{p.agentNomComplet} ({p.agentMatricule})</td>
                                <td>{p.statutAssociation === "ACTIVE" ? "Active" : "Retirée"}</td>
                                <td>{p.bonSortieIndividuelId ? <a href={`/bons-sortie/${p.bonSortieIndividuelId}`}>#{p.bonSortieIndividuelId}</a> : "En cours de génération"}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      )
                  )}
                </EtatAsync>
                <form
                  className="formulaire-ligne"
                  onSubmit={(e) => { e.preventDefault(); ajouterPersonne.mutate(); }}
                >
                  <label htmlFor="agentId">Identifiant de l'agent à ajouter</label>
                  <input id="agentId" type="number" value={nouvelAgentId} onChange={(e) => setNouvelAgentId(e.target.value)} required />
                  <button type="submit" disabled={ajouterPersonne.isPending || !nouvelAgentId}>Ajouter</button>
                </form>
              </section>
            )}
          </>
        )}
      </EtatAsync>
    </div>
  );
}
