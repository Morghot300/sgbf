package com.snef.sgbf.identite.controller;

import com.snef.sgbf.identite.dto.AgentDto;
import com.snef.sgbf.identite.dto.CreerAgentRequest;
import com.snef.sgbf.identite.service.AgentService;
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
 * API du referentiel RH des agents.
 *
 * <p>La creation est reservee a l'Administrateur (section 10). La recherche
 * par nom/prenom reste accessible a tout utilisateur authentifie : elle sert
 * uniquement d'aide a la selection dans les formulaires (bon de sortie,
 * personnes a bord), jamais de mecanisme de resolution automatique - voir
 * l'avertissement dans {@code AgentRepository}.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<AgentDto> lister() {
        return agentService.listerTous();
    }

    @GetMapping("/{id}")
    public AgentDto obtenir(@PathVariable Long id) {
        return agentService.obtenirParId(id);
    }

    @GetMapping("/recherche")
    public List<AgentDto> rechercher(@RequestParam String terme) {
        return agentService.rechercherParNomOuPrenom(terme);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<AgentDto> creer(@Valid @RequestBody CreerAgentRequest requete,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        AgentDto cree = agentService.creer(requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(cree);
    }

    /** Associe un agent du referentiel RH a un compte applicatif existant (section 18, cardinalite 0..1). */
    @PutMapping("/{id}/utilisateur/{utilisateurId}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public AgentDto lierUtilisateur(@PathVariable Long id, @PathVariable Long utilisateurId,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return agentService.lierUtilisateur(id, utilisateurId, principal.getUtilisateur());
    }
}
