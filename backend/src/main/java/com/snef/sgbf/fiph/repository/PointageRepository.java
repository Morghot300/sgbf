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
}
