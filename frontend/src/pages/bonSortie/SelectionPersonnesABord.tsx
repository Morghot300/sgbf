import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ajouterPersonnesABordEnLot, listerAgentsEligibles } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";

/**
 * Sélection multiple d'agents du service du titulaire, à ajouter en une
 * seule fois comme personnes à bord (évolution du 2026-08-19, Lot 4) -
 * remplace l'ancienne saisie d'un identifiant numérique opaque. Le périmètre
 * (service du bon) est entièrement calculé côté serveur ; ce composant ne
 * fait qu'afficher et sélectionner parmi ce que le serveur a déjà filtré.
 */
export function SelectionPersonnesABord({ bonSortieId, onAjoutReussi }: {
  bonSortieId: number;
  onAjoutReussi: () => void;
}) {
  const queryClient = useQueryClient();
  const [recherche, setRecherche] = useState("");
  const [selection, setSelection] = useState<Set<number>>(new Set());
  const [erreur, setErreur] = useState<string | null>(null);

  const agents = useQuery({
    queryKey: ["agents-eligibles", bonSortieId],
    queryFn: () => listerAgentsEligibles(bonSortieId),
  });

  const filtres = useMemo(() => {
    const liste = agents.data ?? [];
    const terme = recherche.trim().toLowerCase();
    if (!terme) return liste;
    return liste.filter((a) =>
      a.nomComplet.toLowerCase().includes(terme) || (a.matricule ?? "").toLowerCase().includes(terme));
  }, [agents.data, recherche]);

  const ajout = useMutation({
    mutationFn: () => ajouterPersonnesABordEnLot(bonSortieId, { agentIds: [...selection] }),
    onSuccess: () => {
      setSelection(new Set());
      void queryClient.invalidateQueries({ queryKey: ["personnes-a-bord", bonSortieId] });
      void queryClient.invalidateQueries({ queryKey: ["agents-eligibles", bonSortieId] });
      onAjoutReussi();
    },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible d'ajouter les personnes sélectionnées.")),
  });

  function basculer(agentId: number) {
    setSelection((precedent) => {
      const suivant = new Set(precedent);
      if (suivant.has(agentId)) {
        suivant.delete(agentId);
      } else {
        suivant.add(agentId);
      }
      return suivant;
    });
  }

  const tousSelectionnes = filtres.length > 0 && filtres.every((a) => selection.has(a.id));
  function basculerTout() {
    setSelection((precedent) => {
      const suivant = new Set(precedent);
      if (tousSelectionnes) {
        filtres.forEach((a) => suivant.delete(a.id));
      } else {
        filtres.forEach((a) => suivant.add(a.id));
      }
      return suivant;
    });
  }

  if (agents.isLoading) {
    return <p>Chargement du personnel du service...</p>;
  }
  if (agents.error) {
    return (
      <div role="alert">
        <p>{extraireMessageErreur(agents.error, "Impossible de charger le personnel éligible.")}</p>
        <button type="button" onClick={() => void agents.refetch()}>Réessayer</button>
      </div>
    );
  }
  if ((agents.data ?? []).length === 0) {
    return <p>Aucune personne du service n'est disponible à ajouter (déjà à bord, ou aucun autre membre du service).</p>;
  }

  return (
    <div className="selection-personnes-a-bord">
      {erreur && <p role="alert">{erreur}</p>}
      <label htmlFor="recherche-agents-eligibles">Rechercher (nom, matricule)</label>
      <input
        id="recherche-agents-eligibles"
        type="text"
        value={recherche}
        onChange={(e) => setRecherche(e.target.value)}
        aria-label="Rechercher une personne par nom ou matricule"
      />
      <table className="tableau tableau--compact">
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                checked={tousSelectionnes}
                onChange={basculerTout}
                aria-label="Tout sélectionner"
                disabled={filtres.length === 0}
              />
            </th>
            <th>Nom</th>
            <th>Matricule</th>
            <th>Service</th>
            <th>Statut</th>
          </tr>
        </thead>
        <tbody>
          {filtres.length === 0 && (
            <tr><td colSpan={5}>Aucune personne ne correspond à cette recherche.</td></tr>
          )}
          {filtres.map((a) => (
            <tr key={a.id}>
              <td>
                <input
                  type="checkbox"
                  id={`agent-eligible-${a.id}`}
                  checked={selection.has(a.id)}
                  onChange={() => basculer(a.id)}
                  aria-label={`Sélectionner ${a.nomComplet}`}
                />
              </td>
              <td><label htmlFor={`agent-eligible-${a.id}`}>{a.nomComplet}</label></td>
              <td>{a.matricule ?? "—"}</td>
              <td>{a.serviceLibelle ?? "—"}</td>
              <td>
                {a.statutCompte !== "ACTIF" && <span> (compte {a.statutCompte.toLowerCase()})</span>}
                {a.dejaAffecteMemeCreneau && <span> — déjà à bord d'un autre bon ce jour-là</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="formulaire-ligne">
        <span>{selection.size} personne(s) sélectionnée(s)</span>
        <button
          type="button"
          onClick={() => ajout.mutate()}
          disabled={selection.size === 0 || ajout.isPending}
        >
          {ajout.isPending ? "Ajout en cours..." : "Ajouter la sélection"}
        </button>
      </div>
    </div>
  );
}
