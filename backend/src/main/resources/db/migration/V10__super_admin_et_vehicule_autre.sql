-- ============================================================================
-- V10 : Evolution fonctionnelle et securisation (2026-08-18)
-- ----------------------------------------------------------------------------
-- 1. Nouveau role SUPER_ADMINISTRATEUR (supervision globale, herite des
--    droits ADMINISTRATEUR cote Spring Security - voir SecurityConfig).
-- 2. Nouvelle valeur de moyen utilise "AUTRE" (en plus de OMNIUM_SERVICE,
--    PERSONNEL, TAXI deja presents) + champ de precision associe, obligatoire
--    uniquement pour AUTRE - controle applicatif (BonSortieService) ET
--    controle en base (defense en profondeur).
-- ============================================================================

INSERT INTO role_metier (code, libelle) VALUES
    ('SUPER_ADMINISTRATEUR', 'Super Administrateur (supervision globale)');

ALTER TABLE bon_sortie
    DROP CONSTRAINT chk_bs_moyen_utilise;

ALTER TABLE bon_sortie
    ADD COLUMN precision_vehicule VARCHAR(200) NULL COMMENT 'Obligatoire uniquement lorsque moyen_utilise = AUTRE',
    ADD CONSTRAINT chk_bs_moyen_utilise CHECK (moyen_utilise IN ('OMNIUM_SERVICE', 'PERSONNEL', 'TAXI', 'AUTRE')),
    ADD CONSTRAINT chk_bs_precision_autre CHECK (moyen_utilise <> 'AUTRE' OR precision_vehicule IS NOT NULL);
