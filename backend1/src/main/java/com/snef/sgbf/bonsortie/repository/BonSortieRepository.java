package com.snef.sgbf.bonsortie.repository;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux bons de sortie (principaux et individuels - voir {@code OrigineBonSortie}). */
public interface BonSortieRepository extends JpaRepository<BonSortie, Long> {

    List<BonSortie> findByAgent_IdOrderByDateSortieDesc(Long agentId);

    List<BonSortie> findByBonSortiePrincipal_Id(Long bonSortiePrincipalId);
}
