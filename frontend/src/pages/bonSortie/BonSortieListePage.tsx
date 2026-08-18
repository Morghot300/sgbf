import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { listerBonsSortie } from "../../api/bonSortieApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutBonSortie } from "../../components/StatutBadge";
import { LIBELLES_MOYEN_UTILISE, LIBELLES_STATUT_BON_SORTIE } from "../../types/bonSortie";

/** Liste des bons de sortie visibles par l'utilisateur courant (perimetre applique cote serveur). */
export default function BonSortieListePage() {
  const requete = useQuery({ queryKey: ["bons-sortie"], queryFn: listerBonsSortie });

  return (
    <div>
      <div className="page-entete">
        <h1>Bons de sortie</h1>
        <Link to="/bons-sortie/nouveau" className="bouton-principal">Nouveau bon de sortie</Link>
      </div>

      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => {
          if (liste.length === 0) {
            return <p>Aucun bon de sortie visible pour le moment.</p>;
          }
          const parDateDecroissante = [...liste].sort((a, b) => b.dateSortie.localeCompare(a.dateSortie));
          return (
            <table className="tableau">
              <thead>
                <tr>
                  <th>Référence</th><th>Agent</th><th>Date</th><th>Moyen</th><th>Motif</th><th>Statut</th><th></th>
                </tr>
              </thead>
              <tbody>
                {parDateDecroissante.map((bs) => (
                  <tr key={bs.id}>
                    <td>#{bs.id}</td>
                    <td>{bs.agentNomComplet}</td>
                    <td>{bs.dateSortie}</td>
                    <td>{LIBELLES_MOYEN_UTILISE[bs.moyenUtilise]}</td>
                    <td>{bs.motifSortie}</td>
                    <td><BadgeStatutBonSortie statut={bs.statut} libelle={LIBELLES_STATUT_BON_SORTIE[bs.statut]} /></td>
                    <td><Link to={`/bons-sortie/${bs.id}`}>Ouvrir</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          );
        }}
      </EtatAsync>
    </div>
  );
}
