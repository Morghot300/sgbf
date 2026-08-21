package com.snef.sgbf.fiph.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Contenu versionne d'une {@link FIPH}, immuable une fois
 * {@link StatutFiphVersion#VALIDEE_DEFINITIVEMENT} (RG-VER-001) - garanti
 * a la fois applicativement ({@code FiphVersionService} refuse toute
 * ecriture sur une version figee) et en base (declencheur SQL, voir
 * {@code V8__fiph.sql}).
 *
 * <p>{@link #motifModification} est obligatoire des la deuxieme version
 * (RG-VER-002, CHECK en base) ; {@link #versionPrecedente} chaine vers la
 * version qu'elle remplace, jamais supprimee ni modifiee (RG-VER-003).
 * {@link #empreinteIntegrite} n'est calculee qu'au passage a
 * {@code VALIDEE_DEFINITIVEMENT} (RG-VER-006).
 *
 * <p>{@link #lockVersion} porte le verrouillage optimiste (RG-SEC-001,
 * section 26.7) : plusieurs personnes habilitees du meme perimetre peuvent
 * intervenir sur le meme document, jamais l'une par-dessus l'autre en
 * silence.
 */
@Entity
@Table(name = "fiph_version")
@Getter
@Setter
@NoArgsConstructor
public class FIPHVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiph_id", nullable = false, updatable = false)
    private FIPH fiph;

    @Column(name = "numero_version", nullable = false, updatable = false)
    private int numeroVersion;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cree_par", nullable = false, updatable = false)
    private Utilisateur creePar;

    /** Obligatoire des lors que {@link #numeroVersion} &gt; 1 (RG-VER-002). */
    @Column(name = "motif_modification", length = 500)
    private String motifModification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_precedente_id", updatable = false)
    private FIPHVersion versionPrecedente;

    /**
     * Date de fin de la periode couverte par CETTE version (evolution du
     * 2026-08-21) - definie par le Charge d'Affaires/la personne habilitee du
     * service, {@code null} tant qu'elle n'a pas encore ete renseignee
     * (statut initial {@code SIGNEE}/{@code BROUILLON} avec periode "ouverte").
     * Portee ici, et non sur {@link FIPH}, car elle peut evoluer d'une version
     * a l'autre (RG-VER-003) sans jamais reecrire retroactivement la periode
     * d'une version deja figee.
     */
    @Column(name = "date_fin_periode")
    private LocalDate dateFinPeriode;

    @Column(name = "total_hn", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalHN = BigDecimal.ZERO;

    @Column(name = "total_hs", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalHS = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_version", nullable = false, length = 30)
    private StatutFiphVersion statutVersion = StatutFiphVersion.BROUILLON;

    /** SHA-256 hexadecimal du contenu fige, calcule uniquement a VALIDEE_DEFINITIVEMENT (RG-VER-006). */
    @Column(name = "empreinte_integrite", length = 64)
    private String empreinteIntegrite;

    /** Signature de l'emetteur (RG-FIPH-019) - distincte des signatures de validation, portees par {@link Validation}. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_emetteur_id")
    private Signature signatureEmetteur;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;
}
