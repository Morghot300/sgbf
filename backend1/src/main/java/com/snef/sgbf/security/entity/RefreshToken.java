package com.snef.sgbf.security.entity;

import com.snef.sgbf.identite.entity.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Jeton de rafraichissement JWT, stocke sous forme hashee uniquement (jamais
 * en clair, au meme titre qu'un mot de passe - voir {@code security.RefreshTokenService}).
 *
 * <p>Contrairement au jeton d'acces ({@link com.snef.sgbf.security.JwtService}),
 * entierement stateless, ce jeton est deliberement statefull : c'est ce qui
 * permet une deconnexion explicite ("logout") et une revocation immediate en
 * cas de compromission suspectee ou de suspension d'un compte (voir
 * {@code UtilisateurService.changerStatut}). Le cout - une table et une
 * verification en base a chaque rafraichissement, une operation rare comparee
 * aux appels API courants - est juge largement acceptable au regard du gain
 * de controle.
 *
 * <p><strong>Sans expiration temporelle</strong> (evolution du 2026-08-26,
 * pour tous les comptes) : {@link #dateExpiration} est desormais {@code null}
 * a l'emission - seules une deconnexion explicite ou une revocation
 * administrative terminent une session, jamais le seul ecoulement du temps.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    /** {@code null} = jeton sans expiration temporelle (evolution du 2026-08-26). */
    @Column(name = "date_expiration")
    private LocalDateTime dateExpiration;

    @Column(name = "revoque", nullable = false)
    private boolean revoque = false;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    public boolean estValide() {
        return !revoque && (dateExpiration == null || dateExpiration.isAfter(LocalDateTime.now()));
    }
}
