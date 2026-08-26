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
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.mission.entity.AffectationMission;
import com.snef.sgbf.mission.service.AffectationMissionService;
import com.snef.sgbf.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Optional;
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
 * l'affectation du principal, qui reste specifique a son propre agent.
 *
 * <p><strong>Anomalie corrigee (evolution du 2026-08-19, Lot 4, point 9)</strong> :
 * jusqu'a cette evolution, l'absence d'affectation active pour la personne a
 * bord faisait ABANDONNER definitivement la generation (retour anticipe,
 * aucune tentative ulterieure) - le frontend affichait alors indefiniment
 * "En cours de generation" alors que rien n'etait effectivement en cours ni
 * ne le serait jamais sans intervention manuelle (aucun mecanisme de
 * "reprise" n'existe reellement malgre ce que suggerait l'ancien
 * commentaire). Desormais coherent avec Lot 2 : le bon individuel est
 * genere quand meme, sans affectation renseignee, avec le meme avertissement
 * actionnable et la meme notification que pour le bon principal - plus
 * jamais de blocage silencieux permanent.
 *
 * <p><strong>En attente de validation du Charge d'Affaires (evolution du
 * 2026-08-26, section 12-15)</strong> : le bon individuel est genere au
 * statut {@code VISE} - jamais directement {@code VALIDE} - exactement comme
 * le visa automatique applique en {@code BonSortieService#creer} a un
 * titulaire sans compte applicatif (meme motif : la personne a bord n'est
 * jamais celle qui declenche elle-meme ce bon, "viser" restant par ailleurs
 * strictement reserve au titulaire lui-meme, RG-BS-004). Ce choix reutilise
 * integralement le circuit de validation niveau 2 deja existant, sans
 * inventer de nouveau statut ni de nouvel endpoint : le Charge d'Affaires du
 * service de la personne retrouve ce bon dans sa liste de travail habituelle
 * (filtre {@code statut=VISE}) et le valide via {@code POST /{id}/valider},
 * comme n'importe quel autre bon de sortie. La FIPH de cette personne
 * (RG-BS-007, RG-FIPH-001, RG-PAB-004) n'est donc initialisee qu'a CE
 * moment-la, par {@link BonSortieService#valider}, jamais avant.
 */
@Service
public class PersonneABordGenerationService {

    private final BonSortiePersonneRepository bonSortiePersonneRepository;
    private final BonSortieRepository bonSortieRepository;
    private final AffectationMissionService affectationMissionService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public PersonneABordGenerationService(BonSortiePersonneRepository bonSortiePersonneRepository,
                                           BonSortieRepository bonSortieRepository,
                                           AffectationMissionService affectationMissionService,
                                           AuditService auditService,
                                           NotificationService notificationService) {
        this.bonSortiePersonneRepository = bonSortiePersonneRepository;
        this.bonSortieRepository = bonSortieRepository;
        this.affectationMissionService = affectationMissionService;
        this.auditService = auditService;
        this.notificationService = notificationService;
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
        Utilisateur personneAgent = association.getAgent();

        Optional<AffectationMission> affectationPersonne =
                affectationMissionService.resoudreActiveADate(personneAgent.getId(), principal.getDateSortie());

        BonSortie individuel = new BonSortie();
        individuel.setAgent(personneAgent);
        individuel.setVehicule(principal.getVehicule());
        affectationPersonne.ifPresent(individuel::setAffectationMission);
        individuel.setMoyenUtilise(principal.getMoyenUtilise());
        individuel.setLt(principal.getLt());
        individuel.setKilometrage(principal.getKilometrage());
        individuel.setDateSortie(principal.getDateSortie());
        individuel.setHeureSortie(principal.getHeureSortie());
        individuel.setHeureRetour(principal.getHeureRetour());
        individuel.setLieu(principal.getLieu());
        individuel.setCodeAffaireSaisi(principal.getCodeAffaireSaisi());
        individuel.setMotifSortie(principal.getMotifSortie());
        // RG-PAB-005 (revise le 2026-08-26) : genere directement au niveau 1 (VISE),
        // en attente de la validation niveau 2 du Charge d'Affaires du service -
        // jamais VALIDE d'emblee (voir Javadoc de classe).
        individuel.setStatut(StatutBonSortie.VISE);
        individuel.setVisePar(auteur);
        individuel.setDateVisa(LocalDateTime.now());
        individuel.setOrigine(OrigineBonSortie.PERSONNE_A_BORD);
        individuel.setBonSortiePrincipal(principal);
        individuel = bonSortieRepository.save(individuel);

        association.setBonSortieIndividuel(individuel);
        bonSortiePersonneRepository.save(association);

        auditService.enregistrer(EntiteAuditable.BON_SORTIE, individuel.getId(), auteur,
                TypeActionAudit.BS_INDIVIDUEL_AUTO_GENERE, null, individuel.getId(),
                null, StatutBonSortie.VISE.name());
        if (affectationPersonne.isEmpty()) {
            auditService.enregistrer(EntiteAuditable.BON_SORTIE, individuel.getId(), auteur,
                    TypeActionAudit.ANOMALIE_AFFECTATION, null,
                    "Bon individuel genere sans affectation active resolue pour la personne a bord "
                            + personneAgent.getMatricule(), null, null);
            if (personneAgent.getService() != null) {
                notificationService.notifierAnomalieAffectation(individuel.getId(), auteur, personneAgent.getService().getId());
            }
        }

        // Le bon individuel est desormais "en attente de validation du Charge
        // d'Affaires" au meme titre que n'importe quel bon vise - c'est SA
        // validation (BonSortieService#valider), et elle seule, qui declenchera
        // la generation/l'enrichissement de la FIPH de cette personne a bord
        // (RG-BS-007, RG-FIPH-001, RG-PAB-004) ; rien n'est initialise ici.
        if (personneAgent.getService() != null) {
            notificationService.notifierBonSortieAValider(individuel.getId(), personneAgent.getService().getId(),
                    "Bon de sortie #" + individuel.getId(), auteur);
        }
    }
}
