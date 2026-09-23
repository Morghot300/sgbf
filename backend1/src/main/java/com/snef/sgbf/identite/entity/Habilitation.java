package com.snef.sgbf.identite.entity;

import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Association utilisateur / role metier / perimetre (RG-HAB-001).
 *
 * <p>C'est cette entite, et non {@link RoleMetier} seul, qui porte
 * effectivement un droit : toute verification d'autorisation dans
 * l'application doit combiner le role ET le perimetre (section 26.2 du
 * document source - "autorisation par role metier ET par perimetre").
 *
 * <p>{@link #service} est nullable pour representer un perimetre global :
 * c'est le cas exclusif de l'habilitation RH (RG-HAB-005, "consultation
 * globale") et, par extension technique raisonnable, de l'Administrateur
 * (gestion transverse des referentiels). {@code HabilitationService} impose
 * cette regle a la creation - elle n'est pas une contrainte de base de
 * donnees car sa condition depend du role associe, ce qu'une simple
 * contrainte {@code CHECK} sur cette seule table ne peut pas exprimer sans
 * jointure (MySQL ne supporte pas les CHECK inter-tables).
 *
 * <p>Un utilisateur peut cumuler plusieurs habilitations (RG-HAB-002), mais
 * ne peut jamais valider a un niveau superieur une FIPH qu'il a lui-meme
 * creee, completee ou modifiee (RG-HAB-004) - cette regle est appliquee au
 * niveau du module FIPH (elle ne peut pas etre exprimee ici, une
 * Habilitation ne "connaissant" pas les documents sur lesquels elle sera
 * exercee).
 */
@Entity
@Table(name = "habilitation")
@Getter
@Setter
@NoArgsConstructor
public class Habilitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_metier_id", nullable = false)
    private RoleMetier roleMetier;

    /** Nullable = perimetre global (RH, Administrateur uniquement - voir Javadoc de classe). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Null = habilitation a duree indeterminee. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par", nullable = false)
    private Utilisateur creePar;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Une habilitation est effectivement exercable "aujourd'hui" si elle est
     * marquee active, que la date de debut est atteinte, et que la date de
     * fin (si definie) n'est pas depassee - traduction directe du principe du
     * moindre privilege borne dans le temps (section 26.2).
     */
    public boolean estEffectiveA(LocalDate date) {
        if (!actif) {
            return false;
        }
        if (dateDebut.isAfter(date)) {
            return false;
        }
        return dateFin == null || !dateFin.isBefore(date);
    }
}
