package com.snef.sgbf.notification.entity;

import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.identite.entity.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Notification interne, strictement liee a un seul destinataire (evolution
 * du 2026-08-19, section 5-7 : "un utilisateur ne doit jamais recevoir les
 * notifications destinees a un autre utilisateur") - chaque evenement qui
 * concerne plusieurs destinataires potentiels (ex. deux Charges d'Affaires
 * du meme service) cree autant de lignes {@link Notification} distinctes,
 * jamais une ligne partagee avec une liste de destinataires.
 *
 * <p>Contrairement a {@code EvenementAudit}, une notification N'EST PAS
 * append-only : {@link #lue} et {@link #dateLecture} sont mis a jour en
 * place lorsque l'utilisateur consulte la notification - la tracabilite
 * definitive de "qui a valide quoi, quand" reste entierement portee par
 * {@code Validation} et {@code EvenementAudit} ; cette table sert
 * uniquement de file d'attente d'affichage, jamais de preuve juridique.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    private Utilisateur destinataire;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TypeNotification type;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "entite_type", nullable = false, length = 30)
    private EntiteAuditable entiteType;

    @Column(name = "entite_id", nullable = false)
    private Long entiteId;

    /** Chemin frontend (ex. {@code /fiph/42}) permettant d'ouvrir directement l'entite concernee (section 6). */
    @Column(name = "lien", nullable = false, length = 200)
    private String lien;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declenche_par_id")
    private Utilisateur declenchePar;

    @Column(name = "lue", nullable = false)
    private boolean lue = false;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_lecture")
    private LocalDateTime dateLecture;
}
