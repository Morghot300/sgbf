package com.snef.sgbf.fiph.entity;

import com.snef.sgbf.identite.entity.Utilisateur;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Trace d'une decision de validation a un niveau donne (2 : Charge
 * d'Affaires, 3 : Responsable d'activite, 4 : Direction - RG-FIPH-012 a 014).
 *
 * <p>Append-only (section 26.3) : aucune validation, une fois enregistree,
 * n'est jamais modifiee ni supprimee - garanti par declencheurs SQL (voir
 * migration) en plus de l'absence de setters ici. Chaque decision cree un
 * enregistrement distinct et horodate, jamais un simple indicateur booleen
 * (RG-FIPH-011, RG-FIPH-017).
 */
@Entity
@Table(name = "validation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Validation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiph_version_id", nullable = false)
    private FIPHVersion fiphVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "niveau_validation", nullable = false)
    private int niveauValidation;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private DecisionValidation decision;

    @CreationTimestamp
    @Column(name = "date_validation", nullable = false, updatable = false)
    private LocalDateTime dateValidation;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_id", nullable = false)
    private Signature signature;

    @Column(name = "statut_avant", length = 30)
    private String statutAvant;

    @Column(name = "statut_apres", length = 30)
    private String statutApres;

    /**
     * TRUE uniquement pour une decision prise au titre de la prise en main
     * exceptionnelle du Super Administrateur (evolution du 2026-08-19,
     * section 12-16) - jamais pour une validation normale d'un acteur
     * metier, meme si cet acteur est lui-meme titulaire du role Super
     * Administrateur (une validation normale de niveau reste normale). Le
     * systeme distingue ainsi clairement les deux dans tout affichage ou
     * export d'historique.
     */
    @Column(name = "prise_en_main_super_admin", nullable = false)
    private boolean priseEnMainSuperAdmin = false;

    public Validation(FIPHVersion fiphVersion, Utilisateur utilisateur, int niveauValidation,
                       DecisionValidation decision, String commentaire, Signature signature,
                       String statutAvant, String statutApres) {
        this(fiphVersion, utilisateur, niveauValidation, decision, commentaire, signature,
                statutAvant, statutApres, false);
    }

    public Validation(FIPHVersion fiphVersion, Utilisateur utilisateur, int niveauValidation,
                       DecisionValidation decision, String commentaire, Signature signature,
                       String statutAvant, String statutApres, boolean priseEnMainSuperAdmin) {
        this.fiphVersion = fiphVersion;
        this.utilisateur = utilisateur;
        this.niveauValidation = niveauValidation;
        this.decision = decision;
        this.commentaire = commentaire;
        this.signature = signature;
        this.statutAvant = statutAvant;
        this.statutApres = statutApres;
        this.priseEnMainSuperAdmin = priseEnMainSuperAdmin;
    }
}
