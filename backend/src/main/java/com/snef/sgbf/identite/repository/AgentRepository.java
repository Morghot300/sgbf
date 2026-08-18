package com.snef.sgbf.identite.repository;

import com.snef.sgbf.identite.entity.Agent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces au referentiel RH des agents. */
public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByMatricule(String matricule);

    Optional<Agent> findByUtilisateur_Id(Long utilisateurId);

    /**
     * Recherche par nom/prenom, utilisee UNIQUEMENT comme aide a la selection
     * manuelle dans l'interface (liste de suggestions) - jamais comme
     * resolution automatique silencieuse, conformement au point I.3 de
     * l'analyse fonctionnelle (risque d'homonymie signale par le document
     * source, section 29.1). La resolution definitive doit toujours se
     * conclure par le choix explicite d'un {@link Agent#getMatricule()}.
     */
    List<Agent> findByNomContainingIgnoreCaseOrPrenomContainingIgnoreCase(String nom, String prenom);

    List<Agent> findByService_IdAndActifTrue(Long serviceId);
}
