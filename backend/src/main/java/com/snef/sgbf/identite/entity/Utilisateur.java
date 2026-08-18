package com.snef.sgbf.identite.entity;

import com.snef.sgbf.referentiel.entity.Service;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Compte applicatif porteur d'une identite (section 18 du document source).
 *
 * <p>Distincte de {@link com.snef.sgbf.identite.entity.Agent} (referentiel
 * RH) : un {@code Agent} n'implique pas necessairement un {@code Utilisateur}
 * (par exemple un agent de terrain sans acces direct au systeme, dont le bon
 * de sortie est saisi pour son compte).
 *
 * <p>Ne porte, par elle-meme, aucun droit : les droits proviennent
 * exclusivement des {@link Habilitation} actives rattachees a ce compte
 * (RG-HAB-001).
 *
 * <p>L'authentification repose uniquement sur {@link #identifiant} (ou
 * {@link #email}) et le mot de passe (decision du 2026-08-17, section K de
 * l'analyse fonctionnelle) : aucun second facteur n'est demande. Un
 * mecanisme de code a usage unique envoye par e-mail a brievement existe
 * puis a ete entierement retire le meme jour ; le champ {@code mfaActif} qui
 * l'avait precede (activation optionnelle par compte) avait deja ete
 * supprime avant cela (voir migration {@code V6__mfa_obligatoire.sql}).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identifiant", nullable = false, unique = true, length = 60)
    private String identifiant;

    /** Adresse e-mail du compte (identifiant alternatif possible a la connexion). */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** Hash BCrypt du mot de passe - jamais le mot de passe en clair (voir {@code security.PasswordConfig}). */
    @Column(name = "mot_de_passe_hash", nullable = false, length = 255)
    private String motDePasseHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_compte", nullable = false, length = 20)
    private StatutCompte statutCompte = StatutCompte.ACTIF;

    // EAGER deliberement : Utilisateur.service est lu apres la fermeture de la
    // session Hibernate dans plusieurs cas legitimes (GET /api/auth/me,
    // UtilisateurMapper) puisque l'entite reste attachee au principal
    // d'authentification bien au-dela de la transaction qui l'a chargee. Une
    // reference simple vers un petit referentiel ne justifie pas le risque de
    // LazyInitializationException que EAGER elimine ici par construction.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private Service service;

    /** Verrouillage optimiste sur les mises a jour de compte (mot de passe, statut...). */
    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;
}
