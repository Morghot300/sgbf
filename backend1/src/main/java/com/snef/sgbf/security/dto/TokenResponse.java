package com.snef.sgbf.security.dto;

/** Reponse porteuse d'un nouveau jeton d'acces, emise par la connexion terminee et par le rafraichissement. */
public record TokenResponse(String jetonAcces, long expiresInSecondes) {
}
