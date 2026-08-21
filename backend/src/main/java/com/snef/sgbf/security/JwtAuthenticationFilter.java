package com.snef.sgbf.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre d'authentification par jeton d'acces JWT, execute une fois par
 * requete, en amont du filtre standard {@code UsernamePasswordAuthenticationFilter}
 * (voir son enregistrement dans {@link SecurityConfig}).
 *
 * <p>Lit l'en-tete {@code Authorization: Bearer <jeton>}, verifie sa
 * signature et son expiration via {@link JwtService}, puis recharge
 * integralement l'utilisateur et ses habilitations actives depuis la base
 * (voir la justification detaillee dans {@link UserDetailsServiceImpl}). En
 * l'absence d'en-tete ou de jeton invalide, la requete poursuit simplement
 * sans authentification - c'est a Spring Security (regles de
 * {@link SecurityConfig}) de decider ensuite si l'endpoint demande
 * necessite d'etre authentifie.
 *
 * <p><strong>Exception strictement limitee au flux SSE (evolution du
 * 2026-08-21)</strong> : {@code GET /api/notifications/stream} accepte
 * egalement le jeton en parametre de requete {@code ?token=}, l'API
 * navigateur {@code EventSource} ne permettant pas de definir d'en-tetes
 * personnalises. Cette tolerance ne s'applique JAMAIS a un autre chemin -
 * jamais un fallback general, qui exposerait le jeton dans les journaux de
 * requetes de tout autre endpoint.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE_BEARER = "Bearer ";
    private static final String CHEMIN_FLUX_NOTIFICATIONS = "/api/notifications/stream";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String enTete = request.getHeader("Authorization");
        String jetonBrut = null;
        if (enTete != null && enTete.startsWith(PREFIXE_BEARER)) {
            jetonBrut = enTete.substring(PREFIXE_BEARER.length());
        } else if (CHEMIN_FLUX_NOTIFICATIONS.equals(request.getRequestURI())) {
            jetonBrut = request.getParameter("token");
        }

        if (jetonBrut != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            String jeton = jetonBrut;
            Optional<Long> utilisateurId = jwtService.extraireUtilisateurIdSiValide(jeton);

            utilisateurId.ifPresent(id -> {
                UserDetails userDetails = userDetailsService.loadUserById(id);
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            });
        }

        filterChain.doFilter(request, response);
    }
}
