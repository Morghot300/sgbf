package com.snef.sgbf.bonsortie.service;

import com.snef.sgbf.bonsortie.dto.AjouterPersonneABordRequest;
import com.snef.sgbf.bonsortie.dto.BonSortiePersonneDto;
import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.BonSortiePersonne;
import com.snef.sgbf.bonsortie.entity.OrigineBonSortie;
import com.snef.sgbf.bonsortie.entity.StatutAssociationPersonne;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.bonsortie.mapper.BonSortiePersonneMapper;
import com.snef.sgbf.bonsortie.repository.BonSortiePersonneRepository;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.AgentRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des personnes a bord d'un bon de sortie principal (section 9.2,
 * RG-PAB-001 a 009). Separee de {@link BonSortieService} pour respecter la
 * responsabilite unique de chaque classe, tout en partageant avec elle le
 * declenchement de la generation automatique
 * ({@link BonSortieService#genererPourPersonnesABordEnAttente}, package-privee,
 * reutilisee ici pour l'ajout tardif - RG-PAB-006).
 */
@org.springframework.stereotype.Service
@Transactional
public class BonSortiePersonneService {

    private final BonSortiePersonneRepository bonSortiePersonneRepository;
    private final AgentRepository agentRepository;
    private final BonSortieService bonSortieService;
    private final BonSortiePersonneMapper bonSortiePersonneMapper;
    private final AuditService auditService;

    public BonSortiePersonneService(BonSortiePersonneRepository bonSortiePersonneRepository,
                                     AgentRepository agentRepository,
                                     BonSortieService bonSortieService,
                                     BonSortiePersonneMapper bonSortiePersonneMapper,
                                     AuditService auditService) {
        this.bonSortiePersonneRepository = bonSortiePersonneRepository;
        this.agentRepository = agentRepository;
        this.bonSortieService = bonSortieService;
        this.bonSortiePersonneMapper = bonSortiePersonneMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BonSortiePersonneDto> listerPourBonSortie(Long bonSortiePrincipalId) {
        return bonSortiePersonneRepository.findByBonSortiePrincipal_Id(bonSortiePrincipalId).stream()
                .map(bonSortiePersonneMapper::toDto).toList();
    }

    /**
     * Associe une personne a bord (RG-PAB-001). Si le bon de sortie
     * principal est deja valide, declenche immediatement la generation de
     * son bon de sortie individuel (RG-PAB-006) plutot que d'attendre un
     * hypothetique nouvel evenement de validation qui n'aura pas lieu.
     */
    public BonSortiePersonneDto ajouter(Long bonSortiePrincipalId, AjouterPersonneABordRequest requete, Utilisateur auteur) {
        BonSortie principal = bonSortieService.chargerBonSortie(bonSortiePrincipalId);
        bonSortieService.verifierAutoServiceOuGestionnaire(auteur, principal.getAgent());
        if (principal.getOrigine() != OrigineBonSortie.PRINCIPALE) {
            throw new BusinessRuleViolationException("section-9.1",
                    "Seul un bon de sortie principal peut recevoir des personnes a bord.");
        }
        Agent personneAgent = agentRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", requete.agentId()));

        // RG-PAB-003 : verification applicative de preexistence, en complement
        // de la contrainte d'unicite en base (message d'erreur clair plutot
        // qu'une erreur SQL brute en cas de course entre deux requetes).
        if (bonSortiePersonneRepository.findByBonSortiePrincipal_IdAndAgent_Id(bonSortiePrincipalId, personneAgent.getId()).isPresent()) {
            throw new ConflictException("Cette personne est deja associee a ce bon de sortie.");
        }

        BonSortiePersonne association = new BonSortiePersonne();
        association.setBonSortiePrincipal(principal);
        association.setAgent(personneAgent);
        association.setStatutAssociation(StatutAssociationPersonne.ACTIVE);
        association = bonSortiePersonneRepository.save(association);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE_PERSONNE, association.getId(), auteur,
                TypeActionAudit.PERSONNE_A_BORD_AJOUTEE, null, bonSortiePersonneMapper.toDto(association), null, null);

        if (principal.getStatut() == StatutBonSortie.VALIDE) {
            bonSortieService.genererPourPersonnesABordEnAttente(bonSortiePrincipalId, auteur);
        }

        return bonSortiePersonneMapper.toDto(association);
    }

    /**
     * Retire une personne a bord (RG-PAB-009) : l'association passe a
     * RETIREE, mais le bon de sortie individuel et la FIPH deja generes,
     * s'ils existent, ne sont JAMAIS supprimes physiquement - ils suivent
     * leur propre cycle de vie.
     */
    public BonSortiePersonneDto retirer(Long associationId, Utilisateur auteur) {
        BonSortiePersonne association = bonSortiePersonneRepository.findById(associationId)
                .orElseThrow(() -> ResourceNotFoundException.of("BonSortiePersonne", associationId));
        bonSortieService.verifierAutoServiceOuGestionnaire(auteur, association.getBonSortiePrincipal().getAgent());

        association.setStatutAssociation(StatutAssociationPersonne.RETIREE);
        association.setDateRetrait(LocalDateTime.now());
        association = bonSortiePersonneRepository.save(association);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE_PERSONNE, association.getId(), auteur,
                TypeActionAudit.PERSONNE_A_BORD_RETIREE, StatutAssociationPersonne.ACTIVE, StatutAssociationPersonne.RETIREE,
                StatutAssociationPersonne.ACTIVE.name(), StatutAssociationPersonne.RETIREE.name());
        return bonSortiePersonneMapper.toDto(association);
    }
}
