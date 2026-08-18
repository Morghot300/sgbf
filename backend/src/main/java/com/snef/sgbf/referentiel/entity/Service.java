package com.snef.sgbf.referentiel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Referentiel des services de l'entreprise.
 *
 * <p>{@link #codeService} est "unique et invariant" (RG-FIPH-006) : c'est le
 * code porte par les lignes de pointage manuelles des agents du service qui
 * ne sont pas concernes par une mission durant la periode (FIPH d'origine
 * {@code MANUELLE}, Code Service - a distinguer du Code Mission, voir
 * {@code fiph.entity.Pointage}).
 *
 * <p>Remarque de nommage : cette classe s'appelle {@code Service} comme dans
 * le document source (section 18), ce qui entre en collision de nom avec
 * l'annotation Spring {@code @Service}. Les classes de service applicatif qui
 * manipulent cette entite annotent donc leur propre classe avec le nom
 * pleinement qualifie {@code @org.springframework.stereotype.Service} pour
 * eviter toute ambiguite d'import.
 */
@Entity
@Table(name = "service")
@Getter
@Setter
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_service", nullable = false, unique = true, length = 20)
    private String codeService;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @CreationTimestamp
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @UpdateTimestamp
    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;
}
