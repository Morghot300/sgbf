package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link Vehicule}. */
public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {
}
