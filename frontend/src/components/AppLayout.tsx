import { useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import logoSnef from "../assets/brand/logo_snef.png";
import { Icone } from "./Icone";
import { MenuTheme } from "./MenuTheme";
import NotificationCenter from "./NotificationCenter";
import { SelecteurLangue } from "./SelecteurLangue";
import { LIBELLES_STATUT_BON_SORTIE, type StatutBonSortie } from "../types/bonSortie";
import { LIBELLES_CATEGORIE_FIPH, type CategorieFiph } from "../types/fiph";
import { LIBELLES_STATUT_MISSION, type StatutMission } from "../types/mission";

const CATEGORIES_FIPH: CategorieFiph[] = [
  "BROUILLONS", "VISEES", "VALIDEES_NIVEAU_2", "VALIDEES_NIVEAU_3", "VALIDEES_DEFINITIVEMENT", "REJETEES",
];
const STATUTS_BON_SORTIE: StatutBonSortie[] = ["BROUILLON", "VISE", "VALIDE"];
const STATUTS_MISSION: StatutMission[] = ["PLANIFIEE", "EN_COURS", "INTERROMPUE", "TERMINEE"];

/**
 * Ossature commune a tout ecran authentifie (evolution du 2026-10-12,
 * inspiree de l'application SNEF "Gestion Administrative" fournie en
 * reference visuelle - sidebar pleine hauteur avec le lockup de marque en
 * tete, barre superieure allegee scopee au contenu plutot qu'un bandeau
 * pleine largeur) : menu lateral de navigation + barre de contenu (theme,
 * langue, notifications, utilisateur), avec le contenu de la page courante
 * rendu via `<Outlet />` (routes imbriquees, voir `App.tsx`).
 *
 * <p>Les liens de navigation sont affiches a tous - c'est le controle serveur
 * (403 sur l'appel API concerne, voir Javadoc de `ProtectedRoute`) qui reste
 * la seule barriere reelle ; masquer un lien n'est qu'un confort d'usage.
 * Seul le lien « Administration » est conditionne a `aLeRole("ADMINISTRATEUR")`,
 * pour eviter d'exposer un module entierement inutilisable a qui n'y a pas acces.
 *
 * <p>Menu volontairement limite aux modules REELLEMENT presents dans
 * l'application (section 19 : "adapter les menus aux fonctionnalites
 * reellement presentes") - pas de rubriques "Agents"/"Services"/"Rapports"
 * suggerees par l'exemple du document source mais sans route/page reelle.
 *
 * <p>Sur petit ecran (section 22), le menu lateral se replie derriere un
 * bouton bascule plutot que de disparaitre : {@code sidebarOuverte} pilote
 * une classe CSS, jamais un demontage du DOM (les liens restent atteignables
 * au clavier/lecteur d'ecran meme replies).
 *
 * <p><strong>Portee de la traduction (section langue, evolution du
 * 2026-10-12)</strong> : seuls les libelles de CETTE ossature sont traduits
 * (voir {@code i18n.ts}) - les pages metier (Bons de sortie, FIPH,
 * Missions, Administration) restent en francais quelle que soit la langue
 * choisie, a traduire dans un increment ulterieur.
 */
export default function AppLayout() {
  const { utilisateur, deconnexion, aLeRole } = useAuth();
  const { t } = useTranslation();
  const [sidebarOuverte, setSidebarOuverte] = useState(false);

  return (
    <div className="app-shell">
      <nav className={`app-sidebar${sidebarOuverte ? " app-sidebar--ouverte" : ""}`}>
        <div className="app-sidebar__marque">
          <img src={logoSnef} alt="SNEF" className="logo-lockup logo-lockup--sidebar" />
          <span>SGBF</span>
        </div>

        <NavLink to="/" end onClick={() => setSidebarOuverte(false)}>
          <Icone nom="tableauDeBord" /> {t("Tableau de bord")}
        </NavLink>

        <div className="app-sidebar__groupe">
          <NavLink to="/bons-sortie" onClick={() => setSidebarOuverte(false)}>
            <Icone nom="document" /> {t("Bons de sortie")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {STATUTS_BON_SORTIE.map((statut) => (
              <li key={statut}>
                <NavLink to={`/bons-sortie?statut=${statut}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_STATUT_BON_SORTIE[statut])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="app-sidebar__groupe">
          <NavLink to="/fiph" end onClick={() => setSidebarOuverte(false)}>
            <Icone nom="fiph" /> {t("FIPH")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {CATEGORIES_FIPH.map((categorie) => (
              <li key={categorie}>
                <NavLink to={`/fiph?categorie=${categorie}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_CATEGORIE_FIPH[categorie])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="app-sidebar__groupe">
          <NavLink to="/missions" onClick={() => setSidebarOuverte(false)}>
            <Icone nom="mission" /> {t("Missions")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {STATUTS_MISSION.map((statut) => (
              <li key={statut}>
                <NavLink to={`/missions?statut=${statut}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_STATUT_MISSION[statut])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        {aLeRole("ADMINISTRATEUR") && (
          <div className="app-sidebar__groupe">
            <NavLink to="/administration" end onClick={() => setSidebarOuverte(false)}>
              <Icone nom="administration" /> {t("Administration")}
            </NavLink>
            <ul className="app-sidebar__sous-menu">
              <li><NavLink to="/administration" end onClick={() => setSidebarOuverte(false)}>{t("Personnel et habilitations")}</NavLink></li>
              <li><NavLink to="/administration/referentiels" onClick={() => setSidebarOuverte(false)}>{t("Référentiels")}</NavLink></li>
            </ul>
          </div>
        )}
      </nav>

      <div className="app-body">
        <header className="app-topbar">
          <button
            type="button"
            className="app-topbar__bascule-menu"
            onClick={() => setSidebarOuverte((v) => !v)}
            aria-label={sidebarOuverte ? t("Fermer le menu") : t("Ouvrir le menu")}
            aria-expanded={sidebarOuverte}
          >
            <Icone nom="menu" />
          </button>
          <div className="app-topbar__espace" />
          <SelecteurLangue />
          <MenuTheme />
          <NotificationCenter />
          <div className="app-topbar__utilisateur">
            <span className="app-topbar__utilisateur-nom">{utilisateur?.identifiant}</span>
            {utilisateur?.serviceLibelle && <span className="app-topbar__utilisateur-service">{utilisateur.serviceLibelle}</span>}
          </div>
          <button type="button" className="app-topbar__deconnexion" onClick={() => void deconnexion()} title={t("Se déconnecter")}>
            <Icone nom="deconnexion" />
          </button>
        </header>
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
