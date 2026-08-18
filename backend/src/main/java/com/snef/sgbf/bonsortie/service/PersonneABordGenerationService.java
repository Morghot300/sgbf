package com.snef.sgbf.bonsortie.service;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.bonsortie.entity.BonSortiePersonne;
import com.snef.sgbf.bonsortie.entity.OrigineBonSortie;
import com.snef.sgbf.bonsortie.entity.StatutBonSortie;
import com.snef.sgbf.bonsortie.repository.BonSortiePersonneRepository;
import com.snef.sgbf.bonsortie.repository.BonSortieRepository;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.fiph.service.FiphService;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.service.AffectationMissionService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generation automatique du bon de sortie individuel d'une personne a bord,
 * dans sa propre transaction isolee (section 9.6, choix de conception
 * "atomicite par personne plutot que transaction globale") : l'echec pour
 * une personne (par exemple, aucune affectation active a la date du
 * deplacement) ne doit jamais faire echouer la validation du bon de sortie
 * principal ni la generation pour les autres personnes a bord.
 *
 * <p>{@link Propagation#REQUIRES_NEW} est indispensable ici : appelee comme
 * simple methode de la meme classe (sans passer par un bean Spring distinct),
 * l'annotation {@code @Transactional} serait ignoree (limite bien connue du
 * proxying AOP de Spring sur les appels internes) et l'isolation
 * recherchee n'existerait pas. C'est pourquoi cette logique vit dans son
 * propre service plutot que comme methode privee de {@code BonSortieService}.
 *
 * <p><strong>Hypothese documentee</strong> (le document source ne precise
 * pas ce point) : la resolution de l'affectation pour la personne a bord se
 * fait sur SA PROPRE {@code AffectationMission} active a la date de sortie
 * du principal (coherent avec le modele ou chaque bon de sortie porte sa
 * propre reference d'affectation, section 8) - pas une copie de
 * l'affectation du principal, qui reste specifique a son propre agent. Si la
 * personne a bord n'a pas d'affectation active a cette date, la generation
 * est journalisee comme echec recuperable (section 9.6 : "rejoue
 * manuellement par une personne habilitee") plutot que de bloquer la chaine.
 *
 * <p>Le bon de sortie individuel genere declenche a son tour, exactement
 * comme un bon de sortie principal, la generation automatique et le
 * prerempissage de la FIPH de cette personne a bord (RG-BS-007, RG-FIPH-001,
 * RG-PAB-004) via {@link FiphService#genererOuEnrichirDepuisBonSortie}.
 */
@Service
public class PersonneABordGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PersonneABordGenerationService.class);

    private final BonSortiePersonneRepository bonSortiePersonneRepository;
    private final BonSortieRepository bonSortieRepository;
    private final AffectationMissionService affectationMissionService;
    private final FiphService fiphService;
    private final AuditService auditService;

    public PersonneABordGenerationService(BonSortiePersonneRepository bonSortiePersonneRepository,
                                           BonSortieRepository bonSortieRepository,
                                           AffectationMissionService affectationMissionService,
                                           FiphService fiphService,
                                           AuditService auditService) {
        this.bonSortiePersonneRepository = bonSortiePersonneRepository;
        this.bonSortieRepository = bonSortieRepository;
        this.affectationMissionService = affectationMissionService;
        this.fiphService = fiphService;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void genererPourAssociation(Long associationId, Utilisateur auteur) {
        BonSortiePersonne association = bonSortiePersonneRepository.findById(associationId)
                .orElseThrow(() -> ResourceNotFoundException.of("BonSortiePersonne", associationId));

        // Garde d'idempotence : ne genere jamais deux fois pour la meme association.
        if (association.getBonSortieIndividuel() != null) {
            return;
        }

        BonSortie principal = association.getBonSortiePrincipal();
        Agent personneAgent = association.getAgent();

        Optional<AffectationMission> affectationPersonne =
                affectationMissionService.resoudreActiveADate(personneAgent.getId(), principal.getDateSortie());

        if (affectationPersonne.isEmpty()) {
            log.warn("Generation automatique impossible pour la personne a bord agent={} (bon de sortie principal={}) : "
                            + "aucune affectation active a la date {}. Reprise manuelle requise (section 9.6).",
                    personneAgent.getMatricule(), principal.getId(), principal.getDateSortie());
            auditService.enregistrerAction(EntiteAuditable.BON_SORTIE_PERSONNE, association.getId(), auteur,
                    TypeActionAudit.MODIFICATION);
            return;
        }

        BonSortie individuel = new BonSortie();
        individuel.setAgent(personneAgent);
        individuel.setVehicule(principal.getVehicule());
        individuel.setAffectationMission(affectationPersonne.get());
        individuel.setMoyenUtilise(principal.getMoyenUtilise());
        individuel.setLt(principal.getLt());
        individuel.setKilometrage(principal.getKilometrage());
        individuel.setDateSortie(principal.getDateSortie());
        individuel.setHeureSortie(principal.getHeureSortie());
        individuel.setHeureRetour(principal.getHeureRetour());
        individuel.setLieu(principal.getLieu());
        individuel.setCodeAffaireSaisi(principal.getCodeAffaireSaisi());
        individuel.setMotifSortie(principal.getMotifSortie());
        // RG-PAB-005 : statut herite, sans visa ni validation separes.
        individuel.setStatut(StatutBonSortie.VALIDE);
        individuel.setOrigine(OrigineBonSortie.PERSONNE_A_BORD);
        individuel.setBonSortiePrincipal(principal);
        individuel = bonSortieRepository.save(individuel);

        association.setBonSortieIndividuel(individuel);
        bonSortiePersonneRepository.save(association);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE, individuel.getId(), auteur,
                TypeActionAudit.BS_INDIVIDUEL_AUTO_GENERE, null, individuel.getId(),
                null, StatutBonSortie.VALIDE.name());

        // RG-BS-007 / RG-FIPH-001 / RG-PAB-004 : declenche, pour cette
        // personne a bord, exactement la meme chaine de generation
        // automatique de FIPH qu'un bon de sortie principal.
        fiphService.genererOuEnrichirDepuisBonSortie(individuel, auteur);
    }
}
