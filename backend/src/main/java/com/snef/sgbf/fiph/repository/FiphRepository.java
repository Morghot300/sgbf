package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.FIPH;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Acces aux FIPH (identite stable). {@link JpaSpecificationExecutor} pour la recherche multicritere recommandee section 30. */
public interface FiphRepository extends JpaRepository<FIPH, Long>, JpaSpecificationExecutor<FIPH> {

    /** RG-FIPH-002 : au plus une FIPH par couple (agent, periode hebdomadaire). */
    Optional<FIPH> findByAgent_IdAndAnneeAndNumeroSemaine(Long agentId, int annee, int numeroSemaine);

    List<FIPH> findByAgent_Id(Long agentId);
}
