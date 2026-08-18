package com.snef.sgbf.security;

import com.snef.sgbf.identite.dto.CreerHabilitationRequest;
import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.identite.service.HabilitationService;
import com.snef.sgbf.identite.service.UtilisateurService;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import java.security.SecureRandom;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cree, au tout premier demarrage, un compte ADMINISTRATEUR si aucun
 * n'existe encore - sans quoi personne ne pourrait jamais se connecter pour
 * en creer un via l'interface (probleme classique de l'oeuf et de la poule
 * sur un systeme dont toute creation de compte passe par un administrateur
 * deja authentifie).
 *
 * <p>Ne s'execute qu'une fois : des qu'une habilitation ADMINISTRATEUR
 * existe en base, ce composant ne fait plus rien au demarrage suivant.
 *
 * <p>Identifiants du compte bootstrap :
 * <ul>
 *   <li>{@code ADMIN_BOOTSTRAP_EMAIL} / {@code ADMIN_BOOTSTRAP_PASSWORD}
 *       (voir {@code backend/.env.example}) si renseignes ;</li>
 *   <li>sinon, un mot de passe aleatoire de 24 caracteres est genere et
 *       affiche UNE SEULE FOIS dans les logs de demarrage - jamais persiste
 *       en clair nulle part, jamais rejoue dans un log ulterieur.</li>
 * </ul>
 * Ce mot de passe initial doit etre change des la premiere connexion (a
 * faire manuellement pour l'instant ; un flux "changer mon mot de passe"
 * dedie sera ajoute avec le module frontend d'administration).
 */
@Component
public class BootstrapAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CARACTERES_MOT_DE_PASSE =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";

    private final HabilitationRepository habilitationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurService utilisateurService;
    private final HabilitationService habilitationService;
    private final String emailConfigure;
    private final String motDePasseConfigure;

    public BootstrapAdminSeeder(HabilitationRepository habilitationRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 UtilisateurService utilisateurService,
                                 HabilitationService habilitationService,
                                 @Value("${app.bootstrap.admin-email:}") String emailConfigure,
                                 @Value("${app.bootstrap.admin-password:}") String motDePasseConfigure) {
        this.habilitationRepository = habilitationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.utilisateurService = utilisateurService;
        this.habilitationService = habilitationService;
        this.emailConfigure = emailConfigure;
        this.motDePasseConfigure = motDePasseConfigure;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean adminExiste = habilitationRepository.existsByActifTrueAndRoleMetier_Code(
                CodeRoleMetier.ADMINISTRATEUR.name());
        if (adminExiste) {
            return;
        }
        if (utilisateurRepository.existsByIdentifiant("admin")) {
            // Un compte "admin" existe deja mais sans habilitation active :
            // situation anormale (retrait manuel en base ?) - on ne recree
            // rien automatiquement pour eviter d'ecraser une decision
            // administrative deliberee ; un administrateur doit intervenir
            // manuellement via le script SQL de provisionnement.
            log.warn("Un compte 'admin' existe sans habilitation ADMINISTRATEUR active. "
                    + "Aucune creation automatique effectuee - verification manuelle requise.");
            return;
        }

        String email = emailConfigure.isBlank() ? "admin@sgbf.local" : emailConfigure;
        boolean motDePasseGenere = motDePasseConfigure.isBlank();
        String motDePasse = motDePasseGenere ? genererMotDePasse() : motDePasseConfigure;

        Utilisateur admin = versEntite(utilisateurService.creer(
                new CreerUtilisateurRequest("admin", email, motDePasse, null), null));

        habilitationService.attribuer(
                new CreerHabilitationRequest(admin.getId(), CodeRoleMetier.ADMINISTRATEUR.name(),
                        null, LocalDate.now(), null),
                admin);

        log.warn("=== Compte administrateur initial cree (identifiant : admin) ===");
        if (motDePasseGenere) {
            log.warn("Mot de passe genere automatiquement (a noter et changer immediatement) : {}", motDePasse);
        } else {
            log.warn("Mot de passe initial : celui fourni via ADMIN_BOOTSTRAP_PASSWORD.");
        }
        log.warn("Adresse e-mail associee : {}", email);
    }

    private String genererMotDePasse() {
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(CARACTERES_MOT_DE_PASSE.charAt(RANDOM.nextInt(CARACTERES_MOT_DE_PASSE.length())));
        }
        return sb.toString();
    }

    private Utilisateur versEntite(com.snef.sgbf.identite.dto.UtilisateurDto dto) {
        // UtilisateurService retourne un DTO (bonne pratique cote API), mais
        // HabilitationService.attribuer attend l'entite complete pour le
        // champ cree_par : un rechargement simple par id suffit ici, le cout
        // est negligeable puisque cette classe ne s'execute qu'une fois.
        return utilisateurRepository.findById(dto.id()).orElseThrow();
    }
}
