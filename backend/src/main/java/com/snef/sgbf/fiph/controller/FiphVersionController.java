package com.snef.sgbf.fiph.controller;

import com.snef.sgbf.common.pdf.DocumentPdf;
import com.snef.sgbf.fiph.dto.CompleterPointageRequest;
import com.snef.sgbf.fiph.dto.CreerNouvelleVersionRequest;
import com.snef.sgbf.fiph.dto.DefinirDateFinRequest;
import com.snef.sgbf.fiph.dto.FiphVersionDto;
import com.snef.sgbf.fiph.dto.PriseEnMainSuperAdminRequest;
import com.snef.sgbf.fiph.dto.ValidationDto;
import com.snef.sgbf.fiph.dto.ValiderFiphRequest;
import com.snef.sgbf.fiph.service.FiphVersionPdfService;
import com.snef.sgbf.fiph.service.FiphVersionService;
import com.snef.sgbf.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * API du cycle de vie d'une version de FIPH : complement, signature,
 * soumission, validation a trois niveaux, versionnement post-validation
 * (section 12, 21 du document source).
 *
 * <p>Le controle fin (role ET perimetre ET separation des responsabilites
 * RG-HAB-004) est entierement delegue a {@link FiphVersionService} ;
 * {@code @PreAuthorize} ne fournit ici qu'un premier filtrage grossier par
 * role, comme dans les autres modules (voir Javadoc de
 * {@code security.CustomUserDetails}).
 */
@RestController
@RequestMapping("/api/fiph-versions")
public class FiphVersionController {

    private final FiphVersionService fiphVersionService;
    private final FiphVersionPdfService fiphVersionPdfService;

    public FiphVersionController(FiphVersionService fiphVersionService, FiphVersionPdfService fiphVersionPdfService) {
        this.fiphVersionService = fiphVersionService;
        this.fiphVersionPdfService = fiphVersionPdfService;
    }

    /** Telechargement de la FIPH terminee au format PDF (section 13.3, RG-DOC-003/004). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> telecharger(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        DocumentPdf document = fiphVersionPdfService.genererPdf(id, principal.getUtilisateur());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(document.contenu());
    }

    @GetMapping("/{id}")
    public FiphVersionDto obtenir(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.obtenirParId(id, principal.getUtilisateur());
    }

    @GetMapping("/fiph/{fiphId}")
    public List<FiphVersionDto> listerPourFiph(@PathVariable Long fiphId, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.listerVersions(fiphId, principal.getUtilisateur());
    }

    @GetMapping("/{id}/validations")
    public List<ValidationDto> listerValidations(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.listerValidations(id, principal.getUtilisateur());
    }

    @PutMapping("/{id}/pointage")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public FiphVersionDto completerPointage(@PathVariable Long id, @Valid @RequestBody CompleterPointageRequest requete,
                                             @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.completerPointage(id, requete, principal.getUtilisateur());
    }

    /** Definit/modifie la date de fin de la periode (evolution du 2026-08-21, section 2/7/8). */
    @PutMapping("/{id}/date-fin")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public FiphVersionDto definirDateFin(@PathVariable Long id, @Valid @RequestBody DefinirDateFinRequest requete,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.definirDateFin(id, requete, principal.getUtilisateur());
    }

    /** Signature de l'emetteur (RG-FIPH-019) - reservee au titulaire, verifie en service. */
    @PostMapping("/{id}/signer")
    public FiphVersionDto signer(@PathVariable Long id, HttpServletRequest request,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.signer(id, request.getRemoteAddr(), principal.getUtilisateur());
    }

    @PostMapping("/{id}/soumettre")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public FiphVersionDto soumettre(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.soumettre(id, principal.getUtilisateur());
    }

    @PostMapping("/{id}/valider/{niveau}")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE', 'RESPONSABLE_ACTIVITE', 'DIRECTION')")
    public FiphVersionDto valider(@PathVariable Long id, @PathVariable int niveau,
                                   @Valid @RequestBody ValiderFiphRequest requete,
                                   HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.valider(id, niveau, requete, request.getRemoteAddr(), principal.getUtilisateur());
    }

    /**
     * Prise en main exceptionnelle par le Super Administrateur (evolution du
     * 2026-08-19, section 11-16) - reservee exclusivement a ce role, jamais
     * accessible a un Administrateur standard meme via un appel direct a
     * l'API (verification en base, en plus de cette annotation, dans
     * {@code FiphVersionService.priseEnMainSuperAdministrateur}).
     */
    @PostMapping("/{id}/prise-en-main-super-admin")
    @PreAuthorize("hasRole('SUPER_ADMINISTRATEUR')")
    public FiphVersionDto priseEnMainSuperAdmin(@PathVariable Long id, @Valid @RequestBody PriseEnMainSuperAdminRequest requete,
                                                 HttpServletRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        return fiphVersionService.priseEnMainSuperAdministrateur(id, requete, request.getRemoteAddr(), principal.getUtilisateur());
    }

    /** RG-VER-001 a 007 : nouvelle version a partir d'une FIPH deja validee definitivement. */
    @PostMapping("/fiph/{fiphId}/nouvelle-version")
    @PreAuthorize("hasAnyRole('CHARGE_AFFAIRES', 'PERSONNE_HABILITEE')")
    public ResponseEntity<FiphVersionDto> creerNouvelleVersion(@PathVariable Long fiphId,
                                                                @Valid @RequestBody CreerNouvelleVersionRequest requete,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        FiphVersionDto nouvelle = fiphVersionService.creerNouvelleVersion(fiphId, requete, principal.getUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(nouvelle);
    }
}
