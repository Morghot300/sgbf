package com.snef.sgbf.mission.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.dto.CreerMissionRequest;
import com.snef.sgbf.mission.dto.MissionDto;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.mission.entity.StatutMission;
import com.snef.sgbf.mission.mapper.MissionMapper;
import com.snef.sgbf.mission.repository.MissionRepository;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
import com.snef.sgbf.referentiel.repository.CodeHNRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des missions (identite stable - section 5.1). Le cycle de vie
 * operationnel (affectation, interruption, reaffectation) est porte par
 * {@link com.snef.sgbf.mission.service.AffectationMissionService}, qui
 * s'appuie sur ce service pour faire evoluer {@link Mission#getStatut()}
 * lorsque necessaire (ex. passage a {@code EN_COURS} des la premiere
 * affectation, ou a {@code INTERROMPUE} lors d'une interruption).
 *
 * <p>Reserve, cote controleur, au Charge d'Affaires et a la personne
 * habilitee (section 16, cas d'utilisation "Affecter un agent a une
 * mission").
 */
@org.springframework.stereotype.Service
@Transactional
public class MissionService {

    private final MissionRepository missionRepository;
    private final CodeHNRepository codeHNRepository;
    private final ChantierRepository chantierRepository;
    private final MissionMapper missionMapper;
    private final AuditService auditService;

    public MissionService(MissionRepository missionRepository, CodeHNRepository codeHNRepository,
                           ChantierRepository chantierRepository, MissionMapper missionMapper,
                           AuditService auditService) {
        this.missionRepository = missionRepository;
        this.codeHNRepository = codeHNRepository;
        this.chantierRepository = chantierRepository;
        this.missionMapper = missionMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MissionDto> listerToutes() {
        return missionRepository.findAll().stream().map(missionMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MissionDto obtenirParId(Long id) {
        return missionMapper.toDto(chargerMission(id));
    }

    /**
     * Reconstitue la chaine complete d'une mission par
     * {@code missionPrecedente} (RG-MIS-006) : de la plus ancienne a la plus
     * recente. Repond au cas d'utilisation "Consulter l'historique d'une
     * mission" (section 16) sans recourir a une table d'historique separee.
     */
    @Transactional(readOnly = true)
    public List<MissionDto> consulterChaineHistorique(Long missionId) {
        List<Mission> chaine = new ArrayList<>();
        Mission courante = chargerMission(missionId);
        while (courante != null) {
            chaine.add(courante);
            courante = courante.getMissionPrecedente();
        }
        java.util.Collections.reverse(chaine);
        return chaine.stream().map(missionMapper::toDto).toList();
    }

    public MissionDto creer(CreerMissionRequest requete, Utilisateur auteur) {
        Mission missionPrecedente = requete.missionPrecedenteId() != null
                ? chargerMission(requete.missionPrecedenteId())
                : null;
        Mission mission = creerMission(requete.codeChantier(), requete.libelleChantier(),
                requete.codeMission(), requete.libelleCodeMission(),
                requete.dateDebutPrevue(), requete.dateFinPrevue(), missionPrecedente, auteur);
        return missionMapper.toDto(mission);
    }

    /**
     * Creation partagee d'une mission a partir de codes chantier/mission
     * saisis librement (evolution du 2026-08-26, section "les mission et
     * code mission ne seront pas des liste deroulante mais une zone texte") -
     * reutilisee par {@link #creer} (creation directe) et par
     * {@link com.snef.sgbf.mission.service.AffectationMissionService#reaffecterPendantMissionEnCours}
     * (une reaffectation "vers une nouvelle mission" fait naitre, par
     * definition, une mission neuve - jamais la reutilisation d'une mission
     * existante prise dans une liste).
     */
    Mission creerMission(String codeChantier, String libelleChantier, String codeMission, String libelleCodeMission,
                          LocalDate dateDebutPrevue, LocalDate dateFinPrevue, Mission missionPrecedente, Utilisateur auteur) {
        Chantier chantier = resoudreOuCreerChantier(codeChantier, libelleChantier);
        CodeHN codeHN = resoudreOuCreerCodeHN(codeMission, libelleCodeMission, chantier);

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(dateDebutPrevue);
        mission.setDateFinPrevue(dateFinPrevue);
        mission.setStatut(StatutMission.PLANIFIEE);
        mission.setMissionPrecedente(missionPrecedente);

        mission = missionRepository.save(mission);
        auditService.enregistrer(EntiteAuditable.MISSION, mission.getId(), auteur,
                TypeActionAudit.CREATION, null, missionMapper.toDto(mission), null, mission.getStatut().name());
        return mission;
    }

    /** Reutilise le chantier existant portant ce code, ou en cree un nouveau (libelle repris du code si non fourni). */
    private Chantier resoudreOuCreerChantier(String codeChantier, String libelleChantier) {
        return chantierRepository.findByCodeAffaire(codeChantier).orElseGet(() -> {
            Chantier nouveau = new Chantier();
            nouveau.setCodeAffaire(codeChantier);
            nouveau.setLibelle(libelleChantier != null && !libelleChantier.isBlank() ? libelleChantier : codeChantier);
            nouveau.setActif(true);
            return chantierRepository.save(nouveau);
        });
    }

    /**
     * Reutilise le code mission existant portant ce code, ou en cree un
     * nouveau rattache au chantier resolu ci-dessus. {@code CodeHN.code} est
     * unique pour toute l'application (pas seulement par chantier) : un code
     * deja utilise sous un AUTRE chantier est refuse plutot que d'etre
     * silencieusement rattache au mauvais chantier.
     */
    private CodeHN resoudreOuCreerCodeHN(String codeMission, String libelleCodeMission, Chantier chantier) {
        return codeHNRepository.findByCode(codeMission)
                .map(existant -> {
                    if (!existant.getChantier().getId().equals(chantier.getId())) {
                        throw new BusinessRuleViolationException("RG-MIS-015",
                                "Le code mission \"" + codeMission + "\" existe deja pour le chantier \""
                                        + existant.getChantier().getLibelle() + "\" - il ne peut pas etre reutilise pour un autre chantier.");
                    }
                    return existant;
                })
                .orElseGet(() -> {
                    CodeHN nouveau = new CodeHN();
                    nouveau.setCode(codeMission);
                    nouveau.setLibelle(libelleCodeMission != null && !libelleCodeMission.isBlank() ? libelleCodeMission : codeMission);
                    nouveau.setChantier(chantier);
                    return codeHNRepository.save(nouveau);
                });
    }

    /**
     * Fait passer une mission a EN_COURS des lors qu'un agent lui est
     * effectivement affecte - qu'il s'agisse d'une premiere affectation
     * (mission PLANIFIEE) ou d'une reprise apres interruption (mission
     * INTERROMPUE, section 6.1 : une reaffectation vers la meme mission
     * relance son cycle). {@link Mission#getDateFinReelle()} est reinitialisee
     * dans ce dernier cas : la mission n'est plus close des lors qu'elle
     * reprend. N'a aucun effet sur une mission deja EN_COURS ou TERMINEE.
     */
    void demarrerOuReprendre(Mission mission) {
        if (mission.getStatut() == StatutMission.PLANIFIEE || mission.getStatut() == StatutMission.INTERROMPUE) {
            mission.setStatut(StatutMission.EN_COURS);
            mission.setDateFinReelle(null);
            missionRepository.save(mission);
        }
    }

    /** Cloture une mission suite a l'interruption de son affectation active (section 6.1) - jamais appele directement par un controleur. */
    void interrompre(Mission mission, java.time.LocalDate dateInterruption) {
        mission.setStatut(StatutMission.INTERROMPUE);
        mission.setDateFinReelle(dateInterruption);
        missionRepository.save(mission);
    }

    /**
     * Prolonge ou reduit la date de fin prevue d'une mission en cours
     * (evolution du 2026-08-26) - simple mutation de champ, jamais appelee
     * directement par un controleur : tous les controles metier (perimetre,
     * date deja passee, coherence avec le travail deja pointe) sont a la
     * charge de {@code AffectationMissionService#modifierDateFinPrevueMission},
     * seul appelant. {@link Mission#getDateFinPrevue()} reste purement
     * informatif/planning : elle ne conditionne ni la resolution d'une
     * affectation active ({@link AffectationMissionService#resoudreActiveADate})
     * ni la generation des jours de pointage d'une FIPH, qui dependent
     * exclusivement de {@link com.snef.sgbf.mission.entity.AffectationMission}.
     */
    void modifierDateFinPrevue(Mission mission, java.time.LocalDate nouvelleDateFinPrevue) {
        mission.setDateFinPrevue(nouvelleDateFinPrevue);
        missionRepository.save(mission);
    }

    Mission chargerMission(Long id) {
        return missionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Mission", id));
    }
}
