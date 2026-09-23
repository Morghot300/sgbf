-- ============================================================================
-- V16 : Assouplissement de la contrainte "une seule affectation ACTIVE"
-- ----------------------------------------------------------------------------
-- Evolution du 2026-08-27 (brief "Evolution avancee du module Bon de Sortie,
-- Missions et FIPH", section 18-22 - decision confirmee explicitement) : le
-- decoupage complet d'un chevauchement de missions (ex. Mission A du lundi au
-- vendredi, Mission B du mercredi au jeudi -> Mission A redevient active le
-- vendredi, la reprise etant creee a l'avance) exige qu'un meme agent puisse
-- porter DEUX affectations ACTIVE simultanement, tant que leurs PERIODES ne se
-- chevauchent pas (l'une couvrant le present/futur proche, l'autre une reprise
-- planifiee plus tard) - ce que l'ancien index unique (au plus une ligne
-- ACTIVE par agent, toutes dates confondues) interdisait par construction.
--
-- L'integrite reelle recherchee - qu'un agent ne soit jamais simultanement
-- affecte a deux missions le MEME JOUR - est desormais garantie exclusivement
-- au niveau applicatif (AffectationMissionService.verifierAucunChevauchementDate,
-- appuyee sur AffectationMissionRepository.trouverChevauchements), qui compare
-- les PERIODES reelles plutot que le seul statut - une verification bien plus
-- fine qu'un index unique ne pourrait jamais l'exprimer nativement en SQL.
-- ============================================================================

ALTER TABLE affectation_mission DROP INDEX uq_affectation_agent_actif;
ALTER TABLE affectation_mission DROP COLUMN agent_si_actif;
