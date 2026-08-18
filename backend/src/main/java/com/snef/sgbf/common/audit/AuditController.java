package com.snef.sgbf.common.audit;

import com.snef.sgbf.security.CustomUserDetails;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation et export du journal d'audit d'une FIPH (section 24 du
 * document source).
 *
 * <p>La consultation de l'historique suit le meme perimetre que la
 * consultation de la FIPH elle-meme (section 14, colonne "Historique" - "Oui"
 * pour tout role deja habilite a consulter le document, controle applique
 * dans {@link AuditHistoryService}). L'export (CSV/PDF) est en revanche
 * explicitement restreint par le document source a la Direction, la RH et
 * l'Administrateur (section 24 : "Export d'audit... consultable par la
 * Direction, la RH et l'Administrateur").
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditHistoryService auditHistoryService;

    public AuditController(AuditHistoryService auditHistoryService) {
        this.auditHistoryService = auditHistoryService;
    }

    @GetMapping("/fiph/{fiphId}")
    public List<EvenementAuditDto> historique(@PathVariable Long fiphId, @AuthenticationPrincipal CustomUserDetails principal) {
        return auditHistoryService.historiqueFiph(fiphId, principal.getUtilisateur());
    }

    @GetMapping("/fiph/{fiphId}/export/csv")
    @PreAuthorize("hasAnyRole('DIRECTION', 'RH', 'ADMINISTRATEUR')")
    public ResponseEntity<byte[]> exporterCsv(@PathVariable Long fiphId, @AuthenticationPrincipal CustomUserDetails principal) {
        DocumentExport export = auditHistoryService.exporterCsv(fiphId, principal.getUtilisateur());
        return reponse(export);
    }

    @GetMapping("/fiph/{fiphId}/export/pdf")
    @PreAuthorize("hasAnyRole('DIRECTION', 'RH', 'ADMINISTRATEUR')")
    public ResponseEntity<byte[]> exporterPdf(@PathVariable Long fiphId, @AuthenticationPrincipal CustomUserDetails principal) {
        DocumentExport export = auditHistoryService.exporterPdf(fiphId, principal.getUtilisateur());
        return reponse(export);
    }

    private ResponseEntity<byte[]> reponse(DocumentExport export) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(export.contenu());
    }
}
