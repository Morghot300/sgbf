import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { listerPersonnelDuService } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";

/**
 * Sélection du personnel d'un service par cases à cocher, filtrable par nom
 * (évolution du 2026-08-27, brief "Evolution avancée du module Bon de
 * Sortie, Missions et FIPH", section 3-4-30) — utilisée AVANT même la
 * création du bon de sortie, pour la "Personne principale" (sélection
 * unique) et les "Personnes à bord" (sélection multiple). Remplace la simple
 * saisie libre d'un identifiant numérique, qui ne permettait ni recherche ni
 * vérification visuelle du nom.
 *
 * <p>Le filtre de recherche n'affecte jamais la sélection déjà faite : une
 * personne cochée reste sélectionnée même si elle disparaît temporairement
 * de la liste filtrée (la sélection est un état séparé de l'affichage).
 */
export function SelectionPersonnelService({ serviceId, mode, selection, onChange, exclureIds }: {
  serviceId: number | null;
  mode: "unique" | "multiple";
  selection: Set<number>;
  onChange: (selection: Set<number>) => void;
  /** Identifiants à exclure de la liste (ex. la personne principale déjà choisie, pour éviter un doublon avec les personnes à bord). */
  exclureIds?: Set<number>;
}) {
  const [recherche, setRecherche] = useState("");
  const personnel = useQuery({
    queryKey: ["personnel-service", serviceId],
    queryFn: () => listerPersonnelDuService(serviceId as number),
    enabled: serviceId !== null,
  });

  const filtres = useMemo(() => {
    const liste = (personnel.data ?? []).filter((p) => !exclureIds?.has(p.id));
    const terme = recherche.trim().toLowerCase();
    if (!terme) return liste;
    return liste.filter((p) =>
      p.nomComplet.toLowerCase().includes(terme) || (p.matricule ?? "").toLowerCase().includes(terme));
  }, [personnel.data, exclureIds, recherche]);

  function basculer(agentId: number) {
    const suivant = new Set(mode === "unique" ? [] : selection);
    if (selection.has(agentId)) {
      if (mode === "multiple") suivant.delete(agentId);
      // En mode "unique", recocher la même personne ne fait rien de spécial : suivant reste vide (décoché).
    } else {
      suivant.add(agentId);
    }
    onChange(suivant);
  }

  if (serviceId === null) {
    return <p className="dashboard-accueil">Sélectionnez d'abord un service pour afficher son personnel.</p>;
  }
  if (personnel.isLoading) {
    return <p>Chargement du personnel du service...</p>;
  }
  if (personnel.error) {
    return (
      <div role="alert">
        <p>{extraireMessageErreur(personnel.error, "Impossible de charger le personnel de ce service.")}</p>
        <button type="button" onClick={() => void personnel.refetch()}>Réessayer</button>
      </div>
    );
  }
  if ((personnel.data ?? []).length === 0) {
    return <p>Aucune personne trouvée dans ce service.</p>;
  }

  return (
    <div className="selection-personnes-a-bord">
      <label htmlFor={`recherche-personnel-${serviceId}`}>Rechercher une personne (nom, matricule)</label>
      <input
        id={`recherche-personnel-${serviceId}`}
        type="text"
        value={recherche}
        onChange={(e) => setRecherche(e.target.value)}
        placeholder="Rechercher une personne..."
      />
      {mode === "multiple" && <p className="dashboard-accueil">{selection.size} personne(s) sélectionnée(s)</p>}
      <table className="tableau tableau--compact">
        <thead>
          <tr><th></th><th>Nom</th><th>Matricule</th><th>Statut</th></tr>
        </thead>
        <tbody>
          {filtres.length === 0 && (
            <tr><td colSpan={4}>Aucune personne ne correspond à cette recherche.</td></tr>
          )}
          {filtres.map((p) => (
            <tr key={p.id}>
              <td>
                <input
                  type="checkbox"
                  id={`personnel-${mode}-${p.id}`}
                  checked={selection.has(p.id)}
                  onChange={() => basculer(p.id)}
                  aria-label={`Sélectionner ${p.nomComplet}`}
                />
              </td>
              <td><label htmlFor={`personnel-${mode}-${p.id}`}>{p.nomComplet}</label></td>
              <td>{p.matricule ?? "—"}</td>
              <td>{p.statutCompte !== "ACTIF" && <span>Compte {p.statutCompte.toLowerCase()}</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
