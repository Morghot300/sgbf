package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.CodeHN;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link CodeHN} (codes mission). */
public interface CodeHNRepository extends JpaRepository<CodeHN, Long> {

    Optional<CodeHN> findByCode(String code);
}
