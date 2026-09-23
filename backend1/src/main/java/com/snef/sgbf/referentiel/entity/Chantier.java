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
 * Referentiel des chantiers / affaires. Un {@link CodeHN} (code mission) est
 * toujours rattache a un chantier (section 7 du document source).
 */
@Entity
@Table(name = "chantier")
@Getter
@Setter
@NoArgsConstructor
public class Chantier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_affaire", nullable = false, unique = true, length = 30)
    private String codeAffaire;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;
}
