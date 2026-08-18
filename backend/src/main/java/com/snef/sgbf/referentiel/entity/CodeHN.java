package com.snef.sgbf.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Referentiel des codes mission ("Code HN"), rattaches a un chantier.
 *
 * <p>Reference par {@code Mission.codeHN} (section 5.1) : c'est ce code qui
 * identifie une mission donnee pour un chantier donne, et qui n'est jamais
 * ecrase en cas de reaffectation (RG-MIS-006) - seule une nouvelle
 * {@code AffectationMission} referencant une nouvelle {@code Mission} est
 * creee, l'ancien code_hn restant intact sur l'ancienne mission.
 */
@Entity
@Table(name = "code_hn")
@Getter
@Setter
@NoArgsConstructor
public class CodeHN {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;
}
