package com.snef.sgbf.identite.repository;

import com.snef.sgbf.identite.entity.Utilisateur;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux comptes applicatifs. */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByIdentifiant(String identifiant);

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByIdentifiant(String identifiant);

    boolean existsByEmail(String email);
}
