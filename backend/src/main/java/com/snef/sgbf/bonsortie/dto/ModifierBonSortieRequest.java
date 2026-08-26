package com.snef.sgbf.bonsortie.dto;

import com.snef.sgbf.bonsortie.entity.MoyenUtilise;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Correction des champs d'un bon de sortie deja cree (evolution du
 * 2026-08-26 - "ajoute la correction des bon de sortie") : reprend
 * exactement les memes champs qu'a la creation ({@link CreerBonSortieRequest}),
 * plus {@link #heureRetour} (inconnue au moment de la creation, remplace
 * l'ancien endpoint dedie {@code /retour}).
 *
 * <p>Reservee au titulaire du bon ou a un gestionnaire (Charge d'Affaires/
 * personne habilitee) de son service - meme perimetre que le visa et la
 * validation ({@code BonSortieService#verifierAutoServiceOuGestionnaire}) -
 * et bloquee des que le bon est {@code VALIDE} (RG-VER-001) : la validation
 * fige definitivement le contenu, une correction ulterieure n'est alors plus
 * possible par construction (aucun mecanisme de nouvelle version pour un bon
 * de sortie, a la difference de la FIPH).
 *
 * <p>{@link #lockVersion} porte le verrouillage optimiste (RG-SEC-001) :
 * doit correspondre a la version lue a l'ouverture de l'edition, sans quoi
 * la modification est rejetee comme conflit de concurrence (section 26.7).
 */
public record ModifierBonSortieRequest(

        Long vehiculeId,

        @NotNull(message = "Le moyen utilise est obligatoire.")
        MoyenUtilise moyenUtilise,

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

        LocalTime heureRetour,

        @NotBlank(message = "Le lieu est obligatoire.")
        @Size(max = 150)
        String lieu,

        @NotBlank(message = "Le code affaire est obligatoire.")
        @Size(max = 30)
        String codeAffaireSaisi,

        @NotBlank(message = "Le motif de sortie est obligatoire.")
        @Size(max = 500, message = "Le motif de sortie ne peut pas depasser 500 caracteres.")
        String motifSortie,

        @NotNull(message = "Le numero de version (lockVersion) est obligatoire pour detecter un conflit de modification concurrente.")
        Integer lockVersion
) {
}
