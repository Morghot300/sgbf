package com.snef.sgbf.identite.repository;

import com.snef.sgbf.identite.entity.Utilisateur;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux comptes applicatifs. */
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByIdentifiant(String identifiant);

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByIdentifiant(String identifiant);

    boolean existsByEmail(String email);

    /** Personnel d'un service donne - utilise pour la selection des personnes a bord eligibles (evolution du 2026-08-19, Lot 4). */
    List<Utilisateur> findByService_Id(Long serviceId);
}
