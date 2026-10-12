import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router-dom";
import { listerMissions } from "../../api/missionApi";
import { EtatAsync } from "../../components/EtatAsync";
import { Icone } from "../../components/Icone";
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
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const statutUrl = searchParams.get("statut") as StatutMission | null;
  const requete = useQuery({ queryKey: ["missions"], queryFn: listerMissions });
  const { aLeRole } = useAuth();

  return (
    <div>
      <div className="page-entete">
        <h1>{t("Missions")}</h1>
        {(aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE")) && (
          <Link to="/missions/nouvelle" className="bouton-principal"><Icone nom="plus" taille={16} />{t("Nouvelle mission")}</Link>
        )}
      </div>
      {statutUrl && (
        <p className="note">
          {t("Catégorie :")} <strong>{t(LIBELLES_STATUT_MISSION[statutUrl])}</strong> — <Link to="/missions">{t("voir toutes les missions")}</Link>
        </p>
      )}
      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(toutes) => {
          const liste = statutUrl ? toutes.filter((m) => m.statut === statutUrl) : toutes;
          return liste.length === 0 ? <p>{t("Aucune mission")}{statutUrl ? t(" dans cette catégorie") : ""}.</p> : (
            <table className="tableau">
              <thead><tr><th>{t("Code HN")}</th><th>{t("Chantier")}</th><th>{t("Début prévu")}</th><th>{t("Fin prévue")}</th><th>{t("Statut")}</th><th></th></tr></thead>
              <tbody>
                {liste.map((m) => (
                  <tr key={m.id}>
                    <td>{m.codeHN}</td>
                    <td>{m.chantierLibelle}</td>
                    <td>{m.dateDebutPrevue}</td>
                    <td>{m.dateFinPrevue}</td>
                    <td><BadgeStatutMission statut={m.statut} libelle={t(LIBELLES_STATUT_MISSION[m.statut])} /></td>
                    <td><Link to={`/missions/${m.id}`}>{t("Ouvrir")}</Link></td>
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
