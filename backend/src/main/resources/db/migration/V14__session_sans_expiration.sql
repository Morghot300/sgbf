-- ============================================================================
-- V14 : session sans expiration d'inactivite (evolution du 2026-08-26)
-- ----------------------------------------------------------------------------
-- Jusqu'ici, un jeton de rafraichissement expirait automatiquement 7 jours
-- apres son emission (glissant a chaque renouvellement reussi), ce qui
-- imposait de fait une reconnexion si l'utilisateur restait plus de 7 jours
-- sans utiliser l'application. Nouvelle regle metier : pour tous les comptes,
-- seule une deconnexion explicite (ou une revocation administrative, deja
-- appliquee automatiquement par UtilisateurService.changerStatut lors de la
-- suspension/desactivation d'un compte) doit terminer une session - plus
-- aucune expiration liee au seul ecoulement du temps.
--
-- `date_expiration NULL` porte desormais ce sens explicite : "ce jeton n'expire
-- jamais par lui-meme". La colonne `revoque` (et le mecanisme de rotation a
-- usage unique) restent totalement inchanges - c'est toujours par ce biais que
-- toute session est terminee, jamais par un DELETE.
-- ============================================================================

ALTER TABLE refresh_token
    MODIFY COLUMN date_expiration DATETIME NULL
    COMMENT 'NULL = jeton sans expiration temporelle (evolution 2026-08-26) ; seules revoque/rotation terminent une session.';
