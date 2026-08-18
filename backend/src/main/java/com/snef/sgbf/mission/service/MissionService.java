package com.snef.sgbf.mission.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
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
        CodeHN codeHN = codeHNRepository.findById(requete.codeHNId())
                .orElseThrow(() -> ResourceNotFoundException.of("CodeHN", requete.codeHNId()));
        Chantier chantier = chantierRepository.findById(requete.chantierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Chantier", requete.chantierId()));

        Mission mission = new Mission();
        mission.setCodeHN(codeHN);
        mission.setChantier(chantier);
        mission.setDateDebutPrevue(requete.dateDebutPrevue());
        mission.setDateFinPrevue(requete.dateFinPrevue());
        mission.setStatut(StatutMission.PLANIFIEE);

        if (requete.missionPrecedenteId() != null) {
            mission.setMissionPrecedente(chargerMission(requete.missionPrecedenteId()));
        }

        mission = missionRepository.save(mission);
        auditService.enregistrer(EntiteAuditable.MISSION, mission.getId(), auteur,
                TypeActionAudit.CREATION, null, missionMapper.toDto(mission), null, mission.getStatut().name());
        return missionMapper.toDto(mission);
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

    Mission chargerMission(Long id) {
        return missionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Mission", id));
    }
}
