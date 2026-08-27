import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { listerMissions } from "../../api/missionApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutMission } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_STATUT_MISSION, type StatutMission } from "../../types/mission";

/**
 * Liste des missions. Le parametre d'URL {@code statut} (evolution du
 * 2026-08-27, sous-menus du menu lateral) filtre par statut - filtrage cote
 * client, la liste complete etant deja chargee en une seule fois (pas de
 * pagination sur cet ecran).
 */
export default function MissionListePage() {
  const [searchParams] = useSearchParams();
  const statutUrl = searchParams.get("statut") as StatutMission | null;
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
      {statutUrl && (
        <p className="note">
          Catégorie : <strong>{LIBELLES_STATUT_MISSION[statutUrl]}</strong> — <Link to="/missions">voir toutes les missions</Link>
        </p>
      )}
      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(toutes) => {
          const liste = statutUrl ? toutes.filter((m) => m.statut === statutUrl) : toutes;
          return liste.length === 0 ? <p>Aucune mission{statutUrl ? " dans cette catégorie" : ""}.</p> : (
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
          );
        }}
      </EtatAsync>
    </div>
  );
}
