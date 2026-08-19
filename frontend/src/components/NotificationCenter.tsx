import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { compterNotificationsNonLues, listerNotifications, marquerNotificationCommeLue } from "../api/notificationApi";
import type { NotificationDto } from "../types/notification";

/** Rafraichissement periodique du compteur/de la liste - pas de flux temps reel (WebSocket) dans cette evolution, une mise a jour toutes les 20s reste largement suffisante pour un usage interne (voir rapport de mission, "mise a jour automatique"). */
const INTERVALLE_ACTUALISATION_MS = 20_000;

/**
 * Centre de notifications (evolution du 2026-08-19, section 7) : cloche
 * dans l'en-tete avec compteur de non-lues, panneau deroulant listant les
 * notifications du compte connecte (jamais celles d'un autre utilisateur -
 * la liste vient de `GET /api/notifications`, resolue cote serveur a partir
 * du jeton, sans aucun parametre d'identifiant possible cote client).
 *
 * Cliquer sur une notification la marque lue puis ouvre directement la FIPH
 * concernee (`notification.lien`), conformement a l'exigence "acces direct
 * a la FIPH concernee" de la mission.
 */
export default function NotificationCenter() {
  const [ouvert, setOuvert] = useState(false);
  const conteneurRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const compte = useQuery({
    queryKey: ["notifications", "compte"],
    queryFn: compterNotificationsNonLues,
    refetchInterval: INTERVALLE_ACTUALISATION_MS,
  });
  const notifications = useQuery({
    queryKey: ["notifications", "liste"],
    queryFn: listerNotifications,
    // Ne charge la liste complete que si le panneau est ouvert - le compteur seul suffit tant qu'il est ferme.
    enabled: ouvert,
    refetchInterval: ouvert ? INTERVALLE_ACTUALISATION_MS : false,
  });

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
  );
}
