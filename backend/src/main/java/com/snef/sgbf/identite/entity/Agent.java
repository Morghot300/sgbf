package com.snef.sgbf.identite.entity;

import com.snef.sgbf.referentiel.entity.Service;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Referentiel RH des agents (section 18 du document source).
 *
 * <p>{@link #matricule} est l'identifiant fonctionnel pivot de toute
 * l'application : c'est lui, et non une saisie de nom/prenom en texte libre,
 * qui permet une resolution fiable d'un agent (RG-PAB-001) - en particulier
 * pour la deduplication des personnes a bord (section 9.4) et pour eviter les
 * risques d'homonymie signales comme point non tranche par le document source
 * (voir analyse fonctionnelle, point I.3).
 *
 * <p>{@link #utilisateur} est optionnel (0..1) : un agent de terrain peut
 * exister dans le referentiel RH sans jamais se connecter lui-meme au
 * systeme (son bon de sortie et sa FIPH sont alors geres pour son compte par
 * le Charge d'Affaires ou la personne habilitee).
 */
@Entity
@Table(name = "agent")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricule", nullable = false, unique = true, length = 20)
    private String matricule;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 100)
    private String prenom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    /** Nom complet forme pour affichage (frontend) et pour les documents generes (bon de sortie, FIPH). */
    public String getNomComplet() {
        return prenom + " " + nom;
    }
}
