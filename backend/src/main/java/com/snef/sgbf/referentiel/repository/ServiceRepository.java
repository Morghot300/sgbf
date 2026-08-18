package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.Service;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link Service} (voir la note de nommage sur cette entite). */
public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByCodeService(String codeService);
}
