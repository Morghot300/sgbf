package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.BusinessRuleViolationException;
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
@org.springframework.stereotype.Service
@Transactional
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

    @Transactional(readOnly = true)
    public List<HabilitationDto> listerPourUtilisateur(Long utilisateurId) {
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

        validerCoherencePerimetre(code, requete.serviceId());
        validerExclusiviteRh(beneficiaire.getId(), code);

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
        habilitation.setActif(false);
        habilitation.setDateFin(LocalDate.now());
        habilitationRepository.save(habilitation);
        auditService.enregistrer(EntiteAuditable.HABILITATION, habilitation.getId(), auteur,
                TypeActionAudit.RETRAIT_HABILITATION, true, false, null, null);
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
