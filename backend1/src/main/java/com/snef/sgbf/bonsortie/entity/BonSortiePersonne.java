package com.snef.sgbf.bonsortie.entity;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Association d'une personne a bord au bon de sortie principal (section 9.2,
 * RG-PAB-001 a 009).
 *
 * <p>L'identification repose exclusivement sur {@link Agent#getMatricule()}
 * (RG-PAB-001) - jamais une saisie de nom/prenom en texte libre, condition
 * necessaire a la deduplication automatique (RG-PAB-003, garantie egalement
 * par une contrainte d'unicite en base - voir migration).
 *
 * <p>{@link #bonSortieIndividuel} reste {@code null} tant que le bon de
 * sortie principal n'est pas valide (aucune generation avant cette etape,
 * section 9.3) ; {@link #fiphId} restera {@code null} jusqu'a la
 * construction du module FIPH (non encore developpe a ce stade).
 */
@Entity
@Table(name = "bon_sortie_personne")
@Getter
@Setter
@NoArgsConstructor
public class BonSortiePersonne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bon_sortie_principal_id", nullable = false)
    private BonSortie bonSortiePrincipal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    // Type Utilisateur, nom de champ/colonne "agent" conserve deliberement (evolution du
    // 2026-08-19, unification Agent/Utilisateur) - voir FIPH.agent pour la justification complete.
    private Utilisateur agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_association", nullable = false, length = 20)
    private StatutAssociationPersonne statutAssociation = StatutAssociationPersonne.ACTIVE;

    @CreationTimestamp
    @Column(name = "date_association", nullable = false, updatable = false)
    private LocalDateTime dateAssociation;

    @Column(name = "date_retrait")
    private LocalDateTime dateRetrait;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_sortie_individuel_id")
    private BonSortie bonSortieIndividuel;

    /**
     * Reference vers la FIPH generee pour cette personne - simple colonne
     * technique (pas d'association JPA) tant que le module FIPH n'existe
     * pas ; deviendra une veritable {@code @ManyToOne} lors de sa
     * construction (voir migration V5, colonne sans contrainte FK pour l'instant).
     */
    @Column(name = "fiph_id")
    private Long fiphId;
}
