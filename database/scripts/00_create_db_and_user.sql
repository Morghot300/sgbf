-- Provisioning script for the SGBF (Systeme de Gestion des Bons de sortie et FIPH) database.
-- Run once by an administrator using the MySQL root account.
-- Creates a dedicated, least-privilege application account distinct from root.

CREATE DATABASE IF NOT EXISTS sgbf_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- Dedicated application user, scoped to localhost only (no remote root-equivalent access).
CREATE USER IF NOT EXISTS 'ITadmin'@'localhost' IDENTIFIED BY 'J3su1s@dm1n';

-- Least privilege: full DML/DDL rights on sgbf_db only (needed for Flyway migrations),
-- no GRANT OPTION, no access to other schemas, no global/administrative privileges.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES,
      CREATE VIEW, SHOW VIEW, TRIGGER, EXECUTE, CREATE ROUTINE, ALTER ROUTINE
    ON sgbf_db.* TO 'ITadmin'@'localhost';

FLUSH PRIVILEGES;

-- Les migrations Flyway (voir backend/src/main/resources/db/migration/V3__evenement_audit.sql)
-- creent des declencheurs (TRIGGER) pour garantir l'ecriture seule du journal
-- d'audit. MySQL exige le privilege SUPER pour creer un trigger tant que la
-- journalisation binaire est active, SAUF si ce reglage serveur est active -
-- c'est la voie recommandee par MySQL lui-meme (message d'erreur 1419) pour
-- eviter d'accorder SUPER a ITadmin, ce qui casserait le principe du moindre
-- privilege. SET PERSIST le rend durable sans editer my.ini.
SET PERSIST log_bin_trust_function_creators = 1;
