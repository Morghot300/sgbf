-- ============================================================================
-- V15 : Lien direct Bon de sortie -> Mission ("Code Mission")
-- ----------------------------------------------------------------------------
-- Evolution du 2026-08-27 (brief "Evolution du module Bon de Sortie et
-- integration avec les FIPH") : le "Code Mission" saisi sur le bon de sortie
-- devenait jusqu'ici un simple texte libre (code_affaire_saisi), jamais lie a
-- une vraie Mission - la resolution de l'affectation restait uniquement
-- automatique, par agent + date, a la validation. Cette colonne ajoute une
-- vraie relation, facultative, choisie explicitement a la creation ou a une
-- correction : lorsqu'elle est renseignee, elle devient prioritaire sur la
-- resolution par date (voir BonSortieService#valider).
-- ============================================================================

ALTER TABLE bon_sortie
    ADD COLUMN mission_id BIGINT NULL AFTER affectation_mission_id,
    ADD CONSTRAINT fk_bs_mission FOREIGN KEY (mission_id) REFERENCES mission (id);
