package com.snef.sgbf.notification.dto;

import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.notification.entity.TypeNotification;
import java.time.LocalDateTime;

/** Representation en lecture d'une {@link com.snef.sgbf.notification.entity.Notification}. */
public record NotificationDto(
        Long id,
        TypeNotification type,
        String titre,
        String message,
        EntiteAuditable entiteType,
        Long entiteId,
        String lien,
        String declencheParIdentifiant,
        boolean lue,
        LocalDateTime dateCreation,
        LocalDateTime dateLecture
) {
}
