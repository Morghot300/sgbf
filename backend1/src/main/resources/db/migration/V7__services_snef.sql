-- ============================================================================
-- V7 : Referentiel des services reels de SNEF Cameroun SA
-- ----------------------------------------------------------------------------
-- Liste communiquee par l'utilisateur le 2026-08-17 (document manuscrit).
-- Le referentiel "service" reste administrable ensuite via l'API
-- (POST /api/referentiels/services, reserve a l'Administrateur) : cette
-- migration ne fait qu'amorcer les donnees reelles de l'entreprise plutot
-- que de laisser un environnement vide de tout service metier reconnaissable.
-- ============================================================================

INSERT INTO service (code_service, libelle) VALUES
    ('DIRECTION',            'Direction'),
    ('RESSOURCES_HUMAINES',  'Ressources Humaines'),
    ('COMPTABILITE',         'Comptabilite'),
    ('INFORMATIQUE',         'Informatique'),
    ('SUPPLY_CHAIN',         'Supply Chain'),
    ('TELECOM',              'Telecom'),
    ('MAINTENANCE',          'Maintenance'),
    ('INDUSTRIE',            'Industrie'),
    ('ATELIER_METAL',        'Atelier Activite Metal'),
    ('QHSE',                 'QHSE'),
    ('TERTIAIRE',            'Tertiaire'),
    ('COMMERCIAL',           'Commercial'),
    ('LOGISTIQUE',           'Logistique');
