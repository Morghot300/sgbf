package com.snef.sgbf.security;

import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adaptateur Spring Security au-dessus de {@link Utilisateur}.
 *
 * <p>Deux niveaux d'autorisation coexistent volontairement dans cette classe,
 * conformement a la section 26.2 du document source ("autorisation par role
 * metier ET par perimetre") :
 * <ul>
 *   <li>les {@link GrantedAuthority} ({@code ROLE_CHARGE_AFFAIRES}, etc.)
 *       permettent les controles grossiers via {@code @PreAuthorize("hasRole(...)")}
 *       sur les controleurs - "cet utilisateur a-t-il ce role, quelque part" ;</li>
 *   <li>{@link #getHabilitationsActives()} expose la liste complete des
 *       habilitations actives (role + service + dates) pour les controles
 *       fins de perimetre effectues dans la couche service (RG-HAB-003,
 *       RG-SEC-002 anti-IDOR) - "cet utilisateur a-t-il CE role SUR CE
 *       service precis".</li>
 * </ul>
 * Le second niveau est le seul realmente suffisant pour les operations
 * sensibles (creer/modifier une FIPH, valider...) ; le premier ne sert qu'a
 * un filtrage grossier au niveau des endpoints.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Utilisateur utilisateur;
    private final List<Habilitation> habilitationsActives;

    public CustomUserDetails(Utilisateur utilisateur, List<Habilitation> habilitationsActives) {
        this.utilisateur = utilisateur;
        // Ne conserve que les habilitations effectivement en vigueur "maintenant"
        // (actif=true ET fenetre de dates courante) - voir Habilitation.estEffectiveA.
        this.habilitationsActives = habilitationsActives.stream()
                .filter(h -> h.estEffectiveA(LocalDate.now()))
                .toList();
    }

    public Long getUtilisateurId() {
        return utilisateur.getId();
    }

    /** Codes de role distincts portes par au moins une habilitation active, tous services confondus. */
    public Set<String> getCodesRolesActifs() {
        return habilitationsActives.stream()
                .map(h -> h.getRoleMetier().getCode())
                .collect(Collectors.toSet());
    }

    /** Vrai si l'utilisateur detient une habilitation active du role donne sur le service donne (RG-HAB-003). */
    public boolean possedeHabilitationSurService(String codeRole, Long serviceId) {
        return habilitationsActives.stream().anyMatch(h ->
                h.getRoleMetier().getCode().equals(codeRole)
                        && h.getService() != null
                        && h.getService().getId().equals(serviceId));
    }

    /** Vrai si l'utilisateur detient une habilitation active a perimetre global du role donne (ex. RH). */
    public boolean possedeHabilitationGlobale(String codeRole) {
        return habilitationsActives.stream().anyMatch(h ->
                h.getRoleMetier().getCode().equals(codeRole) && h.getService() == null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getCodesRolesActifs().stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return utilisateur.getMotDePasseHash();
    }

    @Override
    public String getUsername() {
        return utilisateur.getIdentifiant();
    }

    @Override
    public boolean isAccountNonLocked() {
        return utilisateur.getStatutCompte() != StatutCompte.VERROUILLE;
    }

    @Override
    public boolean isEnabled() {
        return utilisateur.getStatutCompte() == StatutCompte.ACTIF;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
