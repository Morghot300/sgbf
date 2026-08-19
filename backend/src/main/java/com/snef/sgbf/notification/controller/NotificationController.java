package com.snef.sgbf.notification.controller;

import com.snef.sgbf.notification.dto.NotificationDto;
import com.snef.sgbf.notification.service.NotificationService;
import com.snef.sgbf.security.CustomUserDetails;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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
