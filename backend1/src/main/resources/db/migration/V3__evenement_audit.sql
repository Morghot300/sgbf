-- ============================================================================
-- V3 : Journal d'audit unique et polymorphe (evenement_audit)
-- ----------------------------------------------------------------------------
-- Remplace les anciennes tables HistoriqueWorkflow / JournalAudit (choix de
-- conception documente section 24 du document source). Reference polymorphe
-- (entite_type, entite_id) volontairement NON contrainte par cle etrangere
-- (section 20, tableau des tables) : elle doit pouvoir couvrir des entites de
-- nature tres differente (Utilisateur, Habilitation, Mission, AffectationMission,
-- BonSortie, FIPH, FIPHVersion...) sans FK composite ingerable.
--
-- Ecriture seule (append-only) : la section 26.3 et la recommandation
-- d'architecture (section 30) demandent explicitement une garantie technique,
-- pas seulement applicative -> declencheurs interdisant UPDATE et DELETE,
-- qui s'appliquent meme si un bug applicatif tentait l'un ou l'autre.
-- ============================================================================

CREATE TABLE evenement_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    entite_type     VARCHAR(40)  NOT NULL COMMENT 'Ex: UTILISATEUR, HABILITATION, MISSION, AFFECTATION_MISSION, BON_SORTIE, FIPH, FIPH_VERSION',
    entite_id       VARCHAR(40)  NOT NULL COMMENT 'Identifiant de l''entite concernee, sous forme texte (reference polymorphe)',
    utilisateur_id  BIGINT       NULL COMMENT 'NULL pour une action declenchee par le systeme (ex: generation automatique)',
    action          VARCHAR(60)  NOT NULL,
    valeur_avant    JSON         NULL,
    valeur_apres    JSON         NULL,
    statut_avant    VARCHAR(40)  NULL,
    statut_apres    VARCHAR(40)  NULL,
    -- Horodatage exclusivement serveur (section 23.2) : jamais une valeur
    -- fournie par le client, toujours DEFAULT CURRENT_TIMESTAMP.
    date_action     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evenement_audit_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Journal d''audit unique, polymorphe, append-only (section 24).';

-- Index de recherche multicritere recommande section 30 (agent, periode,
-- statut...) : ici la base indispensable des qu'on filtre par entite.
CREATE INDEX idx_evenement_audit_entite ON evenement_audit (entite_type, entite_id, date_action);
CREATE INDEX idx_evenement_audit_utilisateur ON evenement_audit (utilisateur_id, date_action);
CREATE INDEX idx_evenement_audit_action ON evenement_audit (action, date_action);

DELIMITER $$

-- Refuse toute tentative de modification d'un enregistrement d'audit deja
-- ecrit, quelle que soit son origine (application, script, requete manuelle).
CREATE TRIGGER trg_evenement_audit_no_update
    BEFORE UPDATE ON evenement_audit
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'evenement_audit est en ecriture seule (append-only) : UPDATE interdit.';
END$$

-- Refuse toute suppression physique d'un enregistrement d'audit (section 26.3,
-- 26.4 : interdiction de suppression physique).
CREATE TRIGGER trg_evenement_audit_no_delete
    BEFORE DELETE ON evenement_audit
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'evenement_audit est en ecriture seule (append-only) : DELETE interdit.';
END$$

DELIMITER ;
