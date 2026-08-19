-- ============================================================================
-- V12 : Unification Agent / Utilisateur (evolution du 2026-08-19)
-- ----------------------------------------------------------------------------
-- Regle metier : "tout utilisateur de l'application est obligatoirement un
-- agent/personnel de l'entreprise" - il n'existe plus deux entites
-- independantes reliees par un lien 0..1 facultatif (agent.utilisateur_id) :
-- une seule table `users` porte desormais l'identite RH (matricule, nom,
-- prenom) ET, lorsqu'elle existe, l'identite de connexion (identifiant,
-- email, mot_de_passe_hash) d'une meme personne reelle.
--
-- Plan de migration (aucune donnee supprimee avant reprise complete de ses
-- dependances) :
--   1. `users` gagne matricule/nom/prenom ; identifiant/email/mot_de_passe_hash
--      deviennent nullables (une personne sans compte applicatif n'en a pas).
--   2. Chaque `agent` deja lie a un `users` (utilisateur_id NOT NULL) enrichit
--      cette ligne existante (matricule/nom/prenom/service - le service de
--      l'agent, toujours renseigne, est retenu comme reference).
--   3. Chaque `agent` SANS compte (utilisateur_id NULL) devient une NOUVELLE
--      ligne `users` a part entiere, sans identifiant/email/mot de passe.
--   4. Les FK de fiph/bon_sortie/bon_sortie_personne/affectation_mission,
--      qui referencaient `agent(id)`, sont repointees vers `users(id)` -
--      matricule (unique des deux cotes) sert de cle de correspondance pour
--      les agents du cas 3 (nouvelle ligne), utilisateur_id pour ceux du cas 2.
--   5. La table `agent`, entierement videe de sa raison d'etre, est supprimee.
-- ============================================================================

-- --- 1. users gagne l'identite RH, la connexion devient facultative ---------

ALTER TABLE users
    ADD COLUMN matricule VARCHAR(20) NULL COMMENT 'Identifiant fonctionnel RH (ex-agent.matricule)' AFTER id,
    ADD COLUMN nom VARCHAR(100) NULL AFTER matricule,
    ADD COLUMN prenom VARCHAR(100) NULL AFTER nom,
    MODIFY COLUMN identifiant VARCHAR(60) NULL COMMENT 'NULL si cette personne ne dispose pas d''un compte applicatif',
    MODIFY COLUMN email VARCHAR(150) NULL COMMENT 'NULL si cette personne ne dispose pas d''un compte applicatif',
    MODIFY COLUMN mot_de_passe_hash VARCHAR(255) NULL COMMENT 'NULL si cette personne ne dispose pas d''un compte applicatif',
    ADD CONSTRAINT uq_users_matricule UNIQUE (matricule);

-- --- 2. Agents deja lies a un compte : enrichissement de la ligne existante -

UPDATE users u
JOIN agent a ON a.utilisateur_id = u.id
SET u.matricule = a.matricule,
    u.nom = a.nom,
    u.prenom = a.prenom,
    u.service_id = a.service_id;

-- --- 3. Agents sans compte : nouvelle ligne users, sans identite de connexion

INSERT INTO users (matricule, nom, prenom, service_id, statut_compte, lock_version, date_creation, date_modification)
SELECT a.matricule, a.nom, a.prenom, a.service_id,
       CASE WHEN a.actif THEN 'ACTIF' ELSE 'DESACTIVE' END,
       0, a.date_creation, a.date_modification
FROM agent a
WHERE a.utilisateur_id IS NULL;

-- --- 4. Repointage des FK agent_id vers users(id) ---------------------------

ALTER TABLE fiph DROP FOREIGN KEY fk_fiph_agent;
ALTER TABLE bon_sortie DROP FOREIGN KEY fk_bs_agent;
ALTER TABLE bon_sortie_personne DROP FOREIGN KEY fk_bsp_agent;
ALTER TABLE affectation_mission DROP FOREIGN KEY fk_affectation_agent;

UPDATE fiph f
JOIN agent a ON f.agent_id = a.id
JOIN users u ON u.id = COALESCE(a.utilisateur_id, (SELECT u2.id FROM users u2 WHERE u2.matricule = a.matricule))
SET f.agent_id = u.id;

UPDATE bon_sortie bs
JOIN agent a ON bs.agent_id = a.id
JOIN users u ON u.id = COALESCE(a.utilisateur_id, (SELECT u2.id FROM users u2 WHERE u2.matricule = a.matricule))
SET bs.agent_id = u.id;

UPDATE bon_sortie_personne bsp
JOIN agent a ON bsp.agent_id = a.id
JOIN users u ON u.id = COALESCE(a.utilisateur_id, (SELECT u2.id FROM users u2 WHERE u2.matricule = a.matricule))
SET bsp.agent_id = u.id;

UPDATE affectation_mission am
JOIN agent a ON am.agent_id = a.id
JOIN users u ON u.id = COALESCE(a.utilisateur_id, (SELECT u2.id FROM users u2 WHERE u2.matricule = a.matricule))
SET am.agent_id = u.id;

ALTER TABLE fiph ADD CONSTRAINT fk_fiph_agent FOREIGN KEY (agent_id) REFERENCES users (id);
ALTER TABLE bon_sortie ADD CONSTRAINT fk_bs_agent FOREIGN KEY (agent_id) REFERENCES users (id);
ALTER TABLE bon_sortie_personne ADD CONSTRAINT fk_bsp_agent FOREIGN KEY (agent_id) REFERENCES users (id);
ALTER TABLE affectation_mission ADD CONSTRAINT fk_affectation_agent FOREIGN KEY (agent_id) REFERENCES users (id);

-- --- 5. Suppression de la table devenue entierement redondante --------------

DROP TABLE agent;
