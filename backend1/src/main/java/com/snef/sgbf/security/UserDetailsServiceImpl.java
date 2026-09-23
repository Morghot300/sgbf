package com.snef.sgbf.security;

import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charge un {@link CustomUserDetails} a partir de l'identifiant de connexion.
 *
 * <p>Recharge systematiquement les habilitations actives depuis la base a
 * chaque authentification/requete (voir {@link JwtAuthenticationFilter}) :
 * un retrait d'habilitation par un administrateur doit produire effet
 * immediatement, sans attendre l'expiration du jeton d'acces (15 minutes) -
 * c'est le prix, juge acceptable ici, d'une requete supplementaire par appel
 * API face a des jetons d'acces qui resteraient sinon des "capacites" figees
 * jusqu'a expiration.
 */
@org.springframework.stereotype.Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;
    private final HabilitationRepository habilitationRepository;

    public UserDetailsServiceImpl(UtilisateurRepository utilisateurRepository,
                                   HabilitationRepository habilitationRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.habilitationRepository = habilitationRepository;
    }

    /**
     * Accepte indifferemment l'identifiant de connexion ou l'adresse e-mail
     * du compte (page de connexion : "Login ou e-mail", decision du
     * 2026-08-17) - recherche par identifiant d'abord, puis par e-mail
     * seulement si la premiere ne trouve rien, plutot qu'une requete
     * combinee : evite toute ambiguite si un identifiant et un e-mail
     * distincts coincidaient par accident.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifiantOuEmail) {
        Utilisateur utilisateur = utilisateurRepository.findByIdentifiant(identifiantOuEmail)
                .or(() -> utilisateurRepository.findByEmail(identifiantOuEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu."));
        return construire(utilisateur);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new UsernameNotFoundException("Compte inconnu."));
        return construire(utilisateur);
    }

    private CustomUserDetails construire(Utilisateur utilisateur) {
        var habilitations = habilitationRepository.findByUtilisateur_IdAndActifTrue(utilisateur.getId());
        return new CustomUserDetails(utilisateur, habilitations);
    }
}
