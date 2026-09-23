package com.snef.sgbf.referentiel.dto;

import com.snef.sgbf.referentiel.entity.Vehicule.TypeVehicule;

public record VehiculeDto(Long id, String immatriculation, TypeVehicule type) {
}
