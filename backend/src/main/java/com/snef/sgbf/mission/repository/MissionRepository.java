package com.snef.sgbf.mission.repository;

import com.snef.sgbf.mission.entity.Mission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux missions. */
public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByStatut(com.snef.sgbf.mission.entity.StatutMission statut);
}
