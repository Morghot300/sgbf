package com.snef.sgbf.security;

import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.security.entity.RefreshToken;
import com.snef.sgbf.security.entity.RefreshTokenRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emission, verification et rotation des jetons de rafraichissement.
 *
 * <p>Format du jeton remis au client : {@code "<id>.<secret>"}, ou
 * {@code id} est l'identifiant (UUID) de la ligne {@link RefreshToken} et
 * {@code secret} une valeur aleatoire de 256 bits. Seul le hash BCrypt du
 * secret est persiste - c'est le motif "selecteur/verifieur" classique : il
 * permet de retrouver la ligne par une recherche indexee sur {@code id} (pas
 * de parcours de table pour comparer un hash), tout en ne conservant jamais
 * le secret en clair, exactement comme un mot de passe.
 *
 * <p>A chaque rafraichissement reussi, l'ancien jeton est revoque et un
 * nouveau est emis (rotation) : un jeton de rafraichissement ne peut donc
 * jamais servir deux fois, ce qui permet de detecter une eventuelle
 * reutilisation frauduleuse (jeton vole puis rejoue apres coup par son
 * proprietaire legitime - les deux tentatives ne peuvent alors plus reussir
 * toutes les deux).
 */
@Service
@Transactional
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final long dureeValiditeJours;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.security.jwt.refresh-token-ttl-days}") long dureeValiditeJours) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.dureeValiditeJours = dureeValiditeJours;
    }

    /** Emet un nouveau jeton de rafraichissement pour l'utilisateur donne et retourne sa valeur complete ("id.secret"). */
    public String emettre(Utilisateur utilisateur) {
        byte[] secretBrut = new byte[32];
        RANDOM.nextBytes(secretBrut);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBrut);

        RefreshToken jeton = new RefreshToken();
        jeton.setId(UUID.randomUUID().toString());
        jeton.setUtilisateur(utilisateur);
        jeton.setTokenHash(passwordEncoder.encode(secret));
        jeton.setDateExpiration(LocalDateTime.now().plusDays(dureeValiditeJours));
        refreshTokenRepository.save(jeton);

        return jeton.getId() + "." + secret;
    }

    /**
     * Verifie un jeton de rafraichissement presente par le client et, s'il
     * est valide, le revoque immediatement (rotation - voir Javadoc de
     * classe). Retourne l'utilisateur concerne si la verification reussit.
     */
    public Optional<Utilisateur> verifierEtRevoquer(String jetonComplet) {
        String[] parties = jetonComplet.split("\\.", 2);
        if (parties.length != 2) {
            return Optional.empty();
        }
        String id = parties[0];
        String secret = parties[1];

        Optional<RefreshToken> jetonOpt = refreshTokenRepository.findByIdAndRevoqueFalse(id);
        if (jetonOpt.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken jeton = jetonOpt.get();
        if (!jeton.estValide() || !passwordEncoder.matches(secret, jeton.getTokenHash())) {
            return Optional.empty();
        }

        jeton.setRevoque(true);
        refreshTokenRepository.save(jeton);
        return Optional.of(jeton.getUtilisateur());
    }

    /** Revoque tous les jetons actifs d'un utilisateur (deconnexion explicite, ou compromission suspectee). */
    public void revoquerTousPourUtilisateur(Long utilisateurId) {
        List<RefreshToken> jetons = refreshTokenRepository.findByUtilisateur_IdAndRevoqueFalse(utilisateurId);
        jetons.forEach(j -> j.setRevoque(true));
        refreshTokenRepository.saveAll(jetons);
    }
}
