package com.snef.sgbf.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Referentiel configurable des motifs d'interruption de mission (RG-MIS-007).
 *
 * <p>Volontairement une table de reference et non une enumeration Java : le
 * document source (section 6.2) est explicite sur le fait que la liste
 * initiale (seedee dans {@code V1__referentiels.sql}) est une "proposition de
 * modelisation initiale, non une liste definitive" et que son extension ne
 * doit requerir "aucune modification structurelle de la base de donnees ni du
 * modele de classes".
 */
@Entity
@Table(name = "motif_interruption_mission")
@Getter
@Setter
@NoArgsConstructor
public class MotifInterruptionMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
