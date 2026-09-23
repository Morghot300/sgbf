-- ============================================================================
-- V1 : Referentiels de base
-- ----------------------------------------------------------------------------
-- Tables de reference independantes (aucune ne depend d'une autre table du
-- schema applicatif) : service, role_metier, vehicule, chantier, code_hn,
-- motif_interruption_mission.
--
-- Source : Analyse_Conception_Systeme_Gestion_FIPH.docx, section 18
-- (description des classes) et section 20 (description des tables).
-- ============================================================================

-- Referentiel des services (Service.codeService est "unique et invariant" -
-- RG-FIPH-006). Chaque service porte le Code Service utilise sur les lignes
-- de pointage manuelles des agents non concernes par une mission.
CREATE TABLE service (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_service     VARCHAR(20)  NOT NULL,
    libelle          VARCHAR(150) NOT NULL,
    actif            BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_code_service UNIQUE (code_service)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel des services (section 18 du document source).';

-- Referentiel des roles metier applicatifs (section 10, section 18 : code
-- AGENT, CHARGE_AFFAIRES, RESPONSABLE_ACTIVITE, DIRECTION, RH, ADMINISTRATEUR).
-- Une Habilitation associe un utilisateur a un role_metier ET a un perimetre
-- (RG-HAB-001) : le role seul ne donne jamais de droit.
CREATE TABLE role_metier (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    code    VARCHAR(30)  NOT NULL,
    libelle VARCHAR(100) NOT NULL,
    CONSTRAINT uq_role_metier_code UNIQUE (code)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel des roles metier (section 10 et 18).';

INSERT INTO role_metier (code, libelle) VALUES
    ('AGENT',                 'Agent (emetteur)'),
    ('CHARGE_AFFAIRES',       'Charge d''Affaires'),
    ('PERSONNE_HABILITEE',    'Personne habilitee du service'),
    ('RESPONSABLE_ACTIVITE',  'Responsable d''activite'),
    ('DIRECTION',             'Direction'),
    ('RH',                    'Ressources Humaines (lecture seule globale)'),
    ('ADMINISTRATEUR',        'Administrateur du systeme');

-- Referentiel des vehicules (Omnium service ou personnel). L'immatriculation
-- correspond au champ "LT" du bon de sortie papier (RG-BS-006).
CREATE TABLE vehicule (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    immatriculation  VARCHAR(20) NOT NULL,
    type             VARCHAR(30) NOT NULL COMMENT 'OMNIUM_SERVICE ou PERSONNEL',
    date_creation    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vehicule_immatriculation UNIQUE (immatriculation),
    CONSTRAINT chk_vehicule_type CHECK (type IN ('OMNIUM_SERVICE', 'PERSONNEL'))
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel des vehicules (section 18).';

-- Referentiel des chantiers / affaires.
CREATE TABLE chantier (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_affaire  VARCHAR(30)  NOT NULL,
    libelle       VARCHAR(150) NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chantier_code_affaire UNIQUE (code_affaire)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel des chantiers / affaires (section 18).';

-- Referentiel des codes mission (Code HN), rattaches a un chantier. Une
-- Mission reference un code_hn (Mission.codeHN, section 5.1).
CREATE TABLE code_hn (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL,
    libelle      VARCHAR(150) NOT NULL,
    chantier_id  BIGINT       NOT NULL,
    date_creation DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_code_hn_code UNIQUE (code),
    CONSTRAINT fk_code_hn_chantier FOREIGN KEY (chantier_id) REFERENCES chantier (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel des codes mission (section 7 et 18).';

-- Referentiel configurable des motifs d'interruption de mission (RG-MIS-007) :
-- l'ajout d'un nouveau motif ne requiert aucune modification structurelle.
CREATE TABLE motif_interruption_mission (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL,
    libelle       VARCHAR(200) NOT NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_motif_interruption_code UNIQUE (code)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel configurable des motifs d''interruption (section 6.2, RG-MIS-007).';

-- Liste proposee par le document source (section 6.2) : proposition initiale de
-- modelisation, non definitive selon le document lui-meme, mais reprise telle
-- quelle car explicitement fournie avec sa justification (voir analyse, point I.1).
INSERT INTO motif_interruption_mission (code, libelle) VALUES
    ('MISSION_AVORTEE',        'La mission n''a pas pu etre menee a son terme des son engagement.'),
    ('DELAI_DEPASSE',          'Le delai imparti a la mission est depasse sans achevement.'),
    ('CHANGEMENT_AFFECTATION', 'L''agent est reaffecte pour une raison independante de la mission elle-meme.'),
    ('NOUVELLE_MISSION',       'L''agent est requis en priorite sur une nouvelle mission.'),
    ('ARRET_OPERATIONNEL',     'Arret decide pour raison operationnelle (materiel, securite, meteo, etc.).'),
    ('AUTRE',                  'Motif ne correspondant a aucun code predefini ; commentaire libre attendu.');
