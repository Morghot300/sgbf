package com.snef.sgbf.fiph.entity;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Identite stable du document de pointage hebdomadaire (section 4, 17).
 *
 * <p>Porte l'identite et la periode ; le contenu variable (pointages,
 * totaux, statut de validation) est entierement porte par
 * {@link FIPHVersion} - separation documentee section 17 ("FIPHVersion
 * plutot que modification directe de FIPH") qui permet de repondre a
 * l'exigence de non-repudiation sans dupliquer toute la FIPH a chaque
 * revision mineure, et sans jamais ecraser une valeur deja validee.
 *
 * <p>{@link #statut} est un miroir denormalise du statut de
 * {@link #versionCourante} : mis a jour a chaque changement de version
 * courante ou de statut de celle-ci (voir {@code FiphService}), il permet
 * de filtrer/rechercher les FIPH par statut sans jointure (recherche
 * multicritere recommandee section 30) - {@code fiph_version} reste
 * neanmoins l'unique source de verite, jamais reecrite a partir d'ici.
 */
@Entity
@Table(name = "fiph")
@Getter
@Setter
@NoArgsConstructor
public class FIPH {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    /** Copie a la creation (section 8) : Service auquel l'agent appartenait a cet instant, jamais resynchronise ensuite. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "origine", nullable = false, length = 20)
    private OrigineFiph origine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_sortie_id")
    private BonSortie bonSortie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par")
    private Utilisateur creePar;

    @Column(name = "annee", nullable = false)
    private int annee;

    @Column(name = "mois", nullable = false)
    private int mois;

    @Column(name = "numero_semaine", nullable = false)
    private int numeroSemaine;

    @Column(name = "date_debut_periode", nullable = false)
    private LocalDate dateDebutPeriode;

    @Column(name = "date_fin_periode", nullable = false)
    private LocalDate dateFinPeriode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_courante_id")
    private FIPHVersion versionCourante;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutFiphVersion statut;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;
}
