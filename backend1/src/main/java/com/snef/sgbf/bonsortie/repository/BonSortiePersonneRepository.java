package com.snef.sgbf.bonsortie.repository;

import com.snef.sgbf.bonsortie.entity.BonSortiePersonne;
import com.snef.sgbf.bonsortie.entity.StatutAssociationPersonne;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux associations personne a bord. */
public interface BonSortiePersonneRepository extends JpaRepository<BonSortiePersonne, Long> {

    List<BonSortiePersonne> findByBonSortiePrincipal_Id(Long bonSortiePrincipalId);

    /** RG-PAB-002/006 : personnes actives sans bon de sortie individuel genere - la file d'attente de generation automatique. */
    List<BonSortiePersonne> findByBonSortiePrincipal_IdAndStatutAssociationAndBonSortieIndividuelIsNull(
            Long bonSortiePrincipalId, StatutAssociationPersonne statutAssociation);

    /** RG-PAB-003 : verification de preexistence avant insertion (doublee par la contrainte d'unicite en base). */
    Optional<BonSortiePersonne> findByBonSortiePrincipal_IdAndAgent_Id(Long bonSortiePrincipalId, Long agentId);

    /** Associations actives d'un agent, toutes principaux confondus - utilise pour signaler (jamais bloquer) un agent deja a bord d'un autre bon sur le meme creneau (evolution du 2026-08-19, Lot 4). */
    List<BonSortiePersonne> findByAgent_IdAndStatutAssociation(Long agentId, StatutAssociationPersonne statutAssociation);
}
