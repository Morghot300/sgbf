-- ============================================================================
-- V2 : Identite, habilitations et authentification (socle securite)
-- ----------------------------------------------------------------------------
-- users            : comptes applicatifs (section 18 : classe Utilisateur)
-- agent             : referentiel RH (section 18 : classe Agent) ; n'implique
--                     pas necessairement un compte applicatif
-- habilitation      : association utilisateur / role_metier / perimetre
--                     (RG-HAB-001) - c'est elle, et non le role seul, qui
--                     porte les droits
-- refresh_token     : jetons de rafraichissement JWT, revocables cote serveur
--                     (deconnexion explicite / compromission) ; les jetons
--                     d'acces restent stateless et ne sont jamais stockes ici
-- otp_challenge     : codes a usage unique envoyes par e-mail pour le second
--                     facteur d'authentification (MFA), ephemere
-- ============================================================================

CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    identifiant         VARCHAR(60)  NOT NULL COMMENT 'Identifiant de connexion',
    email               VARCHAR(150) NOT NULL COMMENT 'Adresse e-mail (Gmail) recevant les codes MFA',
    mot_de_passe_hash   VARCHAR(255) NOT NULL,
    statut_compte       VARCHAR(20)  NOT NULL DEFAULT 'ACTIF' COMMENT 'ACTIF, VERROUILLE, DESACTIVE',
    mfa_actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    service_id          BIGINT       NULL,
    lock_version        INT          NOT NULL DEFAULT 0,
    date_creation       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_identifiant UNIQUE (identifiant),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_statut_compte CHECK (statut_compte IN ('ACTIF', 'VERROUILLE', 'DESACTIVE')),
    CONSTRAINT fk_users_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Comptes applicatifs (section 18).';

CREATE TABLE agent (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    matricule       VARCHAR(20)  NOT NULL,
    nom             VARCHAR(100) NOT NULL,
    prenom          VARCHAR(100) NOT NULL,
    service_id      BIGINT       NOT NULL,
    utilisateur_id  BIGINT       NULL COMMENT 'Compte applicatif associe, s''il existe (0..1)',
    actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_matricule UNIQUE (matricule),
    CONSTRAINT uq_agent_utilisateur UNIQUE (utilisateur_id),
    CONSTRAINT fk_agent_service FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_agent_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Referentiel RH des agents (section 18).';

-- L'identification de la personne a bord et de l'agent titulaire repose sur
-- Agent.matricule (RG-PAB-001, point I.3 de l'analyse) : cette FK est donc au
-- coeur de la resolution automatique, jamais une saisie de texte libre.

CREATE TABLE habilitation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id  BIGINT   NOT NULL,
    role_metier_id  BIGINT   NOT NULL,
    -- NULL = perimetre global (uniquement valide pour RH - CONSULTATION_GLOBALE -
    -- et ADMINISTRATEUR, controle applicatif dans HabilitationService).
    service_id      BIGINT   NULL,
    date_debut      DATE     NOT NULL,
    date_fin        DATE     NULL,
    actif           BOOLEAN  NOT NULL DEFAULT TRUE,
    cree_par        BIGINT   NOT NULL,
    date_creation   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_habilitation_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id),
    CONSTRAINT fk_habilitation_role FOREIGN KEY (role_metier_id) REFERENCES role_metier (id),
    CONSTRAINT fk_habilitation_service FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_habilitation_cree_par FOREIGN KEY (cree_par) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Association utilisateur / role / perimetre (RG-HAB-001 a 006).';

CREATE INDEX idx_habilitation_utilisateur_actif ON habilitation (utilisateur_id, actif);

-- Jetons de rafraichissement JWT : stockes hashes (jamais en clair), ce qui
-- permet une revocation cote serveur (deconnexion, compromission) sans rendre
-- le systeme d'authentification globalement statefull (les jetons d'acces,
-- eux, restent verifies uniquement par signature).
CREATE TABLE refresh_token (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'UUID',
    utilisateur_id    BIGINT       NOT NULL,
    token_hash        VARCHAR(255) NOT NULL,
    date_expiration   DATETIME     NOT NULL,
    revoque           BOOLEAN      NOT NULL DEFAULT FALSE,
    date_creation     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Jetons de rafraichissement JWT, revocables.';

CREATE INDEX idx_refresh_token_utilisateur ON refresh_token (utilisateur_id, revoque);

-- Challenge MFA par e-mail : code a 6 chiffres, hashe, de courte duree de vie,
-- avec compteur de tentatives pour empecher le brute-force (voir EmailOtpService).
CREATE TABLE otp_challenge (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT 'UUID',
    utilisateur_id    BIGINT       NOT NULL,
    code_hash         VARCHAR(255) NOT NULL,
    date_expiration   DATETIME     NOT NULL,
    tentatives        INT          NOT NULL DEFAULT 0,
    consomme          BOOLEAN      NOT NULL DEFAULT FALSE,
    date_creation     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_otp_challenge_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES users (id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Challenges MFA par code envoye par e-mail (compte Gmail), ephemere.';

CREATE INDEX idx_otp_challenge_utilisateur ON otp_challenge (utilisateur_id, consomme);
