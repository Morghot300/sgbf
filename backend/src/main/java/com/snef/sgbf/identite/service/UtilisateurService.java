package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
import com.snef.sgbf.identite.dto.ModifierEmailRequest;
import com.snef.sgbf.identite.dto.ModifierIdentifiantRequest;
import com.snef.sgbf.identite.dto.ModifierIdentiteRequest;
import com.snef.sgbf.identite.dto.ReinitialiserMotDePasseRequest;
import com.snef.sgbf.identite.dto.UtilisateurDto;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.identite.mapper.UtilisateurMapper;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
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
 *
 * <p><strong>Hierarchie Administrateur / Super Administrateur</strong>
 * (evolution du 2026-08-19, section 1-4) : un Administrateur standard ne
 * doit jamais voir, consulter, ni modifier un compte Super Administrateur -
 * {@link #verifierAccesCompteCible} applique cette regle a CHAQUE point
 * d'entree touchant un compte precis (lecture comme ecriture), et
 * {@link #filtrerSelonAppelant} l'applique aux listes/recherches. Un Super
 * Administrateur, lui, voit tout sans restriction supplementaire.
 */
// noRollbackFor : les methodes guardees par verifierAccesCompteCible journalisent une tentative refusee
// (ACCES_REFUSE) PUIS levent ForbiddenOperationException dans la MEME transaction - sans cette regle, le
// rollback declenche par cette exception annulerait aussi l'ecriture d'audit qui doit pourtant survivre (voir
// Javadoc de verifierAccesCompteCible). Sans risque ici : aucune de ces methodes n'effectue de mutation AVANT
// cet appel de garde (verifie explicitement pour chacune - chargerUtilisateur puis garde, toujours en premier).
@org.springframework.stereotype.Service
@Transactional(noRollbackFor = ForbiddenOperationException.class)
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository serviceRepository;
    private final HabilitationRepository habilitationRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RefreshTokenService refreshTokenService;

    public UtilisateurService(UtilisateurRepository utilisateurRepository, ServiceRepository serviceRepository,
                               HabilitationRepository habilitationRepository,
                               UtilisateurMapper utilisateurMapper, PasswordEncoder passwordEncoder,
                               AuditService auditService, RefreshTokenService refreshTokenService) {
        this.utilisateurRepository = utilisateurRepository;
        this.serviceRepository = serviceRepository;
        this.habilitationRepository = habilitationRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> listerTous(Utilisateur appelant) {
        return utilisateurRepository.findAll().stream()
                .filter(u -> estVisibleParAppelant(u, appelant))
                .map(utilisateurMapper::toDto)
                .toList();
    }

    /**
     * Recherche/filtrage combinable pour l'ecran d'administration (evolution
     * du 2026-08-18, section 4) : {@code terme} correspond a l'identifiant,
     * l'e-mail, ou au nom/prenom de l'agent eventuellement rattache (case
     * insensitive) ; {@code roleCode} filtre sur une habilitation ACTIVE
     * portant ce code (RG-HAB-001) ; {@code serviceId} sur le service de
     * rattachement du compte lui-meme (pas celui d'une habilitation, qui
     * peut differer selon le perimetre attribue) ; {@code statut} sur le
     * statut du compte. Le compte Super Administrateur est systematiquement
     * exclu du resultat pour un appelant qui ne l'est pas lui-meme (evolution
     * du 2026-08-19, section 1).
     */
    @Transactional(readOnly = true)
    public List<UtilisateurDto> rechercher(String terme, Long serviceId, String roleCode, StatutCompte statut, Utilisateur appelant) {
        return utilisateurRepository.findAll().stream()
                .filter(u -> estVisibleParAppelant(u, appelant))
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
        return contient(utilisateur.getIdentifiant(), recherche)
                || contient(utilisateur.getEmail(), recherche)
                || contient(utilisateur.getMatricule(), recherche)
                || contient(utilisateur.getNom(), recherche)
                || contient(utilisateur.getPrenom(), recherche);
    }

    private boolean contient(String valeur, String recherche) {
        return valeur != null && valeur.toLowerCase().contains(recherche);
    }

    // Pas readOnly : un acces refuse a un compte Super Administrateur ecrit un
    // evenement d'audit (ACCES_REFUSE) depuis cette meme methode - une
    // transaction readOnly=true refuse cette ecriture au niveau JDBC/MySQL
    // ("Connection is read-only"), ce qui transformerait un 403 legitime en
    // 500 (bug reproduit et corrige le 2026-08-19). noRollbackFor repete ici
    // (l'annotation de methode remplace entierement celle de la classe,
    // jamais une fusion) - voir le commentaire sur la classe.
    @Transactional(noRollbackFor = ForbiddenOperationException.class)
    public UtilisateurDto obtenirParId(Long id, Utilisateur appelant) {
        Utilisateur cible = chargerUtilisateur(id);
        verifierAccesCompteCible(cible, appelant);
        return utilisateurMapper.toDto(cible);
    }

    /**
     * Cree une personne en une seule operation (evolution du 2026-08-19,
     * "un utilisateur est obligatoirement un agent") : nom/prenom (et
     * matricule, lorsque fourni) sont toujours enregistres ici - il n'existe
     * plus de creation separee d'un "Agent" suivie d'un rattachement a un
     * compte applicatif. {@link CreerUtilisateurRequest#identifiant()},
     * {@link CreerUtilisateurRequest#email()} et
     * {@link CreerUtilisateurRequest#motDePasse()} forment un groupe : soit
     * les trois sont fournis (compte applicatif immediat), soit aucun ne
     * l'est (personne du referentiel uniquement, sans acces direct - son
     * compte pourra etre ajoute plus tard via {@link #ajouterCompteApplicatif}).
     */
    public UtilisateurDto creer(CreerUtilisateurRequest requete, Utilisateur auteur) {
        boolean demandeCompte = requete.identifiant() != null || requete.email() != null || requete.motDePasse() != null;
        if (demandeCompte && (requete.identifiant() == null || requete.identifiant().isBlank()
                || requete.email() == null || requete.email().isBlank()
                || requete.motDePasse() == null || requete.motDePasse().isBlank())) {
            throw new BusinessRuleViolationException("RG-UTIL-001",
                    "Pour creer un compte applicatif, l'identifiant, l'e-mail et le mot de passe doivent "
                            + "etre fournis tous les trois - ou aucun (personne sans acces a l'application).");
        }
        if (demandeCompte) {
            if (utilisateurRepository.existsByIdentifiant(requete.identifiant())) {
                throw new ConflictException("L'identifiant " + requete.identifiant() + " est deja utilise.");
            }
            if (utilisateurRepository.existsByEmail(requete.email())) {
                throw new ConflictException("L'adresse e-mail " + requete.email() + " est deja utilisee.");
            }
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(requete.nom());
        utilisateur.setPrenom(requete.prenom());
        utilisateur.setMatricule(requete.matricule());
        if (demandeCompte) {
            utilisateur.setIdentifiant(requete.identifiant());
            utilisateur.setEmail(requete.email());
            // Le mot de passe fourni en clair par l'administrateur n'est jamais
            // conserve : seul son hash BCrypt l'est, des cette ligne.
            utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.motDePasse()));
        }
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

    /**
     * Ajoute un compte applicatif a une personne du referentiel qui n'en
     * avait pas encore (evolution du 2026-08-19) - remplace l'ancien
     * {@code AgentService.lierUtilisateur}, qui reliait deux entites
     * distinctes ; ici, la personne existe deja, seuls ses attributs de
     * connexion sont completes.
     */
    public UtilisateurDto ajouterCompteApplicatif(Long id, CreerUtilisateurRequest requete, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        verifierAccesCompteCible(utilisateur, auteur);
        if (utilisateur.possedeCompteApplicatif()) {
            throw new ConflictException("Cette personne dispose deja d'un compte applicatif.");
        }
        if (requete.identifiant() == null || requete.identifiant().isBlank()
                || requete.email() == null || requete.email().isBlank()
                || requete.motDePasse() == null || requete.motDePasse().isBlank()) {
            throw new BusinessRuleViolationException("RG-UTIL-001",
                    "L'identifiant, l'e-mail et le mot de passe sont tous obligatoires pour ajouter un compte applicatif.");
        }
        if (utilisateurRepository.existsByIdentifiant(requete.identifiant())) {
            throw new ConflictException("L'identifiant " + requete.identifiant() + " est deja utilise.");
        }
        if (utilisateurRepository.existsByEmail(requete.email())) {
            throw new ConflictException("L'adresse e-mail " + requete.email() + " est deja utilisee.");
        }

        utilisateur.setIdentifiant(requete.identifiant());
        utilisateur.setEmail(requete.email());
        utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.motDePasse()));
        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur, TypeActionAudit.CREATION);
        return utilisateurMapper.toDto(utilisateur);
    }

    public void changerStatut(Long id, StatutCompte nouveauStatut, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        verifierAccesCompteCible(utilisateur, auteur);
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
        verifierAccesCompteCible(utilisateur, auteur);
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
        verifierAccesCompteCible(utilisateur, auteur);
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
        verifierAccesCompteCible(utilisateur, auteur);
        utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.nouveauMotDePasse()));
        utilisateur = utilisateurRepository.save(utilisateur);
        refreshTokenService.revoquerTousPourUtilisateur(utilisateur.getId());

        auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.REINITIALISATION_MOT_DE_PASSE);
        return utilisateurMapper.toDto(utilisateur);
    }

    /** Correction du nom/prenom/matricule d'une personne (evolution du 2026-08-19). */
    public UtilisateurDto modifierIdentite(Long id, ModifierIdentiteRequest requete, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        verifierAccesCompteCible(utilisateur, auteur);
        String avant = utilisateur.getNomComplet();
        utilisateur.setNom(requete.nom());
        utilisateur.setPrenom(requete.prenom());
        utilisateur.setMatricule(requete.matricule());
        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION, avant, utilisateur.getNomComplet(), null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    /** Correction du service de rattachement d'un compte (section 7-9 de la mission d'evolution du 2026-08-18). {@code serviceId} peut etre {@code null} (rattachement retire). */
    public UtilisateurDto modifierService(Long id, Long serviceId, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        verifierAccesCompteCible(utilisateur, auteur);
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

    // --- Hierarchie Administrateur / Super Administrateur (evolution du 2026-08-19) ---

    private boolean estVisibleParAppelant(Utilisateur cible, Utilisateur appelant) {
        return !estSuperAdministrateur(cible.getId()) || estSuperAdministrateur(appelant.getId());
    }

    /**
     * Bloque tout acces (lecture ou ecriture) d'un Administrateur standard a
     * un compte Super Administrateur - section 3 : "Toutes les tentatives
     * doivent etre bloquees cote backend. Le fait qu'un bouton soit absent
     * du frontend ne suffit pas." Le refus est indistinguable, cote message
     * d'erreur, d'un identifiant inexistant plus loin dans la pile
     * ({@link ResourceNotFoundException} n'est PAS utilisee ici a dessein :
     * la ressource existe bel et bien, mais reste hors de portee) - le
     * message reste neanmoins explicite en developpement/log serveur (voir
     * {@code GlobalExceptionHandler.journaliserAccesRefuse}, section 26.4).
     */
    private void verifierAccesCompteCible(Utilisateur cible, Utilisateur appelant) {
        if (!estVisibleParAppelant(cible, appelant)) {
            // Toute tentative (consultation ou modification) d'un Administrateur
            // standard sur le compte Super Administrateur est journalisee (section
            // 4 de l'evolution du 2026-08-19), y compris quand elle provient d'un
            // appel direct a l'API sans passer par une action visible du frontend.
            // Cette ecriture reste dans la MEME transaction que l'exception levee
            // juste apres (pas de Propagation.REQUIRES_NEW : sous
            // @Transactional de test, une transaction imbriquee sur une autre
            // connexion se bloquerait en attente d'un verrou InnoDB deja tenu
            // par la transaction englobante non encore validee - reproduit et
            // corrige le 2026-08-19) ; @Transactional(noRollbackFor =
            // ForbiddenOperationException.class) sur la classe/la methode
            // garantit qu'elle survit malgre le rollback declenche par cette
            // meme exception.
            auditService.enregistrerAction(EntiteAuditable.UTILISATEUR, cible.getId(), appelant,
                    TypeActionAudit.ACCES_REFUSE);
            throw new ForbiddenOperationException(
                    "Ce compte n'est pas accessible depuis votre niveau d'habilitation.");
        }
    }

    private boolean estSuperAdministrateur(Long utilisateurId) {
        return habilitationRepository.findByUtilisateur_IdAndActifTrue(utilisateurId).stream()
                .anyMatch(h -> CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
    }

    private Utilisateur chargerUtilisateur(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
