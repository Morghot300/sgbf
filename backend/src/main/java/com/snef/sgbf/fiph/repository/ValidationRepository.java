package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.Validation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces en lecture (creation uniquement) au journal des validations - voir {@link Validation}, append-only. */
public interface ValidationRepository extends JpaRepository<Validation, Long> {

    List<Validation> findByFiphVersion_IdOrderByDateValidationAsc(Long fiphVersionId);
}
