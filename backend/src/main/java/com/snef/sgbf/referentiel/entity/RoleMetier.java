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
 * Referentiel des roles metier applicatifs (section 10 et 18 du document
 * source). Un role seul ne porte jamais de droit : c'est toujours son
 * association a un utilisateur et a un perimetre via
 * {@link com.snef.sgbf.identite.entity.Habilitation} qui en porte un
 * (RG-HAB-001).
 *
 * <p>Les codes possibles sont fixes par le domaine metier (voir
 * {@link CodeRoleMetier}) meme si cette table reste techniquement une table
 * de reference classique, pour rester alignee avec les libelles humains
 * affiches a l'ecran sans coder ces libelles en dur dans le backend.
 */
@Entity
@Table(name = "role_metier")
@Getter
@Setter
@NoArgsConstructor
public class RoleMetier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "libelle", nullable = false, length = 100)
    private String libelle;
}
