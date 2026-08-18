package com.snef.sgbf.fiph.entity;

import com.snef.sgbf.mission.entity.AffectationMission;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de pointage journaliere d'une {@link FIPHVersion} (section 4.1).
 *
 * <p>Rattachee a l'affectation active CE JOUR-LA precisement
 * ({@link #affectationMission}) et non a la mission directement - c'est ce
 * qui permet a une seule FIPH hebdomadaire de contenir, sans incoherence,
 * des jours rattaches a une affectation interrompue en milieu de semaine et
 * des jours rattaches a la nouvelle affectation (choix de conception
 * documente section 6.4, "granularite journaliere du rattachement").
 *
 * <p>Exclusivite stricte avec {@link #service} (RG-FIPH-007, Code Mission
 * xor Code Service) - portee en base par une contrainte {@code CHECK}
 * (voir migration), pas seulement verifiee ici.
 */
@Entity
@Table(name = "fiph_pointage")
@Getter
@Setter
@NoArgsConstructor
public class Pointage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiph_version_id", nullable = false)
    private FIPHVersion fiphVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "jour_semaine", nullable = false, length = 10)
    private JourSemaine jourSemaine;

    @Column(name = "date_pointage", nullable = false)
    private LocalDate datePointage;

    @Column(name = "heures_normales", nullable = false, precision = 4, scale = 2)
    private BigDecimal heuresNormales = BigDecimal.ZERO;

    @Column(name = "heures_sup", nullable = false, precision = 4, scale = 2)
    private BigDecimal heuresSup = BigDecimal.ZERO;

    /** Code Mission - exclusif avec {@link #service} (RG-FIPH-007). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "affectation_mission_id")
    private AffectationMission affectationMission;

    /** Code Service - exclusif avec {@link #affectationMission} (RG-FIPH-007). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private Service service;
}
