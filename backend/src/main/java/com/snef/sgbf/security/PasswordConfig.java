package com.snef.sgbf.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fournit l'encodeur de mots de passe utilise dans toute l'application -
 * pour les mots de passe de compte, mais aussi, par reutilisation, pour les
 * secrets courte duree (jetons de rafraichissement) qui doivent de la meme
 * facon n'exister en base que sous forme de hash (voir
 * {@code security.RefreshTokenService}).
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt : fonction de hachage lente par conception (facteur de cout
        // reglable), standard de facto pour les mots de passe - jamais un
        // simple SHA-256, trop rapide donc vulnerable au brute-force hors ligne.
        return new BCryptPasswordEncoder();
    }
}
