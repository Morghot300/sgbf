-- ============================================================================
-- V5 : Bon de sortie et personnes a bord
-- ----------------------------------------------------------------------------
-- bon_sortie          : document de sortie VH/personnel, evenement declencheur
--                        de la FIPH de mission (section 3, RG-BS-001 a 007).
-- bon_sortie_personne : association du bon de sortie principal a chacune des
--                        personnes a bord identifiees comme agents
--                        (section 9.2, RG-PAB-001 a 009).
--
-- Choix de conception repris du document source (section 9.1) : une seule
-- entite BonSortie, distinguee par son attribut origine (PRINCIPALE |
-- PERSONNE_A_BORD) plutot que deux classes paralleles - evite de dupliquer
-- l'integralite des champs et des regles deja definies.
-- ============================================================================

CREATE TABLE bon_sortie (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id                 BIGINT       NOT NULL COMMENT 'Titulaire du bon de sortie',
    vehicule_id              BIGINT       NULL,
    affectation_mission_id   BIGINT       NULL COMMENT 'Resolue a la validation (section 3.2, RG-FIPH-020)',
    -- "moyen utilise" figure sur le formulaire papier (section 3.1) mais pas
    -- dans le tableau synthetique des attributs de la section 18 : ajoute par
    -- hypothese documentee (voir analyse fonctionnelle, section D) puisque
    -- explicitement present sur le formulaire d'origine et evoque section 29.1.
    moyen_utilise            VARCHAR(20)  NOT NULL COMMENT 'OMNIUM_SERVICE, PERSONNEL ou TAXI',
    lt                       VARCHAR(20)  NULL COMMENT 'Immatriculation saisie (RG-BS-006) - facultative (point I.4 de l''analyse)',
    kilometrage              INT          NOT NULL,
    date_sortie              DATE         NOT NULL,
    heure_sortie             TIME         NOT NULL,
    heure_retour             TIME         NULL COMMENT 'Renseignee au retour',
    lieu                     VARCHAR(150) NOT NULL,
    -- Code affaire tel que saisi par l'utilisateur, avant resolution vers
    -- l'AffectationMission active (section 8) - jamais reecrit apres coup,
    -- la reference resolue prevaut (section 4.2).
    code_affaire_saisi       VARCHAR(30)  NOT NULL,
    motif_sortie             VARCHAR(500) NOT NULL COMMENT 'Limite de longueur explicite - RG-SEC-003',
    statut                   VARCHAR(20)  NOT NULL DEFAULT 'BROUILLON',
    origine                  VARCHAR(20)  NOT NULL DEFAULT 'PRINCIPALE',
    bon_sortie_principal_id  BIGINT       NULL COMMENT 'Renseigne uniquement pour un bon genere automatiquement (origine PERSONNE_A_BORD)',
    vise_par                 BIGINT       NULL,
    date_visa                DATETIME     NULL,
    valide_par               BIGINT       NULL,
    date_validation          DATETIME     NULL,
    lock_version             INT          NOT NULL DEFAULT 0,
    date_creation             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_modification         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bs_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_bs_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule (id),
    CONSTRAINT fk_bs_affectation FOREIGN KEY (affectation_mission_id) REFERENCES affectation_mission (id),
    CONSTRAINT fk_bs_principal FOREIGN KEY (bon_sortie_principal_id) REFERENCES bon_sortie (id),
    CONSTRAINT fk_bs_vise_par FOREIGN KEY (vise_par) REFERENCES users (id),
    CONSTRAINT fk_bs_valide_par FOREIGN KEY (valide_par) REFERENCES users (id),
    CONSTRAINT chk_bs_moyen_utilise CHECK (moyen_utilise IN ('OMNIUM_SERVICE', 'PERSONNEL', 'TAXI')),
    CONSTRAINT chk_bs_statut CHECK (statut IN ('BROUILLON', 'VISE', 'VALIDE')),
    CONSTRAINT chk_bs_origine CHECK (origine IN ('PRINCIPALE', 'PERSONNE_A_BORD')),
    CONSTRAINT chk_bs_kilometrage CHECK (kilometrage >= 0),
    -- RG-BS-007 / section 9.1 : un bon "personne a bord" reference toujours son
    -- principal ; un bon principal n'en reference jamais un.
    CONSTRAINT chk_bs_origine_principal CHECK (
        (origine = 'PRINCIPALE' AND bon_sortie_principal_id IS NULL)
        OR (origine = 'PERSONNE_A_BORD' AND bon_sortie_principal_id IS NOT NULL)
    )
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Bon de sortie VH/personnel, evenement declencheur de la FIPH de mission (section 3).';

CREATE INDEX idx_bs_agent ON bon_sortie (agent_id, date_sortie);
CREATE INDEX idx_bs_statut ON bon_sortie (statut);
CREATE INDEX idx_bs_principal ON bon_sortie (bon_sortie_principal_id);

CREATE TABLE bon_sortie_personne (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    bon_sortie_principal_id   BIGINT   NOT NULL,
    agent_id                  BIGINT   NOT NULL,
    statut_association        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    date_association          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_retrait              DATETIME NULL,
    bon_sortie_individuel_id  BIGINT   NULL,
    -- Pas de contrainte de cle etrangere vers fiph pour l'instant : cette
    -- table n'existe pas encore (module FIPH, non construit a ce stade du
    -- developpement). La colonne est deja presente (evite une migration
    -- ALTER TABLE disruptive plus tard) ; la contrainte FK sera ajoutee dans
    -- la migration du module FIPH une fois la table cible existante.
    fiph_id                   BIGINT   NULL,
    CONSTRAINT fk_bsp_principal FOREIGN KEY (bon_sortie_principal_id) REFERENCES bon_sortie (id),
    CONSTRAINT fk_bsp_agent FOREIGN KEY (agent_id) REFERENCES agent (id),
    CONSTRAINT fk_bsp_individuel FOREIGN KEY (bon_sortie_individuel_id) REFERENCES bon_sortie (id),
    CONSTRAINT chk_bsp_statut CHECK (statut_association IN ('ACTIVE', 'RETIREE')),
    -- RG-PAB-003 : une personne ne peut etre associee deux fois au meme bon
    -- de sortie principal - garanti en base, pas seulement applicativement.
    CONSTRAINT uq_bsp_principal_agent UNIQUE (bon_sortie_principal_id, agent_id)
) ENGINE = InnoDB
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci
  COMMENT 'Association personne a bord (section 9.2).';

CREATE INDEX idx_bsp_agent ON bon_sortie_personne (agent_id);
