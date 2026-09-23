package com.snef.sgbf.mission.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Donnees necessaires pour reaffecter l'agent d'une affectation interrompue
 * vers une mission cible (RG-MIS-004) - la meme mission (reprise) ou une
 * mission differente portant un nouveau code (RG-MIS-005). La mission cible
 * doit deja exister (creee au prealable via {@code POST /api/missions},
 * avec {@code missionPrecedenteId} renseigne si elle est nouvelle).
 */
public record ReaffecterRequest(

        @NotNull(message = "La mission cible est obligatoire.")
        Long missionCibleId,

        @NotNull(message = "La date de debut de la nouvelle affectation est obligatoire.")
        LocalDate dateDebutAffectation
) {
}
