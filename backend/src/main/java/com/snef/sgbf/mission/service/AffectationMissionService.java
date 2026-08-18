package com.snef.sgbf.mission.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.HabilitationRepository;
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
import java.time.LocalDate;
import java.util.List;
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
 * <p><strong>Portee de ce module :</strong> l'impact d'une interruption sur
 * une FIPH deja generee (RG-FIPH-022 a 024, section 6.4) sera cable lors du
 * developpement du module FIPH (non encore construit) - ce service ne gere
 * ici que le cycle de vie de la mission et de l'affectation elles-memes,
 * conformement au developpement module par module annonce.
 */
@org.springframework.stereotype.Service
@Transactional
public class AffectationMissionService {

    private static final String CODE_MOTIF_AUTRE = "AUTRE";

    private final AffectationMissionRepository affectationMissionRepository;
    private final AgentRepository agentRepository;
    private final MotifInterruptionMissionRepository motifInterruptionMissionRepository;
    private final HabilitationRepository habilitationRepository;
    private final MissionService missionService;
    private final AffectationMissionMapper affectationMissionMapper;
    private final AuditService auditService;

    public AffectationMissionService(AffectationMissionRepository affectationMissionRepository,
                                      AgentRepository agentRepository,
                                      MotifInterruptionMissionRepository motifInterruptionMissionRepository,
                                      HabilitationRepository habilitationRepository,
                                      MissionService missionService,
                                      AffectationMissionMapper affectationMissionMapper,
                                      AuditService auditService) {
        this.affectationMissionRepository = affectationMissionRepository;
        this.agentRepository = agentRepository;
        this.motifInterruptionMissionRepository = motifInterruptionMissionRepository;
        this.habilitationRepository = habilitationRepository;
        this.missionService = missionService;
        this.affectationMissionMapper = affectationMissionMapper;
        this.auditService = auditService;
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
        Agent agent = agentRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", requete.agentId()));
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
     * RG-HAB-003 / RG-SEC-002 (anti-IDOR) : toute action de gestion des
     * missions est bornee au perimetre (service) de l'habilitation active de
     * l'utilisateur - seul un Charge d'Affaires ou une personne habilitee
     * sur le SERVICE DE L'AGENT concerne peut agir, quel que soit le nombre
     * d'habilitations cumulees par ailleurs (RG-HAB-002). Un identifiant
     * d'affectation syntaxiquement valide mais hors perimetre produit un
     * refus d'acces (403), jamais un comportement silencieusement degrade.
     */
    private void verifierPerimetre(Utilisateur auteur, Agent agent) {
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
