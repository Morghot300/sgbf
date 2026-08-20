package com.snef.sgbf.bonsortie.service;

import com.snef.sgbf.bonsortie.dto.BonSortieDto;
import com.snef.sgbf.bonsortie.dto.CreerBonSortieRequest;
import com.snef.sgbf.bonsortie.dto.ModifierRetourRequest;
import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.OrigineBonSortie;
import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.bonsortie.mapper.BonSortieMapper;
import com.snef.sgbf.bonsortie.repository.BonSortiePersonneRepository;
import com.snef.sgbf.bonsortie.repository.BonSortieRepository;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.fiph.service.FiphService;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.service.AffectationMissionService;
import com.snef.sgbf.notification.service.NotificationService;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.repository.VehiculeRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie du bon de sortie principal : creation (libre-service par
 * l'agent), visa (niveau 1), validation (niveau 2, RG-BS-003/004), et
 * declenchement de la generation automatique des bons de sortie individuels
 * des personnes a bord (RG-PAB-002).
 *
 * <p><strong>Portee de ce module :</strong> la validation declenche
 * exactement les etapes decrites par RG-BS-007 pour le bon principal
 * lui-meme (resolution de l'AffectationMission) et pour chaque personne a
 * bord (via {@link PersonneABordGenerationService}), puis delegue a
 * {@link FiphService#genererOuEnrichirDepuisBonSortie} la generation ou
 * l'enrichissement automatique de la FIPH correspondante (RG-BS-007,
 * RG-FIPH-001) - c'est ce dernier appel qui referme la chaine complete
 * decrite section 12.2 du document source, du bon de sortie a la FIPH
 * temporaire deja preremplie.
 */
@org.springframework.stereotype.Service
@Transactional
public class BonSortieService {

    private static final Logger log = LoggerFactory.getLogger(BonSortieService.class);

    private final BonSortieRepository bonSortieRepository;
    private final BonSortiePersonneRepository bonSortiePersonneRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final VehiculeRepository vehiculeRepository;
    private final HabilitationRepository habilitationRepository;
    private final AffectationMissionService affectationMissionService;
    private final PersonneABordGenerationService personneABordGenerationService;
    private final FiphService fiphService;
    private final BonSortieMapper bonSortieMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public BonSortieService(BonSortieRepository bonSortieRepository,
                             BonSortiePersonneRepository bonSortiePersonneRepository,
                             UtilisateurRepository utilisateurRepository,
                             VehiculeRepository vehiculeRepository,
                             HabilitationRepository habilitationRepository,
                             AffectationMissionService affectationMissionService,
                             PersonneABordGenerationService personneABordGenerationService,
                             FiphService fiphService,
                             BonSortieMapper bonSortieMapper,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.bonSortieRepository = bonSortieRepository;
        this.bonSortiePersonneRepository = bonSortiePersonneRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.habilitationRepository = habilitationRepository;
        this.affectationMissionService = affectationMissionService;
        this.personneABordGenerationService = personneABordGenerationService;
        this.fiphService = fiphService;
        this.bonSortieMapper = bonSortieMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public BonSortieDto obtenirParId(Long id, Utilisateur courant) {
        BonSortie bonSortie = chargerBonSortie(id);
        verifierPerimetreLecture(courant, bonSortie);
        return avecAvertissementAffectation(bonSortieMapper.toDto(bonSortie), bonSortie);
    }

    /** Reserve a {@link BonSortiePdfService} : memes droits que la consultation (RG-DOC-007), mais retourne l'entite plutot que le DTO. */
    @Transactional(readOnly = true)
    BonSortie chargerPourImpression(Long id, Utilisateur courant) {
        BonSortie bonSortie = chargerBonSortie(id);
        verifierPerimetreLecture(courant, bonSortie);
        return bonSortie;
    }

    /**
     * Liste des bons de sortie visibles par l'utilisateur courant, avec
     * filtres optionnels combinables (evolution du 2026-08-18, section 1) :
     * date exacte, periode (bornes incluses), statut, service. Un filtre ne
     * peut jamais elargir la visibilite : tous sont appliques APRES le
     * filtrage de perimetre (RG-SEC-002), jamais a sa place.
     *
     * <p>Perimetre : tous les bons pour la RH/Direction/Administrateur/Super
     * Administrateur (lecture globale), ceux du perimetre de service pour un
     * Charge d'Affaires/personne habilitee, les siens propres pour un simple
     * agent (section 14). Filtrage effectue en memoire pour l'instant
     * (volumetrie de developpement) - a revoir avec une specification JPA si
     * le volume de production l'exige.
     */
    @Transactional(readOnly = true)
    public List<BonSortieDto> listerVisibles(Utilisateur courant, LocalDate date, LocalDate dateDebut, LocalDate dateFin,
                                              StatutBonSortie statut, Long serviceId) {
        return entitesVisibles(courant).stream()
                .filter(bs -> date == null || date.equals(bs.getDateSortie()))
                .filter(bs -> dateDebut == null || !bs.getDateSortie().isBefore(dateDebut))
                .filter(bs -> dateFin == null || !bs.getDateSortie().isAfter(dateFin))
                .filter(bs -> statut == null || statut == bs.getStatut())
                .filter(bs -> serviceId == null || serviceId.equals(bs.getAgent().getService().getId()))
                .map(bs -> avecAvertissementAffectation(bonSortieMapper.toDto(bs), bs))
                .toList();
    }

    /**
     * Avertissement actionnable (Lot 2, evolution du 2026-08-19) : jamais
     * bloquant (decision confirmee), visible aussi bien AVANT la validation
     * (pour que le Charge d'Affaires corrige en amont : code affaire ou
     * affectation manquante) qu'APRES si le bon a ete valide malgre cette
     * absence. Resolution live plutot que simple lecture de
     * {@code bonSortie.getAffectationMission()} : tant que le bon n'est pas
     * encore valide, ce champ est toujours nul par construction (resolu
     * seulement a la validation, voir {@link #valider}) - sans resolution
     * live ici, aucun avertissement ne serait jamais visible avant coup.
     */
    private BonSortieDto avecAvertissementAffectation(BonSortieDto dto, BonSortie bonSortie) {
        if (bonSortie.getAffectationMission() != null) {
            return dto;
        }
        boolean resoluble = affectationMissionService
                .resoudreActiveADate(bonSortie.getAgent().getId(), bonSortie.getDateSortie())
                .isPresent();
        if (resoluble) {
            return dto;
        }
        String avertissement = "Aucune affectation active trouvee pour " + bonSortie.getAgent().getNomComplet()
                + " a la date du " + bonSortie.getDateSortie() + " (code affaire saisi : "
                + (bonSortie.getCodeAffaireSaisi() != null ? bonSortie.getCodeAffaireSaisi() : "non renseigne")
                + "). Verifiez l'affectation de l'agent ou corrigez le code affaire avant de valider.";
        return dto.avecAvertissementAffectation(avertissement);
    }

    private List<BonSortie> entitesVisibles(Utilisateur courant) {
        List<Habilitation> habilitations = habilitationRepository.findByUtilisateur_IdAndActifTrue(courant.getId());

        boolean visionGlobale = habilitations.stream().anyMatch(h -> estRoleVisionGlobale(h.getRoleMetier().getCode()));
        if (visionGlobale) {
            return bonSortieRepository.findAll();
        }

        Set<Long> servicesGeres = habilitations.stream()
                .filter(h -> estRoleGestionnaire(h.getRoleMetier().getCode()))
                .map(h -> h.getService() != null ? h.getService().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return bonSortieRepository.findAll().stream()
                .filter(bs -> servicesGeres.contains(bs.getAgent().getService().getId())
                        || bs.getAgent().getId().equals(courant.getId()))
                .toList();
    }

    /**
     * Cree le bon de sortie principal (workflow §12.2, etape 1). Habituellement
     * en libre-service (le titulaire est l'utilisateur authentifie lui-meme) ;
     * peut aussi etre cree POUR LE COMPTE d'un tiers via {@code requete.agentId()}
     * (evolution du 2026-08-19, section notee explicitement par la mission :
     * "chacun cree son bon de sortie, cependant l'Administrateur, le Super
     * Administrateur, le Charge d'Affaires et la Personne habilitee peuvent le
     * faire pour quelqu'un d'autre s'il n'a pas acces a l'application") - voir
     * {@link #resoudreTitulaire}.
     */
    public BonSortieDto creer(CreerBonSortieRequest requete, Utilisateur auteur) {
        Utilisateur titulaire = resoudreTitulaire(requete.agentId(), auteur);
        verifierPrecisionVehicule(requete.moyenUtilise(), requete.precisionVehicule());

        BonSortie bonSortie = new BonSortie();
        bonSortie.setAgent(titulaire);
        if (requete.vehiculeId() != null) {
            bonSortie.setVehicule(vehiculeRepository.findById(requete.vehiculeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Vehicule", requete.vehiculeId())));
        }
        bonSortie.setMoyenUtilise(requete.moyenUtilise());
        bonSortie.setPrecisionVehicule(requete.moyenUtilise() == MoyenUtilise.AUTRE ? requete.precisionVehicule() : null);
        bonSortie.setLt(requete.lt());
        bonSortie.setKilometrage(requete.kilometrage());
        bonSortie.setDateSortie(requete.dateSortie());
        bonSortie.setHeureSortie(requete.heureSortie());
        bonSortie.setLieu(requete.lieu());
        bonSortie.setCodeAffaireSaisi(requete.codeAffaireSaisi());
        bonSortie.setMotifSortie(requete.motifSortie());
        bonSortie.setOrigine(OrigineBonSortie.PRINCIPALE);
        // Visa automatique du createur quand le titulaire n'a pas de compte applicatif
        // (evolution du 2026-08-19) : sinon ce bon resterait bloque en BROUILLON pour
        // toujours, "viser" etant strictement reserve au titulaire lui-meme (RG-BS-004),
        // qui ne peut alors jamais se connecter pour le faire - meme correctif que celui
        // deja applique a la creation manuelle d'une FIPH (voir FiphService#creerFiphEtVersionInitiale).
        boolean visaAutomatique = !titulaire.possedeCompteApplicatif();
        StatutBonSortie statutInitial = visaAutomatique ? StatutBonSortie.VISE : StatutBonSortie.BROUILLON;
        bonSortie.setStatut(statutInitial);
        if (visaAutomatique) {
            bonSortie.setVisePar(auteur);
            bonSortie.setDateVisa(LocalDateTime.now());
        }
        bonSortie = bonSortieRepository.save(bonSortie);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur,
                TypeActionAudit.CREATION, null, bonSortieMapper.toDto(bonSortie), null, statutInitial.name());
        if (visaAutomatique) {
            auditService.enregistrer(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur, TypeActionAudit.VISA,
                    StatutBonSortie.BROUILLON.name(),
                    "Visa automatique du createur " + auteur.getIdentifiant()
                            + " (bon de sortie cree pour le compte de " + titulaire.getNomComplet()
                            + ", sans acces applicatif)",
                    StatutBonSortie.BROUILLON.name(), StatutBonSortie.VISE.name());
            // Le bon est immediatement pret pour le niveau 2 (Lot 3, evolution du 2026-08-19) -
            // meme point d'entree que pour un visa explicite via viser(), jamais duplique.
            notificationService.notifierBonSortieAValider(bonSortie.getId(), titulaire.getService().getId(),
                    "Bon de sortie #" + bonSortie.getId(), auteur);
        }
        return bonSortieMapper.toDto(bonSortie);
    }

    /**
     * Resout le titulaire reel du bon de sortie a creer. {@code null} ou egal
     * a l'auteur : creation en libre-service habituelle. Different de
     * l'auteur : creation POUR LE COMPTE d'un tiers, reservee a
     * l'Administrateur/Super Administrateur (portee globale) ou au Charge
     * d'Affaires/personne habilitee du MEME service que le tiers (evolution
     * du 2026-08-19).
     */
    private Utilisateur resoudreTitulaire(Long agentIdDemande, Utilisateur auteur) {
        if (agentIdDemande == null || agentIdDemande.equals(auteur.getId())) {
            return auteur;
        }
        Utilisateur cible = utilisateurRepository.findById(agentIdDemande)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", agentIdDemande));
        boolean auteurEstAdministrateur = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
                .anyMatch(h -> CodeRoleMetier.ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode())
                        || CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
        if (!auteurEstAdministrateur) {
            verifierPerimetreGestionnaire(auteur, cible);
        }
        return cible;
    }

    /** Renseigne l'heure de retour (verrouillage optimiste - RG-SEC-001). */
    public BonSortieDto renseignerRetour(Long bonSortieId, ModifierRetourRequest requete, Utilisateur auteur) {
        BonSortie bonSortie = chargerBonSortie(bonSortieId);
        verifierAutoServiceOuGestionnaire(auteur, bonSortie.getAgent());
        if (bonSortie.getStatut() == StatutBonSortie.VALIDE) {
            throw new BusinessRuleViolationException("RG-VER-001",
                    "Un bon de sortie valide ne peut plus etre modifie.");
        }
        // L'@Version JPA (lockVersion) rejette automatiquement l'ecriture si
        // la valeur soumise diverge de celle persistee (OptimisticLockingFailureException,
        // traduite en 409 par GlobalExceptionHandler) - la comparaison explicite
        // ci-dessous n'est qu'un message d'erreur plus tot et plus clair.
        if (!bonSortie.getLockVersion().equals(requete.lockVersion())) {
            throw new com.snef.sgbf.common.exception.ConflictException(
                    "Ce bon de sortie a ete modifie entre-temps. Rechargez-le avant de reessayer.");
        }
        bonSortie.setHeureRetour(requete.heureRetour());
        bonSortie = bonSortieRepository.save(bonSortie);
        return bonSortieMapper.toDto(bonSortie);
    }

    /** Visa de l'agent (niveau 1, RG-BS-004) - strictement reserve au titulaire du bon de sortie. */
    public BonSortieDto viser(Long bonSortieId, Utilisateur auteur) {
        BonSortie bonSortie = chargerBonSortie(bonSortieId);
        boolean estTitulaire = bonSortie.getAgent().getId().equals(auteur.getId());
        if (!estTitulaire) {
            throw new ForbiddenOperationException("Seul l'agent titulaire du bon de sortie peut le viser.");
        }
        if (bonSortie.getStatut() != StatutBonSortie.BROUILLON) {
            throw new BusinessRuleViolationException("RG-BS-004", "Seul un bon de sortie en brouillon peut etre vise.");
        }

        StatutBonSortie avant = bonSortie.getStatut();
        bonSortie.setStatut(StatutBonSortie.VISE);
        bonSortie.setVisePar(auteur);
        bonSortie.setDateVisa(LocalDateTime.now());
        bonSortie = bonSortieRepository.save(bonSortie);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur,
                TypeActionAudit.VISA, avant.name(), StatutBonSortie.VISE.name(), avant.name(), StatutBonSortie.VISE.name());
        notificationService.notifierBonSortieAValider(bonSortie.getId(), bonSortie.getAgent().getService().getId(),
                "Bon de sortie #" + bonSortie.getId(), auteur);
        return bonSortieMapper.toDto(bonSortie);
    }

    /**
     * Validation du Charge d'Affaires (niveau 2, RG-BS-003) : resout
     * l'AffectationMission active de l'agent a la date de sortie
     * (section 3.2, RG-FIPH-020), fait passer le bon a VALIDE, puis
     * declenche la generation automatique des bons de sortie individuels
     * des personnes a bord deja associees (RG-PAB-002), chacune dans sa
     * propre transaction isolee (section 9.6).
     *
     * <p><strong>Auto-validation (evolution du 2026-08-19, Lot 1 -
     * decision confirmee explicitement, pas un effet de bord)</strong> :
     * un Charge d'Affaires/personne habilitee qui a lui-meme vise ce bon en
     * tant qu'agent titulaire (cas ou la meme personne cumule les deux
     * qualites) PEUT valider son propre bon - aucun controle de separation
     * des taches n'est applique ici, contrairement a RG-HAB-004 pour les
     * FIPH. Seuls le role (niveau 2) et le perimetre de service
     * ({@link #verifierPerimetreGestionnaire}) conditionnent le droit de
     * valider.
     *
     * <p><strong>Affectation manquante : avertissement, jamais un blocage
     * (evolution du 2026-08-19, Lot 2 - decision confirmee)</strong> : avant
     * cette evolution, l'absence d'AffectationMission active refusait
     * purement et simplement la validation (RG-FIPH-020, {@code 422}). Elle
     * n'empeche plus la validation - {@code bonSortie.affectationMission}
     * reste alors {@code null}, et {@link BonSortieDto#avertissementAffectation()}
     * porte un message actionnable (voir {@link #avecAvertissementAffectation}),
     * deja visible avant meme de valider. Repercussion tracee jusqu'a la
     * FIPH : {@link FiphService} bascule alors la ligne de pointage
     * correspondante sur le service de l'agent plutot que sur une
     * affectation inexistante (voir sa Javadoc), au lieu de laisser les deux
     * colonnes nulles - ce que la contrainte CHECK de {@code fiph_pointage}
     * interdirait de toute maniere (RG-FIPH-007).
     *
     * <p><strong>Idempotence / atomicite</strong> : le controle de statut
     * ci-dessous (uniquement depuis {@code VISE}) combine au verrouillage
     * optimiste JPA ({@code @Version lockVersion} sur {@link BonSortie})
     * rend un double-clic ou un rejeu de requete sans effet - la seconde
     * ecriture est soit refusee des le controle de statut (rechargement
     * entre-temps), soit rejetee par Hibernate avec
     * {@code OptimisticLockingFailureException} (traduite en {@code 409} par
     * {@code GlobalExceptionHandler}) si les deux requetes lisaient le meme
     * etat en concurrence.
     */
    public BonSortieDto valider(Long bonSortieId, Utilisateur auteur) {
        BonSortie bonSortie = chargerBonSortie(bonSortieId);
        verifierPerimetreGestionnaire(auteur, bonSortie.getAgent());
        if (bonSortie.getStatut() != StatutBonSortie.VISE) {
            throw new BusinessRuleViolationException("RG-BS-004",
                    "Le bon de sortie doit d'abord avoir recu le visa de l'agent.");
        }

        Optional<AffectationMission> affectation =
                affectationMissionService.resoudreActiveADate(bonSortie.getAgent().getId(), bonSortie.getDateSortie());

        StatutBonSortie avant = bonSortie.getStatut();
        affectation.ifPresent(bonSortie::setAffectationMission);
        bonSortie.setStatut(StatutBonSortie.VALIDE);
        bonSortie.setValideParCA(auteur);
        bonSortie.setDateValidation(LocalDateTime.now());
        bonSortie = bonSortieRepository.save(bonSortie);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur,
                TypeActionAudit.VALIDATION, avant.name(), StatutBonSortie.VALIDE.name(),
                avant.name(), StatutBonSortie.VALIDE.name());
        if (affectation.isEmpty()) {
            // Anomalie tracee separement (RG-FIPH-020) : jamais silencieuse,
            // meme devenue non bloquante - et notifiee au valideur (Lot 3,
            // notifierAnomalieAffectation) pour suivi.
            auditService.enregistrer(EntiteAuditable.BON_SORTIE, bonSortie.getId(), auteur,
                    TypeActionAudit.ANOMALIE_AFFECTATION, null,
                    "Bon valide sans affectation active resolue pour l'agent a la date de sortie", null, null);
            notificationService.notifierAnomalieAffectation(bonSortie.getId(), auteur, bonSortie.getAgent().getService().getId());
        }

        // RG-BS-007 / RG-FIPH-001 : declenche la generation ou l'enrichissement
        // automatique de la FIPH de l'agent emetteur lui-meme.
        fiphService.genererOuEnrichirDepuisBonSortie(bonSortie, auteur);

        genererPourPersonnesABordEnAttente(bonSortie.getId(), auteur);

        notificationService.notifierBonSortieValide(bonSortie.getId(), bonSortie.getAgent(), auteur);

        return avecAvertissementAffectation(bonSortieMapper.toDto(bonSortie), bonSortie);
    }

    /**
     * Declenche la generation (RG-PAB-002/006) pour chaque personne a bord
     * active de ce bon de sortie principal qui n'a pas encore de bon de
     * sortie individuel - appelee a la validation du principal, et par
     * {@code BonSortiePersonneService} lors d'un ajout tardif (RG-PAB-006).
     */
    void genererPourPersonnesABordEnAttente(Long bonSortiePrincipalId, Utilisateur auteur) {
        var enAttente = bonSortiePersonneRepository
                .findByBonSortiePrincipal_IdAndStatutAssociationAndBonSortieIndividuelIsNull(
                        bonSortiePrincipalId, com.snef.sgbf.bonsortie.entity.StatutAssociationPersonne.ACTIVE);

        for (var association : enAttente) {
            try {
                personneABordGenerationService.genererPourAssociation(association.getId(), auteur);
            } catch (RuntimeException e) {
                // Atomicite par personne (section 9.6) : une personne en echec
                // ne doit jamais empecher la generation pour les autres, ni
                // remettre en cause la validation du bon de sortie principal
                // (deja commitee independamment de cette boucle).
                log.error("Echec de generation automatique pour la personne a bord (association={}) : {}",
                        association.getId(), e.getMessage());
            }
        }
    }

    /**
     * Evolution du 2026-08-18 : lorsque le moyen utilise est {@code AUTRE},
     * la precision devient obligatoire (formulaire papier historique et
     * frontend ne suffisent jamais a eux seuls - le backend reste la source
     * de verite, comme pour toute regle de validation de cette application).
     */
    private void verifierPrecisionVehicule(MoyenUtilise moyenUtilise, String precisionVehicule) {
        if (moyenUtilise == MoyenUtilise.AUTRE && (precisionVehicule == null || precisionVehicule.isBlank())) {
            throw new BusinessRuleViolationException("RG-BS-VEHICULE",
                    "Veuillez preciser le vehicule utilise lorsque le moyen selectionne est \"Autre\".");
        }
    }

    private boolean estRoleVisionGlobale(String code) {
        return CodeRoleMetier.RH.name().equals(code)
                || CodeRoleMetier.DIRECTION.name().equals(code)
                || CodeRoleMetier.ADMINISTRATEUR.name().equals(code)
                || CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(code);
    }

    private boolean estRoleGestionnaire(String code) {
        return CodeRoleMetier.CHARGE_AFFAIRES.name().equals(code)
                || CodeRoleMetier.PERSONNE_HABILITEE.name().equals(code);
    }

    /** RG-HAB-003 / RG-SEC-002 : seul un gestionnaire du service de l'agent peut valider son bon de sortie. */
    private void verifierPerimetreGestionnaire(Utilisateur auteur, Utilisateur agent) {
        Long serviceAgentId = agent.getService().getId();
        boolean habilite = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
                .anyMatch(h -> estRoleGestionnaire(h.getRoleMetier().getCode())
                        && h.getService() != null && h.getService().getId().equals(serviceAgentId));
        if (!habilite) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a gerer les bons de sortie des agents de ce service.");
        }
    }

    /**
     * Autorise soit le titulaire lui-meme, soit un gestionnaire de son
     * service (ex. renseigner le retour, gerer les personnes a bord -
     * section 9.3 : "l'agent emetteur, ou le Charge d'Affaires / la
     * personne habilitee"). Package-privee : reutilisee par
     * {@link BonSortiePersonneService}.
     */
    void verifierAutoServiceOuGestionnaire(Utilisateur auteur, Utilisateur agent) {
        boolean estTitulaire = agent.getId().equals(auteur.getId());
        if (estTitulaire) {
            return;
        }
        verifierPerimetreGestionnaire(auteur, agent);
    }

    /**
     * Controle anti-IDOR (RG-SEC-002, section 26.5) applique a toute
     * consultation d'un bon de sortie par identifiant - meme logique que
     * {@link com.snef.sgbf.fiph.service.FiphService#verifierPerimetreLecture},
     * appliquee ici a l'agent du bon de sortie plutot qu'a celui de la FIPH.
     */
    private void verifierPerimetreLecture(Utilisateur lecteur, BonSortie bonSortie) {
        Utilisateur agent = bonSortie.getAgent();
        if (agent.getId().equals(lecteur.getId())) {
            return;
        }
        Long serviceAgentId = agent.getService().getId();
        boolean autorise = habilitationRepository.findByUtilisateur_IdAndActifTrue(lecteur.getId()).stream()
                .anyMatch(h -> estRoleVisionGlobale(h.getRoleMetier().getCode())
                        || (h.getService() != null && h.getService().getId().equals(serviceAgentId)));
        if (!autorise) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a consulter ce bon de sortie (hors de votre perimetre).");
        }
    }

    BonSortie chargerBonSortie(Long id) {
        return bonSortieRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("BonSortie", id));
    }

    /** Tous les bons de sortie dont cet agent est titulaire - utilise pour signaler (Lot 4) un creneau deja occupe. */
    List<BonSortie> chargerBonsPourAgent(Long agentId) {
        return bonSortieRepository.findByAgent_IdOrderByDateSortieDesc(agentId);
    }
}
