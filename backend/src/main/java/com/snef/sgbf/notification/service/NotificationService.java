package com.snef.sgbf.notification.service;

import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.notification.dto.NotificationDto;
import com.snef.sgbf.notification.entity.Notification;
import com.snef.sgbf.notification.entity.TypeNotification;
import com.snef.sgbf.notification.mapper.NotificationMapper;
import com.snef.sgbf.notification.repository.NotificationRepository;
import com.snef.sgbf.notification.sse.SseEmitterRegistry;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications internes a l'application (evolution du 2026-08-19, section
 * 5-7 du document source de la mission) : jamais un e-mail ni une
 * notification systeme, uniquement une file d'attente consultable dans le
 * "centre de notifications" React, strictement cloisonnee par destinataire.
 *
 * <p>Declenchee UNIQUEMENT apres une validation reellement reussie (jamais
 * avant, jamais sur un rejet ou un retour pour correction) - voir les points
 * d'appel dans {@code FiphService.creerFiphEtVersionInitiale} (visa
 * automatique -&gt; CA/PH) et {@code FiphVersionService} (soumission,
 * chaque niveau valide, validation finale).
 */
@org.springframework.stereotype.Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final HabilitationRepository habilitationRepository;
    private final NotificationMapper notificationMapper;
    private final SseEmitterRegistry sseEmitterRegistry;

    public NotificationService(NotificationRepository notificationRepository,
                                HabilitationRepository habilitationRepository,
                                NotificationMapper notificationMapper,
                                SseEmitterRegistry sseEmitterRegistry) {
        this.notificationRepository = notificationRepository;
        this.habilitationRepository = habilitationRepository;
        this.notificationMapper = notificationMapper;
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listerPourUtilisateur(Utilisateur courant) {
        return notificationRepository.findByDestinataire_IdOrderByDateCreationDesc(courant.getId()).stream()
                .map(notificationMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long compterNonLues(Utilisateur courant) {
        return notificationRepository.countByDestinataire_IdAndLueFalse(courant.getId());
    }

    /** Anti-IDOR (RG-SEC-002) : une notification ne peut etre marquee comme lue que par son propre destinataire. */
    public NotificationDto marquerCommeLue(Long notificationId, Utilisateur courant) {
        Notification notification = notificationRepository.findByIdAndDestinataire_Id(notificationId, courant.getId())
                .orElseThrow(() -> {
                    // Distingue volontairement "n'existe pas" de "existe mais appartient a un autre destinataire" :
                    // dans les deux cas le meme refus est renvoye, pour ne jamais laisser deviner l'existence
                    // d'une notification d'autrui par la difference de message (RG-SEC-002).
                    boolean existePourAutrui = notificationRepository.findById(notificationId).isPresent();
                    if (existePourAutrui) {
                        return new ForbiddenOperationException("Cette notification ne vous appartient pas.");
                    }
                    return ResourceNotFoundException.of("Notification", notificationId);
                });
        if (!notification.isLue()) {
            notification.setLue(true);
            notification.setDateLecture(java.time.LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return notificationMapper.toDto(notification);
    }

    /**
     * Notifie chaque titulaire actif du role Charge d'Affaires OU personne
     * habilitee sur le service concerne (niveau 2) qu'une FIPH est prete
     * pour sa validation.
     */
    public void notifierNiveau2(Long fiphId, Long fiphVersionId, Long serviceId, String referenceFiph, Utilisateur declencheur) {
        List<Habilitation> destinataires = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.CHARGE_AFFAIRES.name(), serviceId);
        List<Habilitation> destinatairesPh = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.PERSONNE_HABILITEE.name(), serviceId);
        String titre = referenceFiph + " disponible pour validation";
        String message = "La FIPH " + referenceFiph + " est prete pour votre validation (Charge d'Affaires / personne habilitee).";
        notifierTous(destinataires, fiphId, fiphVersionId, referenceFiph, titre, message, declencheur);
        notifierTous(destinatairesPh, fiphId, fiphVersionId, referenceFiph, titre, message, declencheur);
    }

    /** Notifie chaque titulaire actif du role Responsable d'activite sur le service concerne (niveau 3). */
    public void notifierNiveau3(Long fiphId, Long fiphVersionId, Long serviceId, String referenceFiph, Utilisateur declencheur) {
        List<Habilitation> destinataires = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.RESPONSABLE_ACTIVITE.name(), serviceId);
        String titre = referenceFiph + " disponible pour validation";
        String message = "La FIPH " + referenceFiph + " a ete validee par le Charge d'Affaires et necessite maintenant votre validation.";
        notifierTous(destinataires, fiphId, fiphVersionId, referenceFiph, titre, message, declencheur);
    }

    /** Notifie chaque titulaire actif du role Direction sur le service concerne (niveau 4, definitif). */
    public void notifierNiveau4(Long fiphId, Long fiphVersionId, Long serviceId, String referenceFiph, Utilisateur declencheur) {
        List<Habilitation> destinataires = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.DIRECTION.name(), serviceId);
        String titre = referenceFiph + " disponible pour validation finale";
        String message = "La FIPH " + referenceFiph + " a ete validee par le Responsable d'activite et necessite votre validation finale.";
        notifierTous(destinataires, fiphId, fiphVersionId, referenceFiph, titre, message, declencheur);
    }

    /** Informe le titulaire de la FIPH (s'il possede un compte applicatif) que sa fiche est definitivement validee. */
    public void notifierValidationFinale(Long fiphId, Long fiphVersionId, String referenceFiph, Utilisateur titulaireCompte, Utilisateur declencheur) {
        // Le titulaire d'une FIPH (Utilisateur.agent) n'est plus jamais null depuis l'unification
        // Agent/Utilisateur du 2026-08-19, mais peut ne pas disposer d'un compte applicatif
        // (possedeCompteApplicatif() == false) - une notification n'a alors aucun sens, personne ne
        // pourra jamais se connecter pour la lire.
        if (titulaireCompte == null || !titulaireCompte.possedeCompteApplicatif()) {
            return;
        }
        creer(titulaireCompte, TypeNotification.FIPH_VALIDEE, referenceFiph + " validee definitivement",
                "La FIPH " + referenceFiph + " a ete validee definitivement par la Direction.",
                EntiteAuditable.FIPH_VERSION, fiphVersionId, "/fiph/" + fiphId, declencheur);
    }

    /**
     * Notifie chaque titulaire actif du role Charge d'Affaires OU personne
     * habilitee sur le service du bon (niveau 2, evolution du 2026-08-19,
     * Lot 3) - meme ciblage que celui deja applique aux FIPH
     * ({@link #notifierNiveau2}), reutilise a l'identique pour rester une
     * seule et unique implementation (exigence explicite de la mission :
     * "une seule implementation, pas deux").
     */
    public void notifierBonSortieAValider(Long bonSortieId, Long serviceId, String reference, Utilisateur declencheur) {
        List<Habilitation> ca = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.CHARGE_AFFAIRES.name(), serviceId);
        List<Habilitation> ph = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.PERSONNE_HABILITEE.name(), serviceId);
        String titre = reference + " disponible pour validation";
        String message = "Le bon de sortie " + reference + " a ete vise et necessite votre validation.";
        String lien = "/bons-sortie/" + bonSortieId;
        java.util.stream.Stream.concat(ca.stream(), ph.stream())
                .map(Habilitation::getUtilisateur)
                .distinct()
                .forEach(destinataire -> creer(destinataire, TypeNotification.BON_SORTIE_A_VALIDER, titre, message,
                        EntiteAuditable.BON_SORTIE, bonSortieId, lien, declencheur));
    }

    /** Informe l'agent titulaire (s'il possede un compte applicatif) que son bon de sortie est definitivement valide. */
    public void notifierBonSortieValide(Long bonSortieId, Utilisateur titulaire, Utilisateur declencheur) {
        if (titulaire == null || !titulaire.possedeCompteApplicatif()) {
            return;
        }
        creer(titulaire, TypeNotification.BON_SORTIE_VALIDE, "Bon de sortie #" + bonSortieId + " valide",
                "Votre bon de sortie #" + bonSortieId + " a ete valide.",
                EntiteAuditable.BON_SORTIE, bonSortieId, "/bons-sortie/" + bonSortieId, declencheur);
    }

    /** Informe l'agent ajoute comme personne a bord (s'il possede un compte applicatif). */
    public void notifierPersonneABordAjoutee(Long bonSortieId, Utilisateur agentAjoute, Utilisateur declencheur) {
        if (agentAjoute == null || !agentAjoute.possedeCompteApplicatif()) {
            return;
        }
        creer(agentAjoute, TypeNotification.PERSONNE_A_BORD_AJOUTEE, "Ajoute a bord du bon de sortie #" + bonSortieId,
                "Vous avez ete ajoute comme personne a bord du bon de sortie #" + bonSortieId + ".",
                EntiteAuditable.BON_SORTIE, bonSortieId, "/bons-sortie/" + bonSortieId, declencheur);
    }

    /**
     * Informe chaque Charge d'Affaires/personne habilitee du service concerne
     * qu'un bon a ete valide malgre l'absence d'affectation active resolue
     * pour l'agent (Lot 2 : avertissement, jamais un blocage - voir
     * {@code BonSortieService#valider}) - suivi possible sans que
     * l'anomalie ne reste silencieuse.
     */
    public void notifierAnomalieAffectation(Long bonSortieId, Utilisateur declencheur, Long serviceId) {
        List<Habilitation> ca = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.CHARGE_AFFAIRES.name(), serviceId);
        List<Habilitation> ph = habilitationRepository.findByRoleMetier_CodeAndService_IdAndActifTrue(
                CodeRoleMetier.PERSONNE_HABILITEE.name(), serviceId);
        String titre = "Anomalie d'affectation - bon de sortie #" + bonSortieId;
        String message = "Le bon de sortie #" + bonSortieId + " a ete valide sans affectation active "
                + "resolue pour l'agent a la date de sortie - verification recommandee.";
        String lien = "/bons-sortie/" + bonSortieId;
        java.util.stream.Stream.concat(ca.stream(), ph.stream())
                .map(Habilitation::getUtilisateur)
                .distinct()
                .forEach(destinataire -> creer(destinataire, TypeNotification.ANOMALIE_AFFECTATION, titre, message,
                        EntiteAuditable.BON_SORTIE, bonSortieId, lien, declencheur));
    }

    private void notifierTous(List<Habilitation> habilitations, Long fiphId, Long fiphVersionId, String referenceFiph,
                               String titre, String message, Utilisateur declencheur) {
        // Un meme utilisateur peut cumuler plusieurs habilitations correspondantes (rare) : deduplique par
        // destinataire pour ne jamais generer de doublon inutile (section 16, applique par coherence ici aussi).
        habilitations.stream()
                .map(Habilitation::getUtilisateur)
                .distinct()
                .forEach(destinataire -> creer(destinataire, TypeNotification.FIPH_A_VALIDER, titre, message,
                        EntiteAuditable.FIPH_VERSION, fiphVersionId, "/fiph/" + fiphId, declencheur));
    }

    private void creer(Utilisateur destinataire, TypeNotification type, String titre, String message,
                        EntiteAuditable entiteType, Long entiteId, String lien, Utilisateur declencheur) {
        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(titre);
        notification.setMessage(message);
        notification.setEntiteType(entiteType);
        notification.setEntiteId(entiteId);
        notification.setLien(lien);
        notification.setDeclenchePar(declencheur);
        notification = notificationRepository.save(notification);

        // Pousse en SSE seulement APRES commit (evolution du 2026-08-21, section 9-11) : si la
        // transaction englobante devait finalement etre annulee (erreur plus loin dans le meme appel),
        // le client ne doit jamais recevoir en temps reel une notification qui n'existe pas vraiment
        // en base. Aucun connecte -> aucun envoi (rattrapage via la liste REST au prochain chargement).
        Notification notificationFinale = notification;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sseEmitterRegistry.notifier(destinataire.getId(), notificationMapper.toDto(notificationFinale));
                        }
                    });
        } else {
            sseEmitterRegistry.notifier(destinataire.getId(), notificationMapper.toDto(notificationFinale));
        }
    }
}
