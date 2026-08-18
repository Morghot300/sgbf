import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { listerMissions } from "../../api/missionApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutMission } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_STATUT_MISSION } from "../../types/mission";

export default function MissionListePage() {
  const requete = useQuery({ queryKey: ["missions"], queryFn: listerMissions });
  const { aLeRole } = useAuth();

  return (
    <div>
      <div className="page-entete">
        <h1>Missions</h1>
        {(aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE")) && (
          <Link to="/missions/nouvelle" className="bouton-principal">Nouvelle mission</Link>
        )}
      </div>
      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => liste.length === 0 ? <p>Aucune mission.</p> : (
          <table className="tableau">
            <thead><tr><th>Code HN</th><th>Chantier</th><th>Début prévu</th><th>Fin prévue</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              {liste.map((m) => (
                <tr key={m.id}>
                  <td>{m.codeHN}</td>
                  <td>{m.chantierLibelle}</td>
                  <td>{m.dateDebutPrevue}</td>
                  <td>{m.dateFinPrevue}</td>
                  <td><BadgeStatutMission statut={m.statut} libelle={LIBELLES_STATUT_MISSION[m.statut]} /></td>
                  <td><Link to={`/missions/${m.id}`}>Ouvrir</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>
    </div>
  );
}
