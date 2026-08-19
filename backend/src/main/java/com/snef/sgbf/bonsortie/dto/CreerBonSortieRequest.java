package com.snef.sgbf.bonsortie.dto;

import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Donnees necessaires a la creation d'un bon de sortie (RG-BS-002 : champs
 * obligatoires "nom et prenom, moyen utilise, kilometrage, date et heure de
 * sortie, lieu, code affaire et motif de sortie"). L'agent titulaire n'est
 * pas un champ de cette requete : il est toujours resolu depuis
 * l'utilisateur authentifie (creation en libre-service, workflow §12.2
 * etape 1 - acteur "Agent").
 */
public record CreerBonSortieRequest(

        Long vehiculeId,

        @NotNull(message = "Le moyen utilise est obligatoire.")
        MoyenUtilise moyenUtilise,

        // Obligatoire uniquement si moyenUtilise == AUTRE - controle explicite
        // en service (BonSortieService.creer), pas via une annotation Bean
        // Validation seule (validation conditionnelle inter-champs).
        @Size(max = 200, message = "La precision du vehicule ne peut pas depasser 200 caracteres.")
        String precisionVehicule,

        @Size(max = 20)
        String lt,

        @NotNull(message = "Le kilometrage est obligatoire.")
        @PositiveOrZero(message = "Le kilometrage ne peut pas etre negatif.")
        Integer kilometrage,

        @NotNull(message = "La date de sortie est obligatoire.")
        LocalDate dateSortie,

        @NotNull(message = "L'heure de sortie est obligatoire.")
        LocalTime heureSortie,

        @NotBlank(message = "Le lieu est obligatoire.")
        @Size(max = 150)
        String lieu,

        @NotBlank(message = "Le code affaire est obligatoire.")
        @Size(max = 30)
        String codeAffaireSaisi,

        @NotBlank(message = "Le motif de sortie est obligatoire.")
        @Size(max = 500, message = "Le motif de sortie ne peut pas depasser 500 caracteres.")
        String motifSortie
) {
}
