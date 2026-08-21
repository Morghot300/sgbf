package com.snef.sgbf.fiph.repository;

import com.snef.sgbf.fiph.entity.FIPH;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acces aux FIPH (identite stable). {@link JpaSpecificationExecutor} pour la recherche multicritere recommandee section 30. */
public interface FiphRepository extends JpaRepository<FIPH, Long>, JpaSpecificationExecutor<FIPH> {

    List<FIPH> findByAgent_Id(Long agentId);

    /**
     * FIPH de cet agent dont la periode (date de debut de la FIPH, date de fin
     * de sa version courante) couvre la date donnee - la date de fin etant
     * desormais definie librement (evolution du 2026-08-21), une periode
     * "ouverte" ({@code dateFinPeriode IS NULL}) couvre toute date posterieure
     * ou egale a son debut. Au plus un resultat attendu en usage normal (voir
     * {@link #trouverChevauchements}, qui empeche la creation d'un second
     * chevauchement) - retourne une liste par prudence plutot que de risquer
     * une {@code NonUniqueResultException} si une incoherence existait malgre tout.
     */
    @Query("SELECT f FROM FIPH f JOIN f.versionCourante v "
            + "WHERE f.agent.id = :agentId AND f.dateDebutPeriode <= :date "
            + "AND (v.dateFinPeriode IS NULL OR v.dateFinPeriode >= :date)")
    List<FIPH> trouverCouvrantDate(@Param("agentId") Long agentId, @Param("date") LocalDate date);

    /**
     * FIPH de cet agent (autres que {@code excluantFiphId}) dont la periode
     * chevaucherait [debut, fin] - utilise avant de definir/modifier une date
     * de fin, pour ne jamais laisser deux FIPH du meme agent se disputer les
     * memes jours.
     */
    @Query("SELECT f FROM FIPH f JOIN f.versionCourante v "
            + "WHERE f.agent.id = :agentId AND f.id <> :excluantFiphId "
            + "AND f.dateDebutPeriode <= :fin AND (v.dateFinPeriode IS NULL OR v.dateFinPeriode >= :debut)")
    List<FIPH> trouverChevauchements(@Param("agentId") Long agentId, @Param("excluantFiphId") Long excluantFiphId,
                                     @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Variante utilisee a la creation (aucune FIPH existante a exclure). */
    default List<FIPH> trouverChevauchements(Long agentId, LocalDate debut, LocalDate fin) {
        return trouverChevauchements(agentId, 0L, debut, fin);
    }
}
