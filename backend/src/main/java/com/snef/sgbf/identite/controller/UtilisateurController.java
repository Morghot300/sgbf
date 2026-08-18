package com.snef.sgbf.identite.controller;

import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
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

    @GetMapping
    public List<UtilisateurDto> lister() {
        return utilisateurService.listerTous();
    }

    @GetMapping("/{id}")
    public UtilisateurDto obtenir(@PathVariable Long id) {
        return utilisateurService.obtenirParId(id);
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

}
