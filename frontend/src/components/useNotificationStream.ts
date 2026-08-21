import { useEffect, useRef } from "react";
import { getJetonAcces } from "../api/httpClient";
import type { NotificationDto } from "../types/notification";

/**
 * Flux temps reel des notifications (evolution du 2026-08-21, section 9-11 :
 * "reellement temps reel", pas un simple rafraichissement manuel) - ouvre une
 * connexion {@link EventSource} vers {@code GET /api/notifications/stream}
 * (SSE) et invoque {@code surNotification} pour chaque evenement recu.
 *
 * Le jeton d'acces est transmis en parametre de requete (jamais en en-tete,
 * {@code EventSource} ne le permettant pas) - voir la Javadoc de
 * {@code JwtAuthenticationFilter} cote backend pour la justification de
 * cette exception strictement limitee a cette route.
 *
 * La connexion est recreee toutes les {@link INTERVALLE_RECONNEXION_MS},
 * avant l'expiration du jeton d'acces (15 min cote backend) : {@code EventSource}
 * n'offre aucun moyen de mettre a jour l'URL d'une connexion existante apres
 * un rafraichissement de jeton, la reconnexion periodique reste donc la
 * strategie la plus simple pour ne jamais rester bloque sur un jeton perime.
 */
const INTERVALLE_RECONNEXION_MS = 10 * 60 * 1000;

export function useNotificationStream(surNotification: (notification: NotificationDto) => void): void {
  const callbackRef = useRef(surNotification);
  callbackRef.current = surNotification;

  useEffect(() => {
    let source: EventSource | null = null;

    function connecter() {
      source?.close();
      const jeton = getJetonAcces();
      if (!jeton) {
        return;
      }
      source = new EventSource(`/api/notifications/stream?token=${encodeURIComponent(jeton)}`);
      source.addEventListener("notification", (evenement) => {
        try {
          const notification = JSON.parse((evenement as MessageEvent).data) as NotificationDto;
          callbackRef.current(notification);
        } catch {
          // Evenement malforme : ignore silencieusement, jamais bloquant pour l'utilisateur
          // (la liste REST reste de toute facon la source de verite de rattrapage).
        }
      });
    }

    connecter();
    const minuteur = setInterval(connecter, INTERVALLE_RECONNEXION_MS);

    return () => {
      clearInterval(minuteur);
      source?.close();
    };
  }, []);
}
