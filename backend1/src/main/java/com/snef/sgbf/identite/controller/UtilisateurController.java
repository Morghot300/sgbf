package com.snef.sgbf.identite.controller;

import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
import com.snef.sgbf.identite.dto.ModifierEmailRequest;
import com.snef.sgbf.identite.dto.ModifierIdentifiantRequest;
import com.snef.sgbf.identite.dto.ModifierIdentiteRequest;
import com.snef.sgbf.identite.dto.ModifierServiceRequest;
import com.snef.sgbf.identite.dto.ReinitialiserMotDePasseRequest;
import com.snef.sgbf.identite.dto.UtilisateurDto;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.service.UtilisateurService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API d'administration des comptes applicatifs.
 *
 * <p>Integralement reservee a l'Administrateur ({@code @PreAuthorize} au
 * niveau classe) : gerer des comptes et leur statut n'est jamais une action
 * accessible a un autre role, quel que soit son perimetre (section 14 de
 * l'analyse fonctionnelle).
 */
@RestController
@RequestMapping("/api/utilisateurs")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    public UtilisateurController(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    /**
     * Liste des comptes, avec recherche/filtrage optionnel combinable
     * (evolution du 2026-08-18, section 4) - voir Javadoc de
     * {@code UtilisateurService.rechercher}.
     */
    @GetMapping
    public List<UtilisateurDto> lister(@RequestParam(required = false) String terme,
                                        @RequestParam(required = false) Long serviceId,
                                        @RequestParam(required = false) String role,
                                        @RequestParam(required = false) StatutCompte statut,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        if (terme == null && serviceId == null && role == null && statut == null) {
            return utilisateurService.listerTous(principal.getUtilisateur());
        }
        return utilisateurService.rechercher(terme, serviceId, role, statut, principal.getUtilisateur());
    }

    @GetMapping("/{id}")
    public UtilisateurDto obtenir(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.obtenirParId(id, principal.getUtilisateur());
    }

    @PostMapping
    public ResponseEntity<UtilisateurDto> creer(@Valid @RequestBody CreerUtilisateurRequest requete,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        UtilisateurDto cree = utilisateurService.creer(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }

    @PutMapping("/{id}/statut/{statut}")
    public ResponseEntity<Void> changerStatut(@PathVariable Long id, @PathVariable StatutCompte statut,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        utilisateurService.changerStatut(id, statut, principal.getUtilisateur());
        return ResponseEntity.noContent().build();
    }

    /** Correction du login de connexion (erreur de saisie, changement administratif - section 7-8 de l'evolution du 2026-08-18). */
    @PutMapping("/{id}/identifiant")
    public UtilisateurDto modifierIdentifiant(@PathVariable Long id, @Valid @RequestBody ModifierIdentifiantRequest requete,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.modifierIdentifiant(id, requete, principal.getUtilisateur());
    }

    /** Correction de l'adresse e-mail (section 7-8 de l'evolution du 2026-08-18). */
    @PutMapping("/{id}/email")
    public UtilisateurDto modifierEmail(@PathVariable Long id, @Valid @RequestBody ModifierEmailRequest requete,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.modifierEmail(id, requete, principal.getUtilisateur());
    }

    /** Correction du nom/prenom/matricule d'une personne (evolution du 2026-08-19). */
    @PutMapping("/{id}/identite")
    public UtilisateurDto modifierIdentite(@PathVariable Long id, @Valid @RequestBody ModifierIdentiteRequest requete,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.modifierIdentite(id, requete, principal.getUtilisateur());
    }

    /**
     * Ajoute un compte applicatif a une personne du referentiel qui n'en
     * avait pas encore (evolution du 2026-08-19) - remplace l'ancien
     * {@code PUT /api/agents/{id}/utilisateur/{utilisateurId}}.
     */
    @PutMapping("/{id}/compte")
    public UtilisateurDto ajouterCompteApplicatif(@PathVariable Long id, @RequestBody CreerUtilisateurRequest requete,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.ajouterCompteApplicatif(id, requete, principal.getUtilisateur());
    }

    /** Correction du service de rattachement (section 7 de l'evolution du 2026-08-18). */
    @PutMapping("/{id}/service")
    public UtilisateurDto modifierService(@PathVariable Long id, @RequestBody ModifierServiceRequest requete,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.modifierService(id, requete.serviceId(), principal.getUtilisateur());
    }

    /**
     * Reinitialisation du mot de passe d'un compte (section 7 de l'evolution
     * du 2026-08-18) - jamais renvoye ni journalise en clair, voir Javadoc de
     * {@link com.snef.sgbf.identite.service.UtilisateurService#reinitialiserMotDePasse}.
     */
    @PutMapping("/{id}/mot-de-passe")
    public UtilisateurDto reinitialiserMotDePasse(@PathVariable Long id, @Valid @RequestBody ReinitialiserMotDePasseRequest requete,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return utilisateurService.reinitialiserMotDePasse(id, requete, principal.getUtilisateur());
    }

}
