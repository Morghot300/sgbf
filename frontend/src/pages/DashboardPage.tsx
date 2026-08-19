import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { listerBonsSortie } from "../api/bonSortieApi";
import { listerFiph } from "../api/fiphApi";
import { EtatAsync } from "../components/EtatAsync";
import { useAuth } from "../auth/AuthContext";
import { LIBELLES_STATUT_FIPH, NIVEAU_VALIDATION_ATTENDU } from "../types/fiph";

/**
 * Tableau de bord : vue de synthese apres connexion (section 16 - "point
 * d'entree commun a tous les acteurs"). Filtrage entierement cote client a
 * partir des listes deja soumises au perimetre de l'utilisateur par le
 * backend (`FiphService.listerVisibles`, `BonSortieService.listerVisibles`) -
 * aucune donnee hors perimetre n'est jamais chargee ici.
 */
export default function DashboardPage() {
  const { utilisateur } = useAuth();
  const bonsSortie = useQuery({ queryKey: ["bons-sortie"], queryFn: () => listerBonsSortie() });
  const fiphs = useQuery({ queryKey: ["fiph"], queryFn: () => listerFiph() });

  const brouillons = bonsSortie.data?.filter((bs) => bs.statut === "BROUILLON").length ?? 0;
  const enAttenteVisa = bonsSortie.data?.filter((bs) => bs.statut === "VISE").length ?? 0;

  return (
    <div>
      <h1>Tableau de bord</h1>
      <p className="dashboard-accueil">
        Bienvenue, {utilisateur?.identifiant}
        {utilisateur?.rolesActifs.length ? ` (${utilisateur.rolesActifs.join(", ")})` : ""}.
      </p>

      <section className="dashboard-cartes">
        <Link to="/bons-sortie" className="carte-resume">
          <span className="carte-resume__nombre">{bonsSortie.data?.length ?? "…"}</span>
          <span className="carte-resume__libelle">Bons de sortie ({brouillons} brouillon(s), {enAttenteVisa} en attente de validation)</span>
        </Link>
        <Link to="/fiph" className="carte-resume">
          <span className="carte-resume__nombre">{fiphs.data?.length ?? "…"}</span>
          <span className="carte-resume__libelle">FIPH visibles</span>
        </Link>
      </section>

      <section>
        <h2>FIPH nécessitant une action de votre part</h2>
        <EtatAsync chargement={fiphs.isLoading} erreur={fiphs.error} donnees={fiphs.data}>
          {(liste) => {
            const enAttente = liste.filter((f) => NIVEAU_VALIDATION_ATTENDU[f.statut] !== undefined || f.statut === "BROUILLON" || f.statut === "EN_COMPLEMENT");
            if (enAttente.length === 0) {
              return <p>Aucune FIPH en attente d'action pour le moment.</p>;
            }
            return (
              <table className="tableau">
                <thead>
                  <tr><th>Agent</th><th>Période</th><th>Statut</th><th></th></tr>
                </thead>
                <tbody>
                  {enAttente.slice(0, 10).map((f) => (
                    <tr key={f.id}>
                      <td>{f.agentNomComplet}</td>
                      <td>Semaine {f.numeroSemaine}/{f.annee}</td>
                      <td>{LIBELLES_STATUT_FIPH[f.statut]}</td>
                      <td><Link to={`/fiph/${f.id}`}>Ouvrir</Link></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            );
          }}
        </EtatAsync>
      </section>
    </div>
  );
}
