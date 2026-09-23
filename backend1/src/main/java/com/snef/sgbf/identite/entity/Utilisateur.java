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
 * Personnel de l'entreprise (evolution du 2026-08-19, "un utilisateur est
 * obligatoirement un agent") - une seule ligne par personne reelle,
 * combinant son identite RH (nom, prenom, matricule, service) et,
 * lorsqu'elle en dispose, son compte applicatif (identifiant, email, mot de
 * passe, statut). Avant cette evolution, ces deux aspects vivaient dans
 * deux entites separees ({@code Utilisateur} et un ancien {@code Agent}
 * aujourd'hui supprime) reliees par un lien 0..1 facultatif - source
 * recurrente d'incoherences (creation en deux etapes, doublons, FIPH/Bon de
 * Sortie devant traverser {@code Agent -> Utilisateur} pour la moindre
 * verification de droits).
 *
 * <p><strong>Le compte applicatif reste optionnel</strong> : une personne de
 * terrain peut exister dans ce referentiel sans jamais se connecter
 * elle-meme au systeme (son bon de sortie et sa FIPH sont alors geres pour
 * son compte par le Charge d'Affaires ou la personne habilitee) - voir
 * {@link #possedeCompteApplicatif()}, qui remplace l'ancien controle
 * {@code agent.getUtilisateur() != null}. Dans ce cas, {@link #identifiant},
 * {@link #email} et {@link #motDePasseHash} restent {@code null} : la
 * personne existe, mais aucune authentification n'est possible pour elle.
 *
 * <p>Ne porte, par elle-meme, aucun droit : les droits proviennent
 * exclusivement des {@link Habilitation} actives rattachees a cette personne
 * (RG-HAB-001). {@link #matricule} reste l'identifiant fonctionnel pivot
 * pour la resolution fiable d'une personne (RG-PAB-001), notamment pour la
 * deduplication des personnes a bord (section 9.4).
 *
 * <p>L'authentification, quand un compte existe, repose uniquement sur
 * {@link #identifiant} (ou {@link #email}) et le mot de passe (decision du
 * 2026-08-17, section K de l'analyse fonctionnelle) : aucun second facteur
 * n'est demande.
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

    /** Identifiant fonctionnel RH (RG-PAB-001) - obligatoire pour toute personne, avec ou sans compte applicatif. */
    @Column(name = "matricule", unique = true, length = 20)
    private String matricule;

    @Column(name = "nom", length = 100)
    private String nom;

    @Column(name = "prenom", length = 100)
    private String prenom;

    /** Identifiant de connexion - {@code null} si cette personne ne dispose pas d'un compte applicatif. */
    @Column(name = "identifiant", unique = true, length = 60)
    private String identifiant;

    /** Adresse e-mail - {@code null} si cette personne ne dispose pas d'un compte applicatif. */
    @Column(name = "email", unique = true, length = 150)
    private String email;

    /** Hash BCrypt du mot de passe, jamais le mot de passe en clair - {@code null} si aucun compte applicatif. */
    @Column(name = "mot_de_passe_hash", length = 255)
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

    /** Nom complet forme pour affichage (frontend) et pour les documents generes (bon de sortie, FIPH). */
    public String getNomComplet() {
        if (prenom == null && nom == null) {
            return identifiant;
        }
        return (prenom != null ? prenom : "") + " " + (nom != null ? nom : "");
    }

    /**
     * Remplace l'ancien controle {@code agent.getUtilisateur() != null} :
     * vrai si cette personne dispose reellement d'un compte applicatif
     * (peut s'authentifier), faux si elle n'existe que dans le referentiel
     * du personnel (bon de sortie/FIPH geres pour son compte par un tiers).
     */
    public boolean possedeCompteApplicatif() {
        return identifiant != null;
    }
}
