import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation();
  const bonsSortie = useQuery({ queryKey: ["bons-sortie"], queryFn: () => listerBonsSortie() });
  const fiphs = useQuery({ queryKey: ["fiph"], queryFn: () => listerFiph() });

  const brouillons = bonsSortie.data?.filter((bs) => bs.statut === "BROUILLON").length ?? 0;
  const enAttenteVisa = bonsSortie.data?.filter((bs) => bs.statut === "VISE").length ?? 0;

  return (
    <div>
      <h1>{t("Tableau de bord")}</h1>
      <p className="dashboard-accueil">
        {t("Bienvenue,")} {utilisateur?.identifiant}
        {utilisateur?.rolesActifs.length ? ` (${utilisateur.rolesActifs.join(", ")})` : ""}.
      </p>

      <section className="dashboard-cartes">
        <Link to="/bons-sortie" className="carte-resume">
          <span className="carte-resume__nombre">{bonsSortie.data?.length ?? "…"}</span>
          <span className="carte-resume__libelle">
            {t("Bons de sortie")} ({brouillons} {t("brouillon(s),")} {enAttenteVisa} {t("en attente de validation")})
          </span>
        </Link>
        <Link to="/fiph" className="carte-resume">
          <span className="carte-resume__nombre">{fiphs.data?.length ?? "…"}</span>
          <span className="carte-resume__libelle">{t("FIPH visibles")}</span>
        </Link>
      </section>

      <section>
        <h2>{t("FIPH nécessitant une action de votre part")}</h2>
        <EtatAsync chargement={fiphs.isLoading} erreur={fiphs.error} donnees={fiphs.data}>
          {(liste) => {
            const enAttente = liste.filter((f) => NIVEAU_VALIDATION_ATTENDU[f.statut] !== undefined || f.statut === "BROUILLON" || f.statut === "EN_COMPLEMENT");
            if (enAttente.length === 0) {
              return <p>{t("Aucune FIPH en attente d'action pour le moment.")}</p>;
            }
            return (
              <table className="tableau">
                <thead>
                  <tr><th>{t("Agent")}</th><th>{t("Période")}</th><th>{t("Statut")}</th><th></th></tr>
                </thead>
                <tbody>
                  {enAttente.slice(0, 10).map((f) => (
                    <tr key={f.id}>
                      <td>{f.agentNomComplet}</td>
                      <td>{t("Semaine")} {f.numeroSemaine}/{f.annee}</td>
                      <td>{t(LIBELLES_STATUT_FIPH[f.statut])}</td>
                      <td><Link to={`/fiph/${f.id}`}>{t("Ouvrir")}</Link></td>
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
