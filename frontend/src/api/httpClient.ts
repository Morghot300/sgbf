import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";

/**
 * Client HTTP unique pour tous les appels a l'API SGBF.
 *
 * Deux decisions de securite importantes, en miroir de ce que fait le
 * backend (voir `backend/.../security/AuthController.java`) :
 *
 * 1. Le jeton d'ACCES n'est jamais ecrit dans `localStorage`/`sessionStorage` :
 *    il est conserve uniquement en memoire (variable de module ci-dessous).
 *    Un stockage navigateur persistant serait lisible par n'importe quel
 *    script injecte en cas de faille XSS ; une variable JS ne survit meme
 *    pas a un rechargement de page (le jeton de rafraichissement, lui,
 *    persiste - dans un cookie `HttpOnly` totalement inaccessible au
 *    JavaScript, voir point 2).
 * 2. `withCredentials: true` : indispensable pour que le navigateur envoie
 *    (et accepte) le cookie `HttpOnly` du jeton de rafraichissement pose par
 *    le backend, y compris en developpement ou frontend et backend tournent
 *    sur des origines differentes (:5173 vs :8080).
 */

let jetonAccesCourant: string | null = null;
let expirationJetonAcces: number | null = null; // epoch millisecondes

export function definirJetonAcces(jeton: string | null, expiresInSecondes: number | null): void {
  jetonAccesCourant = jeton;
  expirationJetonAcces = jeton && expiresInSecondes ? Date.now() + expiresInSecondes * 1000 : null;
}

export function getJetonAcces(): string | null {
  return jetonAccesCourant;
}

/** Marge de securite avant expiration reelle, pour rafraichir un peu en avance plutot que d'essuyer un 401. */
const MARGE_EXPIRATION_MS = 10_000;

function jetonBientotExpire(): boolean {
  return expirationJetonAcces === null || Date.now() > expirationJetonAcces - MARGE_EXPIRATION_MS;
}

export const httpClient = axios.create({
  baseURL: "/api",
  withCredentials: true,
});

/**
 * Chemins qui ne doivent JAMAIS recevoir l'en-tete Authorization : `/auth/login`
 * (aucun jeton n'existe encore a cet instant) et `/auth/refresh` (s'appuie sur
 * le cookie de rafraichissement, pas sur le jeton d'acces courant, potentiellement
 * deja expire - c'est justement pourquoi on l'appelle).
 *
 * Bug corrige ici (2026-08-18) : l'ancienne condition excluait tout chemin
 * commencant par "/auth/", ce qui empechait a tort l'envoi du jeton sur
 * `/auth/me` (l'appel echouait donc systematiquement en 403 juste apres une
 * connexion reussie - jamais repere plus tot faute d'avoir pu mener un test
 * de connexion interactif complet dans un navigateur) et sur `/auth/logout`
 * (qui ne pouvait alors jamais identifier l'utilisateur a deconnecter cote
 * serveur, silencieusement).
 */
const CHEMINS_SANS_JETON = ["/auth/login", "/auth/refresh"];

// Attache automatiquement l'en-tete Authorization sur chaque requete sortante,
// sauf sur les deux routes ci-dessus qui n'en ont pas besoin.
httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (jetonAccesCourant && !CHEMINS_SANS_JETON.some((chemin) => config.url?.startsWith(chemin))) {
    config.headers.Authorization = `Bearer ${jetonAccesCourant}`;
  }
  return config;
});

