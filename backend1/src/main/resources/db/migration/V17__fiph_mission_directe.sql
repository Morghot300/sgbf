-- Evolution du 2026-08-27 (brief "Evolution du module FIPH : creation,
-- consultation, mission, droits par service et refonte ergonomique",
-- section 6-8) : ajoute un champ Mission explicite sur la FIPH elle-meme,
-- au meme titre que ce qui existe deja pour le Bon de Sortie depuis V15
-- (BonSortie.mission).
--
-- Volontairement NULLABLE et purement associatif/descriptif : il ne
-- remplace ni ne modifie la resolution par-jour deja en place sur chaque
-- ligne de pointage (fiph_pointage.affectation_mission_id, granularite
-- journaliere - voir Pointage.java), qui reste l'unique source de verite
-- pour le contenu reellement facture/pointe. Ce champ permet seulement de
-- rattacher explicitement une FIPH a une mission des sa creation manuelle
-- (Code Mission, recherche, nom textuel affiche - section 6/7/8), avec un
-- controle de coherence non bloquant (avertissement) plutot qu'une
-- contrainte en base, dans le droit fil de la philosophie deja en place
-- partout ailleurs dans cette application (avertissementPeriode,
-- avertissementAffectation...).
ALTER TABLE fiph
    ADD COLUMN mission_id BIGINT NULL,
    ADD CONSTRAINT fk_fiph_mission FOREIGN KEY (mission_id) REFERENCES mission (id);
