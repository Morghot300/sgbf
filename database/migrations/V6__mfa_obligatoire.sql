-- ============================================================================
-- V6 : Authentification a deux etapes obligatoire pour tous les utilisateurs
-- ----------------------------------------------------------------------------
-- Remplace le mecanisme precedent, ou le second facteur (code envoye par
-- e-mail) etait conditionne a un indicateur par utilisateur (users.mfa_actif).
-- Decision explicite du 2026-08-17 : le code e-mail devient systematique pour
-- toute connexion, quel que soit l'utilisateur - l'indicateur devient donc
-- sans objet et est supprime plutot que laisse en place, inutilise (aucun
-- systeme MFA parallele conserve).
--
-- otp_challenge gagne trois colonnes pour porter le renvoi de code
-- (section 4 de la demande : delai minimal entre deux renvois, nombre
-- maximal de renvois, invalidation explicite d'un code remplace).
-- ============================================================================

ALTER TABLE users
    DROP COLUMN mfa_actif;

ALTER TABLE otp_challenge
    ADD COLUMN nombre_renvois     INT      NOT NULL DEFAULT 0 COMMENT 'Nombre de fois ou ce challenge a vu son code regenere (renvoi)',
    ADD COLUMN date_dernier_envoi DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Sert au delai minimal entre deux renvois',
    ADD COLUMN invalide           BOOLEAN  NOT NULL DEFAULT FALSE COMMENT 'Vrai si ce challenge a ete remplace par un evenement plus recent (nouvelle tentative de connexion) - distinct de "consomme" (code effectivement valide utilise avec succes)';
