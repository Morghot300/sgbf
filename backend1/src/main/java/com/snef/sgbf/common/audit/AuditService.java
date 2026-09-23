package com.snef.sgbf.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snef.sgbf.identite.entity.Utilisateur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Point d'ecriture unique du journal d'audit ({@link EvenementAudit}).
 *
 * <p>Tous les services metier de l'application doivent passer par cette
 * classe pour tracer une action significative, plutot que de construire un
 * {@link EvenementAudit} eux-memes - cela garantit une serialisation JSON
 * homogene des valeurs avant/apres et centralise la gestion des erreurs de
 * serialisation (qui ne doivent jamais faire echouer l'operation metier
 * elle-meme : un probleme d'audit ne doit pas bloquer l'utilisateur, mais
 * doit etre visible cote diagnostic technique - voir {@link #serialiser}).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final EvenementAuditRepository evenementAuditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(EvenementAuditRepository evenementAuditRepository, ObjectMapper objectMapper) {
        this.evenementAuditRepository = evenementAuditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Enregistre un evenement d'audit complet (valeurs et statuts avant/apres).
     * A utiliser pour toute modification d'etat metier (creation, complement,
     * signature, validation, interruption...).
     */
    public void enregistrer(EntiteAuditable entiteType, Object entiteId, Utilisateur utilisateur,
                             TypeActionAudit action, Object valeurAvant, Object valeurApres,
                             String statutAvant, String statutApres) {
        EvenementAudit evenement = new EvenementAudit(
                entiteType,
                String.valueOf(entiteId),
                utilisateur,
                action,
                serialiser(valeurAvant),
                serialiser(valeurApres),
                statutAvant,
                statutApres);
        evenementAuditRepository.save(evenement);
    }

    /**
     * Variante simplifiee pour les actions sans changement de statut ni de
     * valeur porteuse (ex. connexion reussie, acces refuse, telechargement).
     */
    public void enregistrerAction(EntiteAuditable entiteType, Object entiteId, Utilisateur utilisateur,
                                   TypeActionAudit action) {
        enregistrer(entiteType, entiteId, utilisateur, action, null, null, null, null);
    }

    /**
     * Serialise une valeur en JSON pour stockage dans les colonnes
     * {@code valeur_avant}/{@code valeur_apres}. Une valeur non serialisable
     * ne doit jamais faire echouer l'action metier auditee : on journalise
     * l'anomalie techniquement et on degrade en {@code null} plutot que de
     * propager l'exception.
     */
    private String serialiser(Object valeur) {
        if (valeur == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(valeur);
        } catch (JsonProcessingException e) {
            log.warn("Impossible de serialiser une valeur pour l'audit ({}) : {}",
                    valeur.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
