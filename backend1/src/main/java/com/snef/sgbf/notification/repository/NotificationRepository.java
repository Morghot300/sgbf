package com.snef.sgbf.notification.repository;

import com.snef.sgbf.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux notifications - toujours filtre par destinataire, jamais une lecture globale (section 7). */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDestinataire_IdOrderByDateCreationDesc(Long destinataireId);

    long countByDestinataire_IdAndLueFalse(Long destinataireId);

    /** Recharge une notification en verifiant au passage son destinataire (anti-IDOR, RG-SEC-002). */
    Optional<Notification> findByIdAndDestinataire_Id(Long id, Long destinataireId);
}
