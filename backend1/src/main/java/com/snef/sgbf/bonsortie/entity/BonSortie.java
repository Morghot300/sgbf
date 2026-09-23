package com.snef.sgbf.bonsortie.entity;

import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.entity.Mission;
import com.snef.sgbf.referentiel.entity.Vehicule;
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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Document de sortie VH et personnel - evenement declencheur de toute FIPH
 * de nature Mission (section 3, RG-BS-001 a 007).
 *
 * <p>Une seule classe porte les deux "natures" de bon de sortie decrites par
 * le document source : le bon principal, cree par l'agent emetteur, et le
 * bon individuel genere automatiquement pour chaque personne a bord -
 * distingues uniquement par {@link #origine} et {@link #bonSortiePrincipal}
 * (choix de conception documente section 9.1, repris tel quel).
 *
 * <p>{@link #affectationMission} n'est renseignee qu'a la validation
 * (statut {@code VALIDE}) : avant cette etape, le bon de sortie ne porte que
 * le texte saisi par l'utilisateur ({@link #codeAffaireSaisi}), qui sert
 * uniquement a identifier l'affectation au moment de la resolution -
 * "la reference, une fois posee, prevaut sur le texte saisi" (section 4.2).
 *
 * <p>{@link #lockVersion} porte le verrouillage optimiste exige par
 * RG-SEC-001.
 */
@Entity
@Table(name = "bon_sortie")
@Getter
@Setter
@NoArgsConstructor
public class BonSortie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    // Type Utilisateur, nom de champ/colonne "agent" conserve deliberement (evolution du
    // 2026-08-19, unification Agent/Utilisateur) - voir FIPH.agent pour la justification complete.
    private Utilisateur agent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicule_id")
    private Vehicule vehicule;

    /** Resolue a la validation uniquement (section 3.2, section 8) - null tant que le bon n'est pas VALIDE. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "affectation_mission_id")
    private AffectationMission affectationMission;

    /**
     * Mission choisie explicitement a la creation ou a une correction
     * ulterieure (evolution du 2026-08-27, "Code Mission" du brief
     * "Evolution du module Bon de Sortie") - facultative. Lorsqu'elle est
     * renseignee, elle devient prioritaire sur la resolution automatique par
     * date a la validation ({@link com.snef.sgbf.bonsortie.service.BonSortieService#valider}) :
     * seule une {@link AffectationMission} de l'agent portant EXACTEMENT
     * cette mission est retenue, jamais une autre resolue par simple
     * coincidence de date.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(name = "moyen_utilise", nullable = false, length = 20)
    private MoyenUtilise moyenUtilise;

    /** Obligatoire uniquement lorsque {@link #moyenUtilise} vaut {@link MoyenUtilise#AUTRE} (controle applicatif + CHECK en base). */
    @Column(name = "precision_vehicule", length = 200)
    private String precisionVehicule;

    /** Immatriculation saisie (RG-BS-006) - facultative (point I.4 de l'analyse fonctionnelle). */
    @Column(name = "lt", length = 20)
    private String lt;

    @Column(name = "kilometrage", nullable = false)
    private Integer kilometrage;

    @Column(name = "date_sortie", nullable = false)
    private LocalDate dateSortie;

    @Column(name = "heure_sortie", nullable = false)
    private LocalTime heureSortie;

    @Column(name = "heure_retour")
    private LocalTime heureRetour;

    @Column(name = "lieu", nullable = false, length = 150)
    private String lieu;

    /** Code affaire tel que saisi, avant resolution vers {@link #affectationMission} (section 4.2). */
    @Column(name = "code_affaire_saisi", nullable = false, length = 30)
    private String codeAffaireSaisi;

    /** Limite de longueur explicite - assainissement RG-SEC-003. */
    @Column(name = "motif_sortie", nullable = false, length = 500)
    private String motifSortie;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutBonSortie statut = StatutBonSortie.BROUILLON;

    @Enumerated(EnumType.STRING)
    @Column(name = "origine", nullable = false, length = 20)
    private OrigineBonSortie origine = OrigineBonSortie.PRINCIPALE;

    /** Renseigne uniquement pour un bon d'origine PERSONNE_A_BORD (section 9.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_sortie_principal_id")
    private BonSortie bonSortiePrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vise_par")
    private Utilisateur visePar;

    @Column(name = "date_visa")
    private LocalDateTime dateVisa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par")
    private Utilisateur valideParCA;

    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;
}
