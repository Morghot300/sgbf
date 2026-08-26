package com.snef.sgbf.config;

import com.snef.sgbf.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration centrale de Spring Security.
 *
 * <p>Principes appliques (section 26 du document source) :
 * <ul>
 *   <li><b>API stateless</b> - {@link SessionCreationPolicy#STATELESS} : aucune
 *       session HTTP cote serveur, toute l'authentification repose sur le
 *       jeton JWT presente a chaque requete (voir {@link JwtAuthenticationFilter});</li>
 *   <li><b>CSRF desactive</b> - justifie precisement parce que l'API est
 *       stateless et que l'authentification se fait par en-tete
 *       {@code Authorization} (jamais automatiquement rejoue par le
 *       navigateur, contrairement a un cookie de session classique). Le seul
 *       cookie emis par l'application est le jeton de rafraichissement,
 *       protege independamment par les attributs {@code HttpOnly},
 *       {@code Secure} et {@code SameSite=Strict} poses par
 *       {@code security.AuthController} - c'est cette combinaison, et non
 *       un jeton CSRF, qui le protege contre la resoumission inter-site ;</li>
 *   <li><b>CORS restreint</b> a l'origine du frontend, configurable par
 *       variable d'environnement plutot que codee en dur (dev vs prod).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // active @PreAuthorize sur les controleurs/services
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> originesFrontendAutorisees;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           @Value("${app.security.cors.origine-frontend:http://localhost:5173}") String origineFrontendAutorisee) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        // Plusieurs origines separees par une virgule (ex. localhost + IP LAN
        // en developpement pour un test multi-poste) : une seule variable
        // d'environnement suffit donc, sans introduire de wildcard incompatible
        // avec allowCredentials(true).
        this.originesFrontendAutorisees = List.of(origineFrontendAutorisee.split(","))
                .stream().map(String::trim).toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // voir justification dans la Javadoc de classe
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Authentification elle-meme (et uniquement elle) et documentation API :
                        // ouvertes sans jeton d'acces. /api/auth/me, en particulier, N'EST PAS ici -
                        // elle exige un jeton valide comme tout le reste de l'API.
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/docs/**", "/api/docs.json", "/swagger-ui/**").permitAll()
                        // Tout le reste de l'API exige un jeton d'acces valide ; le controle fin
                        // par role et perimetre est ensuite porte par @PreAuthorize et par les
                        // verifications d'habilitation en couche service (RG-HAB-003, RG-SEC-002).
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Reutilise le AuthenticationManager auto-configure par Spring Boot, qui
     * assemble deja un {@link DaoAuthenticationProvider} a partir des beans
     * {@link UserDetailsService} ({@code security.UserDetailsServiceImpl}) et
     * {@link PasswordEncoder} ({@code security.PasswordConfig}) presents
     * dans le contexte - inutile de le reconstruire manuellement.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Hierarchie de roles (evolution du 2026-08-18, section 11 de la mission
     * "Super Administrateur") : SUPER_ADMINISTRATEUR herite automatiquement
     * de tous les droits accordes a ADMINISTRATEUR par {@code @PreAuthorize}
     * sur l'ensemble des controleurs, sans avoir a dupliquer
     * {@code hasRole('ADMINISTRATEUR')} en {@code hasAnyRole(...)} partout -
     * c'est le backend (Spring Security), et non un simple affichage
     * conditionnel cote React, qui applique cette hierarchie. Beans statiques
     * a dessein : {@code @EnableMethodSecurity} instancie certains
     * post-processeurs de securite tres tot dans le cycle de vie du contexte,
     * avant que les beans non-statiques ne soient disponibles.
     *
     * <p>Cette hierarchie ne s'applique qu'aux controles Spring Security
     * ({@code @PreAuthorize("hasRole(...)")}) - les controles de perimetre
     * "vision globale" ecrits a la main (ex. {@code FiphService.estRoleVisionGlobale})
     * comparent directement le code de role et doivent donc lister
     * SUPER_ADMINISTRATEUR explicitement, ce qui est fait a chaque endroit
     * pertinent.
     *
     * <p><strong>Extension du 2026-08-26</strong> : SUPER_ADMINISTRATEUR
     * herite desormais aussi de CHARGE_AFFAIRES, PERSONNE_HABILITEE,
     * RESPONSABLE_ACTIVITE et DIRECTION - indispensable pour qu'il puisse
     * seulement franchir le premier filtrage grossier par role des
     * controleurs (viser/valider un bon de sortie, soumettre/valider une
     * FIPH a n'importe quel niveau, gerer une affectation) ; le controle FIN
     * de perimetre par service, dans la couche service, le reconnait
     * separement et explicitement comme validateur legitime sur N'IMPORTE
     * QUEL service (voir {@code BonSortieService.verifierPerimetreGestionnaire}
     * et les methodes equivalentes) - les deux couches devaient etre
     * etendues ensemble, l'une sans l'autre restant insuffisante.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMINISTRATEUR > ROLE_ADMINISTRATEUR
                ROLE_SUPER_ADMINISTRATEUR > ROLE_CHARGE_AFFAIRES
                ROLE_SUPER_ADMINISTRATEUR > ROLE_PERSONNE_HABILITEE
                ROLE_SUPER_ADMINISTRATEUR > ROLE_RESPONSABLE_ACTIVITE
                ROLE_SUPER_ADMINISTRATEUR > ROLE_DIRECTION
                """);
    }

    @Bean
    static DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(originesFrontendAutorisees);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Indispensable pour que le navigateur envoie le cookie httpOnly du
        // jeton de rafraichissement lors des appels cross-origin en developpement
        // (frontend sur :5173, backend sur :8080).
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
