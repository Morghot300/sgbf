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
import com.snef.sgbf.mission.dto.ReaffecterRequest;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.mission.entity.StatutAffectation;
import com.snef.sgbf.mission.entity.StatutMission;
import com.snef.sgbf.mission.mapper.AffectationMissionMapper;
import com.snef.sgbf.mission.repository.AffectationMissionRepository;
import com.snef.sgbf.referentiel.entity.MotifInterruptionMission;
import com.snef.sgbf.referentiel.repository.MotifInterruptionMissionRepository;
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

    private final AffectationMissionRepository affectationMissionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MotifInterruptionMissionRepository motifInterruptionMissionRepository;
    private final HabilitationRepository habilitationRepository;
    private final MissionService missionService;
    private final AffectationMissionMapper affectationMissionMapper;
    private final AuditService auditService;
    private final PointageRepository pointageRepository;

    public AffectationMissionService(AffectationMissionRepository affectationMissionRepository,
                                      UtilisateurRepository utilisateurRepository,
                                      MotifInterruptionMissionRepository motifInterruptionMissionRepository,
                                      HabilitationRepository habilitationRepository,
                                      MissionService missionService,
                                      AffectationMissionMapper affectationMissionMapper,
                                      AuditService auditService,
                                      PointageRepository pointageRepository) {
        this.affectationMissionRepository = affectationMissionRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.motifInterruptionMissionRepository = motifInterruptionMissionRepository;
        this.habilitationRepository = habilitationRepository;
        this.missionService = missionService;
        this.affectationMissionMapper = affectationMissionMapper;
        this.auditService = auditService;
        this.pointageRepository = pointageRepository;
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
        verifierAucuneAffectationActive(agent.getId());

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
        verifierAucuneAffectationActive(affectationPrecedente.getAgent().getId());

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
     * creee - reproduisant exactement l'exemple du brief (mission 1 du 01/08
     * au 20/08, nouvelle affectation le 11/08 -&gt; mission 1 se termine le
     * 10/08, mission 2 commence le 11/08).
     *
     * <p><strong>Retroactivite refusee (decision confirmee)</strong> : la
     * nouvelle date de debut doit etre strictement posterieure au dernier
     * jour deja pointe pour cet agent ({@link PointageRepository#trouverDernierJourPointe}),
     * toutes FIPH confondues - jamais de reecriture silencieuse d'un
     * pointage deja valide via un bon de sortie, en coherence avec le
     * principe de non-ecrasement deja applique ailleurs (RG-MIS-003/006).
     *
     * <p><strong>Aucun recalcul FIPH necessaire</strong> : chaque jour est
     * rattache a l'affectation active CE JOUR-LA au moment ou son bon de
     * sortie est valide, jamais retroactivement en bloc (voir Javadoc de
     * {@link com.snef.sgbf.fiph.entity.Pointage}) - les bons de sortie
     * valides a partir de la nouvelle date se rattacheront donc
     * naturellement, un par un, a la nouvelle affectation.
     */
    public AffectationMissionDto reaffecterPendantMissionEnCours(ReaffecterMiMissionRequest requete, Utilisateur auteur) {
        Utilisateur agent = utilisateurRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", requete.agentId()));
        verifierPerimetre(auteur, agent);

        AffectationMission affectationActive = affectationMissionRepository
                .findByAgent_IdAndStatutAffectation(agent.getId(), StatutAffectation.ACTIVE)
                .orElseThrow(() -> new BusinessRuleViolationException("RG-MIS-009",
                        "Cet agent n'a aucune affectation active a reaffecter. "
                                + "Utilisez la creation d'affectation initiale (POST /affectations-mission)."));

        Mission missionCible = missionService.chargerMission(requete.missionCibleId());
        verifierMissionOuverte(missionCible);

        LocalDate nouvelleDateDebut = requete.dateDebutAffectation();
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

        LocalDate dateInterruption = nouvelleDateDebut.minusDays(1);
        MotifInterruptionMission motif = motifInterruptionMissionRepository.findAll().stream()
                .filter(m -> m.getCode().equals(CODE_MOTIF_NOUVELLE_MISSION) && m.isActif())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Motif d'interruption '" + CODE_MOTIF_NOUVELLE_MISSION + "' introuvable ou inactif en base."));

        StatutAffectation statutAvant = affectationActive.getStatutAffectation();
        affectationActive.setDateFinAffectation(dateInterruption);
        affectationActive.setStatutAffectation(StatutAffectation.INTERROMPUE);
        affectationActive.setMotifInterruption(motif);
        affectationActive.setCommentaireInterruption("Reaffectation automatique vers la mission "
                + missionCible.getCodeHN().getCode() + " a partir du " + nouvelleDateDebut + ".");
        // saveAndFlush (et non save) : l'index unique uq_affectation_agent_actif
        // (au plus une affectation ACTIVE par agent, voir migration) doit voir
        // cette ligne repassee a INTERROMPUE AVANT l'insertion de la nouvelle
        // ligne ACTIVE ci-dessous - Hibernate ordonnance par defaut les INSERT
        // avant les UPDATE au sein d'un meme flush, ce qui violerait sinon
        // transitoirement la contrainte (deux lignes ACTIVE simultanees).
        affectationMissionRepository.saveAndFlush(affectationActive);
        missionService.interrompre(affectationActive.getMission(), dateInterruption);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, affectationActive.getId(), auteur,
                TypeActionAudit.INTERRUPTION, statutAvant, StatutAffectation.INTERROMPUE,
                statutAvant.name(), StatutAffectation.INTERROMPUE.name());

        AffectationMission nouvelleAffectation = new AffectationMission();
        nouvelleAffectation.setAgent(agent);
        nouvelleAffectation.setMission(missionCible);
        nouvelleAffectation.setDateDebutAffectation(nouvelleDateDebut);
        nouvelleAffectation.setStatutAffectation(StatutAffectation.ACTIVE);
        nouvelleAffectation.setAffectationPrecedente(affectationActive);
        nouvelleAffectation.setCreePar(auteur);
        nouvelleAffectation = affectationMissionRepository.save(nouvelleAffectation);

        missionService.demarrerOuReprendre(missionCible);

        auditService.enregistrer(EntiteAuditable.AFFECTATION_MISSION, nouvelleAffectation.getId(), auteur,
                TypeActionAudit.REAFFECTATION, affectationActive.getId(), affectationMissionMapper.toDto(nouvelleAffectation),
                StatutAffectation.ACTIVE.name(), StatutAffectation.ACTIVE.name());
        return affectationMissionMapper.toDto(nouvelleAffectation);
    }

    /**
     * RG-HAB-003 / RG-SEC-002 (anti-IDOR) : toute action de gestion des
     * missions est bornee au perimetre (service) de l'habilitation active de
     * l'utilisateur - seul un Charge d'Affaires ou une personne habilitee
     * sur le SERVICE DE L'AGENT concerne peut agir, quel que soit le nombre
     * d'habilitations cumulees par ailleurs (RG-HAB-002). Un identifiant
     * d'affectation syntaxiquement valide mais hors perimetre produit un
     * refus d'acces (403), jamais un comportement silencieusement degrade.
     */
    private void verifierPerimetre(Utilisateur auteur, Utilisateur agent) {
        Long serviceAgentId = agent.getService().getId();
        boolean habilite = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
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

    private void verifierMissionOuverte(Mission mission) {
        if (mission.getStatut() == StatutMission.TERMINEE) {
            throw new BusinessRuleViolationException("section-5.1",
                    "Impossible d'affecter un agent a une mission deja terminee.");
        }
    }

    /**
     * Double controle (applicatif ici, index unique en base - voir
     * migration) : au plus une affectation ACTIVE par agent a un instant
     * donne. Le controle applicatif permet un message clair ; l'index
     * unique reste le filet de securite reel (section 20.1).
     */
    private void verifierAucuneAffectationActive(Long agentId) {
        if (affectationMissionRepository.findByAgent_IdAndStatutAffectation(agentId, StatutAffectation.ACTIVE).isPresent()) {
            throw new ConflictException("Cet agent possede deja une affectation active. "
                    + "Interrompez-la avant d'en creer une nouvelle.");
        }
    }

    private AffectationMission chargerAffectation(Long id) {
        return affectationMissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AffectationMission", id));
    }
}
