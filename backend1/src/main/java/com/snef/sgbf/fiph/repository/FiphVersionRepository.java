package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.FIPHVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux versions de FIPH. */
public interface FiphVersionRepository extends JpaRepository<FIPHVersion, Long> {

    List<FIPHVersion> findByFiph_IdOrderByNumeroVersionAsc(Long fiphId);
}
