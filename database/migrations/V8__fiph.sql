-- ============================================================================
-- V8 : FIPH (Fiche Individuelle de Pointage Hebdomadaire)
-- ----------------------------------------------------------------------------
-- fiph            : identite stable du document (agent, service, origine,
--                    periode) - section 4, 17.
-- fiph_version     : contenu versionne, immuable une fois valide
--                    definitivement (RG-VER-001 a 007, choix de conception
--                    documente section 17 : "FIPHVersion plutot que
--                    modification directe de FIPH").
-- fiph_pointage    : ligne de pointage journaliere, rattachee a
--                    l'affectation active ce jour-la OU au code service -
--                    jamais les deux (RG-FIPH-007).
-- signature        : preuve technique (visa ou signature electronique).
-- validation       : trace d'une decision de validation a un niveau donne.
-- ============================================================================

CREATE TABLE signature (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(30)  NOT NULL COMMENT 'VISA_APPLICATIF ou SIGNATURE_ELECTRONIQUE (section 22, 23.1)',
    empreinte        VARCHAR(255) NOT NULL COMMENT 'Identifiant de session ou empreinte cryptographique selon le type',
    date_signature   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    adresse_ip       VARCHAR(45)  NULL COMMENT 'IPv4 ou IPv6',
    CONSTRAINT chk_signature_type CHECK (type IN ('VISA_APPLICATIF', 'SIGNATURE_ELECTRONIQUE'))
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Preuve technique associee a une signature ou une validation (section 22).';

CREATE TABLE fiph (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id            BIGINT      NOT NULL,
    service_id          BIGINT      NOT NULL COMMENT 'Copie a la creation (section 8) - source de verite Agent.service a cet instant',
    origine             VARCHAR(20) NOT NULL COMMENT 'BON_SORTIE ou MANUELLE (RG-FIPH-004)',
    bon_sortie_id       BIGINT      NULL COMMENT 'Bon de sortie declencheur, si origine BON_SORTIE',
    cree_par            BIGINT      NULL COMMENT 'Utilisateur createur, pour une FIPH MANUELLE (0..1)',
    annee               INT         NOT NULL,
    mois                INT         NOT NULL,
    numero_semaine      INT         NOT NULL,
    -- Bornes de la periode hebdomadaire, calculees a la creation (norme ISO
    -- 8601 - semaine du lundi au dimanche) et conservees pour affichage/
    -- requetage direct sans recalcul repete.
    date_debut_periode  DATE        NOT NULL,
    date_fin_periode    DATE        NOT NULL,
    version_courante_id BIGINT      NULL COMMENT 'Pointeur vers la version active (NULL uniquement le temps de la toute premiere insertion)',
    statut              VARCHAR(30) NOT NULL COMMENT 'Miroir de fiph_version.statut_version pour la version courante - permet des requetes sans jointure',
    date_creation       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fiph_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_fiph_service FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_fiph_bon_sortie FOREIGN KEY (bon_sortie_id) REFERENCES bon_sortie (id),
    CONSTRAINT fk_fiph_cree_par FOREIGN KEY (cree_par) REFERENCES users (id),
    CONSTRAINT chk_fiph_origine CHECK (origine IN ('BON_SORTIE', 'MANUELLE')),
    -- RG-FIPH-002 : une FIPH est unique par couple (agent, periode hebdomadaire).
    CONSTRAINT uq_fiph_agent_periode UNIQUE (agent_id, annee, numero_semaine)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Identite stable de la FIPH (section 4, 17).';

CREATE TABLE fiph_version (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    fiph_id               BIGINT       NOT NULL,
    numero_version        INT          NOT NULL,
    date_creation          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cree_par              BIGINT       NOT NULL,
    -- RG-VER-002 : obligatoire des lors que numero_version > 1 (CHECK ci-dessous).
    motif_modification     VARCHAR(500) NULL,
    version_precedente_id  BIGINT       NULL,
    total_hn               DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT 'Total heures normales',
    total_hs               DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT 'Total heures supplementaires',
    statut_version         VARCHAR(30)  NOT NULL DEFAULT 'BROUILLON',
    -- RG-VER-006 : calculee uniquement au passage a VALIDEE_DEFINITIVEMENT.
    empreinte_integrite     VARCHAR(64)  NULL COMMENT 'SHA-256 hexadecimal du contenu fige',
    -- Signature de l'emetteur (RG-FIPH-019) ; distincte des signatures de
    -- validation, portees individuellement par chaque ligne "validation".
    signature_emetteur_id   BIGINT       NULL,
    lock_version            INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiph_version_fiph FOREIGN KEY (fiph_id) REFERENCES fiph (id),
    CONSTRAINT fk_fiph_version_cree_par FOREIGN KEY (cree_par) REFERENCES users (id),
    CONSTRAINT fk_fiph_version_precedente FOREIGN KEY (version_precedente_id) REFERENCES fiph_version (id),
    CONSTRAINT fk_fiph_version_signature FOREIGN KEY (signature_emetteur_id) REFERENCES signature (id),
    CONSTRAINT chk_fiph_version_statut CHECK (statut_version IN (
        'BROUILLON', 'EN_COMPLEMENT', 'SIGNEE', 'SOUMISE',
        'VALIDEE_NIVEAU_2', 'VALIDEE_NIVEAU_3', 'VALIDEE_DEFINITIVEMENT',
        'REJETEE', 'RETOUR_POUR_CORRECTION', 'ANNULEE', 'EN_REVISION'
    )),
    -- RG-VER-002 : motif obligatoire pour toute version au-dela de la premiere.
    CONSTRAINT chk_fiph_version_motif CHECK (numero_version = 1 OR motif_modification IS NOT NULL),
    CONSTRAINT uq_fiph_version_numero UNIQUE (fiph_id, numero_version)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Contenu versionne et immuable apres validation definitive (section 17, 21).';

ALTER TABLE fiph
    ADD CONSTRAINT fk_fiph_version_courante FOREIGN KEY (version_courante_id) REFERENCES fiph_version (id);

CREATE INDEX idx_fiph_version_fiph ON fiph_version (fiph_id);
CREATE INDEX idx_fiph_statut ON fiph (statut);
-- Recherche multicritere recommandee section 30 (agent, service, periode, statut, origine).
CREATE INDEX idx_fiph_recherche ON fiph (agent_id, service_id, annee, numero_semaine, statut, origine);

CREATE TABLE fiph_pointage (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    fiph_version_id         BIGINT      NOT NULL,
    jour_semaine            VARCHAR(10) NOT NULL COMMENT 'LUNDI a DIMANCHE',
    date_pointage            DATE        NOT NULL,
    heures_normales          DECIMAL(4,2) NOT NULL DEFAULT 0,
    heures_sup               DECIMAL(4,2) NOT NULL DEFAULT 0,
    affectation_mission_id   BIGINT      NULL,
    service_id               BIGINT      NULL COMMENT 'Code Service - exclusif avec affectation_mission_id (RG-FIPH-007)',
    CONSTRAINT fk_pointage_version FOREIGN KEY (fiph_version_id) REFERENCES fiph_version (id),
    CONSTRAINT fk_pointage_affectation FOREIGN KEY (affectation_mission_id) REFERENCES affectation_mission (id),
    CONSTRAINT fk_pointage_service FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT chk_pointage_jour CHECK (jour_semaine IN ('LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI', 'DIMANCHE')),
    -- RG-FIPH-007 : exclusivite stricte, portee en base (CHECK), pas
    -- seulement applicative (section 20.1, recommandation section 30).
    CONSTRAINT chk_pointage_exclusivite CHECK (
        (affectation_mission_id IS NOT NULL AND service_id IS NULL)
        OR (affectation_mission_id IS NULL AND service_id IS NOT NULL)
    ),
    CONSTRAINT uq_pointage_version_jour UNIQUE (fiph_version_id, date_pointage)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Ligne de pointage journaliere (section 4.1, 6.4).';

CREATE INDEX idx_pointage_affectation ON fiph_pointage (affectation_mission_id);

CREATE TABLE validation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    fiph_version_id     BIGINT      NOT NULL,
    utilisateur_id      BIGINT      NOT NULL,
    niveau_validation   INT         NOT NULL COMMENT '2 (Charge Affaires), 3 (Responsable activite) ou 4 (Direction)',
    decision            VARCHAR(30) NOT NULL,
    date_validation      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    commentaire         VARCHAR(500) NULL,
    signature_id        BIGINT      NOT NULL,
    statut_avant        VARCHAR(30) NULL,
    statut_apres        VARCHAR(30) NULL,
    CONSTRAINT fk_validation_version FOREIGN KEY (fiph_version_id) REFERENCES fiph_version (id),
    CONSTRAINT fk_validation_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id),
    CONSTRAINT fk_validation_signature FOREIGN KEY (signature_id) REFERENCES signature (id),
    CONSTRAINT chk_validation_niveau CHECK (niveau_validation BETWEEN 2 AND 4),
    CONSTRAINT chk_validation_decision CHECK (decision IN ('VALIDEE', 'REJETEE', 'RETOUR_POUR_CORRECTION'))
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Trace d''une decision de validation a un niveau donne (section 18, 22).';

CREATE INDEX idx_validation_version ON validation (fiph_version_id);

DELIMITER $$

-- Section 20.1 / 26.3 : validation, signature et fiph_version (une fois
-- VALIDEE_DEFINITIVEMENT) sont protegees contre toute suppression ou
-- modification a posteriori - garantie technique, pas seulement applicative.
CREATE TRIGGER trg_validation_no_delete
    BEFORE DELETE ON validation
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validation : suppression physique interdite (section 26.3).';
END$$

CREATE TRIGGER trg_validation_no_update
    BEFORE UPDATE ON validation
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validation : ecriture seule, modification interdite (section 26.3).';
END$$

CREATE TRIGGER trg_signature_no_delete
    BEFORE DELETE ON signature
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signature : suppression physique interdite (section 26.3).';
END$$

CREATE TRIGGER trg_signature_no_update
    BEFORE UPDATE ON signature
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'signature : ecriture seule, modification interdite (section 26.3).';
END$$

-- RG-VER-001 : une fois VALIDEE_DEFINITIVEMENT, une version est figee -
-- refuse toute UPDATE ulterieure, y compris applicative (recommandation
-- explicite section 26.3 : "doublee si possible d'un declencheur base de
-- donnees refusant l'UPDATE").
CREATE TRIGGER trg_fiph_version_immuable
    BEFORE UPDATE ON fiph_version
    FOR EACH ROW
BEGIN
    IF OLD.statut_version = 'VALIDEE_DEFINITIVEMENT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'fiph_version : une version validee definitivement est immuable (RG-VER-001).';
    END IF;
END$$

DELIMITER ;
