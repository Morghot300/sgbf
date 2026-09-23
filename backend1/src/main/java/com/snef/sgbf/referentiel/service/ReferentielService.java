package com.snef.sgbf.referentiel.service;

import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.referentiel.dto.ChantierDto;
import com.snef.sgbf.referentiel.dto.CodeHNDto;
import com.snef.sgbf.referentiel.dto.CreerChantierRequest;
import com.snef.sgbf.referentiel.dto.CreerCodeHNRequest;
import com.snef.sgbf.referentiel.dto.CreerServiceRequest;
import com.snef.sgbf.referentiel.dto.CreerVehiculeRequest;
import com.snef.sgbf.referentiel.dto.MotifInterruptionDto;
import com.snef.sgbf.referentiel.dto.ServiceDto;
import com.snef.sgbf.referentiel.dto.VehiculeDto;
import com.snef.sgbf.referentiel.entity.Chantier;
import com.snef.sgbf.referentiel.entity.CodeHN;
import com.snef.sgbf.referentiel.entity.MotifInterruptionMission;
import com.snef.sgbf.referentiel.entity.Vehicule;
import com.snef.sgbf.referentiel.repository.ChantierRepository;
import com.snef.sgbf.referentiel.repository.CodeHNRepository;
import com.snef.sgbf.referentiel.repository.MotifInterruptionMissionRepository;
import com.snef.sgbf.referentiel.repository.ServiceRepository;
import com.snef.sgbf.referentiel.repository.VehiculeRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration des referentiels simples (section 10 : "Administre les
 * referentiels : services, chantiers, codes, vehicules").
 *
 * <p>Regroupes en un seul service, plutot que cinq services quasi-identiques,
 * parce qu'aucun d'entre eux ne porte de logique metier au-dela d'un CRUD
 * avec controle d'unicite - les separer n'apporterait aucune cohesion
 * supplementaire, seulement de la repetition. {@link com.snef.sgbf.referentiel.entity.MotifInterruptionMission}
 * ne propose volontairement pas de creation ici : le document source
 * (section 6.2) fournit deja une liste initiale seedee en base
 * ({@code V1__referentiels.sql}) et son extension reste rare - une gestion
 * complete sera ajoutee si un besoin operationnel le justifie.
 */
@org.springframework.stereotype.Service
@Transactional
public class ReferentielService {

    private final ServiceRepository serviceRepository;
    private final ChantierRepository chantierRepository;
    private final CodeHNRepository codeHNRepository;
    private final VehiculeRepository vehiculeRepository;
    private final MotifInterruptionMissionRepository motifInterruptionMissionRepository;

    public ReferentielService(ServiceRepository serviceRepository, ChantierRepository chantierRepository,
                               CodeHNRepository codeHNRepository, VehiculeRepository vehiculeRepository,
                               MotifInterruptionMissionRepository motifInterruptionMissionRepository) {
        this.serviceRepository = serviceRepository;
        this.chantierRepository = chantierRepository;
        this.codeHNRepository = codeHNRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.motifInterruptionMissionRepository = motifInterruptionMissionRepository;
    }

    // --- Service ---

    @Transactional(readOnly = true)
    public List<ServiceDto> listerServices() {
        return serviceRepository.findAll().stream().map(ReferentielService::versDto).toList();
    }

    public ServiceDto creerService(CreerServiceRequest requete) {
        if (serviceRepository.findByCodeService(requete.codeService()).isPresent()) {
            throw new ConflictException("Le code service " + requete.codeService() + " existe deja.");
        }
        com.snef.sgbf.referentiel.entity.Service service = new com.snef.sgbf.referentiel.entity.Service();
        service.setCodeService(requete.codeService());
        service.setLibelle(requete.libelle());
        return versDto(serviceRepository.save(service));
    }

    // --- Chantier ---

    @Transactional(readOnly = true)
    public List<ChantierDto> listerChantiers() {
        return chantierRepository.findAll().stream().map(ReferentielService::versDto).toList();
    }

    public ChantierDto creerChantier(CreerChantierRequest requete) {
        Chantier chantier = new Chantier();
        chantier.setCodeAffaire(requete.codeAffaire());
        chantier.setLibelle(requete.libelle());
        return versDto(chantierRepository.save(chantier));
    }

    // --- CodeHN (code mission) ---

    @Transactional(readOnly = true)
    public List<CodeHNDto> listerCodesHN() {
        return codeHNRepository.findAll().stream().map(ReferentielService::versDto).toList();
    }

    public CodeHNDto creerCodeHN(CreerCodeHNRequest requete) {
        Chantier chantier = chantierRepository.findById(requete.chantierId())
                .orElseThrow(() -> ResourceNotFoundException.of("Chantier", requete.chantierId()));
        CodeHN codeHN = new CodeHN();
        codeHN.setCode(requete.code());
        codeHN.setLibelle(requete.libelle());
        codeHN.setChantier(chantier);
        return versDto(codeHNRepository.save(codeHN));
    }

    // --- Vehicule ---

    @Transactional(readOnly = true)
    public List<VehiculeDto> listerVehicules() {
        return vehiculeRepository.findAll().stream().map(ReferentielService::versDto).toList();
    }

    public VehiculeDto creerVehicule(CreerVehiculeRequest requete) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(requete.immatriculation());
        vehicule.setType(requete.type());
        return versDto(vehiculeRepository.save(vehicule));
    }

    // --- MotifInterruptionMission (lecture seule ici, voir Javadoc de classe) ---

    @Transactional(readOnly = true)
    public List<MotifInterruptionDto> listerMotifsInterruption() {
        return motifInterruptionMissionRepository.findByActifTrue().stream()
                .map(ReferentielService::versDto).toList();
    }

    private static ServiceDto versDto(com.snef.sgbf.referentiel.entity.Service s) {
        return new ServiceDto(s.getId(), s.getCodeService(), s.getLibelle(), s.isActif());
    }

    private static ChantierDto versDto(Chantier c) {
        return new ChantierDto(c.getId(), c.getCodeAffaire(), c.getLibelle(), c.isActif());
    }

    private static CodeHNDto versDto(CodeHN c) {
        return new CodeHNDto(c.getId(), c.getCode(), c.getLibelle(), c.getChantier().getId(), c.getChantier().getLibelle());
    }

    private static VehiculeDto versDto(Vehicule v) {
        return new VehiculeDto(v.getId(), v.getImmatriculation(), v.getType());
    }

    private static MotifInterruptionDto versDto(MotifInterruptionMission m) {
        return new MotifInterruptionDto(m.getId(), m.getCode(), m.getLibelle(), m.isActif());
    }
}
