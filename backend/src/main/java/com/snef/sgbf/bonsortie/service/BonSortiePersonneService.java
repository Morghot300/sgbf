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
import com.snef.sgbf.bonsortie.dto.AgentEligibleDto;
import com.snef.sgbf.bonsortie.dto.AjouterPersonnesABordEnLotRequest;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final UtilisateurRepository utilisateurRepository;
    private final BonSortieService bonSortieService;
    private final BonSortiePersonneMapper bonSortiePersonneMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public BonSortiePersonneService(BonSortiePersonneRepository bonSortiePersonneRepository,
                                     UtilisateurRepository utilisateurRepository,
                                     BonSortieService bonSortieService,
                                     BonSortiePersonneMapper bonSortiePersonneMapper,
                                     AuditService auditService,
                                     NotificationService notificationService) {
        this.bonSortiePersonneRepository = bonSortiePersonneRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.bonSortieService = bonSortieService;
        this.bonSortiePersonneMapper = bonSortiePersonneMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
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
        Utilisateur personneAgent = utilisateurRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", requete.agentId()));

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
        notificationService.notifierPersonneABordAjoutee(bonSortiePrincipalId, personneAgent, auteur);

        if (principal.getStatut() == StatutBonSortie.VALIDE) {
            bonSortieService.genererPourPersonnesABordEnAttente(bonSortiePrincipalId, auteur);
        }

        return bonSortiePersonneMapper.toDto(association);
    }

    /**
     * Ajout groupe, transactionnel et idempotent (evolution du 2026-08-19,
     * Lot 4) : rejoue sans effet si des personnes de la selection sont deja
     * associees (silencieusement ignorees, jamais une erreur qui ferait
     * echouer tout le lot) - la contrainte d'unicite {@code uq_bsp_principal_agent}
     * reste le filet de securite ultime en cas de course concurrente. Tout ou
     * rien pour les echecs reels (agent inexistant, hors perimetre) : toute
     * exception fait echouer l'ensemble du lot (methode {@code @Transactional}
     * par heritage de la classe).
     */
    public List<BonSortiePersonneDto> ajouterEnLot(Long bonSortiePrincipalId, AjouterPersonnesABordEnLotRequest requete, Utilisateur auteur) {
        BonSortie principal = bonSortieService.chargerBonSortie(bonSortiePrincipalId);
        bonSortieService.verifierAutoServiceOuGestionnaire(auteur, principal.getAgent());
        if (principal.getOrigine() != OrigineBonSortie.PRINCIPALE) {
            throw new BusinessRuleViolationException("section-9.1",
                    "Seul un bon de sortie principal peut recevoir des personnes a bord.");
        }

        Set<Long> dejaAssocies = bonSortiePersonneRepository.findByBonSortiePrincipal_Id(bonSortiePrincipalId).stream()
                .map(a -> a.getAgent().getId())
                .collect(Collectors.toSet());

        List<BonSortiePersonneDto> resultats = new ArrayList<>();
        for (Long agentId : Set.copyOf(requete.agentIds())) {
            if (dejaAssocies.contains(agentId)) {
                continue; // idempotence : deja associe, rejeu sans effet plutot qu'une erreur qui ferait echouer tout le lot.
            }
            Utilisateur personneAgent = utilisateurRepository.findById(agentId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", agentId));

            BonSortiePersonne association = new BonSortiePersonne();
            association.setBonSortiePrincipal(principal);
            association.setAgent(personneAgent);
            association.setStatutAssociation(StatutAssociationPersonne.ACTIVE);
            association = bonSortiePersonneRepository.save(association);

            auditService.enregistrer(EntiteAuditable.BON_SORTIE_PERSONNE, association.getId(), auteur,
                    TypeActionAudit.PERSONNE_A_BORD_AJOUTEE, null, bonSortiePersonneMapper.toDto(association), null, null);
            notificationService.notifierPersonneABordAjoutee(bonSortiePrincipalId, personneAgent, auteur);
            resultats.add(bonSortiePersonneMapper.toDto(association));
        }

        if (principal.getStatut() == StatutBonSortie.VALIDE && !resultats.isEmpty()) {
            bonSortieService.genererPourPersonnesABordEnAttente(bonSortiePrincipalId, auteur);
        }
        return resultats;
    }

    /**
     * Personnes du service du titulaire du bon eligibles a y etre ajoutees
     * comme personne a bord (evolution du 2026-08-19, Lot 4) - le perimetre
     * (service du bon) est calcule ici, cote serveur, jamais recu du client
     * (RG-SEC-002 : un {@code serviceId} envoye par le client serait
     * trivialement manipulable pour recuperer le personnel d'un AUTRE
     * service). Exclut le titulaire lui-meme, les personnes deja actives sur
     * ce bon, et les comptes inactifs/desactives ; signale (sans jamais
     * bloquer) une personne deja a bord d'un AUTRE bon a la meme date de
     * sortie.
     */
    @Transactional(readOnly = true)
    public List<AgentEligibleDto> listerAgentsEligibles(Long bonSortiePrincipalId, Utilisateur auteur) {
        BonSortie principal = bonSortieService.chargerBonSortie(bonSortiePrincipalId);
        bonSortieService.verifierAutoServiceOuGestionnaire(auteur, principal.getAgent());

        Set<Long> dejaAssocies = bonSortiePersonneRepository.findByBonSortiePrincipal_Id(bonSortiePrincipalId).stream()
                .map(a -> a.getAgent().getId())
                .collect(Collectors.toSet());
        Long serviceId = principal.getAgent().getService() != null ? principal.getAgent().getService().getId() : null;
        List<Utilisateur> personnel = serviceId != null ? utilisateurRepository.findByService_Id(serviceId) : List.of();

        return personnel.stream()
                .filter(u -> !u.getId().equals(principal.getAgent().getId()))
                .filter(u -> !dejaAssocies.contains(u.getId()))
                .map(u -> new AgentEligibleDto(u.getId(), u.getNomComplet(), u.getMatricule(),
                        u.getService() != null ? u.getService().getLibelle() : null, u.getStatutCompte(),
                        dejaAffecteMemeCreneau(u.getId(), principal)))
                .filter(dto -> dto.statutCompte() == StatutCompte.ACTIF || dto.statutCompte() == StatutCompte.VERROUILLE)
                .toList();
    }

    private boolean dejaAffecteMemeCreneau(Long agentId, BonSortie principal) {
        boolean commeTitulaire = bonSortieService.chargerBonsPourAgent(agentId).stream()
                .anyMatch(bs -> !bs.getId().equals(principal.getId()) && bs.getDateSortie().equals(principal.getDateSortie()));
        boolean commePersonneABord = bonSortiePersonneRepository
                .findByAgent_IdAndStatutAssociation(agentId, StatutAssociationPersonne.ACTIVE).stream()
                .anyMatch(a -> !a.getBonSortiePrincipal().getId().equals(principal.getId())
                        && a.getBonSortiePrincipal().getDateSortie().equals(principal.getDateSortie()));
        return commeTitulaire || commePersonneABord;
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
