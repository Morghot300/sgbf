import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import logoSnef from "../assets/brand/logo_snef.png";
import NotificationCenter from "./NotificationCenter";

/**
 * Ossature commune a tout ecran authentifie : en-tete (organisation,
 * utilisateur connecte, deconnexion) et navigation principale, avec le
 * contenu de la page courante rendu via `<Outlet />` (routes imbriquees,
 * voir `App.tsx`).
 *
 * Les liens de navigation sont affiches a tous - c'est le controle serveur
 * (403 sur l'appel API concerne, voir Javadoc de `ProtectedRoute`) qui reste
 * la seule barriere reelle ; masquer un lien n'est qu'un confort d'usage.
 * Seul le lien « Administration » est conditionne a `aLeRole("ADMINISTRATEUR")`,
 * pour eviter d'exposer un module entierement inutilisable a qui n'y a pas acces.
 */
export default function AppLayout() {
  const { utilisateur, deconnexion, aLeRole } = useAuth();

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header__marque">
          <img src={logoSnef} alt="SNEF" className="logo-lockup logo-lockup--entete" />
          <span>SGBF</span>
        </div>
        <nav className="app-nav">
          <NavLink to="/" end>Tableau de bord</NavLink>
          <NavLink to="/bons-sortie">Bons de sortie</NavLink>
          <NavLink to="/fiph">FIPH</NavLink>
          <NavLink to="/missions">Missions</NavLink>
          {aLeRole("ADMINISTRATEUR") && <NavLink to="/administration">Administration</NavLink>}
        </nav>
        <div className="app-header__utilisateur">
          <NotificationCenter />
          <span>
            {utilisateur?.identifiant}
            {utilisateur?.serviceLibelle ? ` — ${utilisateur.serviceLibelle}` : ""}
          </span>
          <button type="button" onClick={() => void deconnexion()}>Se déconnecter</button>
        </div>
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
}
