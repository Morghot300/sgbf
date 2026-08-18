package com.snef.sgbf.mission.entity;

import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.referentiel.entity.MotifInterruptionMission;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Lien entre un agent et une mission pour une periode ; porte le cycle de
 * vie operationnel au jour le jour (creation, interruption, reaffectation -
 * RG-MIS-001 a 008, section 5.2 du document source).
 *
 * <p><strong>Immuable apres creation</strong> sur ses colonnes d'identite
 * (mission, agent, date de debut) - garanti par un declencheur SQL
 * (voir {@code V4__mission_affectation.sql}) en plus du controle applicatif :
 * toute correction procede de la cloture de cette ligne (statut,
 * {@link #dateFinAffectation}) suivie de la creation d'une nouvelle ligne
 * chainee via {@link #affectationPrecedente} (RG-MIS-006), jamais d'une
 * reecriture en place.
 *
 * <p>Au plus une ligne par agent peut porter le statut {@code ACTIVE} a un
 * instant donne - contrainte portee en base par un index unique sur une
 * colonne generee (voir migration), pas seulement applicative.
 */
@Entity
@Table(name = "affectation_mission")
@Getter
@Setter
@NoArgsConstructor
public class AffectationMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mission_id", nullable = false, updatable = false)
    private Mission mission;

    @Column(name = "date_debut_affectation", nullable = false, updatable = false)
    private LocalDate dateDebutAffectation;

    @Column(name = "date_fin_affectation")
    private LocalDate dateFinAffectation;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_affectation", nullable = false, length = 20)
    private StatutAffectation statutAffectation = StatutAffectation.ACTIVE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "motif_interruption_id")
    private MotifInterruptionMission motifInterruption;

    /** Commentaire libre attendu lorsque {@link #motifInterruption} est AUTRE (section 6.2 ; hypothese documentee, voir point I de l'analyse). */
    @Column(name = "commentaire_interruption", length = 500)
    private String commentaireInterruption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affectation_precedente_id")
    private AffectationMission affectationPrecedente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par", nullable = false)
    private Utilisateur creePar;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;
}
