package com.snef.sgbf.identite.service;

import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.identite.dto.CreerUtilisateurRequest;
import com.snef.sgbf.identite.dto.UtilisateurDto;
import com.snef.sgbf.identite.entity.StatutCompte;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.mapper.UtilisateurMapper;
import com.snef.sgbf.identite.repository.UtilisateurRepository;
import com.snef.sgbf.referentiel.entity.Service;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion administrative des comptes applicatifs.
 *
 * <p>Reserve a l'Administrateur (section 10 : "Gere les utilisateurs, les
 * roles et les habilitations"). Les mots de passe ne transitent jamais en
 * clair au-dela de cette couche service : ils sont hashes immediatement via
 * {@link PasswordEncoder} (BCrypt, voir {@code security.PasswordConfig}) et
 * le hash seul est persiste.
 */
@org.springframework.stereotype.Service
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final ServiceRepository serviceRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UtilisateurService(UtilisateurRepository utilisateurRepository, ServiceRepository serviceRepository,
                               UtilisateurMapper utilisateurMapper, PasswordEncoder passwordEncoder,
                               AuditService auditService) {
        this.utilisateurRepository = utilisateurRepository;
        this.serviceRepository = serviceRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto> listerTous() {
        return utilisateurRepository.findAll().stream().map(utilisateurMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UtilisateurDto obtenirParId(Long id) {
        return utilisateurMapper.toDto(chargerUtilisateur(id));
    }

    public UtilisateurDto creer(CreerUtilisateurRequest requete, Utilisateur auteur) {
        if (utilisateurRepository.existsByIdentifiant(requete.identifiant())) {
            throw new ConflictException("L'identifiant " + requete.identifiant() + " est deja utilise.");
        }
        if (utilisateurRepository.existsByEmail(requete.email())) {
            throw new ConflictException("L'adresse e-mail " + requete.email() + " est deja utilisee.");
        }

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdentifiant(requete.identifiant());
        utilisateur.setEmail(requete.email());
        // Le mot de passe fourni en clair par l'administrateur n'est jamais
        // conserve : seul son hash BCrypt l'est, des cette ligne.
        utilisateur.setMotDePasseHash(passwordEncoder.encode(requete.motDePasse()));
        utilisateur.setStatutCompte(StatutCompte.ACTIF);

        if (requete.serviceId() != null) {
            Service service = serviceRepository.findById(requete.serviceId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Service", requete.serviceId()));
            utilisateur.setService(service);
        }

        utilisateur = utilisateurRepository.save(utilisateur);

        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.CREATION, null, utilisateurMapper.toDto(utilisateur), null, null);
        return utilisateurMapper.toDto(utilisateur);
    }

    public void changerStatut(Long id, StatutCompte nouveauStatut, Utilisateur auteur) {
        Utilisateur utilisateur = chargerUtilisateur(id);
        StatutCompte statutAvant = utilisateur.getStatutCompte();
        utilisateur.setStatutCompte(nouveauStatut);
        utilisateurRepository.save(utilisateur);
        auditService.enregistrer(EntiteAuditable.UTILISATEUR, utilisateur.getId(), auteur,
                TypeActionAudit.MODIFICATION, statutAvant, nouveauStatut,
                statutAvant.name(), nouveauStatut.name());
    }

    private Utilisateur chargerUtilisateur(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
