package com.snef.sgbf.mission.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.mission.dto.AffectationMissionDto;
import com.snef.sgbf.mission.dto.AffecterAgentRequest;
import com.snef.sgbf.mission.dto.InterrompreAffectationRequest;
import com.snef.sgbf.mission.dto.ModifierDateFinPrevueRequest;
import com.snef.sgbf.mission.dto.MissionDto;
import com.snef.sgbf.mission.dto.ReaffecterRequest;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.mission.entity.StatutAffectation;
import com.snef.sgbf.mission.entity.StatutMission;
import com.snef.sgbf.mission.mapper.AffectationMissionMapper;
import com.snef.sgbf.mission.repository.AffectationMissionRepository;
import com.snef.sgbf.notification.service.NotificationService;
import com.snef.sgbf.referentiel.entity.MotifInterruptionMission;
import com.snef.sgbf.referentiel.repository.MotifInterruptionMissionRepository;
import com.snef.sgbf.fiph.entity.Pointage;
import com.snef.sgbf.fiph.repository.PointageRepository;
import com.snef.sgbf.mission.dto.ReaffecterMiMissionRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie operationnel des affectations agent/mission : creation,
 * interruption, reaffectation (RG-MIS-001 a 008, section 5 a 8 du document
 * source).
 *
 * <p>Reserve, cote controleur, au Charge d'Affaires et a la personne
 * habilitee (section 10, 16). Chaque operation est historisee
 * (RG-MIS-008) et respecte le principe de non-ecrasement (RG-MIS-003,
 * RG-MIS-006) : une affectation interrompue n'est jamais supprimee ni
 * reecrite, seule une nouvelle ligne chainee est creee.
 *
 * <p><strong>Impact FIPH (evolution du 2026-08-20, section 9-13) :</strong>
 * le module FIPH, non construit au moment ou le paragraphe precedent a ete
 * ecrit, existe desormais - {@link #reaffecterPendantMissionEnCours} cable
 * ce point, mais volontairement au minimum : chaque {@link com.snef.sgbf.fiph.entity.Pointage}
 * journalier est deja rattache a l'affectation active CE JOUR-LA au moment
 * ou son bon de sortie est valide (voir Javadoc de {@code Pointage}), donc
 * aucun recalcul en masse n'est necessaire apres une reaffectation - seule
 * une lecture (jamais une ecriture) de {@link com.snef.sgbf.fiph.repository.PointageRepository}
 * est requise ici, pour interdire toute reaffectation retroactive sur des
 * jours deja pointes (decision confirmee, voir Javadoc de la methode).
 */
@org.springframework.stereotype.Service
@Transactional
public class AffectationMissionService {

    private static final String CODE_MOTIF_AUTRE = "AUTRE";
    private static final String CODE_MOTIF_NOUVELLE_MISSION = "NOUVELLE_MISSION";
    /** Sentinelle "periode ouverte" pour la recherche de chevauchement - bornee au type DATE de MySQL (9999-12-31), jamais LocalDate.MAX. */
    private static final LocalDate DATE_MAX_CHEVAUCHEMENT = LocalDate.of(9999, 12, 31);

    private final AffectationMissionRepository affectationMissionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MotifInterruptionMissionRepository motifInterruptionMissionRepository;
    private final HabilitationRepository habilitationRepository;
    private final MissionService missionService;
    private final AffectationMissionMapper affectationMissionMapper;
    private final AuditService auditService;
    private final PointageRepository pointageRepository;
    private final NotificationService notificationService;

    public AffectationMissionService(AffectationMissionRepository affectationMissionRepository,
                                      UtilisateurRepository utilisateurRepository,
                                      MotifInterruptionMissionRepository motifInterruptionMissionRepository,
                                      HabilitationRepository habilitationRepository,
                                      MissionService missionService,
                                      AffectationMissionMapper affectationMissionMapper,
                                      AuditService auditService,
                                      PointageRepository pointageRepository,
                                      NotificationService notificationService) {
        this.affectationMissionRepository = affectationMissionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.motifInterruptionMissionRepository = motifInterruptionMissionRepository;
        this.habilitationRepository = habilitationRepository;
        this.missionService = missionService;
        this.affectationMissionMapper = affectationMissionMapper;
        this.auditService = auditService;
        this.pointageRepository = pointageRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<AffectationMissionDto> listerPourMission(Long missionId) {
        return affectationMissionRepository.findByMission_IdOrderByDateDebutAffectationAsc(missionId).stream()
                .map(affectationMissionMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AffectationMissionDto obtenirParId(Long id) {
        return affectationMissionMapper.toDto(chargerAffectation(id));
    }

    /**
     * Resout l'affectation active d'un agent a une date donnee - utilise
     * (au sein du backend, pas expose directement en API) par le futur
     * module Bon de sortie pour rattacher un bon de sortie valide a
     * l'affectation en vigueur (RG-FIPH-025, section 8).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AffectationMission> resoudreActiveADate(Long agentId, LocalDate date) {
        return affectationMissionRepository.trouverActivesAgentADate(agentId, date).stream().findFirst();
    }

    /**
     * Cree l'affectation initiale d'un agent sur une mission (cas
     * d'utilisation "Affecter un agent a une mission", section 16).
     */
    public AffectationMissionDto affecter(AffecterAgentRequest requete, Utilisateur auteur) {
        Utilisateur agent = utilisateurRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", requete.agentId()));
        Mission mission = missionService.chargerMission(requete.missionId());

        verifierPerimetre(auteur, agent);
        verifierMissionOuverte(mission);
        verifierAucunChevauchementDate(agent.getId(), requete.dateDebutAffectation(), null, null);

        AffectationMission affectation = new AffectationMission();
        affectation.setAgent(agent);
        affectation.setMission(mission);
        affectation.setDateDebutAffectation(requete.dateDebutAffectation());
        affectation.setStatutAffectation(StatutAffectation.ACTIVE);
        affectation.setCreePar(auteur);
        affectation = affectationMissionRepository.save(affectation);

        missionService.demarrerOuReprendre(mission);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, affectation.getId(), auteur,
                TypeActionAudit.AFFECTATION, null, affectationMissionMapper.toDto(affectation),
                null, StatutAffectation.ACTIVE.name());
        return affectationMissionMapper.toDto(affectation);
    }

    /**
     * Declare l'interruption d'une affectation en cours (RG-MIS-001,
     * RG-MIS-002, section 6.1). Ne cree jamais de nouvelle affectation -
     * voir {@link #reaffecter} pour l'etape suivante, optionnelle et
     * distincte (RG-MIS-004 : la reaffectation n'est pas automatique).
     */
    public AffectationMissionDto interrompre(Long affectationId, InterrompreAffectationRequest requete, Utilisateur auteur) {
        AffectationMission affectation = chargerAffectation(affectationId);
        verifierPerimetre(auteur, affectation.getAgent());
        if (affectation.getStatutAffectation() != StatutAffectation.ACTIVE) {
            throw new BusinessRuleViolationException("RG-MIS-001",
                    "Seule une affectation active peut etre interrompue.");
        }

        MotifInterruptionMission motif = motifInterruptionMissionRepository.findAll().stream()
                .filter(m -> m.getCode().equals(requete.motifCode()) && m.isActif())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Motif d'interruption inconnu ou inactif : " + requete.motifCode()));

        if (CODE_MOTIF_AUTRE.equals(motif.getCode())
                && (requete.commentaire() == null || requete.commentaire().isBlank())) {
            throw new BusinessRuleViolationException("section-6.2",
                    "Un commentaire est obligatoire lorsque le motif d'interruption est \"Autre\".");
        }

        StatutAffectation statutAvant = affectation.getStatutAffectation();

        // Cloture de l'affectation en cours (section 6.1, etape "Cloture de
        // l'affectation en cours") - la ligne n'est ni supprimee ni ecrasee,
        // seuls son statut et sa date de fin evoluent (RG-MIS-003).
        affectation.setDateFinAffectation(requete.dateInterruption());
        affectation.setStatutAffectation(StatutAffectation.INTERROMPUE);
        affectation.setMotifInterruption(motif);
        affectation.setCommentaireInterruption(requete.commentaire());
        affectationMissionRepository.save(affectation);

        // Mise a jour de la mission (section 6.1, etape "Mise a jour de la mission").
        missionService.interrompre(affectation.getMission(), requete.dateInterruption());

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, affectation.getId(), auteur,
                TypeActionAudit.INTERRUPTION, statutAvant, StatutAffectation.INTERROMPUE,
                statutAvant.name(), StatutAffectation.INTERROMPUE.name());
        return affectationMissionMapper.toDto(affectation);
    }

    /**
     * Reaffecte l'agent d'une affectation interrompue vers une mission
     * cible - la meme (reprise) ou une autre portant un nouveau code
     * (RG-MIS-004, RG-MIS-005). Chaine la nouvelle affectation a l'ancienne
     * via {@code affectationPrecedente}, sans jamais alterer cette derniere
     * (RG-MIS-006).
     */
    public AffectationMissionDto reaffecter(Long affectationInterrompueId, ReaffecterRequest requete, Utilisateur auteur) {
        AffectationMission affectationPrecedente = chargerAffectation(affectationInterrompueId);
        verifierPerimetre(auteur, affectationPrecedente.getAgent());
        if (affectationPrecedente.getStatutAffectation() != StatutAffectation.INTERROMPUE) {
            throw new BusinessRuleViolationException("RG-MIS-004",
                    "Seule une affectation interrompue peut faire l'objet d'une reaffectation.");
        }

        Mission missionCible = missionService.chargerMission(requete.missionCibleId());
        verifierMissionOuverte(missionCible);
        // Exclut l'affectation precedente elle-meme : sa date de fin (le jour de l'interruption)
        // peut coincider avec le jour de reprise choisi ici, une passation le meme jour restant
        // toleree (comportement deja etabli avant l'evolution du 2026-08-27).
        verifierAucunChevauchementDate(affectationPrecedente.getAgent().getId(), requete.dateDebutAffectation(), null,
                affectationPrecedente.getId());

        AffectationMission nouvelleAffectation = new AffectationMission();
        nouvelleAffectation.setAgent(affectationPrecedente.getAgent());
        nouvelleAffectation.setMission(missionCible);
        nouvelleAffectation.setDateDebutAffectation(requete.dateDebutAffectation());
        nouvelleAffectation.setStatutAffectation(StatutAffectation.ACTIVE);
        nouvelleAffectation.setAffectationPrecedente(affectationPrecedente);
        nouvelleAffectation.setCreePar(auteur);
        nouvelleAffectation = affectationMissionRepository.save(nouvelleAffectation);

        missionService.demarrerOuReprendre(missionCible);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, nouvelleAffectation.getId(), auteur,
                TypeActionAudit.REAFFECTATION, affectationInterrompueId, affectationMissionMapper.toDto(nouvelleAffectation),
                StatutAffectation.INTERROMPUE.name(), StatutAffectation.ACTIVE.name());
        return affectationMissionMapper.toDto(nouvelleAffectation);
    }

    /**
     * Reaffecte un agent vers une nouvelle mission alors que sa mission
     * actuelle est encore ACTIVE (evolution du 2026-08-20, section 9-13) -
     * a la difference de {@link #interrompre}/{@link #reaffecter} (parcours
     * manuel en deux etapes, date et motif d'interruption choisis
     * librement), cette methode agit en une seule operation atomique :
     * l'affectation active est automatiquement close a la veille de
     * {@link ReaffecterMiMissionRequest#dateDebutAffectation}, motif fixe
     * {@value #CODE_MOTIF_NOUVELLE_MISSION}, puis la nouvelle affectation est
     * creee.
     *
     * <p><strong>Decoupage complet d'un chevauchement (evolution du
     * 2026-08-27, section 18-22 du brief "Evolution avancee du module Bon de
     * Sortie, Missions et FIPH" - decision confirmee explicitement)</strong> :
     * si {@link ReaffecterMiMissionRequest#dateFinAffectation} est renseignee
     * ET que l'affectation d'origine s'etendait au-dela (ou etait ouverte),
     * la mission precedente REPREND automatiquement le lendemain, jusqu'a son
     * propre terme d'origine - reproduisant exactement l'exemple du brief
     * (mission A du lundi au vendredi, mission B du mercredi au jeudi -&gt;
     * mission A devient lundi+mardi (avant, cette methode) PUIS vendredi
     * (apres, reprise automatique), mission B devient mercredi+jeudi).
     * Necessite qu'un agent puisse porter plusieurs affectations ACTIVE
     * simultanees (V16, {@link #verifierAucunChevauchementDate}) - le
     * controle d'integrite reel se fait desormais sur les PERIODES, jamais
     * sur le seul statut. Laisser {@code dateFinAffectation} vide reproduit
     * exactement le comportement d'origine (bascule permanente vers B,
     * jamais de reprise de A).
     *
     * <p><strong>Retroactivite refusee (decision confirmee)</strong> : la
     * nouvelle date de debut doit etre strictement posterieure au dernier
     * jour deja pointe pour cet agent ({@link PointageRepository#trouverDernierJourPointe}),
     * toutes FIPH confondues - jamais de reecriture silencieuse d'un
     * pointage deja valide via un bon de sortie, en coherence avec le
     * principe de non-ecrasement deja applique ailleurs (RG-MIS-003/006). La
     * reprise eventuelle de la mission precedente porte necessairement sur
     * des jours strictement posterieurs a la nouvelle date de fin, donc
     * toujours au-dela de ce meme controle.
     *
     * <p><strong>Aucun recalcul FIPH necessaire</strong> : chaque jour est
     * rattache a l'affectation active CE JOUR-LA au moment ou son bon de
     * sortie est valide, jamais retroactivement en bloc (voir Javadoc de
     * {@link com.snef.sgbf.fiph.entity.Pointage}) - les bons de sortie
     * valides a partir de la nouvelle date se rattacheront donc
     * naturellement, un par un, a la bonne affectation (nouvelle mission ou
     * reprise de l'ancienne).
     */
    public AffectationMissionDto reaffecterPendantMissionEnCours(ReaffecterMiMissionRequest requete, Utilisateur auteur) {
        Utilisateur agent = utilisateurRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", requete.agentId()));
        verifierPerimetre(auteur, agent);

        LocalDate nouvelleDateDebut = requete.dateDebutAffectation();
        // Resolue par PERIODE (evolution du 2026-08-27, V16) plutot que par le seul statut ACTIVE :
        // un agent peut desormais porter plusieurs affectations ACTIVE simultanees (reprise
        // planifiee incluse) - celle qui nous interesse ici est celle qui couvre deja la nouvelle
        // date de debut elle-meme (et non la veille : si la nouvelle date coincide avec le debut
        // de l'affectation en cours ou lui est anterieure, RG-MIS-010 ci-dessous doit la refuser
        // avec un message clair, plutot que RG-MIS-009 par une resolution qui ne la trouverait pas).
        AffectationMission affectationActive = resoudreActiveADate(agent.getId(), nouvelleDateDebut)
                .orElseThrow(() -> new BusinessRuleViolationException("RG-MIS-009",
                        "Cet agent n'a aucune affectation active a reaffecter. "
                                + "Utilisez la creation d'affectation initiale (POST /affectations-mission)."));

        if (!nouvelleDateDebut.isAfter(affectationActive.getDateDebutAffectation())) {
            throw new BusinessRuleViolationException("RG-MIS-010",
                    "La nouvelle affectation doit debuter apres le debut de l'affectation en cours ("
                            + affectationActive.getDateDebutAffectation() + ").");
        }

        Optional<LocalDate> dernierJourPointe = pointageRepository.trouverDernierJourPointe(agent.getId());
        if (dernierJourPointe.isPresent() && !nouvelleDateDebut.isAfter(dernierJourPointe.get())) {
            throw new BusinessRuleViolationException("RG-MIS-011",
                    "Impossible de reaffecter cet agent a partir du " + nouvelleDateDebut
                            + " : un pointage existe deja jusqu'au " + dernierJourPointe.get()
                            + ". La reaffectation ne peut porter que sur des jours non encore pointes.");
        }

        // Evolution du 2026-08-27 (section 18-22, decoupage des missions chevauchantes) : une date
        // de fin facultative borne la nouvelle affectation - au-dela, la mission precedente reprend
        // automatiquement (voir javadoc de la classe). Verifie qu'aucune AUTRE affectation de cet
        // agent (reprise anterieure comprise) ne chevauche deja cette nouvelle periode.
        LocalDate nouvelleDateFin = requete.dateFinAffectation();
        verifierAucunChevauchementDate(agent.getId(), nouvelleDateDebut, nouvelleDateFin, affectationActive.getId());

        // La mission cible (et son chantier/code mission, trouves ou crees a la volee - evolution
        // du 2026-08-26) n'est creee qu'une fois toutes les gardes ci-dessus validees, pour ne
        // jamais laisser de donnees referentielles orphelines si la reaffectation echoue.
        Mission missionCible = missionService.creerMission(requete.codeChantier(), requete.libelleChantier(),
                requete.codeMission(), requete.libelleCodeMission(),
                requete.dateDebutPrevueMission(), requete.dateFinPrevueMission(), null, auteur);

        LocalDate dateInterruption = nouvelleDateDebut.minusDays(1);
        MotifInterruptionMission motif = motifInterruptionMissionRepository.findAll().stream()
                .filter(m -> m.getCode().equals(CODE_MOTIF_NOUVELLE_MISSION) && m.isActif())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Motif d'interruption '" + CODE_MOTIF_NOUVELLE_MISSION + "' introuvable ou inactif en base."));

        // "Avant" : l'affectation en cours est tronquee (comme avant l'evolution du 2026-08-27),
        // jamais supprimee ni ecrasee (RG-MIS-003).
        Mission missionPrecedente = affectationActive.getMission();
        LocalDate dateFinOriginaleA = affectationActive.getDateFinAffectation(); // null = periode ouverte
        StatutAffectation statutAvant = affectationActive.getStatutAffectation();
        affectationActive.setDateFinAffectation(dateInterruption);
        affectationActive.setStatutAffectation(StatutAffectation.INTERROMPUE);
        affectationActive.setMotifInterruption(motif);
        affectationActive.setCommentaireInterruption("Reaffectation automatique vers la mission "
                + missionCible.getCodeHN().getCode() + " a partir du " + nouvelleDateDebut + ".");
        affectationMissionRepository.saveAndFlush(affectationActive);
        missionService.interrompre(missionPrecedente, dateInterruption);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, affectationActive.getId(), auteur,
                TypeActionAudit.INTERRUPTION, statutAvant, StatutAffectation.INTERROMPUE,
                statutAvant.name(), StatutAffectation.INTERROMPUE.name());

        // "Pendant" : la nouvelle affectation, bornee si une date de fin a ete fournie.
        AffectationMission nouvelleAffectation = new AffectationMission();
        nouvelleAffectation.setAgent(agent);
        nouvelleAffectation.setMission(missionCible);
        nouvelleAffectation.setDateDebutAffectation(nouvelleDateDebut);
        nouvelleAffectation.setDateFinAffectation(nouvelleDateFin);
        nouvelleAffectation.setStatutAffectation(StatutAffectation.ACTIVE);
        nouvelleAffectation.setAffectationPrecedente(affectationActive);
        nouvelleAffectation.setCreePar(auteur);
        nouvelleAffectation = affectationMissionRepository.save(nouvelleAffectation);

        missionService.demarrerOuReprendre(missionCible);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, nouvelleAffectation.getId(), auteur,
                TypeActionAudit.REAFFECTATION, affectationActive.getId(), affectationMissionMapper.toDto(nouvelleAffectation),
                StatutAffectation.ACTIVE.name(), StatutAffectation.ACTIVE.name());

        // "Apres" : reprise automatique de la mission precedente au lendemain de la date de fin de
        // la nouvelle affectation, si celle-ci se terminait avant le propre terme (ou l'ouverture)
        // de l'affectation d'origine - decoupage complet du chevauchement (section 18-22, decision
        // confirmee explicitement). Une nouvelle date de fin absente sur la nouvelle affectation
        // (diversion permanente) reproduit exactement le comportement d'origine : aucune reprise.
        boolean reprisePossible = nouvelleDateFin != null && (dateFinOriginaleA == null || dateFinOriginaleA.isAfter(nouvelleDateFin));
        if (reprisePossible) {
            AffectationMission reprise = new AffectationMission();
            reprise.setAgent(agent);
            reprise.setMission(missionPrecedente);
            reprise.setDateDebutAffectation(nouvelleDateFin.plusDays(1));
            reprise.setDateFinAffectation(dateFinOriginaleA);
            reprise.setStatutAffectation(StatutAffectation.ACTIVE);
            reprise.setAffectationPrecedente(affectationActive);
            reprise.setCreePar(auteur);
            reprise = affectationMissionRepository.save(reprise);

            missionService.demarrerOuReprendre(missionPrecedente);

            auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, reprise.getId(), auteur,
                    TypeActionAudit.REAFFECTATION, affectationActive.getId(), affectationMissionMapper.toDto(reprise),
                    StatutAffectation.INTERROMPUE.name(), StatutAffectation.ACTIVE.name());
        }

        return affectationMissionMapper.toDto(nouvelleAffectation);
    }

    /**
     * Prolonge ou reduit la date de fin prevue d'une mission en cours
     * (evolution du 2026-08-26) - alternative a {@link #reaffecterPendantMissionEnCours}
     * lorsque le besoin reel n'est pas de faire naitre une nouvelle mission
     * (MIS-002) mais simplement d'ajuster l'echeance planifiee de la mission
     * ACTUELLE (MIS-001 reste MIS-001).
     *
     * <p><strong>Portee volontairement limitee</strong> : {@link Mission#getDateFinPrevue()}
     * est un champ de planification/reporting - il ne conditionne ni la
     * resolution d'une affectation active ({@link #resoudreActiveADate}, qui
     * reste ouverte tant que l'affectation est {@code ACTIVE}, independamment
     * de cette date) ni la generation des jours de pointage d'une FIPH (qui
     * depend exclusivement de l'affectation, voir {@code FiphVersionService#definirDateFin}).
     * Modifier cette date ne fait donc jamais apparaitre ou disparaitre de
     * jours de pointage par elle-meme.
     *
     * <p>Perimetre verifie via l'affectation ACTIVE de la mission (RG-HAB-003) -
     * une mission sans affectation active n'a rien a prolonger/reduire.
     */
    public MissionDto modifierDateFinPrevueMission(Long missionId, ModifierDateFinPrevueRequest requete, Utilisateur auteur) {
        Mission mission = missionService.chargerMission(missionId);
        verifierMissionOuverte(mission);

        AffectationMission affectationActive = affectationMissionRepository
                .findByMission_IdOrderByDateDebutAffectationAsc(missionId).stream()
                .filter(a -> a.getStatutAffectation() == StatutAffectation.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("RG-MIS-012",
                        "Cette mission n'a aucune affectation active : rien a prolonger ou reduire."));
        verifierPerimetre(auteur, affectationActive.getAgent());

        LocalDate nouvelleFin = requete.nouvelleDateFinPrevue();
        if (nouvelleFin.isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("RG-MIS-013",
                    "La nouvelle date de fin prevue (" + nouvelleFin + ") ne peut pas etre deja passee.");
        }
        if (nouvelleFin.isBefore(mission.getDateDebutPrevue())) {
            throw new BusinessRuleViolationException("RG-MIS-014",
                    "La date de fin prevue ne peut pas etre anterieure a la date de debut prevue de la mission ("
                            + mission.getDateDebutPrevue() + ").");
        }

        LocalDate ancienneFin = mission.getDateFinPrevue();
        boolean reduction = ancienneFin != null && nouvelleFin.isBefore(ancienneFin);
        if (reduction) {
            List<Pointage> pointagesBloquants = pointageRepository.trouverPointagesApresDateAvecHeures(missionId, nouvelleFin);
            if (!pointagesBloquants.isEmpty()) {
                String jours = pointagesBloquants.stream()
                        .map(p -> p.getDatePointage().toString()).distinct()
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new BusinessRuleViolationException("RG-MIS-015",
                        "Impossible de reduire la date de fin prevue en-deca du " + nouvelleFin
                                + " : des heures sont deja pointees pour les jours suivants : " + jours + ".");
            }
        }

        missionService.modifierDateFinPrevue(mission, nouvelleFin);

        auditService.enregistrer(EntiteAuditable.MISSION, mission.getId(), auteur, TypeActionAudit.MODIFICATION,
                ancienneFin != null ? ancienneFin.toString() : "non definie", nouvelleFin.toString(),
                mission.getStatut().name(), mission.getStatut().name());

        notificationService.notifierMissionModifiee(mission.getId(), affectationActive.getAgent(),
                "Mission " + mission.getCodeHN().getCode(), auteur);

        return missionService.obtenirParId(missionId);
    }

    /**
     * RG-HAB-003 / RG-SEC-002 (anti-IDOR) : toute action de gestion des
     * missions est bornee au perimetre (service) de l'habilitation active de
     * l'utilisateur - seul un Charge d'Affaires ou une personne habilitee
     * sur le SERVICE DE L'AGENT concerne peut agir, quel que soit le nombre
     * d'habilitations cumulees par ailleurs (RG-HAB-002). Un identifiant
     * d'affectation syntaxiquement valide mais hors perimetre produit un
     * refus d'acces (403), jamais un comportement silencieusement degrade.
     *
     * <p><strong>Exception Super Administrateur (evolution du 2026-08-26)</strong> :
     * voir la Javadoc equivalente dans {@code BonSortieService.verifierPerimetreGestionnaire}.
     */
    private void verifierPerimetre(Utilisateur auteur, Utilisateur agent) {
        List<Habilitation> habilitationsAuteur = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId());
        if (habilitationsAuteur.stream().anyMatch(this::estSuperAdministrateur)) {
            return;
        }
        if (agent.getService() == null) {
            // Bug reel corrige le 2026-08-26 : voir la Javadoc equivalente dans
            // BonSortieService.verifierPerimetreGestionnaire.
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a gerer les missions des agents du service de cet agent.");
        }
        Long serviceAgentId = agent.getService().getId();
        boolean habilite = habilitationsAuteur.stream()
                .anyMatch(h -> estRoleGestionnaire(h) && h.getService() != null
                        && h.getService().getId().equals(serviceAgentId));
        if (!habilite) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a gerer les missions des agents du service de cet agent.");
        }
    }

    private boolean estRoleGestionnaire(Habilitation habilitation) {
        String code = habilitation.getRoleMetier().getCode();
        return CodeRoleMetier.CHARGE_AFFAIRES.name().equals(code)
                || CodeRoleMetier.PERSONNE_HABILITEE.name().equals(code);
    }

    private boolean estSuperAdministrateur(Habilitation habilitation) {
        return CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(habilitation.getRoleMetier().getCode());
    }

    private void verifierMissionOuverte(Mission mission) {
        if (mission.getStatut() == StatutMission.TERMINEE) {
            throw new BusinessRuleViolationException("section-5.1",
                    "Impossible d'affecter un agent a une mission deja terminee.");
        }
    }

    /**
     * Empeche un agent d'etre affecte a deux missions sur des jours qui se
     * chevauchent - controle par PERIODE reelle (evolution du 2026-08-27, V16),
     * et non plus par le seul statut ACTIVE : depuis le decoupage des
     * missions chevauchantes (section 18-22 du brief "Evolution avancee..."),
     * un agent peut legitimement porter deux affectations ACTIVE simultanees
     * (l'une en cours, une reprise planifiee plus tard) tant que leurs
     * periodes ne se recouvrent jamais reellement.
     */
    private void verifierAucunChevauchementDate(Long agentId, LocalDate debut, LocalDate fin, Long exclureAffectationId) {
        LocalDate finEffective = fin != null ? fin : DATE_MAX_CHEVAUCHEMENT;
        List<AffectationMission> chevauchements = affectationMissionRepository.trouverChevauchements(
                agentId, exclureAffectationId != null ? exclureAffectationId : 0L, debut, finEffective);
        if (!chevauchements.isEmpty()) {
            AffectationMission conflit = chevauchements.get(0);
            throw new ConflictException("Cet agent possede deja une affectation sur une periode qui chevauche celle-ci ("
                    + conflit.getDateDebutAffectation() + " - "
                    + (conflit.getDateFinAffectation() != null ? conflit.getDateFinAffectation() : "en cours")
                    + "). Interrompez-la ou ajustez les dates avant d'en creer une nouvelle.");
        }
    }

    private AffectationMission chargerAffectation(Long id) {
        return affectationMissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AffectationMission", id));
    }
}
