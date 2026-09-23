package com.snef.sgbf.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emission et verification des jetons d'acces JWT (JJWT / HS256).
 *
 * <p>Seul le jeton d'ACCES est traite ici et reste entierement stateless : sa
 * seule validite tient a sa signature et a sa date d'expiration courte
 * (15 minutes par defaut, voir {@code app.security.jwt.access-token-ttl-minutes}).
 * Le jeton de RAFRAICHISSEMENT, lui, est gere par
 * {@link RefreshTokenService} et reste revocable cote serveur (voir sa
 * Javadoc pour la justification de cette asymetrie).
 *
 * <p>Le sujet ({@code sub}) du jeton porte l'identifiant technique (id) de
 * l'{@link com.snef.sgbf.identite.entity.Utilisateur}, pas son identifiant de
 * connexion : {@link JwtAuthenticationFilter} recharge systematiquement
 * l'utilisateur et ses habilitations depuis la base a partir de cet id (voir
 * la justification dans {@link UserDetailsServiceImpl}), le jeton ne porte
 * donc volontairement aucune revendication de role - un role revoque ne doit
 * jamais rester utilisable jusqu'a expiration du jeton.
 */
@Component
public class JwtService {

    private final SecretKey cleSignature;
    private final long dureeAccesMinutes;

    public JwtService(@Value("${app.security.jwt.secret}") String secretBase64,
                       @Value("${app.security.jwt.access-token-ttl-minutes}") long dureeAccesMinutes) {
        // La cle doit faire au moins 256 bits pour HS256 - voir backend/.env.example
        // pour la commande de generation recommandee.
        this.cleSignature = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secretBase64));
        this.dureeAccesMinutes = dureeAccesMinutes;
    }

    public String genererJetonAcces(Long utilisateurId) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(utilisateurId))
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(dureeAccesMinutes, ChronoUnit.MINUTES)))
                .signWith(cleSignature)
                .compact();
    }

    /** @return l'id utilisateur porte par le jeton si sa signature et son expiration sont valides, sinon vide. */
    public java.util.Optional<Long> extraireUtilisateurIdSiValide(String jeton) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(cleSignature)
                    .build()
                    .parseSignedClaims(jeton)
                    .getPayload();
            return java.util.Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // Jeton expire, signature invalide, ou malforme : dans tous les cas,
            // l'appelant doit simplement considerer la requete comme non authentifiee.
            return java.util.Optional.empty();
        }
    }

    public long getDureeAccesMinutes() {
        return dureeAccesMinutes;
    }
}
