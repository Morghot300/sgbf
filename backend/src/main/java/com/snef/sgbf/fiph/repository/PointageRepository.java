package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.Pointage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux lignes de pointage journalier. */
public interface PointageRepository extends JpaRepository<Pointage, Long> {

    List<Pointage> findByFiphVersion_IdOrderByDatePointageAsc(Long fiphVersionId);

    Optional<Pointage> findByFiphVersion_IdAndDatePointage(Long fiphVersionId, LocalDate datePointage);
}
