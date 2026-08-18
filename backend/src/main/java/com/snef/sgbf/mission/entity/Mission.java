package com.snef.sgbf.mission.entity;

import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
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
 * Identite stable d'une mission : code, chantier, dates prevues (section 5.1
 * du document source).
 *
 * <p>Distincte d'{@link AffectationMission}, qui seule porte le cycle de vie
 * operationnel (qui y est affecte, depuis quand) - une Mission conserve son
 * identite et son {@link #codeHN} tout au long de sa duree de vie,
 * independamment des agents qui s'y succedent (choix de conception
 * documente section 5.2).
 *
 * <p>{@link #missionPrecedente} n'est renseignee que lorsque cette mission
 * resulte d'une reaffectation consecutive a l'interruption d'une autre
 * (RG-MIS-005) - c'est cette chaine, et non une table d'historique separee,
 * qui porte la tracabilite complete (RG-MIS-006).
 */
@Entity
@Table(name = "mission")
@Getter
@Setter
@NoArgsConstructor
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "code_hn_id", nullable = false)
    private CodeHN codeHN;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(name = "date_debut_prevue", nullable = false)
    private LocalDate dateDebutPrevue;

    @Column(name = "date_fin_prevue", nullable = false)
    private LocalDate dateFinPrevue;

    /** Renseignee a la cloture de la mission, normale ou anticipee (interruption). */
    @Column(name = "date_fin_reelle")
    private LocalDate dateFinReelle;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutMission statut = StatutMission.PLANIFIEE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_precedente_id")
    private Mission missionPrecedente;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;
}
