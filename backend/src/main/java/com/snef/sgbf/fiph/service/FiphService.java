package com.snef.sgbf.fiph.service;

import com.snef.sgbf.bonsortie.entity.BonSortie;
import com.snef.sgbf.common.audit.AuditService;
import com.snef.sgbf.common.audit.EntiteAuditable;
import com.snef.sgbf.common.audit.TypeActionAudit;
import com.snef.sgbf.common.exception.ConflictException;
import com.snef.sgbf.common.exception.ForbiddenOperationException;
import com.snef.sgbf.common.exception.ResourceNotFoundException;
import com.snef.sgbf.fiph.dto.CreerFiphManuelleRequest;
import com.snef.sgbf.fiph.dto.FiphDto;
import com.snef.sgbf.fiph.entity.FIPH;
import com.snef.sgbf.fiph.entity.FIPHVersion;
import com.snef.sgbf.fiph.entity.JourSemaine;
import com.snef.sgbf.fiph.entity.OrigineFiph;
import com.snef.sgbf.fiph.entity.Pointage;
import com.snef.sgbf.fiph.entity.Signature;
import com.snef.sgbf.fiph.entity.StatutFiphVersion;
import com.snef.sgbf.fiph.entity.TypeSignature;
import com.snef.sgbf.fiph.mapper.FiphMapper;
import com.snef.sgbf.fiph.repository.FiphRepository;
import com.snef.sgbf.fiph.repository.FiphVersionRepository;
import com.snef.sgbf.fiph.repository.PointageRepository;
import com.snef.sgbf.fiph.repository.SignatureRepository;
import com.snef.sgbf.identite.entity.Agent;
import com.snef.sgbf.identite.entity.Habilitation;
import com.snef.sgbf.identite.entity.Utilisateur;
import com.snef.sgbf.identite.repository.AgentRepository;
import com.snef.sgbf.identite.repository.HabilitationRepository;
import com.snef.sgbf.referentiel.entity.CodeRoleMetier;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion de l'identite stable de la FIPH (creation, resolution par periode -
 * RG-FIPH-001 a 004) et point d'entree de la generation automatique
 * declenchee par la validation d'un bon de sortie (RG-BS-007, RG-FIPH-001).
 *
 * <p>Le cycle de vie d'une VERSION (completer, signer, soumettre, valider,
 * nouvelle version) est porte par {@link FiphVersionService} - separation
 * qui reflete fidelement celle du document source entre {@code FIPH}
 * (identite) et {@code FIPHVersion} (contenu, section 17).
 */
@org.springframework.stereotype.Service
@Transactional
public class FiphService {

    private static final Logger log = LoggerFactory.getLogger(FiphService.class);

    private final FiphRepository fiphRepository;
    private final FiphVersionRepository fiphVersionRepository;
    private final PointageRepository pointageRepository;
    private final AgentRepository agentRepository;
    private final HabilitationRepository habilitationRepository;
    private final SignatureRepository signatureRepository;
    private final FiphMapper fiphMapper;
    private final AuditService auditService;

