package com.snef.sgbf.referentiel.repository;

import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux donnees du referentiel {@link RoleMetier}. */
public interface RoleMetierRepository extends JpaRepository<RoleMetier, Long> {

    Optional<RoleMetier> findByCode(String code);

    default Optional<RoleMetier> findByCode(CodeRoleMetier code) {
        return findByCode(code.name());
    }
}
