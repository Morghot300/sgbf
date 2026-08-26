package com.snef.sgbf.notification.sse;

import com.snef.sgbf.notification.dto.NotificationDto;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registre en memoire des connexions Server-Sent Events actives, une par
 * onglet/session utilisateur connecte (evolution du 2026-08-21, section
 * 9-11 : notifications "reellement temps reel").
 *
 * <p><strong>Choix SSE plutot que WebSocket</strong> : le besoin est
 * strictement unidirectionnel (serveur -&gt; client, "une notification est
 * apparue") - jamais l'inverse. SSE fonctionne sur une simple requete HTTP
 * longue duree (aucun nouveau protocole, aucune negociation de handshake
 * separee a securiser), se reconnecte automatiquement cote navigateur
 * ({@code EventSource}) et s'integre directement dans Spring MVC
 * ({@link SseEmitter}) sans dependance ni infrastructure supplementaire
 * (contrairement a WebSocket/STOMP, qui aurait impose son propre modele
 * d'authentification et un broker de messages pour rester coherent avec la
 * securite JWT deja en place). Suffisant pour un usage interne mono-instance ;
 * un passage a plusieurs instances applicatives necessiterait un relais
 * partage (Redis pub/sub par exemple) - non requis a ce jour.
 *
 * <p><strong>Portee volontairement mono-instance</strong> : ce registre vit
 * en memoire JVM, comme la plupart des composants applicatifs de ce projet.
 * Si l'application venait a etre deployee sur plusieurs instances, un
 * utilisateur connecte a l'instance A ne recevrait aucun evenement pousse
 * par une action survenue sur l'instance B - la liste REST (jamais retiree)
 * reste alors la source de verite de rattrapage.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** Pas de timeout applicatif : c'est le client (EventSource) qui ferme et rouvre la connexion a sa guise. */
    private static final long DUREE_SANS_TIMEOUT = 0L;

    private final Map<Long, List<SseEmitter>> emettersParUtilisateur = new ConcurrentHashMap<>();

    public SseEmitter enregistrer(Long utilisateurId) {
        SseEmitter emitter = new SseEmitter(DUREE_SANS_TIMEOUT);
        List<SseEmitter> liste = emettersParUtilisateur.computeIfAbsent(utilisateurId, id -> new CopyOnWriteArrayList<>());
        liste.add(emitter);

        emitter.onCompletion(() -> retirer(utilisateurId, emitter));
        emitter.onTimeout(() -> retirer(utilisateurId, emitter));
        emitter.onError(e -> retirer(utilisateurId, emitter));

        // Evenement immediat a la connexion : confirme au client que le flux est ouvert
        // (utile pour distinguer "connecte, en attente" d'un echec silencieux).
        try {
            emitter.send(SseEmitter.event().name("connecte").data("ok"));
        } catch (Exception e) {
            terminerSurEchec(utilisateurId, emitter, e);
        }
        return emitter;
    }

    private void retirer(Long utilisateurId, SseEmitter emitter) {
        emettersParUtilisateur.computeIfPresent(utilisateurId, (id, liste) -> {
            liste.remove(emitter);
            return liste.isEmpty() ? null : liste;
        });
    }

    /**
     * Termine explicitement le contexte asynchrone Servlet lorsqu'un envoi
     * echoue (client deconnecte), en plus de retirer l'emetteur du registre.
     *
     * <p><strong>Corrige un bug reel observe en conditions reelles (jamais en
     * test)</strong> : sans cet appel a {@code completeWithError}, le contexte
     * asynchrone restait "orphelin" jusqu'a ce que le conteneur (Tomcat) le
     * detecte et tente lui-meme de le finaliser plus tard - cette finalisation
     * tardive redeclenche un passage dans la chaine de filtres Spring Security
     * (application {@code STATELESS}, aucune session a laquelle se raccrocher)
     * sans jeton disponible sur ce re-dispatch interne, produisant un
     * {@code AccessDeniedException} alors que la reponse SSE etait deja
     * committee (rien de plus qu'un log bruyant cote serveur, mais qui merite
     * d'etre elimine a la source plutot que tolere).
     *
     * <p><strong>{@code catch (Exception)}, pas seulement {@code IOException}
     * (deuxieme bug reel corrige le 2026-08-26)</strong> : {@code SseEmitter#send}
     * peut aussi lever une {@link IllegalStateException} non verifiee (ex.
     * "ResponseBodyEmitter is already set complete") lorsque le flux vient de
     * passer dans un etat terminal juste avant cet appel (course normale entre
     * la deconnexion cote client et une notification en cours d'envoi cote
     * serveur). Comme {@link #notifier} s'execute de maniere synchrone au sein
     * de la transaction metier qui a declenche la notification (ex. ajout
     * d'une personne a bord), une telle exception non rattrapee remontait
     * jusqu'a l'appelant et faisait echouer en {@code 500} une operation
     * metier n'ayant pourtant rien a voir avec la diffusion temps reel -
     * simple mecanisme de rattrapage "best effort" qui ne doit JAMAIS pouvoir
     * casser son appelant (la liste REST des notifications reste la source de
     * verite de toute facon).
     */
    private void terminerSurEchec(Long utilisateurId, SseEmitter emitter, Exception e) {
        log.debug("Emetteur SSE ferme pour l'utilisateur {} : {}", utilisateurId, e.getMessage());
        retirer(utilisateurId, emitter);
        try {
            emitter.completeWithError(e);
        } catch (RuntimeException ignoree) {
            // L'emetteur est deja dans un etat terminal (cause la plus probable de l'echec
            // ci-dessus) : rien de plus a faire, surtout pas remonter cette seconde exception.
            log.debug("completeWithError sans effet (emetteur deja termine) pour l'utilisateur {}", utilisateurId);
        }
    }

    /** Pousse une notification a toutes les connexions actives du destinataire (aucune si non connecte - rattrapage par la liste REST). Ne propage jamais d'exception : un echec de diffusion temps reel ne doit jamais faire echouer l'operation metier qui l'a declenchee. */
    public void notifier(Long utilisateurId, NotificationDto notification) {
        List<SseEmitter> liste = emettersParUtilisateur.get(utilisateurId);
        if (liste == null || liste.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(liste)) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (Exception e) {
                terminerSurEchec(utilisateurId, emitter, e);
            }
        }
    }
}
