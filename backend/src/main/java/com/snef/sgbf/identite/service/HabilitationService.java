package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.dto.CreerHabilitationRequest;
import com.snef.sgbf.identite.dto.HabilitationDto;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.mapper.HabilitationMapper;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import com.snef.sgbf.referentiel.entity.RoleMetier;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.RoleMetierRepository;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribution et retrait des habilitations (RG-HAB-001 a 006).
 *
 * <p>C'est ici, et uniquement ici, que sont appliquees les regles
 * structurantes du modele d'autorisation :
 * <ul>
 *   <li><b>RG-HAB-005</b> - l'habilitation RH est exclusive : elle ne peut
 *       jamais coexister, pour un meme utilisateur, avec une habilitation de
 *       creation/modification/validation ;</li>
 *   <li>perimetre coherent avec le role - un role "global" (RH,
 *       ADMINISTRATEUR) n'a pas de service, tout autre role en exige un.</li>
 * </ul>
 * Un controle "MFA obligatoire pour les roles valideurs" a brievement existe
 * ici le 2026-08-17, avant que le second facteur ne soit lui-meme supprime
 * de l'application le meme jour (authentification simple - identifiant/e-mail
 * + mot de passe uniquement, voir section K de l'analyse fonctionnelle) :
 * cette classe ne porte donc plus aucune logique relative a un second facteur.
 * La regle de separation des responsabilites RG-HAB-004 (ne jamais valider un
 * document qu'on a soi-meme cree/complete/modifie) ne peut pas etre verifiee
 * ici : elle depend du document concerne et est donc appliquee au moment de
 * la validation, dans le module FIPH.
 */
// noRollbackFor : verifierAccesCibleNonSuperAdmin journalise une tentative refusee (ACCES_REFUSE) PUIS leve
// ForbiddenOperationException dans la MEME transaction (voir le meme commentaire, plus detaille, sur
// UtilisateurService) - sans cette regle le rollback annulerait aussi cette ecriture d'audit. Sans risque : dans
// attribuer/retirer/listerPourUtilisateur, cette garde est toujours appelee AVANT toute mutation.
@org.springframework.stereotype.Service
@Transactional(noRollbackFor = ForbiddenOperationException.class)
public class HabilitationService {

    private final HabilitationRepository habilitationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RoleMetierRepository roleMetierRepository;
    private final ServiceRepository serviceRepository;
    private final HabilitationMapper habilitationMapper;
    private final AuditService auditService;

    public HabilitationService(HabilitationRepository habilitationRepository,
                                UtilisateurRepository utilisateurRepository,
                                RoleMetierRepository roleMetierRepository,
                                ServiceRepository serviceRepository,
                                HabilitationMapper habilitationMapper,
                                AuditService auditService) {
        this.habilitationRepository = habilitationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.roleMetierRepository = roleMetierRepository;
        this.serviceRepository = serviceRepository;
        this.habilitationMapper = habilitationMapper;
        this.auditService = auditService;
    }

    // Pas readOnly (l'ecriture d'audit y echouerait au niveau JDBC/MySQL) et noRollbackFor repete ici (l'annotation
    // de methode remplace entierement celle de la classe, jamais une fusion) - voir les commentaires sur la
    // classe et sur UtilisateurService.obtenirParId pour le detail des deux bugs corriges le 2026-08-19.
    @Transactional(noRollbackFor = ForbiddenOperationException.class)
    public List<HabilitationDto> listerPourUtilisateur(Long utilisateurId, Utilisateur appelant) {
        verifierAccesCibleNonSuperAdmin(utilisateurId, appelant, EntiteAuditable.UTILISATEUR);
        return habilitationRepository.findByUtilisateur_Id(utilisateurId).stream()
                .map(habilitationMapper::toDto).toList();
    }

    public HabilitationDto attribuer(CreerHabilitationRequest requete, Utilisateur auteur) {
        Utilisateur beneficiaire = utilisateurRepository.findById(requete.utilisateurId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", requete.utilisateurId()));
        RoleMetier roleMetier = roleMetierRepository.findByCode(requete.roleMetierCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role metier inconnu : " + requete.roleMetierCode()));
        CodeRoleMetier code = CodeRoleMetier.valueOf(roleMetier.getCode());

        // Protection contre l'escalade de privileges (section 3 de l'evolution du
        // 2026-08-19) : un Administrateur standard ne peut attribuer AUCUNE
        // habilitation a un compte deja Super Administrateur - meme un role sans
        // rapport, comme AGENT ou RH - la surface de risque est le compte cible,
        // pas seulement le role demande (voir aussi validerAttributionSuperAdministrateur
        // ci-dessous, qui couvre le cas symetrique : demander le role
        // SUPER_ADMINISTRATEUR lui-meme, pour n'importe quel beneficiaire).
        verifierAccesCibleNonSuperAdmin(beneficiaire.getId(), auteur, EntiteAuditable.HABILITATION);
        validerCoherencePerimetre(code, requete.serviceId());
        validerExclusiviteRh(beneficiaire.getId(), code);
        validerAttributionSuperAdministrateur(code, auteur);
        validerUnSeulServiceParRole(beneficiaire.getId(), code);

        Service service = null;
        if (requete.serviceId() != null) {
            service = serviceRepository.findById(requete.serviceId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Service", requete.serviceId()));
        }

        Habilitation habilitation = new Habilitation();
        habilitation.setUtilisateur(beneficiaire);
        habilitation.setRoleMetier(roleMetier);
        habilitation.setService(service);
        habilitation.setDateDebut(requete.dateDebut());
        habilitation.setDateFin(requete.dateFin());
        habilitation.setActif(true);
        habilitation.setCreePar(auteur);
        habilitation = habilitationRepository.save(habilitation);

        auditService.enregistrer(EntiteAuditable.HABILITATION, habilitation.getId(), auteur,
                TypeActionAudit.ATTRIBUTION_HABILITATION, null, habilitationMapper.toDto(habilitation),
                null, null);
        return habilitationMapper.toDto(habilitation);
    }

    public void retirer(Long habilitationId, Utilisateur auteur) {
        Habilitation habilitation = habilitationRepository.findById(habilitationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Habilitation", habilitationId));
        // Symetrique de l'attribution : un Administrateur standard ne doit
        // jamais pouvoir retirer QUELQUE habilitation que ce soit d'un compte
        // deja Super Administrateur (retrait de privilege = meme surface de
        // risque que son attribution), pas seulement l'habilitation
        // SUPER_ADMINISTRATEUR elle-meme.
        verifierAccesCibleNonSuperAdmin(habilitation.getUtilisateur().getId(), auteur, EntiteAuditable.HABILITATION);
        habilitation.setActif(false);
        habilitation.setDateFin(LocalDate.now());
        habilitationRepository.save(habilitation);
        auditService.enregistrer(EntiteAuditable.HABILITATION, habilitation.getId(), auteur,
                TypeActionAudit.RETRAIT_HABILITATION, true, false, null, null);
    }

    /**
     * Reaffecte, en une seule action tracee, un titulaire de Charge
     * d'Affaires / Personne habilitee / Responsable d'Activite vers un
     * autre service (evolution du 2026-08-19, section 10 : "Si un
     * utilisateur doit reellement changer de service, il faut modifier son
     * service d'affectation, avec tracabilite, plutot que lui attribuer
     * plusieurs services simultanement"). Retire l'ancienne habilitation et
     * en cree une nouvelle, du meme role, sur le nouveau service, dans la
     * MEME transaction - un seul evenement d'audit CHANGEMENT_SERVICE_HABILITATION
     * porte l'avant/apres, plutot que deux evenements RETRAIT/ATTRIBUTION
     * distincts qui rendraient moins evident, a la relecture du journal,
     * qu'il s'agit d'une seule et meme reaffectation plutot que de deux
     * decisions independantes.
     */
    public HabilitationDto changerServiceHabilitation(Long habilitationId, Long nouveauServiceId, Utilisateur auteur) {
        Habilitation ancienne = habilitationRepository.findById(habilitationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Habilitation", habilitationId));
        verifierAccesCibleNonSuperAdmin(ancienne.getUtilisateur().getId(), auteur, EntiteAuditable.HABILITATION);
        CodeRoleMetier code = CodeRoleMetier.valueOf(ancienne.getRoleMetier().getCode());
        if (!code.estServiceExclusif()) {
            throw new BusinessRuleViolationException("RG-HAB-007",
                    "Le role " + code + " n'est pas concerne par un changement de service dedie "
                            + "(role a perimetre global ou multi-service par construction).");
        }
        Service nouveauService = serviceRepository.findById(nouveauServiceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Service", nouveauServiceId));
        Long ancienServiceId = ancienne.getService() != null ? ancienne.getService().getId() : null;
        if (nouveauServiceId.equals(ancienServiceId)) {
            throw new BusinessRuleViolationException("RG-HAB-007",
                    "Le nouveau service est identique au service actuel de cette habilitation.");
        }

        ancienne.setActif(false);
        ancienne.setDateFin(LocalDate.now());
        habilitationRepository.save(ancienne);

        Habilitation nouvelle = new Habilitation();
        nouvelle.setUtilisateur(ancienne.getUtilisateur());
        nouvelle.setRoleMetier(ancienne.getRoleMetier());
        nouvelle.setService(nouveauService);
        nouvelle.setDateDebut(LocalDate.now());
        nouvelle.setDateFin(null);
        nouvelle.setActif(true);
        nouvelle.setCreePar(auteur);
        nouvelle = habilitationRepository.save(nouvelle);

        auditService.enregistrer(EntiteAuditable.HABILITATION, nouvelle.getId(), auteur,
                TypeActionAudit.CHANGEMENT_SERVICE_HABILITATION,
                ancienServiceId, nouveauServiceId, null, null);
        return habilitationMapper.toDto(nouvelle);
    }

    /**
     * Un seul service actif a la fois pour les roles concernes (evolution du
     * 2026-08-19, section 1 : "Un Charge d'Affaires appartient a un seul
     * service", meme regle pour Personne habilitee et Responsable
     * d'Activite). Verifiee en base a l'attribution, independamment du
     * service demande - un utilisateur qui detient deja une habilitation
     * active de ce role, quel que soit son service actuel, doit d'abord en
     * changer via {@link #changerServiceHabilitation} plutot que d'en
     * cumuler une seconde.
     */
    private void validerUnSeulServiceParRole(Long utilisateurId, CodeRoleMetier code) {
        if (!code.estServiceExclusif()) {
            return;
        }
        boolean possedeDejaCeRole = habilitationRepository.findByUtilisateur_IdAndActifTrue(utilisateurId).stream()
                .anyMatch(h -> code.name().equals(h.getRoleMetier().getCode()));
        if (possedeDejaCeRole) {
            throw new BusinessRuleViolationException("RG-HAB-007",
                    "Cet utilisateur detient deja une habilitation " + code + " active sur un service. "
                            + "Utilisez le changement de service plutot que d'en attribuer une seconde.");
        }
    }

    /**
     * Protection contre l'escalade de privileges (evolution du 2026-08-18,
     * section 12 de la mission "Super Administrateur") : seul un titulaire
     * DEJA actif de l'habilitation SUPER_ADMINISTRATEUR peut en attribuer une
     * nouvelle - a un tiers, ou (a fortiori) a lui-meme. Un Administrateur
     * standard, meme habilite a gerer les habilitations en general
     * ({@code @PreAuthorize("hasRole('ADMINISTRATEUR')")} au niveau du
     * controleur), ne franchit jamais cette barriere supplementaire, verifiee
     * ici en base de donnees plutot que sur le seul jeton JWT presente par
     * l'appelant - impossible a contourner par un appel direct a l'API.
     */
    private void validerAttributionSuperAdministrateur(CodeRoleMetier codeDemande, Utilisateur auteur) {
        if (codeDemande != CodeRoleMetier.SUPER_ADMINISTRATEUR) {
            return;
        }
        boolean auteurEstDejaSuperAdministrateur = habilitationRepository
                .findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
                .anyMatch(h -> CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
        if (!auteurEstDejaSuperAdministrateur) {
            throw new ForbiddenOperationException(
                    "Seul un Super Administrateur deja habilite peut attribuer le role Super Administrateur.");
        }
    }

    /**
     * Bloque toute lecture ou ecriture d'un Administrateur standard sur les
     * habilitations d'un compte deja Super Administrateur (evolution du
     * 2026-08-19, section 1-4) - verifie en base a chaque appel, jamais sur
     * le seul JWT de l'appelant, et journalise systematiquement la tentative
     * (ACCES_REFUSE) qu'elle vienne du frontend ou d'un appel direct a l'API.
     */
    private void verifierAccesCibleNonSuperAdmin(Long utilisateurCibleId, Utilisateur appelant, EntiteAuditable entiteAuditee) {
        boolean cibleEstSuperAdmin = habilitationRepository.findByUtilisateur_IdAndActifTrue(utilisateurCibleId).stream()
                .anyMatch(h -> CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
        if (!cibleEstSuperAdmin) {
            return;
        }
        boolean appelantEstSuperAdmin = habilitationRepository.findByUtilisateur_IdAndActifTrue(appelant.getId()).stream()
                .anyMatch(h -> CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(h.getRoleMetier().getCode()));
        if (appelantEstSuperAdmin) {
            return;
        }
        auditService.enregistrerAction(entiteAuditee, utilisateurCibleId, appelant, TypeActionAudit.ACCES_REFUSE);
        throw new ForbiddenOperationException(
                "Les habilitations de ce compte ne sont pas accessibles depuis votre niveau d'habilitation.");
    }

    /** Un role a perimetre global n'a jamais de service ; tout autre role en exige toujours un. */
    private void validerCoherencePerimetre(CodeRoleMetier code, Long serviceId) {
        if (code.estPerimetreGlobal() && serviceId != null) {
            throw new BusinessRuleViolationException("RG-HAB-001",
                    "Le role " + code + " est a perimetre global : aucun service ne doit etre precise.");
        }
        if (!code.estPerimetreGlobal() && serviceId == null) {
            throw new BusinessRuleViolationException("RG-HAB-001",
                    "Le role " + code + " requiert un service de rattachement.");
        }
    }

    /**
     * RG-HAB-005 : l'habilitation RH ne peut jamais etre cumulee avec une
     * autre habilitation active, dans un sens comme dans l'autre.
     */
    private void validerExclusiviteRh(Long utilisateurId, CodeRoleMetier codeDemande) {
        boolean possedeAutreHabilitationActive = habilitationRepository
                .existsByUtilisateur_IdAndActifTrueAndRoleMetier_CodeNot(utilisateurId, CodeRoleMetier.RH.name());

        if (codeDemande == CodeRoleMetier.RH && possedeAutreHabilitationActive) {
            throw new BusinessRuleViolationException("RG-HAB-005",
                    "Impossible d'attribuer l'habilitation RH : cet utilisateur detient deja une autre "
                            + "habilitation active. L'habilitation RH est exclusive.");
        }
        if (codeDemande != CodeRoleMetier.RH) {
            boolean possedeDejaRh = habilitationRepository.findByUtilisateur_IdAndActifTrue(utilisateurId).stream()
                    .anyMatch(h -> CodeRoleMetier.RH.name().equals(h.getRoleMetier().getCode()));
            if (possedeDejaRh) {
                throw new BusinessRuleViolationException("RG-HAB-005",
                        "Impossible d'attribuer ce role : cet utilisateur detient l'habilitation RH, "
                                + "exclusive de toute autre habilitation.");
            }
        }
    }
}
