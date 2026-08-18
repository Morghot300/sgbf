package com.snef.sgbf.security.entity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux jetons de rafraichissement. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByIdAndRevoqueFalse(String id);

    List<RefreshToken> findByUtilisateur_IdAndRevoqueFalse(Long utilisateurId);
}
