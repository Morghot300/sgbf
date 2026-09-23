package com.snef.sgbf.common.audit;

/** Export de journal d'audit (CSV ou PDF, section 24) - jamais persiste, genere a la demande. */
public record DocumentExport(byte[] contenu, String nomFichier, String contentType) {
}
