package com.snef.sgbf.common.audit;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Enregistrement unique et immuable du journal d'audit applicatif.
 *
 * <p>Entite au coeur de l'exigence de non-repudiation (section 23 du document
 * source) : chaque action metier significative (creation, complement,
 * signature, validation, modification, interruption...) doit produire une
 * ligne ici, avec son auteur, son horodatage serveur, et l'etat avant/apres.
 *
 * <p><strong>Append-only</strong> : cette entite n'expose volontairement
 * aucun setter apres construction (voir {@link #EvenementAudit}) et sa table
 * porte des declencheurs SQL interdisant UPDATE/DELETE (voir
 * {@code V3__evenement_audit.sql}) - la garantie est donc double : applicative
 * ET base de donnees, comme demande section 26.3.
 *
 * <p>La reference vers l'entite concernee est polymorphe ({@link #entiteType}
 * + {@link #entiteId}) et n'est pas portee par une cle etrangere : elle doit
 * pouvoir designer indifferemment une Mission, un BonSortie, une FIPH, etc.
 * (choix de conception documente section 24 du document source).
 */
@Entity
@Table(name = "evenement_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // reserve a Hibernate ; utiliser le constructeur metier ci-dessous
public class EvenementAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entite_type", nullable = false, length = 40)
    private EntiteAuditable entiteType;

    @Column(name = "entite_id", nullable = false, length = 40)
    private String entiteId;

    /** Nullable : une action peut etre declenchee par le systeme lui-meme (ex. generation automatique de FIPH). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60)
    private TypeActionAudit action;

    /** Etat du champ modifie avant l'action, serialise en JSON (peut etre null pour une creation). */
    @Column(name = "valeur_avant", columnDefinition = "json")
    private String valeurAvant;

    /** Etat du champ modifie apres l'action, serialise en JSON (peut etre null pour une consultation/suppression). */
    @Column(name = "valeur_apres", columnDefinition = "json")
    private String valeurApres;

    @Column(name = "statut_avant", length = 40)
    private String statutAvant;

    @Column(name = "statut_apres", length = 40)
    private String statutApres;

    /** Horodatage exclusivement serveur (section 23.2) : jamais fourni par le client. */
    @Column(name = "date_action", nullable = false, updatable = false)
    private LocalDateTime dateAction;

    /**
     * Seul point de creation autorise : un evenement d'audit est toujours
     * entierement constitue a la construction, jamais modifie ensuite (voir
     * {@link AuditService} pour les methodes d'ecriture de haut niveau).
     */
    public EvenementAudit(EntiteAuditable entiteType, String entiteId, Utilisateur utilisateur,
                           TypeActionAudit action, String valeurAvant, String valeurApres,
                           String statutAvant, String statutApres) {
        this.entiteType = entiteType;
        this.entiteId = entiteId;
        this.utilisateur = utilisateur;
        this.action = action;
        this.valeurAvant = valeurAvant;
        this.valeurApres = valeurApres;
        this.statutAvant = statutAvant;
        this.statutApres = statutApres;
        this.dateAction = LocalDateTime.now();
    }
}
