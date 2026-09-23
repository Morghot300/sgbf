-- ============================================================================
-- V11 : Notifications internes et prise en main exceptionnelle par le
-- Super Administrateur (evolution du 2026-08-19)
-- ----------------------------------------------------------------------------
-- notification         : file d'attente par destinataire, jamais partagee
--                         entre utilisateurs (section 7 de la mission).
-- validation.prise_en_main_super_admin : distingue une decision de
--                         validation normale d'une intervention exceptionnelle
--                         du Super Administrateur (section 12-13).
-- ============================================================================

ALTER TABLE validation
    ADD COLUMN prise_en_main_super_admin BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'TRUE si cette decision a ete prise par un Super Administrateur au titre de la prise en main exceptionnelle (section 12-16), jamais une validation normale';

CREATE TABLE notification (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    destinataire_id     BIGINT       NOT NULL COMMENT 'Jamais visible par un autre utilisateur que lui (section 7)',
    type                VARCHAR(40)  NOT NULL,
    titre               VARCHAR(200) NOT NULL,
    message             VARCHAR(500) NOT NULL,
    entite_type         VARCHAR(30)  NOT NULL,
    entite_id           BIGINT       NOT NULL,
    lien                VARCHAR(200) NOT NULL COMMENT 'Chemin frontend permettant d''ouvrir directement l''entite concernee',
    declenche_par_id    BIGINT       NULL COMMENT 'Utilisateur ayant provoque l''evenement (peut etre NULL pour une action systeme)',
    lue                 BOOLEAN      NOT NULL DEFAULT FALSE,
    date_creation       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_lecture        DATETIME     NULL,
    CONSTRAINT fk_notification_destinataire FOREIGN KEY (destinataire_id) REFERENCES users (id),
    CONSTRAINT fk_notification_declencheur FOREIGN KEY (declenche_par_id) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Notifications internes par destinataire (section 5-7 de la mission workflow FIPH + notifications).';

CREATE INDEX idx_notification_destinataire ON notification (destinataire_id, lue, date_creation);
