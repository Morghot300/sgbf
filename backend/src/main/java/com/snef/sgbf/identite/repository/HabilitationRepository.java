package com.snef.sgbf.identite.repository;

import com.snef.sgbf.identite.entity.Habilitation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acces aux habilitations (association utilisateur / role / perimetre). */
public interface HabilitationRepository extends JpaRepository<Habilitation, Long> {

    /** Toutes les habilitations (actives ou non) d'un utilisateur, pour affichage/administration. */
    List<Habilitation> findByUtilisateur_Id(Long utilisateurId);

    /**
     * Habilitations actives d'un utilisateur - c'est cette liste qui alimente
     * ses autorisations effectives ({@link com.snef.sgbf.security.CustomUserDetails}).
     *
     * <p>{@code roleMetier} et {@code service} sont charges par jointure
     * (FETCH) plutot que paresseusement : {@code CustomUserDetails.getAuthorities()}
     * est invoquee par Spring Security APRES la fermeture de la transaction
     * de {@code UserDetailsServiceImpl.loadUserById} (le contrat
     * {@code UserDetails} n'est pas concu pour rester attache a une session
     * Hibernate) - un acces paresseux y leverait {@code LazyInitializationException}.
     */
    @Query("SELECT h FROM Habilitation h "
            + "JOIN FETCH h.roleMetier "
            + "LEFT JOIN FETCH h.service "
            + "WHERE h.utilisateur.id = :utilisateurId AND h.actif = true")
    List<Habilitation> findByUtilisateur_IdAndActifTrue(@Param("utilisateurId") Long utilisateurId);

    /** Utilise par RG-HAB-005 (exclusivite RH) pour verifier l'absence d'autre habilitation active. */
    boolean existsByUtilisateur_IdAndActifTrueAndRoleMetier_CodeNot(Long utilisateurId, String roleMetierCode);

    /**
     * Existence pure (COUNT/EXISTS SQL), sans charger la moindre entite ni
     * proxy : utilisee par {@code BootstrapAdminSeeder}, qui s'execute en
     * dehors de tout contexte transactionnel Spring (un
     * {@code ApplicationRunner} n'ouvre pas de session Hibernate a lui seul).
     */
    boolean existsByActifTrueAndRoleMetier_Code(String roleMetierCode);
}