/**
 * Rafraichissement automatique du jeton d'acces sur reponse 403.
 *
 * <p><strong>403, pas 401</strong> (bug reel corrige le 2026-08-26, decouvert
 * en verification live d'un simple rechargement de page) : ce backend ne
 * renvoie JAMAIS 401 pour une ressource protegee, ni pour l'absence de
 * jeton, ni pour un jeton expire - uniquement pour un ECHEC DE CONNEXION
 * (identifiants incorrects/compte desactive, voir {@code GlobalExceptionHandler}).
 * Toute autre absence d'autorisation valide (aucun jeton, jeton expire,
 * habilitation insuffisante) se traduit uniformement par {@code 403}
 * (choix explicite anti-enumeration/anti-IDOR, {@code AccessDeniedException} ->
 * {@code HttpStatus.FORBIDDEN}). En verifiant `=== 401`, ce rafraichissement
 * automatique ne se declenchait donc jamais en pratique : un jeton d'acces
 * expire (ou un simple rechargement de page, qui perd le jeton conserve
 * uniquement en memoire) laissait l'utilisateur bloque sur la page de
 * connexion malgre un cookie de rafraichissement pourtant toujours valide.
 *
 * <p>Une seule tentative de rafraichissement a la fois est autorisee (variable
 * `rafraichissementEnCours` partagee par toutes les requetes qui echouent en
 * meme temps) : sans cette mise en file, plusieurs appels API simultanement
 * en echec declencheraient chacun leur propre appel `/auth/refresh`, et
 * comme celui-ci fait tourner le jeton de rafraichissement (voir
 * `RefreshTokenService`, rotation a usage unique), seul le premier
 * reussirait - les suivants echoueraient a tort. Un 403 legitime (role/
 * perimetre insuffisant, jeton pourtant valide) declenche certes une tentative
 * de rafraichissement inutile, mais sans consequence : le nouveau jeton ne
 * change rien aux habilitations, la requete rejouee echoue de nouveau avec
 * le meme 403, seulement retardee d'un aller-retour reseau.
 */
let rafraichissementEnCours: Promise<string | null> | null = null;

async function rafraichirJeton(): Promise<string | null> {
  if (!rafraichissementEnCours) {
    rafraichissementEnCours = httpClient
      .post<{ jetonAcces: string; expiresInSecondes: number }>("/auth/refresh")
      .then((reponse) => {
        definirJetonAcces(reponse.data.jetonAcces, reponse.data.expiresInSecondes);
        return reponse.data.jetonAcces;
      })
      .catch(() => {
        definirJetonAcces(null, null);
        return null;
      })
      .finally(() => {
        rafraichissementEnCours = null;
      });
  }
  return rafraichissementEnCours;
}

httpClient.interceptors.response.use(
  (reponse) => reponse,
  async (erreur: AxiosError) => {
    const requeteOriginale = erreur.config as (InternalAxiosRequestConfig & { _dejaRetentee?: boolean }) | undefined;

    // Bug reel corrige le 2026-08-26 (avec le passage a 403 ci-dessus) : cette garde ne doit exclure
    // du rafraichissement que login/refresh eux-memes (boucle infinie sinon) - PAS "/auth/me", qui
    // commence pourtant aussi par "/auth/" et etait donc, par erreur, exclu de tout rafraichissement
    // automatique alors que c'est justement l'appel qui en a le plus besoin (restauration silencieuse
    // de la session au demarrage de l'application). Reutilise CHEMINS_SANS_JETON : les deux exclusions
    // (jamais de jeton pose sur la requete sortante / jamais de rafraichissement tente sur l'echec)
    // visent exactement les 2 memes chemins, pour la meme raison.
    const estAppelLoginOuRefresh = CHEMINS_SANS_JETON.some((chemin) => requeteOriginale?.url?.startsWith(chemin));
    if (erreur.response?.status === 403 && requeteOriginale && !requeteOriginale._dejaRetentee && !estAppelLoginOuRefresh) {
      requeteOriginale._dejaRetentee = true;
      const nouveauJeton = await rafraichirJeton();
      if (nouveauJeton) {
        requeteOriginale.headers.Authorization = `Bearer ${nouveauJeton}`;
        return httpClient(requeteOriginale);
      }
    }
    return Promise.reject(erreur);
  },
);

/** A appeler proactivement (ex. au montage de l'app) si le jeton courant approche de son expiration. */
export async function assurerJetonValide(): Promise<void> {
  if (jetonAccesCourant && jetonBientotExpire()) {
    await rafraichirJeton();
  }
}

/**
 * Extrait un message d'erreur affichable a l'utilisateur a partir d'une
 * erreur Axios/API.
 *
 * Le backend renvoie systematiquement un corps RFC 7807 (`ProblemDetail`,
 * voir `GlobalExceptionHandler`) dont le champ `detail` est deja redige pour
 * un utilisateur final - jamais un detail technique. Cette fonction le
 * reprend tel quel plutot que de le remplacer par un message generique
 * potentiellement trompeur (ex. afficher "mot de passe incorrect" pour une
 * erreur qui n'a rien a voir avec les identifiants).
 */
export function extraireMessageErreur(erreur: unknown, messageParDefaut: string): string {
  if (axios.isAxiosError(erreur)) {
    const detail = (erreur.response?.data as { detail?: string } | undefined)?.detail;
    if (detail) {
      return detail;
    }
  }
  return messageParDefaut;
}