    public FiphService(FiphRepository fiphRepository, FiphVersionRepository fiphVersionRepository,
                        PointageRepository pointageRepository, AgentRepository agentRepository,
                        HabilitationRepository habilitationRepository, SignatureRepository signatureRepository,
                        FiphMapper fiphMapper, AuditService auditService) {
        this.fiphRepository = fiphRepository;
        this.fiphVersionRepository = fiphVersionRepository;
        this.pointageRepository = pointageRepository;
        this.agentRepository = agentRepository;
        this.habilitationRepository = habilitationRepository;
        this.signatureRepository = signatureRepository;
        this.fiphMapper = fiphMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public FiphDto obtenirParId(Long id, Utilisateur courant) {
        FIPH fiph = chargerFiph(id);
        verifierPerimetreLecture(courant, fiph);
        return fiphMapper.toDto(fiph);
    }

    /**
     * Liste des FIPH visibles, avec filtres optionnels combinables
     * (evolution du 2026-08-18, section 2) : date exacte, periode (une FIPH
     * est retenue des lors que sa periode hebdomadaire chevauche l'intervalle
     * demande), statut de la version courante, service. Toujours appliques
     * APRES le filtrage de perimetre (RG-SEC-002).
     */
    @Transactional(readOnly = true)
    public List<FiphDto> listerVisibles(Utilisateur courant, java.time.LocalDate date, java.time.LocalDate dateDebut,
                                         java.time.LocalDate dateFin, StatutFiphVersion statut, Long serviceId) {
        return entitesVisibles(courant).stream()
                .filter(f -> date == null || !date.isBefore(f.getDateDebutPeriode()) && !date.isAfter(f.getDateFinPeriode()))
                .filter(f -> dateDebut == null || !f.getDateFinPeriode().isBefore(dateDebut))
                .filter(f -> dateFin == null || !f.getDateDebutPeriode().isAfter(dateFin))
                .filter(f -> statut == null || statut == f.getStatut())
                .filter(f -> serviceId == null || serviceId.equals(f.getService().getId()))
                .map(fiphMapper::toDto)
                .toList();
    }

    private List<FIPH> entitesVisibles(Utilisateur courant) {
        List<Habilitation> habilitations = habilitationRepository.findByUtilisateur_IdAndActifTrue(courant.getId());
        boolean visionGlobale = habilitations.stream().anyMatch(h -> estRoleVisionGlobale(h.getRoleMetier().getCode()));
        if (visionGlobale) {
            return fiphRepository.findAll();
        }

        var servicesGeres = habilitations.stream()
                .filter(h -> estRoleGestionnaire(h.getRoleMetier().getCode()))
                .map(h -> h.getService() != null ? h.getService().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return fiphRepository.findAll().stream()
                .filter(f -> servicesGeres.contains(f.getService().getId())
                        || (f.getAgent().getUtilisateur() != null && f.getAgent().getUtilisateur().getId().equals(courant.getId())))
                .toList();
    }

    /**
     * Creation manuelle d'une FIPH d'origine {@code MANUELLE} (Code Service,
     * section 2) pour un agent non concerne par une mission durant la
     * periode - reservee au Charge d'Affaires et a la personne habilitee du
     * service de l'agent (RG-FIPH-004, RG-FIPH-010).
     */
    public FiphDto creerManuelle(CreerFiphManuelleRequest requete, Utilisateur auteur) {
        Agent agent = agentRepository.findById(requete.agentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", requete.agentId()));
        verifierPerimetreGestionnaire(auteur, agent);

        if (fiphRepository.findByAgent_IdAndAnneeAndNumeroSemaine(agent.getId(), requete.annee(), requete.numeroSemaine()).isPresent()) {
            throw new ConflictException("Une FIPH existe deja pour cet agent sur cette periode (RG-FIPH-002).");
        }

        LocalDate lundi = lundiDeLaSemaineIso(requete.annee(), requete.numeroSemaine());
        FIPH fiph = creerFiphEtVersionInitiale(agent, OrigineFiph.MANUELLE, null, lundi, auteur);
        return fiphMapper.toDto(fiph);
    }

    /**
     * Point d'entree de la generation/enrichissement automatique declenche
     * par la validation d'un bon de sortie (RG-BS-007, RG-FIPH-001,
     * RG-FIPH-002, RG-FIPH-003) - appele par
     * {@code BonSortieService.valider()} pour l'emetteur, et par
     * {@code PersonneABordGenerationService} pour chaque personne a bord.
     *
     * <p>Quatre cas, du plus simple au plus contraint :
     * <ol>
     *   <li>aucune FIPH n'existe pour (agent, periode) - creation complete
     *       (RG-FIPH-001), avec visa automatique de l'agent titulaire
     *       (voir {@link #creerFiphEtVersionInitiale}) ;</li>
     *   <li>une FIPH existe, sa version courante est encore
     *       {@link StatutFiphVersion#estPointageModifiable() modifiable}
     *       (brouillon, en complement, ou signee mais pas encore engagee
     *       dans le circuit de validation) - la ligne de pointage du jour
     *       est simplement ajoutee/mise a jour sur cette version
     *       (RG-FIPH-002) ;</li>
     *   <li>la version courante est SOUMISE/VALIDEE_NIVEAU_2/3 -
     *       extension raisonnee, documentee, des cas confirmes RG-FIPH-022/023
     *       (interruption sur FIPH deja engagee dans le circuit) : ajouter
     *       une ligne sans revalidation reviendrait a modifier silencieusement
     *       un contenu deja verifie par un valideur, ce que le document
     *       proscrit comme principe general. La version repasse donc en
     *       EN_COMPLEMENT, la ligne est ajoutee, une nouvelle validation
     *       complete du circuit sera necessaire ;</li>
     *   <li>la version courante est VALIDEE_DEFINITIVEMENT - RG-VER-001 et
     *       section 27.4 ("aucune voie de modification directe ... quelle
     *       que soit la nature de la correction demandee") imposent une
     *       nouvelle FIPHVersion, jamais une modification en place.</li>
     * </ol>
     */
    public void genererOuEnrichirDepuisBonSortie(BonSortie bonSortie, Utilisateur auteur) {
        Agent agent = bonSortie.getAgent();
        LocalDate date = bonSortie.getDateSortie();
        int annee = date.get(IsoFields.WEEK_BASED_YEAR);
        int numeroSemaine = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        Optional<FIPH> fiphExistante = fiphRepository.findByAgent_IdAndAnneeAndNumeroSemaine(agent.getId(), annee, numeroSemaine);

        if (fiphExistante.isEmpty()) {
            FIPH fiph = creerFiphEtVersionInitiale(agent, OrigineFiph.BON_SORTIE, bonSortie, date.with(DayOfWeek.MONDAY), auteur);
            ajouterOuMettreAJourPointage(fiph.getVersionCourante(), bonSortie);
            recalculerTotaux(fiph.getVersionCourante());
            log.info("FIPH creee automatiquement (id={}) pour l'agent {} suite a la validation du bon de sortie {}",
                    fiph.getId(), agent.getMatricule(), bonSortie.getId());
            return;
        }

        FIPH fiph = fiphExistante.get();
        FIPHVersion versionCourante = fiph.getVersionCourante();

        if (versionCourante.getStatutVersion().estFigee()) {
            // Cas 4 : versionnement obligatoire (RG-VER-001, section 27.4).
            FIPHVersion nouvelleVersion = creerVersionSuivante(fiph, versionCourante, auteur,
                    "Nouveau bon de sortie valide pour la periode (jour supplementaire)");
            ajouterOuMettreAJourPointage(nouvelleVersion, bonSortie);
            recalculerTotaux(nouvelleVersion);
            return;
        }

        if (!versionCourante.getStatutVersion().estPointageModifiable()) {
            // Cas 3 : la version est deja engagee dans le circuit de
            // validation (soumise ou au-dela) - repasse en EN_COMPLEMENT
            // plutot que d'accepter une modification silencieuse
            // (extension raisonnee de RG-FIPH-022/023, voir Javadoc).
            StatutFiphVersion avant = versionCourante.getStatutVersion();
            versionCourante.setStatutVersion(StatutFiphVersion.EN_COMPLEMENT);
            fiph.setStatut(StatutFiphVersion.EN_COMPLEMENT);
            fiphVersionRepository.save(versionCourante);
            fiphRepository.save(fiph);
            auditService.enregistrer(EntiteAuditable.FIPH_VERSION, versionCourante.getId(), auteur,
                    TypeActionAudit.MODIFICATION, avant.name(), StatutFiphVersion.EN_COMPLEMENT.name(),
                    avant.name(), StatutFiphVersion.EN_COMPLEMENT.name());
        }

        // Cas 2 (et suite du cas 3) : ajout/mise a jour de la ligne de pointage.
        ajouterOuMettreAJourPointage(fiph.getVersionCourante(), bonSortie);
        recalculerTotaux(fiph.getVersionCourante());
    }

    /**
     * Cree la FIPH et sa version initiale.
     *
     * <p><strong>Visa automatique de l'agent titulaire (evolution du
     * workflow FIPH, 2026-08-18)</strong> - pour une origine
     * {@link OrigineFiph#BON_SORTIE} uniquement : la precreation decoule
     * elle-meme de la validation d'un bon de sortie que l'agent a deja visee
     * (niveau 1) avant que le Charge d'Affaires ne le valide (niveau 2) - cet
     * agent est donc considere comme ayant deja accompli sa part du
     * processus, et il ne doit plus lui etre demande de signer une seconde
     * fois sa FIPH. La version initiale est donc creee directement au statut
     * {@link StatutFiphVersion#SIGNEE}, avec une {@link Signature}
     * enregistree automatiquement (type {@link TypeSignature#VISA_APPLICATIF}),
     * plutot qu'au statut {@code BROUILLON} en attente d'un appel explicite a
     * {@code FiphVersionService.signer()}. Le circuit de validation demarre
     * donc directement au Charge d'Affaires / a la personne habilitee
     * (niveau 2, voir {@code FiphVersionService.valider}), sans etape de
     * signature ni de soumission bloquante.
     *
     * <p>Pour une origine {@link OrigineFiph#MANUELLE} (Code Service), rien
     * ne change : il n'existe alors aucun bon de sortie deja visee par
     * l'agent dont deduire un visa automatique, la version reste creee au
     * statut {@code BROUILLON} et l'agent titulaire doit encore la signer
     * lui-meme via {@code FiphVersionService.signer()}.
     */
    private FIPH creerFiphEtVersionInitiale(Agent agent, OrigineFiph origine, BonSortie bonSortieDeclencheur,
                                             LocalDate lundiDeLaSemaine, Utilisateur auteur) {
        FIPH fiph = new FIPH();
        fiph.setAgent(agent);
        fiph.setService(agent.getService());
        fiph.setOrigine(origine);
        fiph.setBonSortie(bonSortieDeclencheur);
        if (origine == OrigineFiph.MANUELLE) {
            fiph.setCreePar(auteur);
        }
        fiph.setAnnee(lundiDeLaSemaine.get(IsoFields.WEEK_BASED_YEAR));
        fiph.setMois(lundiDeLaSemaine.getMonthValue());
        fiph.setNumeroSemaine(lundiDeLaSemaine.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        fiph.setDateDebutPeriode(lundiDeLaSemaine);
        fiph.setDateFinPeriode(lundiDeLaSemaine.with(DayOfWeek.SUNDAY));
        StatutFiphVersion statutInitial = origine == OrigineFiph.BON_SORTIE
                ? StatutFiphVersion.SIGNEE : StatutFiphVersion.BROUILLON;
        fiph.setStatut(statutInitial);
        fiph = fiphRepository.save(fiph);

        FIPHVersion version = new FIPHVersion();
        version.setFiph(fiph);
        version.setNumeroVersion(1);
        version.setCreePar(auteur);
        version.setStatutVersion(statutInitial);
        if (origine == OrigineFiph.BON_SORTIE) {
            Signature visaAutomatique = signatureRepository.save(new Signature(TypeSignature.VISA_APPLICATIF,
                    "auto:agent:" + agent.getId() + ":bon-sortie:"
                            + (bonSortieDeclencheur != null ? bonSortieDeclencheur.getId() : "n/a"),
                    null));
            version.setSignatureEmetteur(visaAutomatique);
        }
        version = fiphVersionRepository.save(version);

        fiph.setVersionCourante(version);
        fiph = fiphRepository.save(fiph);

        auditService.enregistrer(EntiteAuditable.FIPH, fiph.getId(), auteur,
                origine == OrigineFiph.BON_SORTIE ? TypeActionAudit.FIPH_AUTO_GENEREE : TypeActionAudit.CREATION,
                null, fiphMapper.toDto(fiph), null, statutInitial.name());
        if (origine == OrigineFiph.BON_SORTIE) {
            // Trace separement le visa automatique de l'agent (RG-FIPH-017 :
            // toute action doit rester individuellement identifiable dans
            // l'historique, meme lorsqu'elle est acquise d'office plutot que
            // saisie manuellement).
            auditService.enregistrer(EntiteAuditable.FIPH_VERSION, version.getId(), auteur, TypeActionAudit.SIGNATURE,
                    StatutFiphVersion.BROUILLON.name(),
                    "Visa automatique de l'agent titulaire " + agent.getMatricule()
                            + " (deja acquis via le visa du bon de sortie declencheur)",
                    StatutFiphVersion.BROUILLON.name(), StatutFiphVersion.SIGNEE.name());
        }
        return fiph;
    }

    /** RG-VER-001 a 007 : nouvelle version chainee, contenu copie de la precedente, statut BROUILLON. */
    FIPHVersion creerVersionSuivante(FIPH fiph, FIPHVersion versionPrecedente, Utilisateur auteur, String motif) {
        FIPHVersion nouvelle = new FIPHVersion();
        nouvelle.setFiph(fiph);
        nouvelle.setNumeroVersion(versionPrecedente.getNumeroVersion() + 1);
        nouvelle.setCreePar(auteur);
        nouvelle.setMotifModification(motif);
        nouvelle.setVersionPrecedente(versionPrecedente);
        nouvelle.setStatutVersion(StatutFiphVersion.BROUILLON);
        nouvelle = fiphVersionRepository.save(nouvelle);

        // RG-VER-003 : la version precedente n'est ni supprimee ni modifiee -
        // ses lignes de pointage sont COPIEES (nouvelles lignes, nouvel id),
        // jamais reattachees a la nouvelle version.
        for (Pointage ancienne : pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(versionPrecedente.getId())) {
            Pointage copie = new Pointage();
            copie.setFiphVersion(nouvelle);
            copie.setJourSemaine(ancienne.getJourSemaine());
            copie.setDatePointage(ancienne.getDatePointage());
            copie.setHeuresNormales(ancienne.getHeuresNormales());
            copie.setHeuresSup(ancienne.getHeuresSup());
            copie.setAffectationMission(ancienne.getAffectationMission());
            copie.setService(ancienne.getService());
            pointageRepository.save(copie);
        }

        fiph.setVersionCourante(nouvelle);
        fiph.setStatut(StatutFiphVersion.BROUILLON);
        fiphRepository.save(fiph);

        auditService.enregistrer(EntiteAuditable.FIPH_VERSION, nouvelle.getId(), auteur,
                TypeActionAudit.CREATION_VERSION, versionPrecedente.getNumeroVersion(), nouvelle.getNumeroVersion(),
                versionPrecedente.getStatutVersion().name(), StatutFiphVersion.BROUILLON.name());
        return nouvelle;
    }

    /** Ajoute ou met a jour (idempotent) la ligne de pointage correspondant a la date du bon de sortie. */
    private void ajouterOuMettreAJourPointage(FIPHVersion version, BonSortie bonSortie) {
        LocalDate date = bonSortie.getDateSortie();
        Pointage pointage = pointageRepository.findByFiphVersion_IdAndDatePointage(version.getId(), date)
                .orElseGet(Pointage::new);
        pointage.setFiphVersion(version);
        pointage.setJourSemaine(JourSemaine.depuis(date.getDayOfWeek()));
        pointage.setDatePointage(date);
        pointage.setAffectationMission(bonSortie.getAffectationMission());
        pointage.setService(null);
        // Les heures normales/supplementaires ne sont JAMAIS deduites
        // automatiquement d'un seul bon de sortie (point non tranche §29.1,
        // "les heures d'un seul bon de sortie ne suffisent pas a determiner
        // heures normales / heures supplementaires") : elles restent a zero
        // jusqu'a completion explicite (RG-FIPH-009/010, voir FiphVersionService).
        if (pointage.getId() == null) {
            pointage.setHeuresNormales(java.math.BigDecimal.ZERO);
            pointage.setHeuresSup(java.math.BigDecimal.ZERO);
        }
        pointageRepository.save(pointage);
    }

    /** Recalcule les totaux d'une version a partir de l'ensemble de ses lignes de pointage. */
    void recalculerTotaux(FIPHVersion version) {
        List<Pointage> pointages = pointageRepository.findByFiphVersion_IdOrderByDatePointageAsc(version.getId());
        java.math.BigDecimal totalHN = pointages.stream().map(Pointage::getHeuresNormales)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalHS = pointages.stream().map(Pointage::getHeuresSup)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        version.setTotalHN(totalHN);
        version.setTotalHS(totalHS);
        fiphVersionRepository.save(version);
    }

    /** Lundi de la semaine ISO 8601 (annee_semaine, numero_semaine) donnee. */
    private LocalDate lundiDeLaSemaineIso(int anneeSemaine, int numeroSemaine) {
        return LocalDate.of(anneeSemaine, 1, 4)
                .with(IsoFields.WEEK_BASED_YEAR, anneeSemaine)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, numeroSemaine)
                .with(DayOfWeek.MONDAY);
    }

    private boolean estRoleVisionGlobale(String code) {
        return CodeRoleMetier.RH.name().equals(code)
                || CodeRoleMetier.DIRECTION.name().equals(code)
                || CodeRoleMetier.ADMINISTRATEUR.name().equals(code)
                || CodeRoleMetier.SUPER_ADMINISTRATEUR.name().equals(code);
    }

    boolean estRoleGestionnaire(String code) {
        return CodeRoleMetier.CHARGE_AFFAIRES.name().equals(code)
                || CodeRoleMetier.PERSONNE_HABILITEE.name().equals(code);
    }

    /** RG-HAB-003 / RG-FIPH-010 : creation/modification bornee au perimetre (service) de l'habilitation active. */
    void verifierPerimetreGestionnaire(Utilisateur auteur, Agent agent) {
        Long serviceAgentId = agent.getService().getId();
        boolean habilite = habilitationRepository.findByUtilisateur_IdAndActifTrue(auteur.getId()).stream()
                .anyMatch(h -> estRoleGestionnaire(h.getRoleMetier().getCode())
                        && h.getService() != null && h.getService().getId().equals(serviceAgentId));
        if (!habilite) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a gerer les FIPH des agents de ce service.");
        }
    }

    public FIPH chargerFiph(Long id) {
        return fiphRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("FIPH", id));
    }

    /**
     * Controle anti-IDOR (RG-SEC-002, section 26.5) applique a toute
     * consultation d'une FIPH par identifiant : un identifiant syntaxiquement
     * valide ne doit jamais, a lui seul, autoriser l'acces (RG-DOC-007).
     * Accorde l'acces en lecture au titulaire de la FIPH, a tout detenteur
     * d'une habilitation active sur le service concerne (Charge d'Affaires,
     * personne habilitee, Responsable d'activite - section 14, colonne
     * "Consulter") et aux roles a vision globale (RH, Direction,
     * Administrateur).
     *
     * <p><strong>Hypothese documentee</strong> (le document source n'est pas
     * plus precis pour le Responsable d'activite que "FIPH en attente a son
     * niveau", section 14) : cette methode retient, pour la lecture
     * uniquement, l'appartenance au meme service plutot qu'une restriction
     * supplementaire au statut de la version courante - un Responsable
     * d'activite verra ainsi, en lecture seule, l'ensemble des FIPH de son
     * service et pas seulement celles actuellement a son niveau. Ce
     * elargissement ne retire aucun droit accorde par le texte (les FIPH
     * concernees lui deviennent de toute facon visibles au fil du circuit de
     * validation) et evite d'introduire une regle d'acces plus fine que ce
     * que le texte source specifie explicitement.
     */
    public void verifierPerimetreLecture(Utilisateur lecteur, FIPH fiph) {
        if (fiph.getAgent().getUtilisateur() != null && fiph.getAgent().getUtilisateur().getId().equals(lecteur.getId())) {
            return;
        }
        Long serviceFiphId = fiph.getService().getId();
        boolean autorise = habilitationRepository.findByUtilisateur_IdAndActifTrue(lecteur.getId()).stream()
                .anyMatch(h -> estRoleVisionGlobale(h.getRoleMetier().getCode())
                        || (h.getService() != null && h.getService().getId().equals(serviceFiphId)));
        if (!autorise) {
            throw new ForbiddenOperationException(
                    "Vous n'etes pas habilite a consulter cette FIPH (hors de votre perimetre).");
        }
    }
}
