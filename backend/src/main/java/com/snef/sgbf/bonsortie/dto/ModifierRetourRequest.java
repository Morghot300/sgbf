package com.snef.sgbf.bonsortie.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Renseigne l'heure de retour, inconnue au moment de la creation du bon de
 * sortie. {@link #lockVersion} porte le verrouillage optimiste (RG-SEC-001) :
 * doit correspondre a la version lue a l'ouverture de l'edition, sans quoi
 * la modification est rejetee comme conflit de concurrence (section 26.7).
 */
public record ModifierRetourRequest(

        @NotNull(message = "L'heure de retour est obligatoire.")
        LocalTime heureRetour,

        @NotNull(message = "Le numero de version (lockVersion) est obligatoire pour detecter un conflit de modification concurrente.")
        Integer lockVersion
) {
}
