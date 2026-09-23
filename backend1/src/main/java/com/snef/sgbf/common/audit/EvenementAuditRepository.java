package com.snef.sgbf.common.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Acces en ecriture (creation uniquement) et en lecture au journal d'audit.
 *
 * <p><strong>Important :</strong> bien que {@link JpaRepository} expose des
 * methodes {@code delete*}, elles ne doivent JAMAIS etre appelees sur ce
 * depot - la table {@code evenement_audit} porte des declencheurs SQL qui les
 * feraient de toute facon echouer (voir {@code V3__evenement_audit.sql}).
 * L'heritage de {@link JpaRepository} est un choix pragmatique (recherche
 * paginee et par specification prete a l'emploi) ; la garantie d'immuabilite
 * reelle est portee par la base de donnees, pas par cette interface.
 *
 * <p>{@link JpaSpecificationExecutor} permet la recherche multicritere
 * recommandee section 30 du document source (agent, service, periode,
 * type d'action, entite...).
 */
public interface EvenementAuditRepository
        extends JpaRepository<EvenementAudit, Long>, JpaSpecificationExecutor<EvenementAudit> {

    /** Historique complet d'une entite donnee, du plus ancien au plus recent (section 24). */
    List<EvenementAudit> findByEntiteTypeAndEntiteIdOrderByDateActionAsc(EntiteAuditable entiteType, String entiteId);

    /** Historique paginee des actions d'un utilisateur donne, du plus recent au plus ancien. */
    Page<EvenementAudit> findByUtilisateur_IdOrderByDateActionDesc(Long utilisateurId, Pageable pageable);
}
