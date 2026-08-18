import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { recupererUtilisateurCourant, seConnecter, seDeconnecter } from "../api/authApi";
import { definirJetonAcces } from "../api/httpClient";
import type { UtilisateurCourant } from "../types/auth";

/**
 * Etat d'authentification global de l'application, expose via React Context.
 *
 * Depuis le 2026-08-17, la connexion est simple : identifiant (ou e-mail) et
 * mot de passe corrects suffisent, en un seul appel, a obtenir un jeton
 * d'acces et un utilisateur authentifie - aucune seconde etape n'existe plus
 * (voir `backend/.../security/AuthController.java`).
 */

interface AuthContextValue {
  utilisateur: UtilisateurCourant | null;
  chargementInitial: boolean;
  connexion: (identifiant: string, motDePasse: string) => Promise<void>;
  deconnexion: () => Promise<void>;
  /** Vrai si l'utilisateur authentifie porte le role donne (ex. "ADMINISTRATEUR") - affichage uniquement, jamais une autorisation en soi. */
  aLeRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [utilisateur, setUtilisateur] = useState<UtilisateurCourant | null>(null);
  const [chargementInitial, setChargementInitial] = useState(true);

  // Au montage de l'application, tente silencieusement un rafraichissement :
  // si un cookie de rafraichissement valide existe encore (session precedente
  // non expiree), l'utilisateur retrouve sa session sans avoir a se reconnecter.
  useEffect(() => {
    let annule = false;
    (async () => {
      try {
        const utilisateurCourant = await recupererUtilisateurCourant();
        if (!annule) {
          setUtilisateur(utilisateurCourant);
        }
      } catch {
        // Aucune session valide - c'est le cas normal d'un premier acces, pas une erreur a signaler.
      } finally {
        if (!annule) {
          setChargementInitial(false);
        }
      }
    })();
    return () => {
      annule = true;
    };
  }, []);

  const connexion = useCallback(async (identifiant: string, motDePasse: string) => {
    const jetons = await seConnecter(identifiant, motDePasse);
    definirJetonAcces(jetons.jetonAcces, jetons.expiresInSecondes);
    setUtilisateur(await recupererUtilisateurCourant());
  }, []);

  const deconnexion = useCallback(async () => {
    try {
      await seDeconnecter();
    } finally {
      // Le nettoyage local a lieu meme si l'appel serveur echoue (ex. reseau
      // coupe) : l'utilisateur ne doit jamais rester bloque "connecte" cote
      // interface alors que la session serveur, elle, est peut-etre deja perdue.
      definirJetonAcces(null, null);
      setUtilisateur(null);
    }
  }, []);

  const aLeRole = useCallback(
    (role: string) => utilisateur?.rolesActifs.includes(role) ?? false,
    [utilisateur],
  );

  const valeur = useMemo(
    () => ({
      utilisateur,
      chargementInitial,
      connexion,
      deconnexion,
      aLeRole,
    }),
    [utilisateur, chargementInitial, connexion, deconnexion, aLeRole],
  );

  return <AuthContext.Provider value={valeur}>{children}</AuthContext.Provider>;
}

/** Hook d'acces au contexte d'authentification - leve une erreur explicite si utilise hors de {@link AuthProvider}. */
export function useAuth(): AuthContextValue {
  const contexte = useContext(AuthContext);
  if (!contexte) {
    throw new Error("useAuth doit etre utilise a l'interieur d'un <AuthProvider>.");
  }
  return contexte;
}
