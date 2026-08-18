package com.snef.sgbf.referentiel.dto;

import com.snef.sgbf.referentiel.entity.Vehicule.TypeVehicule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreerVehiculeRequest(
        @NotBlank @Size(max = 20) String immatriculation,
        @NotNull TypeVehicule type
) {
}
