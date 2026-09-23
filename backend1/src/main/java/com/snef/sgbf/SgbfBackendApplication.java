package com.snef.sgbf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree de l'application SGBF (Systeme de Gestion des Bons de sortie
 * et des FIPH) pour SNEF Cameroun SA - Groupe SNEF.
 *
 * <p>Cette application backend expose une API REST consommee par le frontend
 * React (voir {@code frontend/}) et s'appuie sur MySQL comme unique source de
 * verite persistante, avec un schema entierement pilote par des migrations
 * Flyway versionnees (voir {@code src/main/resources/db/migration}).
 */
@SpringBootApplication
public class SgbfBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgbfBackendApplication.class, args);
    }
}
