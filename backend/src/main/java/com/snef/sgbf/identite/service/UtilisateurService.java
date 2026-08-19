package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
import com.snef.sgbf.identite.dto.ModifierEmailRequest;
import com.snef.sgbf.identite.dto.ModifierIdentifiantRequest;
import com.snef.sgbf.identite.dto.ReinitialiserMotDePasseRequest;
import com.snef.sgbf.identite.dto.UtilisateurDto;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.mapper.UtilisateurMapper;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import com.snef.sgbf.security.RefreshTokenService;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion administrative des comptes applicatifs.
 *
 * <p>Reserve a l'Administrateur (section 10 : "Gere les utilisateurs, les
 * roles et les habilitations"). Les mots de passe ne transitent jamais en
 * clair au-dela de cette couche service : ils sont hashes immediatement via
 * {@link PasswordEncoder} (BCrypt, voir {@code security.PasswordConfig}) et
 * le hash seul est persiste.
 */
@org.springframework.stereotype.Service
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository serviceRepository;
    private final HabilitationRepository habilitationRepository;
    private final AgentRepository agentRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public UtilisateurService(UtilisateurRepository utilisateurRepository, ServiceRepository serviceRepository,
                               HabilitationRepository habilitationRepository, AgentRepository agentRepository,
                               UtilisateurMapper utilisateurMapper, PasswordEncoder passwordEncoder,
                               AuditService auditService, RefreshTokenService refreshTokenService) {
        this.utilisateurRepository = utilisateurRepository;
        this.serviceRepository = serviceRepository;
        this.habilitationRepository = habilitationRepository;
        this.agentRepository = agentRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> listerTous() {
        return utilisateurRepository.findAll().stream().map(utilisateurMapper::toDto).toList();
    }

    /**
     * Recherche/filtrage combinable pour l'ecran d'administration (evolution
     * du 2026-08-18, section 4) : {@code terme} correspond a l'identifiant,
     * l'e-mail, ou au nom/prenom de l'agent eventuellement rattache (case
     * insensitive) ; {@code roleCode} filtre sur une habilitation ACTIVE
     * portant ce code (RG-HAB-001) ; {@code serviceId} sur le service de
     * rattachement du compte lui-meme (pas celui d'une habilitation, qui
     * peut differer selon le perimetre attribue) ; {@code statut} sur le
     * statut du compte.
     */
    @Transactional(readOnly = true)
    public List<UtilisateurDto> rechercher(String terme, Long serviceId, String roleCode, StatutCompte statut) {
        return utilisateurRepository.findAll().stream()
                .filter(u -> serviceId == null || (u.getService() != null && serviceId.equals(u.getService().getId())))
                .filter(u -> statut == null || statut == u.getStatutCompte())
                .filter(u -> roleCode == null || habilitationRepository.findByUtilisateur_IdAndActifTrue(u.getId()).stream()
                        .anyMatch(h -> roleCode.equalsIgnoreCase(h.getRoleMetier().getCode())))
                .filter(u -> terme == null || terme.isBlank() || correspondAuTerme(u, terme))
                .map(utilisateurMapper::toDto)
                .toList();
    }

    private boolean correspondAuTerme(Utilisateur utilisateur, String terme) {
        String recherche = terme.toLowerCase();
        if (utilisateur.getIdentifiant().toLowerCase().contains(recherche)
                || utilisateur.getEmail().toLowerCase().contains(recherche)) {
            return true;
        }
        return agentRepository.findByUtilisateur_Id(utilisateur.getId())
                .map(a -> a.getNom().toLowerCase().contains(recherche) || a.getPrenom().toLowerCase().contains(recherche))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public UtilisateurDto obtenirParId(Long id) {
        return utilisateurMapper.toDto(chargerUtilisateur(id));
    }

    public UtilisateurDto creer(CreerUtilisateurRequest requete, Utilisateur auteur) {
        if (utilisateurRepository.existsByIdentifiant(requete.identifiant())) {
            throw new ConflictException("L'identifiant " + requete.identifiant() + " est deja utilise.");
        }
        if (utilisateurRepository.existsByEmail(requete.email())) {
            throw new ConflictException("L'adresse e-mail " + requete.email() + " est deja utilisee.");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(requete.identifiant());
        utilisateur.setEmail(requete.email());
        // Le mot de passe fourni en clair par l'administrateur n'est jamais
        // conserve : seul son hash BCrypt l'est, des cette ligne.
        utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.motDePasse()));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);

        if (requete.serviceId() != null) {
            Service service = serviceRepository.findById(requete.serviceId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Service", requete.serviceId()));
            utilisateur.setService(service);
        }

        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.CREATION, null, utilisateurMapper.toDto(utilisateur), null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    public void changerStatut(Long id, StatutCompte nouveauStatut, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        StatutCompte statutAvant = utilisateur.getStatutCompte();
        utilisateur.setStatutCompte(nouveauStatut);
        utilisateurRepository.save(utilisateur);
        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION, statutAvant, nouveauStatut,
                statutAvant.name(), nouveauStatut.name());
        // Un compte desactive/verrouille ne doit plus pouvoir renouveler son
        // jeton d'acces via un jeton de rafraichissement deja en poche.
        if (nouveauStatut != StatutCompte.ACTIF) {
            refreshTokenService.revoquerTousPourUtilisateur(utilisateur.getId());
        }
    }

    /**
     * Correction du login de connexion (section 8 de la mission d'evolution
     * du 2026-08-18) : unicite revalidee explicitement (message clair) en
     * plus de la contrainte UNIQUE en base, qui reste le filet de securite
     * reel en cas de requetes concurrentes.
     */
    public UtilisateurDto modifierIdentifiant(Long id, ModifierIdentifiantRequest requete, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        String identifiantAvant = utilisateur.getIdentifiant();
        if (!requete.identifiant().equals(identifiantAvant) && utilisateurRepository.existsByIdentifiant(requete.identifiant())) {
            throw new ConflictException("L'identifiant " + requete.identifiant() + " est deja utilise.");
        }
        utilisateur.setIdentifiant(requete.identifiant());
        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION_IDENTIFIANT, identifiantAvant, utilisateur.getIdentifiant(), null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    /** Correction de l'adresse e-mail (section 8 de la mission d'evolution du 2026-08-18). */
    public UtilisateurDto modifierEmail(Long id, ModifierEmailRequest requete, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        String emailAvant = utilisateur.getEmail();
        if (!requete.email().equals(emailAvant) && utilisateurRepository.existsByEmail(requete.email())) {
            throw new ConflictException("L'adresse e-mail " + requete.email() + " est deja utilisee.");
        }
        utilisateur.setEmail(requete.email());
        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION_EMAIL, emailAvant, utilisateur.getEmail(), null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    /**
     * Reinitialisation du mot de passe par l'Administrateur (section 7 de la
     * mission d'evolution du 2026-08-18). Le mot de passe fourni en clair
     * n'est jamais journalise ni conserve : seul son hash BCrypt l'est,
     * exactement comme a la creation d'un compte - l'evenement d'audit ne
     * porte que la trace du FAIT qu'une reinitialisation a eu lieu, jamais
     * la valeur, avant ni apres. Tous les jetons de rafraichissement actifs
     * de ce compte sont revoques immediatement : une session deja ouverte
     * avec l'ancien mot de passe ne pourra plus se renouveler.
     */
    public UtilisateurDto reinitialiserMotDePasse(Long id, ReinitialiserMotDePasseRequest requete, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.nouveauMotDePasse()));
        utilisateur = utilisateurRepository.save(utilisateur);
        refreshTokenService.revoquerTousPourUtilisateur(utilisateur.getId());

        auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.REINITIALISATION_MOT_DE_PASSE);
        return utilisateurMapper.toDto(utilisateur);
    }

    /** Correction du service de rattachement d'un compte (section 7-9 de la mission d'evolution du 2026-08-18). {@code serviceId} peut etre {@code null} (rattachement retire). */
    public UtilisateurDto modifierService(Long id, Long serviceId, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        Long serviceAvantId = utilisateur.getService() != null ? utilisateur.getService().getId() : null;
        Service service = null;
        if (serviceId != null) {
            service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Service", serviceId));
        }
        utilisateur.setService(service);
        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION, serviceAvantId, serviceId, null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    private Utilisateur chargerUtilisateur(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
