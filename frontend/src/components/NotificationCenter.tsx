import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { compterNotificationsNonLues, listerNotifications, marquerNotificationCommeLue } from "../api/notificationApi";
import type { NotificationDto } from "../types/notification";
import { useNotificationStream } from "./useNotificationStream";

/**
 * Filet de securite en cas de flux SSE coupe (reseau, proxy intermediaire,
 * etc.) - le mecanisme principal est desormais le flux temps reel
 * ({@link useNotificationStream}, evolution du 2026-08-21, section 9-11),
 * jamais un simple rechargement manuel. Un intervalle large (2 min) suffit
 * a une reconciliation de secours, la reactivite reelle venant du SSE.
 */
const INTERVALLE_SECOURS_MS = 120_000;
/** Duree d'affichage d'un popup avant disparition automatique. */
const DUREE_TOAST_MS = 8_000;

/**
 * Centre de notifications (evolution du 2026-08-19, section 7 ; temps reel
 * evolution du 2026-08-21, section 9-11) : cloche dans l'en-tete avec
 * compteur de non-lues, panneau deroulant listant les notifications du
 * compte connecte (jamais celles d'un autre utilisateur - la liste vient de
 * `GET /api/notifications`, resolue cote serveur a partir du jeton, sans
 * aucun parametre d'identifiant possible cote client), et desormais un
 * popup temps reel (SSE) a l'arrivee de chaque nouvelle notification.
 *
 * Cliquer sur une notification (dans le panneau ou dans un popup) la marque
 * lue puis ouvre directement la FIPH ou le bon de sortie concerne
 * (`notification.lien`), conformement a l'exigence "acces direct a l'objet
 * concerne" de la mission - jamais un identifiant fourni par le popup
 * lui-meme : c'est le meme flux de navigation, revalide cote serveur a
 * l'ouverture de la page cible, qui s'applique dans les deux cas.
 */
export default function NotificationCenter() {
  const [ouvert, setOuvert] = useState(false);
  const [toasts, setToasts] = useState<NotificationDto[]>([]);
  const conteneurRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const compte = useQuery({
    queryKey: ["notifications", "compte"],
    queryFn: compterNotificationsNonLues,
    refetchInterval: INTERVALLE_SECOURS_MS,
  });
  const notifications = useQuery({
    queryKey: ["notifications", "liste"],
    queryFn: listerNotifications,
    // Ne charge la liste complete que si le panneau est ouvert - le compteur seul suffit tant qu'il est ferme.
    enabled: ouvert,
    refetchInterval: ouvert ? INTERVALLE_SECOURS_MS : false,
  });

  useNotificationStream((notification) => {
    void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    invaliderEcransConcernes(notification);
    setToasts((liste) => [...liste, notification]);
    setTimeout(() => {
      setToasts((liste) => liste.filter((t) => t.id !== notification.id));
    }, DUREE_TOAST_MS);
  });

  /**
   * Statut en temps reel (section 13) : si l'utilisateur a deja ouvert
   * l'ecran de l'objet concerne par cet evenement, son affichage doit se
   * mettre a jour immediatement, sans attendre son propre intervalle de
   * secours - jamais un rechargement manuel. `NotificationCenter` est monte
   * globalement (voir `AppLayout`), donc actif quelle que soit la page
   * consultee ; l'invalidation cible les prefixes de cles utilises par les
   * pages FIPH/Bon de sortie/Mission concernees, jamais un identifiant
   * precis fourni par la notification elle-meme (chaque page revalide de
   * toute facon ses propres droits d'acces aupres du serveur au rechargement).
   */
  function invaliderEcransConcernes(notification: NotificationDto) {
    const prefixesParType: Record<string, string[]> = {
      FIPH_VERSION: ["fiph", "fiph-detail", "fiph-version", "fiph-validations", "fiph-toutes-versions", "fiph-audit"],
      BON_SORTIE: ["bon-sortie", "bons-sortie", "personnes-a-bord"],
    };
    const prefixes = prefixesParType[notification.entiteType] ?? [];
    if (prefixes.length === 0) {
      return;
    }
    void queryClient.invalidateQueries({
      predicate: (q) => typeof q.queryKey[0] === "string" && prefixes.includes(q.queryKey[0]),
    });
  }

  async function ouvrirDepuisToast(notification: NotificationDto) {
    setToasts((liste) => liste.filter((t) => t.id !== notification.id));
    await ouvrirNotification(notification);
  }

  // Ferme le panneau au clic en dehors (comportement standard d'un menu deroulant).
  useEffect(() => {
    function surClicExterieur(e: MouseEvent) {
      if (conteneurRef.current && !conteneurRef.current.contains(e.target as Node)) {
        setOuvert(false);
      }
    }
    document.addEventListener("mousedown", surClicExterieur);
    return () => document.removeEventListener("mousedown", surClicExterieur);
  }, []);

  async function ouvrirNotification(notification: NotificationDto) {
    if (!notification.lue) {
      try {
        await marquerNotificationCommeLue(notification.id);
        void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      } catch {
        // Une erreur reseau lors du marquage ne doit pas empecher l'utilisateur d'ouvrir la FIPH.
      }
    }
    setOuvert(false);
    navigate(notification.lien);
  }

  const nombreNonLues = compte.data ?? 0;

  return (
    <>
    <div className="notification-centre" ref={conteneurRef}>
      <button
        type="button"
        className="notification-centre__cloche"
        aria-label={nombreNonLues > 0 ? `Notifications (${nombreNonLues} non lues)` : "Notifications"}
        aria-expanded={ouvert}
        onClick={() => setOuvert((v) => !v)}
      >
        🔔
        {nombreNonLues > 0 && <span className="notification-centre__badge">{nombreNonLues > 99 ? "99+" : nombreNonLues}</span>}
      </button>

      {ouvert && (
        <div className="notification-centre__panneau" role="menu">
          <h3>Notifications</h3>
          {notifications.isLoading && <p className="etat-chargement">Chargement...</p>}
          {notifications.data && notifications.data.length === 0 && <p>Aucune notification.</p>}
          {notifications.data && notifications.data.length > 0 && (
            <ul className="notification-centre__liste">
              {notifications.data.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`notification-centre__item${n.lue ? "" : " notification-centre__item--non-lue"}`}
                    onClick={() => void ouvrirNotification(n)}
                  >
                    <span className="notification-centre__titre">{n.titre}</span>
                    <span className="notification-centre__message">{n.message}</span>
                    <span className="notification-centre__date">{new Date(n.dateCreation).toLocaleString("fr-FR")}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
    {toasts.length > 0 && (
      <div className="notification-toast-pile" role="status" aria-live="polite">
        {toasts.map((t) => (
          <button
            key={t.id}
            type="button"
            className="notification-toast"
            onClick={() => void ouvrirDepuisToast(t)}
          >
            <span className="notification-toast__titre">🔔 {t.titre}</span>
            <span className="notification-toast__message">{t.message}</span>
          </button>
        ))}
      </div>
    )}
    </>
  );
}
