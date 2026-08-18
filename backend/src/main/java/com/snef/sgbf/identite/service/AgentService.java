package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.dto.AgentDto;
import com.snef.sgbf.identite.dto.CreerAgentRequest;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.mapper.AgentMapper;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion du referentiel RH des agents.
 *
 * <p>Reserve, cote controleur, a l'Administrateur (section 14 : "Administre
 * les referentiels"). Ce service ne gere que la fiche Agent elle-meme ; la
 * creation d'un compte applicatif associe releve de {@link UtilisateurService}
 * et reste une operation distincte (un Agent peut tres bien n'avoir jamais de
 * compte).
 */
@org.springframework.stereotype.Service
@Transactional
public class AgentService {

    private final AgentRepository agentRepository;
    private final ServiceRepository serviceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgentMapper agentMapper;
    private final AuditService auditService;

    public AgentService(AgentRepository agentRepository, ServiceRepository serviceRepository,
                         UtilisateurRepository utilisateurRepository,
                         AgentMapper agentMapper, AuditService auditService) {
        this.agentRepository = agentRepository;
        this.serviceRepository = serviceRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.agentMapper = agentMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AgentDto> listerTous() {
        return agentRepository.findAll().stream().map(agentMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AgentDto obtenirParId(Long id) {
        return agentMapper.toDto(chargerAgent(id));
    }

    /**
     * Recherche d'aide a la saisie par nom/prenom (autocompletion frontend
     * uniquement) - voir l'avertissement dans
     * {@link com.snef.sgbf.identite.repository.AgentRepository#findByNomContainingIgnoreCaseOrPrenomContainingIgnoreCase}.
     */
    @Transactional(readOnly = true)
    public List<AgentDto> rechercherParNomOuPrenom(String terme) {
        return agentRepository.findByNomContainingIgnoreCaseOrPrenomContainingIgnoreCase(terme, terme)
                .stream().map(agentMapper::toDto).toList();
    }

    public AgentDto creer(CreerAgentRequest requete, Utilisateur auteur) {
        if (agentRepository.findByMatricule(requete.matricule()).isPresent()) {
            throw new ConflictException("Un agent existe deja avec le matricule " + requete.matricule() + ".");
        }
        Service service = serviceRepository.findById(requete.serviceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service", requete.serviceId()));

        Agent agent = new Agent();
        agent.setMatricule(requete.matricule());
        agent.setNom(requete.nom());
        agent.setPrenom(requete.prenom());
        agent.setService(service);
        agent = agentRepository.save(agent);

        auditService.enregistrer(EntiteAuditable.AGENT, agent.getId(), auteur,
                TypeActionAudit.CREATION, null, agentMapper.toDto(agent), null, null);
        return agentMapper.toDto(agent);
    }

    /**
     * Associe un agent du referentiel RH a un compte applicatif (0..1,
     * section 18) - un agent de terrain sans acces direct au systeme peut
     * tres bien n'en avoir aucun ; cette operation est reservee a
     * l'Administrateur (controle au niveau du controleur).
     */
    public AgentDto lierUtilisateur(Long agentId, Long utilisateurId, Utilisateur auteur) {
        Agent agent = chargerAgent(agentId);
        if (agent.getUtilisateur() != null) {
            throw new ConflictException("Cet agent est deja associe a un compte applicatif.");
        }
        Utilisateur compte = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", utilisateurId));
        agent.setUtilisateur(compte);
        agent = agentRepository.save(agent);
        auditService.enregistrer(EntiteAuditable.AGENT, agent.getId(), auteur,
                TypeActionAudit.MODIFICATION, null, utilisateurId, null, null);
        return agentMapper.toDto(agent);
    }

    public void desactiver(Long id, Utilisateur auteur) {
        Agent agent = chargerAgent(id);
        boolean etatAvant = agent.isActif();
        agent.setActif(false);
        agentRepository.save(agent);
        auditService.enregistrer(EntiteAuditable.AGENT, agent.getId(), auteur,
                TypeActionAudit.DESACTIVATION, etatAvant, false, null, null);
    }

    private Agent chargerAgent(Long id) {
        return agentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Agent", id));
    }
}
