package com.snef.sgbf.fiph.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.EvenementAudit;
import com.snef.sgbf.common.audit.EvenementAuditRepository;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.fiph.dto.CompleterPointageRequest;
import com.snef.sgbf.fiph.dto.CreerNouvelleVersionRequest;
import com.snef.sgbf.fiph.dto.FiphVersionDto;
import com.snef.sgbf.fiph.dto.PointageDto;
import com.snef.sgbf.fiph.dto.PriseEnMainSuperAdminRequest;
import com.snef.sgbf.fiph.dto.ValidationDto;
import com.snef.sgbf.fiph.dto.ValiderFiphRequest;
import com.snef.sgbf.fiph.entity.DecisionValidation;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.entity.FIPHVersion;
import com.snef.sgbf.fiph.entity.OrigineFiph;
import com.snef.sgbf.fiph.entity.Pointage;
import com.snef.sgbf.fiph.entity.Signature;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import com.snef.sgbf.fiph.entity.TypeSignature;
import com.snef.sgbf.fiph.entity.Validation;
import com.snef.sgbf.fiph.mapper.PointageMapper;
import com.snef.sgbf.fiph.mapper.ValidationMapper;
import com.snef.sgbf.fiph.repository.FiphRepository;
import com.snef.sgbf.fiph.repository.FiphVersionRepository;
import com.snef.sgbf.fiph.repository.PointageRepository;
import com.snef.sgbf.fiph.repository.SignatureRepository;
import com.snef.sgbf.fiph.repository.ValidationRepository;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.mission.service.AffectationMissionService;
import com.snef.sgbf.notification.service.NotificationService;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie complet d'une {@link FIPHVersion} : complement, signature de
 * l'emetteur, soumission, circuit de validation a trois niveaux, et
 * versionnement post-validation (RG-FIPH-009 a 025, RG-VER-001 a 007,
 * section 12 et 21 du document source).
 *
 * <p>C'est ici, et uniquement ici, qu'est appliquee la separation des
 * responsabilites RG-HAB-004 : un utilisateur ne peut jamais valider, a un
 * niveau superieur, une version dont il a lui-meme SAISI le contenu (pointage
 * complete/modifie) - verifie en interrogeant le journal d'audit (seule
 * source fiable de "qui a touche cette version", puisque plusieurs personnes
 * habilitees du meme perimetre peuvent successivement completer un meme
 * document). La CREATION seule d'une FIPH n'empeche plus son createur de la
 * valider ensuite (evolution du 2026-08-19, voir Javadoc de
 * {@link #verifierSeparationResponsabilites}) - seul le contenu saisi
 * (pointage) reste bloquant.
 */
@org.springframework.stereotype.Service
@Transactional
public class FiphVersionService {

    private static final Set<String> ACTIONS_MODIFICATRICES = Set.of("CREATION", "COMPLEMENT", "MODIFICATION");

    private final FiphRepository fiphRepository;
    private final FiphVersionRepository fiphVersionRepository;
    private final PointageRepository pointageRepository;
    private final ValidationRepository validationRepository;
    private final SignatureRepository signatureRepository;
    private final HabilitationRepository habilitationRepository;
    private final EvenementAuditRepository evenementAuditRepository;
    private final AffectationMissionService affectationMissionService;
    private final FiphService fiphService;
    private final PointageMapper pointageMapper;
    private final ValidationMapper validationMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public FiphVersionService(FiphRepository fiphRepository, FiphVersionRepository fiphVersionRepository,
                               PointageRepository pointageRepository, ValidationRepository validationRepository,
                               SignatureRepository signatureRepository, HabilitationRepository habilitationRepository,
                               EvenementAuditRepository evenementAuditRepository,
                               AffectationMissionService affectationMissionService, FiphService fiphService,
                               PointageMapper pointageMapper, ValidationMapper validationMapper,
                               AuditService auditService, NotificationService notificationService) {
        this.fiphRepository = fiphRepository;
        this.fiphVersionRepository = fiphVersionRepository;
        this.pointageRepository = pointageRepository;
        this.validationRepository = validationRepository;
        this.signatureRepository = signatureRepository;
        this.habilitationRepository = habilitationRepository;
        this.evenementAuditRepository = evenementAuditRepository;
        this.affectationMissionService = affectationMissionService;
        this.fiphService = fiphService;
        this.pointageMapper = pointageMapper;
        this.validationMapper = validationMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public FiphVersionDto obtenirParId(Long id, Utilisateur courant) {
        FIPHVersion version = chargerVersion(id);
        fiphService.verifierPerimetreLecture(courant, version.getFiph());
        return versDto(version);
    }

    @Transactional(readOnly = true)
    public List<FiphVersionDto> listerVersions(Long fiphId, Utilisateur courant) {
        fiphService.verifierPerimetreLecture(courant, fiphService.chargerFiph(fiphId));
        return fiphVersionRepository.findByFiph_IdOrderByNumeroVersionAsc(fiphId).stream()
                .map(this::versDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ValidationDto> listerValidations(Long fiphVersionId, Utilisateur courant) {
        FIPHVersion version = chargerVersion(fiphVersionId);
        fiphService.verifierPerimetreLecture(courant, version.getFiph());
        return validationRepository.findByFiphVersion_IdOrderByDateValidationAsc(fiphVersionId).stream()
                .map(validationMapper::toDto).toList();
    }

    /**
     * Complete une ligne de pointage (RG-FIPH-009/010) : reserve au Charge
     * d'Affaires et a la personne habilitee du service concerne. Jamais
     * bloquant pour la suite du circuit (RG-FIPH-009) - une FIPH peut etre
     * signee sans avoir ete completee.
     */
    public FiphVersionDto completerPointage(Long fiphVersionId, CompleterPointageRequest requete, Utilisateur auteur) {
        FIPHVersion version = chargerVersion(fiphVersionId);
        fiphService.verifierPerimetreGestionnaire(auteur, version.getFiph().getAgent());
        verifierPointageModifiable(version);

        Pointage pointage = pointageRepository.findByFiphVersion_IdAndDatePointage(version.getId(), requete.datePointage())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune ligne de pointage pour le " + requete.datePointage() + " sur cette version."));
        pointage.setHeuresNormales(requete.heuresNormales());
        pointage.setHeuresSup(requete.heuresSup());
        pointageRepository.save(pointage);

        if (version.getStatutVersion() == StatutFiphVersion.BROUILLON) {
            version.setStatutVersion(StatutFiphVersion.EN_COMPLEMENT);
            version.getFiph().setStatut(StatutFiphVersion.EN_COMPLEMENT);
            fiphRepository.save(version.getFiph());
        }
        fiphService.recalculerTotaux(version);
        fiphVersionRepository.save(version);

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), auteur,
                TypeActionAudit.COMPLEMENT, null, requete, null, version.getStatutVersion().name());
        return versDto(chargerVersion(fiphVersionId));
    }

    /**
     * Signature de l'emetteur (RG-FIPH-019) : condition prealable
     * obligatoire a la soumission, reservee au titulaire de la FIPH.
     * Controle de coherence RG-FIPH-025 execute avant toute signature.
     *
     * <p><strong>Reste pertinente uniquement pour une FIPH
     * {@link OrigineFiph#MANUELLE}</strong> (evolution du workflow FIPH,
     * 2026-08-18) : pour une origine {@link OrigineFiph#BON_SORTIE}, le visa
     * de l'agent titulaire est desormais acquis automatiquement des la
     * precreation (voir {@code FiphService#creerFiphEtVersionInitiale}) - la
     * version demarre alors directement a {@code SIGNEE}, et cette methode
     * echoue naturellement si on l'appelle a nouveau ({@link #verifierModifiable}
     * n'accepte que {@code BROUILLON}/{@code EN_COMPLEMENT}).
     */
    public FiphVersionDto signer(Long fiphVersionId, String adresseIp, Utilisateur auteur) {
        FIPHVersion version = chargerVersion(fiphVersionId);
        Utilisateur titulaire = version.getFiph().getAgent();
        boolean estTitulaire = titulaire.getId().equals(auteur.getId());
        if (!estTitulaire) {
            throw new ForbiddenOperationException("Seul l'agent titulaire de la FIPH peut la signer.");
        }
        verifierModifiable(version);
        controlerCoherenceAffectations(version);

        Signature signature = signatureRepository.save(
                new Signature(TypeSignature.VISA_APPLICATIF, "session:" + auteur.getId(), adresseIp));

        StatutFiphVersion avant = version.getStatutVersion();
        version.setSignatureEmetteur(signature);
        version.setStatutVersion(StatutFiphVersion.SIGNEE);
        fiphVersionRepository.save(version);
        majStatutFiph(version);

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), auteur,
                TypeActionAudit.SIGNATURE, avant.name(), StatutFiphVersion.SIGNEE.name(),
                avant.name(), StatutFiphVersion.SIGNEE.name());
        return versDto(chargerVersion(fiphVersionId));
    }

    /**
     * Soumission au circuit de validation (RG-FIPH-019 : uniquement apres
     * signature). Reservee au gestionnaire du service. Reste disponible mais
     * n'est plus une etape obligatoire depuis l'evolution du workflow FIPH
     * (2026-08-18) : le Charge d'Affaires/la personne habilitee peut valider
     * directement au niveau 2 une version {@code SIGNEE} sans passer par
     * cette methode (voir {@link #ETATS_ELIGIBLES_NIVEAU_2}).
     */
    public FiphVersionDto soumettre(Long fiphVersionId, Utilisateur auteur) {
        FIPHVersion version = chargerVersion(fiphVersionId);
        fiphService.verifierPerimetreGestionnaire(auteur, version.getFiph().getAgent());
        if (version.getStatutVersion() != StatutFiphVersion.SIGNEE) {
            throw new BusinessRuleViolationException("RG-FIPH-019",
                    "La FIPH doit d'abord etre signee par l'emetteur avant d'etre soumise.");
        }

        version.setStatutVersion(StatutFiphVersion.SOUMISE);
        fiphVersionRepository.save(version);
        majStatutFiph(version);

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), auteur,
                TypeActionAudit.SOUMISSION, StatutFiphVersion.SIGNEE.name(), StatutFiphVersion.SOUMISE.name(),
                StatutFiphVersion.SIGNEE.name(), StatutFiphVersion.SOUMISE.name());

        // Pendant metier de la notification a l'entree du bon de sortie
        // (FiphService#creerFiphEtVersionInitiale) pour une FIPH MANUELLE :
        // la soumission est ici le premier moment ou elle est reellement
        // prete pour le niveau 2 (evolution du 2026-08-19, section 5).
        FIPH fiph = version.getFiph();
        notificationService.notifierNiveau2(fiph.getId(), version.getId(), fiph.getService().getId(),
                "FIPH #" + fiph.getId(), auteur);
        return versDto(chargerVersion(fiphVersionId));
    }

    /**
     * Decision de validation a un niveau donne (2 : Charge d'Affaires ou
     * personne habilitee ("Responsable designe") - RG-FIPH-012 ; 3 :
     * Responsable d'activite - RG-FIPH-013 ; 4 : Direction (DG),
     * definitive - RG-FIPH-014/015). Une seule des deux habilitations
     * possibles au niveau 2 suffit a faire avancer le circuit ; si le meme
     * utilisateur porte a la fois l'habilitation Charge d'Affaires et
     * Responsable d'activite sur le service concerne, rien ne l'empeche
     * d'effectuer successivement les deux validations (niveau 2 puis niveau
     * 3) - chacune reste neanmoins tracee individuellement (une ligne
     * {@link Validation} distincte par niveau). Applique RG-HAB-004 (separation des
     * responsabilites) avant toute autre verification.
     */
    public FiphVersionDto valider(Long fiphVersionId, int niveau, ValiderFiphRequest requete, String adresseIp, Utilisateur auteur) {
        FIPHVersion version = chargerVersion(fiphVersionId);
        verifierRoleNiveau(auteur, version, niveau);
        verifierSeparationResponsabilites(version, auteur);
        verifierSequencementNiveau(version, niveau);
        if (niveau == 2) {
            // RG-FIPH-025 : verifiee ici (plutot qu'uniquement a la signature,
            // comme avant l'evolution du workflow du 2026-08-18) car le
            // Charge d'Affaires/la personne habilitee est desormais le
            // premier acteur humain a s'engager sur une FIPH issue d'un bon
            // de sortie (visa de l'agent titulaire acquis d'office, sans
            // controle manuel de sa part). Reste egalement executee dans
            // signer() pour une FIPH MANUELLE, ou elle demeure pertinente.
            controlerCoherenceAffectations(version);
        }

        if (requete.decision() != DecisionValidation.VALIDEE
                && (requete.commentaire() == null || requete.commentaire().isBlank())) {
            throw new BusinessRuleViolationException("RG-FIPH-026",
                    "Un commentaire est obligatoire pour un rejet ou un retour pour correction.");
        }

        Signature signature = signatureRepository.save(
                new Signature(TypeSignature.VISA_APPLICATIF, "session:" + auteur.getId(), adresseIp));

        StatutFiphVersion statutAvant = version.getStatutVersion();
        StatutFiphVersion statutApres = determinerNouveauStatut(version, niveau, requete.decision());

        validationRepository.save(new Validation(version, auteur, niveau, requete.decision(),
                requete.commentaire(), signature, statutAvant.name(), statutApres.name()));

        // RG-FIPH-012 : la premiere validation de niveau 2 suffit a faire
        // avancer le circuit ; une seconde validation du deuxieme Charge
        // d'Affaires reste enregistree (ligne "validation" ci-dessus) mais
        // ne fait pas regresser ni ne force pas de nouveau le statut.
        if (statutApres != statutAvant) {
            version.setStatutVersion(statutApres);
            if (statutApres == StatutFiphVersion.VALIDEE_DEFINITIVEMENT) {
                version.setEmpreinteIntegrite(calculerEmpreinte(version));
            }
            fiphVersionRepository.save(version);
            majStatutFiph(version);
        }

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), auteur,
                TypeActionAudit.VALIDATION, statutAvant.name(), statutApres.name(),
                statutAvant.name(), statutApres.name());

        // Notification du niveau suivant (evolution du 2026-08-19, section 5) :
        // uniquement apres une decision VALIDEE qui a reellement fait
        // progresser le statut - jamais sur un rejet/retour pour correction,
        // et jamais sur une seconde validation de niveau 2 deja atteint (le
        // circuit n'a alors pas progresse, statutApres == statutAvant).
        if (requete.decision() == DecisionValidation.VALIDEE && statutApres != statutAvant) {
            FIPH fiph = version.getFiph();
            String reference = "FIPH #" + fiph.getId();
            switch (niveau) {
                case 2 -> notificationService.notifierNiveau3(fiph.getId(), version.getId(), fiph.getService().getId(), reference, auteur);
                case 3 -> notificationService.notifierNiveau4(fiph.getId(), version.getId(), fiph.getService().getId(), reference, auteur);
                case 4 -> notificationService.notifierValidationFinale(fiph.getId(), version.getId(), reference,
                        fiph.getAgent(), auteur);
                default -> { }
            }
        }
        return versDto(chargerVersion(fiphVersionId));
    }

    /**
     * Prise en main exceptionnelle d'une FIPH par le Super Administrateur
     * (evolution du 2026-08-19, section 11-16) : fait progresser une
     * FIPHVersion, depuis quelque etat non-final qu'elle soit (brouillon,
     * en attente d'un niveau quelconque, rejetee, en revision...), jusqu'a
     * {@code VALIDEE_DEFINITIVEMENT} en une seule operation - "il doit
     * pouvoir franchir les etapes du workflow uniquement en raison de ses
     * privileges de Super Administrateur" (section 12).
     *
     * <p><strong>Bypass delibere et documente</strong> des controles normaux
     * ({@link #verifierRoleNiveau}, {@link #verifierSeparationResponsabilites},
     * {@link #verifierSequencementNiveau}, {@link #controlerCoherenceAffectations}) :
     * c'est precisement l'objet de ce privilege exceptionnel, jamais
     * accessible a un Administrateur standard (seul {@code @PreAuthorize}
     * niveau controleur ET la verification en base ci-dessous, en defense
     * en profondeur, y donnent acces). Chaque niveau restant genere neanmoins
     * sa propre ligne {@link Validation}, marquee {@code priseEnMainSuperAdmin = true}
     * pour rester distinguable a jamais d'une validation normale (section 13) -
     * la tracabilite du processus normal n'est jamais effacee, uniquement
     * completee.
     *
     * <p>Le commentaire de justification (section 14) est obligatoire et
     * conserve sur CHAQUE ligne de validation generee, en plus de
     * l'evenement d'audit consolide {@link TypeActionAudit#PRISE_EN_MAIN_SUPER_ADMIN}.
     */
    public FiphVersionDto priseEnMainSuperAdministrateur(Long fiphVersionId, PriseEnMainSuperAdminRequest requete,
                                                          String adresseIp, Utilisateur superAdmin) {
        boolean estReellementSuperAdmin = habilitationRepository.findByUtilisateur_IdAndActifTrue(superAdmin.getId()).stream()
                .anyMatch(h -> CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
        if (!estReellementSuperAdmin) {
            throw new ForbiddenOperationException(
                    "La prise en main exceptionnelle d'une FIPH est reservee au Super Administrateur.");
        }

        FIPHVersion version = chargerVersion(fiphVersionId);
        StatutFiphVersion statutInitial = version.getStatutVersion();
        if (statutInitial == StatutFiphVersion.VALIDEE_DEFINITIVEMENT) {
            throw new BusinessRuleViolationException("SUPER-ADMIN-PRISE-EN-MAIN",
                    "Cette FIPH est deja validee definitivement : aucune prise en main n'est necessaire.");
        }

        int niveauDepart = switch (statutInitial) {
            case VALIDEE_NIVEAU_2 -> 3;
            case VALIDEE_NIVEAU_3 -> 4;
            default -> 2; // brouillon, en complement, signee, soumise, rejetee, retour pour correction, annulee, en revision
        };

        StatutFiphVersion statutCourant = statutInitial;
        for (int niveau = niveauDepart; niveau <= 4; niveau++) {
            Signature signature = signatureRepository.save(
                    new Signature(TypeSignature.VISA_APPLICATIF, "prise-en-main-super-admin:" + superAdmin.getId(), adresseIp));
            StatutFiphVersion statutSuivant = switch (niveau) {
                case 2 -> StatutFiphVersion.VALIDEE_NIVEAU_2;
                case 3 -> StatutFiphVersion.VALIDEE_NIVEAU_3;
                default -> StatutFiphVersion.VALIDEE_DEFINITIVEMENT;
            };
            validationRepository.save(new Validation(version, superAdmin, niveau, DecisionValidation.VALIDEE,
                    requete.commentaire(), signature, statutCourant.name(), statutSuivant.name(), true));
            statutCourant = statutSuivant;
        }

        version.setStatutVersion(statutCourant);
        version.setEmpreinteIntegrite(calculerEmpreinte(version));
        fiphVersionRepository.save(version);
        majStatutFiph(version);

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), superAdmin,
                TypeActionAudit.PRISE_EN_MAIN_SUPER_ADMIN,
                java.util.Map.of("etapeInitiale", statutInitial.name(), "commentaire", requete.commentaire()),
                java.util.Map.of("etapeFinale", statutCourant.name(), "niveauxFranchis", niveauDepart + " a 4"),
                statutInitial.name(), statutCourant.name());

        return versDto(chargerVersion(fiphVersionId));
    }

    /**
     * RG-VER-001 a 007 : cree une nouvelle version a partir d'une version
     * validee definitivement, avec motif obligatoire (RG-VER-002). Seuls le
     * Charge d'Affaires et la personne habilitee du service peuvent demander
     * et effectuer cette operation (RG-VER-005).
     */
    public FiphVersionDto creerNouvelleVersion(Long fiphId, CreerNouvelleVersionRequest requete, Utilisateur auteur) {
        FIPH fiph = fiphService.chargerFiph(fiphId);
        fiphService.verifierPerimetreGestionnaire(auteur, fiph.getAgent());

        FIPHVersion versionCourante = fiph.getVersionCourante();
        if (versionCourante.getStatutVersion() != StatutFiphVersion.VALIDEE_DEFINITIVEMENT) {
            throw new BusinessRuleViolationException("RG-VER-001",
                    "Une nouvelle version ne peut etre creee qu'a partir d'une version deja validee definitivement.");
        }

        FIPHVersion nouvelle = fiphService.creerVersionSuivante(fiph, versionCourante, auteur, requete.motifModification());
        return versDto(chargerVersion(nouvelle.getId()));
    }

    // --- Controles ---

    /** Gate stricte : n'autorise que les etats ou la signature du titulaire n'a pas encore ete apposee (voir {@code signer}). */
    private void verifierModifiable(FIPHVersion version) {
        if (!version.getStatutVersion().estModifiable()) {
            throw new BusinessRuleViolationException("RG-VER-001",
                    "Cette version n'est plus modifiable dans son etat actuel (" + version.getStatutVersion() + ").");
        }
    }

    /**
     * Gate elargie utilisee pour le complement du pointage : contrairement a
     * {@link #verifierModifiable}, autorise egalement l'etat {@code SIGNEE},
     * mais uniquement pour une FIPH {@link OrigineFiph#BON_SORTIE}.
     * Necessaire depuis que le visa de l'agent titulaire y est acquis
     * automatiquement des la precreation (voir
     * {@code FiphService#creerFiphEtVersionInitiale}) : le pointage doit
     * rester corrigeable par le Charge d'Affaires / la personne habilitee
     * jusqu'a l'entree reelle dans le circuit de validation, meme si la
     * version est deja techniquement "signee". Pour une FIPH
     * {@link OrigineFiph#MANUELLE}, la signature reste un acte delibere du
     * titulaire : la gate stricte {@link #verifierModifiable} continue de
     * s'appliquer, pour ne pas permettre de modifier silencieusement un
     * pointage apres que l'agent l'a personnellement signe.
     */
    private void verifierPointageModifiable(FIPHVersion version) {
        boolean modifiable = version.getFiph().getOrigine() == OrigineFiph.BON_SORTIE
                ? version.getStatutVersion().estPointageModifiable()
                : version.getStatutVersion().estModifiable();
        if (!modifiable) {
            throw new BusinessRuleViolationException("RG-VER-001",
                    "Le pointage de cette version n'est plus modifiable dans son etat actuel (" + version.getStatutVersion() + ").");
        }
    }

    /** RG-FIPH-025 : chaque ligne rattachee a une affectation doit correspondre a une affectation reellement active de l'agent ce jour-la. */
    private void controlerCoherenceAffectations(FIPHVersion version) {
        for (Pointage pointage : pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(version.getId())) {
            if (pointage.getAffectationMission() == null) {
                continue;
            }
            boolean coherent = affectationMissionService
                    .resoudreActiveADate(version.getFiph().getAgent().getId(), pointage.getDatePointage())
                    .map(a -> a.getId().equals(pointage.getAffectationMission().getId()))
                    .orElse(false);
            if (!coherent) {
                throw new BusinessRuleViolationException("RG-FIPH-025",
                        "Incoherence detectee : la ligne du " + pointage.getDatePointage()
                                + " ne correspond plus a une affectation active de l'agent. "
                                + "Une personne habilitee doit corriger cette ligne avant de poursuivre.");
            }
        }
    }

    private void verifierRoleNiveau(Utilisateur auteur, FIPHVersion version, int niveau) {
        CodeRoleMetier roleAttendu = switch (niveau) {
            case 2 -> CodeRoleMetier.CHARGE_AFFAIRES;
            case 3 -> CodeRoleMetier.RESPONSABLE_ACTIVITE;
            case 4 -> CodeRoleMetier.DIRECTION;
            default -> throw new BusinessRuleViolationException("section-12", "Niveau de validation invalide : " + niveau);
        };
        Long serviceId = version.getFiph().getService().getId();
        boolean habilite = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
                .filter(h -> h.getService() != null && h.getService().getId().equals(serviceId))
                .anyMatch(h -> roleAttendu.name().equals(h.getRoleMetier().getCode())
                        // Niveau 2 accepte aussi la personne habilitee, au meme titre que le Charge d'Affaires (RG-FIPH-010, RG-HAB-003).
                        || (niveau == 2 && CodeRoleMetier.PERSONNE_HABILITEE.name().equals(h.getRoleMetier().getCode())));
        if (!habilite) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a valider cette FIPH au niveau " + niveau + ".");
        }
    }

    /**
     * RG-HAB-004 : un utilisateur ne peut jamais valider une version dont il
     * a lui-meme SAISI le contenu (pointage complete/modifie), verifie via le
     * journal d'audit (entree {@code FIPH_VERSION}), seule source exhaustive
     * de "qui a agi sur cette version".
     *
     * <p><strong>Auto-validation de la creation elle-meme, autorisee
     * (evolution du 2026-08-19, section 11 : "Auto-validation de sa propre
     * FIPH")</strong> - jusqu'au 2026-08-18, un blocage supplementaire,
     * specifique a {@link OrigineFiph#MANUELLE}, empechait aussi le
     * CREATEUR de valider sa propre creation, meme s'il n'avait rien saisi
     * de plus (aucun pointage complete). La mission du 2026-08-19 demande
     * explicitement l'inverse : "Un Charge d'Affaires ou une Personne
     * habilitee peut creer sa propre FIPH lorsqu'il y est autorise. Il peut
     * ensuite la valider au niveau 2 UNIQUEMENT PARCE QU'IL DISPOSE DE
     * L'HABILITATION CORRESPONDANTE SUR SON PROPRE SERVICE" - c'est-a-dire
     * que la creation seule (acte administratif d'enregistrement, pas un
     * choix de contenu) ne doit plus, a elle seule, faire obstacle. Ce
     * blocage specifique a donc ete retire : seule la creation d'une FIPH
     * {@code MANUELLE} elle-meme (action {@code CREATION}, journalisee sur
     * l'entite {@code FIPH}, jamais sur {@code FIPH_VERSION}) echappe donc au
     * controle d'historique ci-dessous, exactement comme le visa automatique
     * (action {@code SIGNATURE}) en echappait deja pour une FIPH
     * {@link OrigineFiph#BON_SORTIE} - les deux origines se comportent
     * desormais de facon coherente sur ce point. Completer le pointage
     * (RG-FIPH-009/010) reste, dans tous les cas, bloquant pour la
     * validation : c'est ce controle d'historique qui protege reellement
     * contre le conflit d'interet vise par RG-HAB-004.
     */
    private void verifierSeparationResponsabilites(FIPHVersion version, Utilisateur auteur) {
        List<EvenementAudit> historique = evenementAuditRepository
                .findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable.FIPH_VERSION, String.valueOf(version.getId()));
        boolean auteurADejaModifie = historique.stream()
                .anyMatch(e -> e.getUtilisateur() != null && e.getUtilisateur().getId().equals(auteur.getId())
                        && ACTIONS_MODIFICATRICES.contains(e.getAction().name()));
        if (auteurADejaModifie) {
            throw new ForbiddenOperationException(
                    "Vous ne pouvez pas valider une FIPH que vous avez creee ou completee (RG-HAB-004).");
        }
    }

    /**
     * Etats depuis lesquels une validation de niveau 2 (Charge d'Affaires ou
     * personne habilitee) peut etre engagee directement, sans etape de
     * signature ni de soumission bloquante prealable (evolution du workflow
     * FIPH, 2026-08-18, etendue aux deux origines le 2026-08-19) : le visa
     * (de l'agent titulaire pour {@link OrigineFiph#BON_SORTIE}, du createur
     * CA/PH pour {@link OrigineFiph#MANUELLE}) etant desormais acquis
     * d'office dans les deux cas (voir
     * {@code FiphService#creerFiphEtVersionInitiale}), {@code SIGNEE} est
     * dans les deux cas le point d'entree habituel ; {@code BROUILLON}/
     * {@code EN_COMPLEMENT} restent egalement acceptes (ex. pointage
     * complete puis valide sans etape de soumission separee) ; {@code SOUMISE}
     * reste acceptee pour compatibilite avec un appel explicite a
     * {@code soumettre()}, toujours possible mais jamais obligatoire.
     *
     * <p>Avant le 2026-08-19, une FIPH {@link OrigineFiph#MANUELLE} restait
     * soumise a un prealable different (signature explicite puis soumission
     * obligatoires, seul {@code SOUMISE} accepte) - devenu incoherent une
     * fois le visa automatique du createur applique des la creation (voir
     * Javadoc de {@code FiphService#creerFiphEtVersionInitiale}) : les deux
     * origines partagent maintenant exactement le meme ensemble d'etats
     * eligibles.
     */
    private static final Set<StatutFiphVersion> ETATS_ELIGIBLES_NIVEAU_2 = Set.of(
            StatutFiphVersion.BROUILLON, StatutFiphVersion.EN_COMPLEMENT,
            StatutFiphVersion.SIGNEE, StatutFiphVersion.SOUMISE);

    private void verifierSequencementNiveau(FIPHVersion version, int niveau) {
        if (niveau == 2) {
            // RG-FIPH-012 : une seconde validation de niveau 2, alors que le
            // statut est deja VALIDEE_NIVEAU_2, reste acceptee (a titre
            // informatif) plutot que rejetee comme hors sequence.
            boolean secondeValidationNiveau2 = version.getStatutVersion() == StatutFiphVersion.VALIDEE_NIVEAU_2;
            boolean eligible = ETATS_ELIGIBLES_NIVEAU_2.contains(version.getStatutVersion());
            if (!eligible && !secondeValidationNiveau2) {
                throw new BusinessRuleViolationException("RG-FIPH-013",
                        "Cette FIPH n'est pas au statut requis pour une validation de niveau 2 "
                                + "(statut actuel : " + version.getStatutVersion() + ").");
            }
            return;
        }
        StatutFiphVersion statutRequis = switch (niveau) {
            case 3 -> StatutFiphVersion.VALIDEE_NIVEAU_2;
            case 4 -> StatutFiphVersion.VALIDEE_NIVEAU_3;
            default -> throw new BusinessRuleViolationException("section-12", "Niveau de validation invalide : " + niveau);
        };
        if (version.getStatutVersion() != statutRequis) {
            throw new BusinessRuleViolationException("RG-FIPH-013",
                    "Cette FIPH n'est pas au statut requis pour une validation de niveau " + niveau
                            + " (statut actuel : " + version.getStatutVersion() + ").");
        }
    }

    private StatutFiphVersion determinerNouveauStatut(FIPHVersion version, int niveau, DecisionValidation decision) {
        if (decision == DecisionValidation.REJETEE) {
            return StatutFiphVersion.REJETEE;
        }
        if (decision == DecisionValidation.RETOUR_POUR_CORRECTION) {
            return StatutFiphVersion.EN_COMPLEMENT;
        }
        // decision == VALIDEE
        if (niveau == 2 && version.getStatutVersion() == StatutFiphVersion.VALIDEE_NIVEAU_2) {
            return StatutFiphVersion.VALIDEE_NIVEAU_2; // second CA - deja au niveau atteint, pas de progression supplementaire
        }
        return switch (niveau) {
            case 2 -> StatutFiphVersion.VALIDEE_NIVEAU_2;
            case 3 -> StatutFiphVersion.VALIDEE_NIVEAU_3;
            case 4 -> StatutFiphVersion.VALIDEE_DEFINITIVEMENT;
            default -> throw new BusinessRuleViolationException("section-12", "Niveau de validation invalide : " + niveau);
        };
    }

    private void majStatutFiph(FIPHVersion version) {
        FIPH fiph = version.getFiph();
        fiph.setStatut(version.getStatutVersion());
        fiphRepository.save(fiph);
    }

    /** RG-VER-006 : empreinte SHA-256 du contenu fige, calculee uniquement au passage a VALIDEE_DEFINITIVEMENT. */
    private String calculerEmpreinte(FIPHVersion version) {
        StringBuilder contenu = new StringBuilder();
        contenu.append(version.getFiph().getId()).append('|').append(version.getNumeroVersion()).append('|');
        for (Pointage p : pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(version.getId())) {
            contenu.append(p.getDatePointage()).append(':').append(p.getHeuresNormales()).append(':').append(p.getHeuresSup()).append(';');
        }
        contenu.append(version.getTotalHN()).append('|').append(version.getTotalHS());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contenu.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti disponible dans toute JVM standard (algorithme obligatoire de la specification) :
            // cette branche est inatteignable en pratique, jamais qu'une garde defensive.
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM.", e);
        }
    }

    private FiphVersionDto versDto(FIPHVersion version) {
        List<PointageDto> pointages = pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(version.getId())
                .stream().map(pointageMapper::toDto).toList();
        return new FiphVersionDto(
                version.getId(), version.getFiph().getId(), version.getNumeroVersion(), version.getDateCreation(),
                version.getCreePar().getIdentifiant(), version.getMotifModification(),
                version.getVersionPrecedente() != null ? version.getVersionPrecedente().getId() : null,
                version.getTotalHN(), version.getTotalHS(), version.getStatutVersion(),
                version.getEmpreinteIntegrite(), version.getLockVersion(), pointages);
    }

    private FIPHVersion chargerVersion(Long id) {
        return fiphVersionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("FIPHVersion", id));
    }
}
