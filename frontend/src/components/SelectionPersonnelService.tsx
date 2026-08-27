import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { extraireMessageErreur } from "../api/httpClient";
import type { AgentEligibleDto } from "../types/bonSortie";

/**
 * Sélection du personnel d'un service par cases à cocher, filtrable par nom
 * (évolution du 2026-08-27, brief "Evolution avancée du module Bon de
 * Sortie, Missions et FIPH", section 3-4-30) — utilisée AVANT même la
 * création du document, pour une sélection unique ou multiple parmi le
 * personnel d'un service. Remplace la simple saisie libre d'un identifiant
 * numérique, qui ne permettait ni recherche ni vérification visuelle du nom.
 *
 * <p>Le filtre de recherche n'affecte jamais la sélection déjà faite : une
 * personne cochée reste sélectionnée même si elle disparaît temporairement
 * de la liste filtrée (la sélection est un état séparé de l'affichage).
 *
 * <p>Composant partagé (évolution du 2026-08-27, brief "Evolution du module
 * FIPH", section 2-3-31) : {@link #chargerPersonnel} est injecté par
 * l'appelant plutôt que codé en dur, chaque module (Bon de Sortie, FIPH)
 * ayant son propre périmètre d'accès réel au personnel d'un service (vérifié
 * côté serveur dans les deux cas, jamais seulement ici) — évite de dupliquer
 * ce composant pour une différence qui ne tient qu'à l'endpoint appelé.
 */
export function SelectionPersonnelService({ serviceId, mode, selection, onChange, exclureIds, chargerPersonnel }: {
  serviceId: number | null;
  mode: "unique" | "multiple";
  selection: Set<number>;
  onChange: (selection: Set<number>) => void;
  /** Identifiants à exclure de la liste (ex. la personne principale déjà choisie, pour éviter un doublon avec les personnes à bord). */
  exclureIds?: Set<number>;
  /** Fonction de chargement du personnel d'un service — propre à chaque module appelant (périmètre vérifié côté serveur). */
  chargerPersonnel: (serviceId: number) => Promise<AgentEligibleDto[]>;
}) {
  const [recherche, setRecherche] = useState("");
  // `chargerPersonnel` est volontairement EXCLUE de la clé de cache (react-query) : une fonction
  // passée inline par l'appelant changerait de reference a chaque rendu, invalidant le cache a
  // chaque fois. Le serviceId seul suffit a distinguer les requetes (une page donnee n'utilise
  // jamais deux chargeurs differents pour le meme service).
  const personnel = useQuery({
    queryKey: ["personnel-service", serviceId],
    queryFn: () => chargerPersonnel(serviceId as number),
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
