-- ============================================================================
-- V13 : periode de FIPH flexible (evolution du 2026-08-21)
-- ----------------------------------------------------------------------------
-- Jusqu'ici, une FIPH representait exactement une semaine ISO (uq_fiph_agent_periode,
-- date_fin_periode = dimanche de la semaine, calculee, jamais choisie). La nouvelle
-- regle metier : la date de debut reste automatiquement issue du Bon de Sortie
-- (immuable, portee par `fiph`), mais la date de fin est desormais definie
-- librement par le Charge d'Affaires/la personne habilitee, et peut depasser une
-- seule semaine.
--
-- `date_fin_periode` migre de `fiph` vers `fiph_version` : c'est une donnee de
-- CONTENU (elle determine quels jours de pointage la version couvre), pas
-- d'IDENTITE stable - deplacement coherent avec le principe deja documente
-- dans FIPH.java ("FIPHVersion plutot que modification directe de FIPH").
-- Chaque version garde ainsi sa propre periode, jamais reecrite retroactivement
-- lorsqu'une version ulterieure change de date de fin (RG-VER-003/006).
-- ============================================================================

ALTER TABLE fiph DROP INDEX uq_fiph_agent_periode;

ALTER TABLE fiph_version
    ADD COLUMN date_fin_periode DATE NULL
    COMMENT 'Definie par le Charge d''Affaires/la personne habilitee ; NULL tant que non encore renseignee.';

-- Reprend, pour CHAQUE version existante (pas seulement la courante), la date de
-- fin qui etait jusqu'ici partagee au niveau de la FIPH - aucune donnee perdue.
--
-- EXCEPTION NECESSAIRE : une version VALIDEE_DEFINITIVEMENT est protegee par un
-- declencheur (trg_fiph_version_immuable, voir V8__fiph.sql) qui refuse toute
-- UPDATE ulterieure, y compris celle-ci (RG-VER-001 - decouvert en tentant cette
-- migration sur la base de developpement reelle, jamais sur une base de test
-- toujours vide). Coherent avec l'intention meme du declencheur : une version
-- deja figee ne doit jamais etre retouchee, pas meme par une migration de
-- schema. Ces versions gardent donc `date_fin_periode` a NULL apres migration -
-- acceptable : la notion de date de fin choisie explicitement par le CA/la
-- personne habilitee est une nouveaute de cette evolution, une version figee
-- avant celle-ci n'en a jamais eu besoin pour etre valide.
UPDATE fiph_version fv
JOIN fiph f ON f.id = fv.fiph_id
SET fv.date_fin_periode = f.date_fin_periode
WHERE fv.statut_version <> 'VALIDEE_DEFINITIVEMENT';

ALTER TABLE fiph DROP COLUMN date_fin_periode;
