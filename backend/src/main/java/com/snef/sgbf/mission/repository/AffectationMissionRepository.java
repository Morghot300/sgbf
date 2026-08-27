package com.snef.sgbf.mission.repository;

import com.snef.sgbf.mission.entity.AffectationMission;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acces aux affectations agent/mission. */
public interface AffectationMissionRepository extends JpaRepository<AffectationMission, Long> {

    /** L'affectation ACTIVE courante d'un agent, s'il en a une (au plus une par construction - voir migration). */
    Optional<AffectationMission> findByAgent_IdAndStatutAffectation(
            Long agentId, com.snef.sgbf.mission.entity.StatutAffectation statutAffectation);

    List<AffectationMission> findByMission_IdOrderByDateDebutAffectationAsc(Long missionId);

    /**
     * Resout l'affectation d'un agent active a une date precise - operation
     * executee a chaque creation de bon de sortie et a chaque controle de
     * coherence (RG-FIPH-025), recommandation d'index explicite section 30
     * du document source (voir aussi {@code idx_affectation_agent_dates}
     * dans la migration).
     *
     * <p>Une affectation est active a la date {@code D} si
     * {@code dateDebutAffectation <= D} et que
     * {@code dateFinAffectation} est soit non renseignee, soit {@code >= D}.
     */
    @Query("SELECT a FROM AffectationMission a "
            + "WHERE a.agent.id = :agentId "
            + "AND a.dateDebutAffectation <= :date "
            + "AND (a.dateFinAffectation IS NULL OR a.dateFinAffectation >= :date) "
            + "ORDER BY a.dateDebutAffectation DESC")
    List<AffectationMission> trouverActivesAgentADate(@Param("agentId") Long agentId, @Param("date") LocalDate date);

    /**
     * Affectations d'un agent (autres que {@code excluAffectationId}) dont la
     * periode chevaucherait [debut, fin] - evolution du 2026-08-27 (V16) :
     * remplace le controle par index unique "une seule ACTIVE par agent",
     * desormais trop grossier pour permettre le decoupage de missions
     * chevauchantes (une reprise planifiee, elle aussi ACTIVE, ne chevauche
     * jamais reellement la periode de l'affectation en cours).
     */
    @Query("SELECT a FROM AffectationMission a "
            + "WHERE a.agent.id = :agentId AND a.id <> :excluAffectationId "
            + "AND a.dateDebutAffectation <= :fin AND (a.dateFinAffectation IS NULL OR a.dateFinAffectation >= :debut)")
    List<AffectationMission> trouverChevauchements(@Param("agentId") Long agentId, @Param("excluAffectationId") Long excluAffectationId,
                                                    @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Variante utilisee a la creation (aucune affectation existante a exclure). */
    default List<AffectationMission> trouverChevauchements(Long agentId, LocalDate debut, LocalDate fin) {
        return trouverChevauchements(agentId, 0L, debut, fin);
    }
}
