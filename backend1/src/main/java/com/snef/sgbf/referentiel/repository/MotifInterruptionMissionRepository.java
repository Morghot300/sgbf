package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.MotifInterruptionMission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link MotifInterruptionMission}. */
public interface MotifInterruptionMissionRepository extends JpaRepository<MotifInterruptionMission, Long> {

    List<MotifInterruptionMission> findByActifTrue();
}
