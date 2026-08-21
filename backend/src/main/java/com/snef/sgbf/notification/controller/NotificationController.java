package com.snef.sgbf.notification.controller;

import com.snef.sgbf.notification.dto.NotificationDto;
import com.snef.sgbf.notification.service.NotificationService;
import com.snef.sgbf.notification.sse.SseEmitterRegistry;
import com.snef.sgbf.security.CustomUserDetails;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API du centre de notifications (evolution du 2026-08-19, section 7).
 * Aucun controle de role ici : chaque utilisateur authentifie ne voit que
 * SES PROPRES notifications, resolues a partir du jeton (jamais d'un
 * parametre de requete) - anti-IDOR par construction, pas par verification
 * ajoutee.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public NotificationController(NotificationService notificationService, SseEmitterRegistry sseEmitterRegistry) {
        this.notificationService = notificationService;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    /**
     * Flux Server-Sent Events temps reel (evolution du 2026-08-21, section
     * 9-11) - authentifie comme tout le reste de l'API (voir
     * {@code JwtAuthenticationFilter}), a une exception pres : le jeton peut
     * etre fourni en parametre de requete {@code ?token=} en plus de l'en-tete
     * {@code Authorization}, {@code EventSource} (API navigateur native pour
     * consommer un flux SSE) ne permettant pas de definir d'en-tetes
     * personnalises. Cette exception est strictement limitee a cette seule
     * route dans le filtre (jamais un fallback general en query param, qui
     * affaiblirait la securite des autres endpoints).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter flux(@AuthenticationPrincipal CustomUserDetails principal) {
        return sseEmitterRegistry.enregistrer(principal.getUtilisateur().getId());
    }

    @GetMapping
    public List<NotificationDto> lister(@AuthenticationPrincipal CustomUserDetails principal) {
        return notificationService.listerPourUtilisateur(principal.getUtilisateur());
    }

    @GetMapping("/non-lues/compte")
    public Map<String, Long> compterNonLues(@AuthenticationPrincipal CustomUserDetails principal) {
        return Map.of("nombre", notificationService.compterNonLues(principal.getUtilisateur()));
    }

    @PutMapping("/{id}/lue")
    public NotificationDto marquerCommeLue(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return notificationService.marquerCommeLue(id, principal.getUtilisateur());
    }
}
