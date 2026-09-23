-- ============================================================================
-- V4 : Missions et affectations
-- ----------------------------------------------------------------------------
-- mission             : identite stable d'une mission (code, chantier, dates
--                        prevues). Peut etre interrompue avant son terme
--                        (section 5.1, RG-MIS-001).
-- affectation_mission  : lien agent / mission pour une periode ; porte le
--                        cycle de vie operationnel (creation, interruption,
--                        reaffectation) - jamais Mission elle-meme (section 5.2).
--
-- Choix de conception repris tel quel du document source (section 5.2) :
-- separer Mission (identite) et AffectationMission (cycle de vie) permet de
-- representer une reaffectation sans jamais recreer ni ecraser une mission.
-- ============================================================================

CREATE TABLE mission (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_hn_id             BIGINT      NOT NULL,
    chantier_id            BIGINT      NOT NULL,
    date_debut_prevue      DATE        NOT NULL,
    date_fin_prevue        DATE        NOT NULL,
    date_fin_reelle        DATE        NULL COMMENT 'Renseignee a la cloture, normale ou anticipee',
    statut                 VARCHAR(20) NOT NULL DEFAULT 'PLANIFIEE',
    mission_precedente_id  BIGINT      NULL COMMENT 'Renseignee uniquement si issue d''une reaffectation (RG-MIS-005)',
    date_creation          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_code_hn FOREIGN KEY (code_hn_id) REFERENCES code_hn (id),
    CONSTRAINT fk_mission_chantier FOREIGN KEY (chantier_id) REFERENCES chantier (id),
    CONSTRAINT fk_mission_precedente FOREIGN KEY (mission_precedente_id) REFERENCES mission (id),
    CONSTRAINT chk_mission_statut CHECK (statut IN ('PLANIFIEE', 'EN_COURS', 'INTERROMPUE', 'TERMINEE')),
    CONSTRAINT chk_mission_dates CHECK (date_fin_prevue >= date_debut_prevue)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Identite stable d''une mission (section 5.1).';

CREATE INDEX idx_mission_statut ON mission (statut);

CREATE TABLE affectation_mission (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id                    BIGINT      NOT NULL,
    mission_id                  BIGINT      NOT NULL,
    date_debut_affectation      DATE        NOT NULL,
    date_fin_affectation        DATE        NULL,
    statut_affectation          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    motif_interruption_id       BIGINT      NULL,
    -- Commentaire libre attendu lorsque motif_interruption = AUTRE (section 6.2) :
    -- absent de la description des tables du document source (point non
    -- precise), ajoute par hypothese documentee (voir analyse fonctionnelle,
    -- point I). Limite de longueur explicite = assainissement RG-SEC-003.
    commentaire_interruption    VARCHAR(500) NULL,
    affectation_precedente_id   BIGINT      NULL COMMENT 'Chainage vers l''affectation close lors d''une reaffectation (RG-MIS-005)',
    cree_par                    BIGINT      NOT NULL,
    date_creation                DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_affectation_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_affectation_mission FOREIGN KEY (mission_id) REFERENCES mission (id),
    CONSTRAINT fk_affectation_motif FOREIGN KEY (motif_interruption_id) REFERENCES motif_interruption_mission (id),
    CONSTRAINT fk_affectation_precedente FOREIGN KEY (affectation_precedente_id) REFERENCES affectation_mission (id),
    CONSTRAINT fk_affectation_cree_par FOREIGN KEY (cree_par) REFERENCES users (id),
    CONSTRAINT chk_affectation_statut
        CHECK (statut_affectation IN ('ACTIVE', 'INTERROMPUE', 'TERMINEE', 'TRANSFEREE')),
    -- RG-MIS-002 : motif obligatoire des lors que l'affectation est interrompue.
    CONSTRAINT chk_affectation_motif_si_interrompue
        CHECK (statut_affectation <> 'INTERROMPUE' OR motif_interruption_id IS NOT NULL)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Lien agent/mission pour une periode ; jamais supprimee (RG-MIS-003, section 5.2).';

CREATE INDEX idx_affectation_mission ON affectation_mission (mission_id);
-- Recommandation explicite section 30 : index sur (agent, dates) pour
-- accelerer la resolution de l'affectation active a une date donnee,
-- operation executee a chaque creation de bon de sortie (RG-FIPH-025).
CREATE INDEX idx_affectation_agent_dates ON affectation_mission (agent_id, date_debut_affectation, date_fin_affectation);

-- Unicite partielle : au plus une affectation ACTIVE par agent a un instant
-- donne (section 20.1). Standard SQL n'exprime pas simplement une contrainte
-- unique conditionnelle ; l'astuce MySQL (colonne generee, non NULL
-- uniquement pour les lignes ACTIVE + index unique dessus, les NULL
-- multiples etant autorises dans un index unique) permet de la porter
-- reellement en base plutot que de la laisser purement applicative.
ALTER TABLE affectation_mission
    ADD COLUMN agent_si_actif BIGINT
        GENERATED ALWAYS AS (CASE WHEN statut_affectation = 'ACTIVE' THEN agent_id ELSE NULL END) STORED;
CREATE UNIQUE INDEX uq_affectation_agent_actif ON affectation_mission (agent_si_actif);

DELIMITER $$

-- RG-MIS-003 : une affectation n'est jamais supprimee physiquement.
CREATE TRIGGER trg_affectation_mission_no_delete
    BEFORE DELETE ON affectation_mission
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'affectation_mission : suppression physique interdite (RG-MIS-003).';
END$$

-- RG-MIS-003 / RG-MIS-006 : mission_id, agent_id et date_debut_affectation
-- sont immuables apres creation. Toute correction procede de la cloture de
-- la ligne existante (statut, date_fin_affectation) suivie de la creation
-- d'une nouvelle ligne chainee via affectation_precedente_id - jamais d'une
-- reecriture de l'identite de l'affectation.
CREATE TRIGGER trg_affectation_mission_immutabilite
    BEFORE UPDATE ON affectation_mission
    FOR EACH ROW
BEGIN
    IF NOT (OLD.mission_id <=> NEW.mission_id)
        OR NOT (OLD.agent_id <=> NEW.agent_id)
        OR NOT (OLD.date_debut_affectation <=> NEW.date_debut_affectation) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'affectation_mission : mission_id, agent_id et date_debut_affectation sont immuables apres creation (RG-MIS-006).';
    END IF;
END$$

DELIMITER ;
