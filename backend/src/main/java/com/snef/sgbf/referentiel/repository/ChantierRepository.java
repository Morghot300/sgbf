package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.Chantier;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link Chantier}. */
public interface ChantierRepository extends JpaRepository<Chantier, Long> {
}
