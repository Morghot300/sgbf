package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.Pointage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acces aux lignes de pointage journalier. */
public interface PointageRepository extends JpaRepository<Pointage, Long> {

    List<Pointage> findByFiphVersion_IdOrderByDatePointageAsc(Long fiphVersionId);

    Optional<Pointage> findByFiphVersion_IdAndDatePointage(Long fiphVersionId, LocalDate datePointage);

    /**
     * Dernier jour deja pointe pour un agent, toutes FIPH confondues -
     * utilise par {@code AffectationMissionService#reaffecterPendantMissionEnCours}
     * (evolution du 2026-08-20) pour refuser toute reaffectation retroactive :
     * jamais de reecriture silencieuse d'un pointage deja valide via un bon
     * de sortie (meme principe de non-ecrasement que RG-MIS-003/006).
     */
    @Query("SELECT MAX(p.datePointage) FROM Pointage p WHERE p.fiphVersion.fiph.agent.id = :agentId")
    Optional<LocalDate> trouverDernierJourPointe(@Param("agentId") Long agentId);

    /**
     * Jours deja pointes avec des heures reellement saisies, rattaches a
     * cette mission (via l'affectation), au-dela d'une date donnee - utilise
     * par {@code MissionService#modifierDateFinPrevue} pour refuser de
     * reduire la date de fin prevue d'une mission en-deca d'un travail deja
     * enregistre (meme principe anti-retroactivite que RG-MIS-011).
     */
    @Query("SELECT p FROM Pointage p WHERE p.affectationMission.mission.id = :missionId "
            + "AND p.datePointage > :date AND (p.heuresNormales <> 0 OR p.heuresSup <> 0)")
    List<Pointage> trouverPointagesApresDateAvecHeures(@Param("missionId") Long missionId, @Param("date") LocalDate date);
}
