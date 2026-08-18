import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

/**
 * Empeche l'affichage de son contenu tant que l'utilisateur n'est pas
 * authentifie - redirige vers /login sinon.
 *
 * Purement cosmetique / experience utilisateur : ce garde-fou cote frontend
 * n'est jamais la protection reelle d'une donnee ou d'une action, qui reste
 * entierement revalidee cote serveur a chaque appel API (brief §11 - "les
 * controles de securite doivent etre realises cote backend, et ne doivent
 * jamais reposer uniquement sur le frontend").
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { utilisateur, chargementInitial } = useAuth();

  if (chargementInitial) {
    return <p>Chargement...</p>;
  }
  if (!utilisateur) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

/**
 * Variante de {@link ProtectedRoute} qui exige en plus un role precis
 * (affichage uniquement, voir Javadoc de classe). Utile pour masquer, par
 * exemple, les ecrans d'administration aux utilisateurs qui n'ont aucune
 * chance de pouvoir les utiliser - l'API refusera de toute facon la moindre
 * action si ce garde-fou etait contourne.
 */
export function RouteAvecRole({ role, children }: { role: string; children: ReactNode }) {
  const { aLeRole } = useAuth();
  return aLeRole(role) ? <>{children}</> : <Navigate to="/" replace />;
}
