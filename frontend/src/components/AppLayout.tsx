import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import logoSnef from "../assets/brand/logo_snef.png";
import { Icone } from "./Icone";
import NotificationCenter from "./NotificationCenter";
import { LIBELLES_CATEGORIE_FIPH, type CategorieFiph } from "../types/fiph";

const CATEGORIES_FIPH: CategorieFiph[] = [
  "BROUILLONS", "VISEES", "VALIDEES_NIVEAU_2", "VALIDEES_NIVEAU_3", "VALIDEES_DEFINITIVEMENT", "REJETEES",
];

/**
 * Ossature commune a tout ecran authentifie : en-tete (organisation,
 * utilisateur connecte, deconnexion, notifications - INCHANGEE) et menu
 * lateral de navigation (evolution du 2026-08-27, brief "Evolution du module
 * FIPH", section 19-22 - remplace l'ancienne barre horizontale), avec le
 * contenu de la page courante rendu via `<Outlet />` (routes imbriquees, voir
 * `App.tsx`).
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
 */
export default function AppLayout() {
  const { utilisateur, deconnexion, aLeRole } = useAuth();
  const [sidebarOuverte, setSidebarOuverte] = useState(false);

  return (
    <div className="app-shell">
      <header className="app-header">
        <button
          type="button"
          className="app-header__bascule-menu"
          onClick={() => setSidebarOuverte((v) => !v)}
          aria-label={sidebarOuverte ? "Fermer le menu" : "Ouvrir le menu"}
          aria-expanded={sidebarOuverte}
        >
          ☰
        </button>
        <div className="app-header__marque">
          <img src={logoSnef} alt="SNEF" className="logo-lockup logo-lockup--entete" />
          <span>SGBF</span>
        </div>
        <div className="app-header__utilisateur">
          <NotificationCenter />
          <span>
            {utilisateur?.identifiant}
            {utilisateur?.serviceLibelle ? ` — ${utilisateur.serviceLibelle}` : ""}
          </span>
          <button type="button" onClick={() => void deconnexion()}>Se déconnecter</button>
        </div>
      </header>
      <div className="app-body">
        <nav className={`app-sidebar${sidebarOuverte ? " app-sidebar--ouverte" : ""}`}>
          <NavLink to="/" end onClick={() => setSidebarOuverte(false)}>
            <Icone nom="tableauDeBord" /> Tableau de bord
          </NavLink>
          <NavLink to="/bons-sortie" onClick={() => setSidebarOuverte(false)}>
            <Icone nom="document" /> Bons de sortie
          </NavLink>

          <div className="app-sidebar__groupe">
            <NavLink to="/fiph" end onClick={() => setSidebarOuverte(false)}>
              <Icone nom="fiph" /> FIPH
            </NavLink>
            <ul className="app-sidebar__sous-menu">
              {CATEGORIES_FIPH.map((categorie) => (
                <li key={categorie}>
                  <NavLink to={`/fiph?categorie=${categorie}`} onClick={() => setSidebarOuverte(false)}>
                    {LIBELLES_CATEGORIE_FIPH[categorie]}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>

          <NavLink to="/missions" onClick={() => setSidebarOuverte(false)}>
            <Icone nom="mission" /> Missions
          </NavLink>
          {aLeRole("ADMINISTRATEUR") && (
            <NavLink to="/administration" onClick={() => setSidebarOuverte(false)}>
              <Icone nom="administration" /> Administration
            </NavLink>
          )}
        </nav>
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
