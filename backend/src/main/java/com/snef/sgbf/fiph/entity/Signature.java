package com.snef.sgbf.fiph.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Preuve technique associee a un acte de signature ou de validation
 * (section 22). Referencee soit depuis {@link FIPHVersion} (signature de
 * l'emetteur, RG-FIPH-019), soit depuis {@link Validation} (signature du
 * valideur) - jamais un simple indicateur booleen (RG-FIPH-017 : toute
 * action est associee a un utilisateur identifiable de maniere univoque).
 *
 * <p>Append-only comme {@code evenement_audit}, {@code validation} et
 * {@code affectation_mission} (section 26.3) : declencheurs SQL interdisant
 * UPDATE/DELETE en plus de l'absence de setters ici.
 */
@Entity
@Table(name = "signature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeSignature type;

    /** Identifiant de session pour un visa applicatif, empreinte cryptographique pour une signature electronique. */
    @Column(name = "empreinte", nullable = false, length = 255)
    private String empreinte;

    @CreationTimestamp
    @Column(name = "date_signature", nullable = false, updatable = false)
    private LocalDateTime dateSignature;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    public Signature(TypeSignature type, String empreinte, String adresseIp) {
        this.type = type;
        this.empreinte = empreinte;
        this.adresseIp = adresseIp;
    }
}
