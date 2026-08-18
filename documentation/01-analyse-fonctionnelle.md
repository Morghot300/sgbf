# Analyse fonctionnelle — Système de gestion des Bons de sortie et des FIPH (SGBF)

**Source** : `Analyse_Conception_Systeme_Gestion_FIPH.docx` (SNEF Cameroun SA — Groupe SNEF, Cellule Analyse & Conception SI, 11 août 2026), complété par les deux présentations de vulgarisation du même processus (`Presentation_Processus_BonSortie_FIPH_Complete.docx`, `..._Flux.docx` — aucune règle nouvelle, confirment le contenu ci-dessous en langage non technique).

Ce document restitue l'analyse imposée par le point 6 du brief : il ne remplace pas le document source (qui reste la référence de traçabilité, cité ici par ses numéros de section et ses codes de règles RG-*), il en confirme la compréhension avant tout développement.

---

## A. Synthèse fonctionnelle

Le système remplace un circuit papier reliant deux documents jusqu'ici indépendants :

- le **Bon de sortie VH et personnel**, qui trace les déplacements de véhicule et de personnel ;
- la **FIPH** (Fiche Individuelle de Pointage Hebdomadaire), qui trace le temps de travail hebdomadaire de chaque agent.

Le changement structurant : **la validation d'un bon de sortie devient le fait générateur automatique et exclusif de toute FIPH rattachée à une mission**. La FIPH est créée au statut `BROUILLON`, préremplie sans ressaisie, puis suit un cycle : complément optionnel → signature de l'agent → soumission → validation à 3 niveaux (Chargé d'Affaires, Responsable d'activité, Direction) → statut final `VALIDEE_DEFINITIVEMENT`, immuable. Toute correction post-validation crée une nouvelle version plutôt que de modifier l'existant (non-répudiation).

Deux extensions accompagnent ce cœur de processus :
1. **Missions et interruptions** — une mission peut être interrompue avant son terme et donner lieu à une réaffectation, sans jamais supprimer l'historique (séparation Mission / AffectationMission, section 5-8 du document source).
2. **Personnes à bord** — chaque personne accompagnant l'émetteur d'un bon de sortie reçoit automatiquement son propre bon de sortie individuel et sa propre FIPH (section 9).

La RH dispose d'un accès global en lecture seule (section 25). L'impression du bon de sortie validé et le téléchargement PDF de la FIPH terminée sont des opérations de lecture pure, générées à la demande, jamais stockées (section 13).

## B. Acteurs et rôles (section 10, 16)

| Acteur | Peut créer/modifier une FIPH | Peut valider | Particularité |
|---|---|---|---|
| **Agent (émetteur)** | Non | Visa du bon de sortie (niv. 1) ; signature FIPH | Consulte uniquement ses propres documents |
| **Chargé d'Affaires (CA)** | Oui | Bon de sortie (niv. 2) ; FIPH niv. 2 | Responsable hiérarchique de l'agent ; jusqu'à 2 CA par FIPH, un seul suffit à valider (RG-FIPH-012) |
| **Personne habilitée du service** | Oui | Selon habilitation cumulée | Mêmes droits que le CA sur son périmètre, sans porter nécessairement le titre |
| **Responsable d'activité** | Non | FIPH niv. 3 | Intervient après le niveau 2 |
| **Direction** | Non | FIPH niv. 4 (définitive) | Dernier niveau ; informée des révisions post-validation |
| **RH** | Non (aucun droit d'écriture) | Non | Lecture seule globale, habilitation exclusive `CONSULTATION_GLOBALE` (RG-HAB-005) |
| **Administrateur** | Non (métier) | Aucune validation métier | Gère comptes, habilitations, référentiels ; principe du moindre privilège |

Note de conception héritée du document source : le rôle **« Agent de pointage »** de l'ancien système **disparaît** — sa fonction est reprise par le système (génération auto) et par le CA / la personne habilitée (création manuelle, complément). Un même compte peut cumuler des habilitations, mais **ne peut jamais valider à un niveau supérieur une FIPH qu'il a lui-même créée, complétée ou modifiée** (RG-HAB-004) — séparation des responsabilités appliquée côté serveur, jamais uniquement côté interface.

## C. Modules fonctionnels

1. **Authentification & habilitations** — comptes, rôles métier, habilitations (rôle × périmètre).
2. **Référentiels** — Service, Vehicule, Chantier, CodeHN, MotifInterruptionMission, Agent.
3. **Missions & affectations** — Mission, AffectationMission, interruption, réaffectation, chaînage historique.
4. **Bon de sortie** — création, visa, validation 2 niveaux, personnes à bord, impression.
5. **FIPH** — génération automatique, préremplissage, complément, signature, circuit de validation 3 niveaux, versionnement, téléchargement PDF.
6. **Audit & traçabilité** — journal unique polymorphe, non-répudiation, export.
7. **Consultation RH** — lecture seule transverse.
8. **Administration** — gestion des utilisateurs, habilitations, référentiels.

## D. Fonctionnalités détaillées par module (extraits ; le détail complet est porté par les 39 cas d'utilisation de la section 16 du document source)

- **Bon de sortie** : créer, viser (niv. 1), valider (niv. 2), ajouter/retirer une personne à bord, imprimer (si `VALIDE`).
- **FIPH** : génération et préremplissage automatiques (déclenchés par RG-BS-007/RG-FIPH-001), création manuelle (origine `MANUELLE`, Code Service), compléter, modifier (avant signature), signer, soumettre, valider (niv. 2/3/4), rejeter/retourner pour correction (**proposé**, RG-FIPH-026/027), créer une nouvelle version (post-validation), télécharger le PDF (si `VALIDEE_DEFINITIVEMENT`).
- **Mission** : affecter un agent, interrompre (motif obligatoire), réaffecter, consulter l'historique par chaînage `affectationPrecedente` / `missionPrecedente` (aucune table d'historique séparée, RG-MIS-006).
- **Audit** : consultation filtrée par entité, export CSV/PDF (Direction, RH, Administrateur).

## E. Workflow (section 12)

```
Agent crée BS → Visa agent (niv.1) → Validation CA (niv.2, RG-BS-003)
      → [Système] Génération auto FIPH (BROUILLON) + préremplissage (RG-FIPH-001/003)
      → Complément optionnel (CA / personne habilitée, RG-FIPH-009/010)
      → Signature émetteur (RG-FIPH-019, condition obligatoire avant soumission)
      → Soumission → Validation niv.2 (1 des 2 CA, RG-FIPH-012)
      → Validation niv.3 (Responsable d'activité, RG-FIPH-013)
      → Validation niv.4 (Direction, RG-FIPH-014) → VALIDEE_DEFINITIVEMENT (figée)
```

Machine d'états FIPH (section 12.3) : `BROUILLON → EN_COMPLEMENT → SIGNEE → SOUMISE → VALIDEE_NIVEAU_2 → VALIDEE_NIVEAU_3 → VALIDEE_DEFINITIVEMENT`, avec branches proposées `REJETEE`, `RETOUR_POUR_CORRECTION`, `ANNULEE`, `EN_REVISION` (voir section I).

Scénarios d'interruption de mission (section 6.4, 27.3) — traitement différencié selon le statut FIPH au moment de l'interruption :
| Statut FIPH | Traitement |
|---|---|
| BROUILLON / EN_COMPLEMENT | Bascule silencieuse des jours futurs sur la nouvelle affectation, jours passés inchangés (RG-FIPH-022) |
| SIGNEE / SOUMISE | Invalidation des validations obtenues, retour à `EN_COMPLEMENT`, nouvelle signature + soumission requises (RG-FIPH-023) |
| VALIDEE_DEFINITIVEMENT | Jamais modifiée en place ; nouvelle `FIPHVersion` avec revalidation intégrale (RG-FIPH-024, RG-VER-007) |

Génération pour les personnes à bord (section 9.3, 9.7) : associations `BonSortiePersonne` créées avant validation du principal (aucun document généré) ; à la validation du principal (`RG-BS-003`), génération atomique **par personne** (RG-PAB-002, section 9.6) du bon de sortie individuel puis de la FIPH ; ajout ultérieur déclenche la même chaîne immédiatement (RG-PAB-006).

## F. Règles métier — inventaire (le détail normatif complet reste le document source, sections 11 et 20.1 ; reproduit ici par famille, non par règle, pour éviter la duplication)

| Famille | Nombre | Portée |
|---|---|---|
| RG-MIS (missions/affectations) | 8 | Interruption, non-écrasement du code mission, historisation |
| RG-BS (bon de sortie) | 7 | Rattachement, validation 2 niveaux, déclenchement FIPH |
| RG-FIPH (FIPH) | 25 (001-025) | Unicité, double origine, préremplissage, séquencement des validations, immuabilité |
| RG-HAB (habilitations) | 6 | Composition, cumul, séparation des responsabilités, exclusivité RH |
| RG-VER (versionnement) | 7 | Immuabilité post-validation, motif obligatoire, revalidation intégrale |
| RG-DOC (impression/export) | 8 | Conditions d'impression/téléchargement, neutralité (lecture pure), nommage |
| RG-PAB (personnes à bord) | 9 | Association, déduplication, génération automatique |
| RG-SEC (sécurité/concurrence) | 3 | Verrouillage optimiste, anti-IDOR, assainissement des entrées |
| RG-FIPH proposées (026-029) | 4 | Rejet, retour pour correction, annulation, suppléance — **non confirmées**, voir section I |

Contraintes d'intégrité base de données explicitement demandées (section 20.1) : exclusivité Code Mission/Code Service en `CHECK`, unicité `(agent_id, numero_semaine, annee)` sur `fiph`, `motif_modification` obligatoire si `numero_version > 1`, interdiction de `DELETE` sur `validation`/`signature`/`evenement_audit`/`affectation_mission`, interdiction d'`UPDATE` sur les colonnes d'identité de `affectation_mission` après création, `lock_version` optimiste sur `bon_sortie` et `fiph_version`.

## G. Données — entités principales et relations (sections 17-20)

Entités confirmées : `Utilisateur`, `Agent`, `RoleMetier`, `Habilitation`, `Service`, `Vehicule`, `BonSortie`, `BonSortiePersonne`, `Mission`, `AffectationMission`, `MotifInterruptionMission`, `Chantier`, `CodeHN`, `FIPH`, `FIPHVersion`, `Pointage`, `Validation`, `Signature`, `EvenementAudit`.

Relations clés :
- `Mission (1) — (0..n) AffectationMission` ; `Agent (1) — (0..n) AffectationMission` ; réflexive `Mission.missionPrecedente (0..1)` et `AffectationMission.affectationPrecedente (0..1)` — chaîne d'historique sans table dédiée.
- `AffectationMission (1) — (0..n) BonSortie`, `(1) — (0..n) FIPH`, `(1) — (0..n) Pointage` (granularité journalière, section 6.4).
- `BonSortie (principal, 1) — (0..n) BonSortiePersonne — (0..1) BonSortie (individuel)`, `— (0..1) FIPH`.
- `FIPH (1) — (0..n) FIPHVersion`, `FIPH.versionCourante → FIPHVersion` ; `FIPHVersion.versionPrecedente` réflexive.
- `Pointage` référence **exclusivement** `affectationMission` OU `codeService`, jamais les deux, jamais aucun (RG-FIPH-007, CHECK en base).
- `EvenementAudit` : table unique polymorphe (`entiteType`, `entiteId`) remplaçant les anciennes `HistoriqueWorkflow` + `JournalAudit`.

Choix de conception structurants explicitement justifiés dans le document source, repris tels quels (aucune réinterprétation) : FIPH/FIPHVersion séparées (immuabilité sans duplication totale), Mission/AffectationMission séparées (réaffectation sans perte du code), un seul `BonSortie` avec attribut `origine` plutôt que deux classes, aucune entité persistée pour les documents générés (PDF régénéré à la demande).

## H. Sécurité (section 26)

- **Authentification** : authentification simple — identifiant (ou e-mail) et mot de passe, en un seul appel, sans étape supplémentaire (décision explicite du 2026-08-17, qui annule et remplace un second facteur par code e-mail mis en place puis retiré le même jour ; voir section L « Suppression de l'authentification à deux facteurs » ci-dessous pour le détail complet). Cette décision remplace également l'exigence initiale du document source de MFA « obligatoire pour les rôles valideurs, recommandé pour les autres » (section 26.1), explicitement écartée par le donneur d'ordre. Fédération LDAP/SSO **si disponible** (non confirmée, voir section I) ; expiration de session par inactivité (durée de vie courte du jeton d'accès + rotation du jeton de rafraîchissement).
- **Autorisation** : RBAC + périmètre (Habilitation = rôle × service), contrôlé côté serveur systématiquement, jamais côté interface seule ; séparation stricte création/validation (RG-HAB-004).
- **Intégrité** : `CHECK` en base pour l'exclusivité Code Mission/Code Service ; append-only sur `validation`, `signature`, `evenement_audit`, `affectation_mission` ; empreinte SHA-256 sur chaque `FIPHVersion` validée définitivement (RG-VER-006) ; interdiction technique d'`UPDATE` sur une version figée.
- **Anti-IDOR** : tout accès à une ressource identifiée (FIPH, FIPHVersion, BonSortie) vérifié côté serveur contre l'habilitation active de l'utilisateur ; refus = 403 journalisé, jamais 404 (RG-SEC-002).
- **Validation des entrées** : limite de longueur + assainissement systématique sur tout champ texte libre ; accès aux données exclusivement par requêtes paramétrées/ORM (élimination de l'injection SQL par construction) ; échappement systématique à l'affichage et à la génération documentaire.
- **Concurrence** : verrouillage optimiste (`lockVersion`) sur `bon_sortie` et `fiph_version`, conflit signalé explicitement, aucune fusion automatique (RG-SEC-001, section 26.7).
- **Journalisation** : tentatives d'accès refusées journalisées séparément du journal fonctionnel ; horodatage exclusivement serveur.

## I. Points ambigus ou non confirmés (le document source les recense déjà en section 29 ; reproduits ici avec la décision de conception retenue pour ne pas bloquer le développement, conformément au point 20 du brief — **aucune de ces décisions n'altère une règle métier confirmée, toutes restent réversibles et clairement isolées**)

| # | Point non confirmé (section source) | Décision retenue pour le développement | Réversibilité |
|---|---|---|---|
| 1 | Statuts/règles « proposées » (REJETEE, RETOUR_POUR_CORRECTION, ANNULEE, EN_REVISION, RG-FIPH-026 à 029) — §11.9, §12.3 | **Implémentées telles que proposées par le document source** (ce ne sont pas des règles inventées : elles figurent déjà, explicitement marquées « proposées », avec leur justification) ; sans elles, le workflow n'aurait aucune porte de sortie pour une FIPH refusée. Isolées dans le code par un commentaire `// RG proposée — à confirmer`. | Oui — retirer un statut/une transition n'affecte pas le noyau confirmé |
| 2 | Champs « Lieu » et « Motif de sortie » du BS → aucune correspondance FIPH certaine — §4.2 | Non recopiés automatiquement vers la FIPH ; `motifSortie` reste consultable sur le BS uniquement | Oui |
| 3 | Fiabilité de la résolution du matricule par nom/prénom (homonymie) — §4.2, §29.1 | Résolution appuyée sur `Agent.matricule` uniquement, jamais sur texte libre (RG-PAB-001) ; en cas d'homonymie, sélection explicite obligatoire dans l'UI, jamais de résolution automatique silencieuse | Oui |
| 4 | Champ `LT` obligatoire si VH personnel ? — §29.1 | Facultatif par défaut (le document ne l'impose pas) | Oui — passage en `NOT NULL` conditionnel possible |
| 5 | Niveau de preuve de signature (visa simple vs signature électronique qualifiée) — §22, §23, §29.1 | Visa applicatif horodaté serveur + IP en phase 1 (traçabilité, non-répudiation technique) ; architecture `Signature.type` prête pour une signature qualifiée ultérieure. **Point à arbitrer avec la Direction juridique**, explicitement signalé comme tel par le document source lui-même (droit camerounais, loi n°2010/012) | Oui — extension, pas de rupture |
| 6 | Délégation/suppléance d'un valideur absent — §29.1, §29.2 | Non implémentée en phase 1 (aucune spécification exploitable) ; l'absence de spécification est documentée comme limite connue | N/A — hors périmètre déclaré |
| 7 | Durée de conservation/archivage légale — §26.4, §29.1 | Archivage à froid immuable prévu au niveau architecture, sans durée codée en dur (paramétrable) | Oui |
| 8 | Personnes à bord : agent RH obligatoire ou externe admis ? — §29.3 | Phase 1 : uniquement des `Agent` du référentiel (le document l'exige pour la déduplication, §9.2) ; pas de gestion de « visiteur » | Oui — extension possible |
| 9 | Plafond de personnes à bord — §29.3 | Aucun plafond technique imposé (non spécifié) | Oui |
| 10 | Revalidation intégrale vs allégée après interruption mineure — §29.2, §21 | Revalidation intégrale par défaut, conforme à RG-VER-004/RG-FIPH-024 (règle confirmée, pas une hypothèse) | — |
| 11 | Notification systématique de la Direction sur interruption — §29.2 | Cas d'utilisation « Notifier la Direction » implémenté uniquement pour une interruption touchant une FIPH déjà validée définitivement (le document le propose ainsi, §16) | Oui |
| 12 | Incohérence de nommage « SNEF Cameroun SA » (document d'analyse) vs « Omnium Service » (présentations, en-tête FIPH) | Traité comme deux libellés internes coexistants (Omnium Service = entité/activité au sein du Groupe SNEF portant le pointage) ; nom d'application neutre retenu : **SGBF**. N'affecte aucune règle métier. | Oui — simple paramètre de configuration `app.organisation.nom` |

Tous les autres éléments (rôle « chauffeur » distinct, heure de retour prévue/réelle, champ Observations) sont explicitement listés comme non tranchés en §29.4 et ne sont donc **pas modélisés** en phase 1, conformément à la consigne de ne rien inventer.

## J. Modification du mécanisme MFA (décision du 2026-08-17)

Le second facteur d'authentification a été entièrement revu à la demande de l'utilisateur, en remplacement — et non en complément — du mécanisme précédent. Aucun système MFA parallèle n'a été conservé.

**Ancien fonctionnement (retiré)** : un indicateur par compte (`Utilisateur.mfaActif`) rendait le second facteur optionnel ; `HabilitationService` refusait seulement d'attribuer un rôle valideur à un compte sans MFA actif.

**Nouveau fonctionnement** : toute connexion, pour tout compte, franchit systématiquement deux étapes — mot de passe, puis code à 6 chiffres envoyé par e-mail, avec renvoi possible (anti-abus). `POST /api/auth/login` ne délivre plus jamais de jeton directement, y compris en cas de mot de passe correct.

| Élément | Avant | Après |
|---|---|---|
| **Entité** `Utilisateur` | Portait `mfaActif` (colonne `mfa_actif`) | Colonne supprimée (migration `V6__mfa_obligatoire.sql`) |
| **Entité** `OtpChallenge` | `id`, `utilisateur`, `codeHash`, `dateExpiration`, `tentatives`, `consomme` | + `nombreRenvois`, `dateDernierEnvoi`, `invalide` (renvoi et invalidation croisée entre tentatives) |
| **Service** `EmailOtpService` | Génération/vérification simple | + `renvoyer()` (anti-abus : délai minimal, plafond de renvois) ; `verifier()` distingue désormais code incorrect / expiré / déjà utilisé / trop de tentatives (exceptions dédiées `MfaException`, au lieu d'un simple `Optional` vide) ; toute nouvelle tentative de connexion invalide les challenges non consommés précédents du même compte |
| **Service** `HabilitationService` | `validerMfaSiRoleValideur()` bloquait l'attribution d'un rôle valideur si MFA inactif | Méthode supprimée (sans objet : le MFA n'est plus jamais inactif) |
| **Contrôleur** `AuthController` | `login()` bifurquait (jeton direct si `mfaActif=false`) | `login()` renvoie toujours un `challengeId`, jamais de jeton ; nouvel endpoint `POST /api/auth/mfa/renvoyer` |
| **Contrôleur** `UtilisateurController` | `PUT /api/utilisateurs/{id}/mfa/{actif}` | Endpoint supprimé |
| **DTO** | `CreerUtilisateurRequest.mfaActif`, `UtilisateurDto.mfaActif`, `UtilisateurCourantDto.mfaActif`, `LoginResponse.mfaRequis/jetonAcces` | Champs retirés ; `LoginResponse` simplifié à `{challengeId, expiresInSecondes}` |
| **Config Spring Security** | `/api/auth/mfa/verifier` seul public | + `/api/auth/mfa/renvoyer` ajouté aux routes publiques (`SecurityConfig`) |
| **Config applicative** | `app.security.mfa.otp-*` | + `app.security.mfa.resend-delai-secondes` (30s), `resend-max` (3) |
| **React** | `LoginPage` : bifurcation directe si pas de MFA | Toujours deux écrans ; ajout bouton « Renvoyer le code » (avec compte à rebours local), compte à rebours d'expiration, bouton « Retour à la connexion » ; `AuthContext` expose `renvoyerCode`, `annulerVerification`, `expirationCodeMs` |
| **Règle métier devenue obsolète** | Section 26.1 du document source (« MFA obligatoire pour les rôles valideurs, recommandé pour les autres ») | Remplacée par une exigence plus stricte et uniforme (tous comptes) — décision utilisateur du 2026-08-17, documentée ici comme override explicite du document source |

**Sécurité conservée** : RBAC et périmètre par habilitation inchangés (aucune des vérifications `verifierPerimetreGestionnaire`/`verifierAutoServiceOuGestionnaire` des modules Mission et Bon de sortie n'a été touchée) ; un jeton d'accès reste strictement impossible à obtenir sans avoir franchi les deux étapes (vérifié par test — voir ci-dessous, scénario 8).

**Tests** : suite d'intégration `AuthentificationDeuxEtapesIT` (HTTP réel via MockMvc, base MySQL de test dédiée, `EmailService` remplacé par un mock capturant le code envoyé — jamais un raccourci de production) couvrant les 8 scénarios demandés : connexion normale, mauvais mot de passe, mauvais code, code expiré, code déjà utilisé, renvoi de code, limitation des tentatives, impossibilité de contourner la vérification. **8/8 réussis.** Vérifié également en conditions réelles via le frontend (Chrome/navigateur intégré) : étape mot de passe, déclenchement systématique du second facteur, restitution fidèle du message d'erreur serveur en cas d'échec d'envoi (503, faute de compte SMTP réel configuré à ce stade).

## K. Module Audit, impression, export PDF, droits RH (implémenté le 2026-08-17)

Couvre les sections 13 (impression et export documentaire, RG-DOC-001 à 008), 14 (matrice des habilitations), 24 (gestion de l'audit et de l'historique), 25 (droits RH en lecture seule) et 26.4/26.5 (journalisation des accès refusés, anti-IDOR) du document source. Aucune migration de schéma requise : repose entièrement sur `evenement_audit` (déjà en place depuis le socle) et sur une génération de documents strictement dynamique, sans aucune entité persistée (choix de conception documenté section 13.5 du document source, repris tel quel).

**Faille anti-IDOR corrigée (RG-SEC-002, section 26.5)** : avant ce travail, `GET /api/fiph/{id}`, `GET /api/fiph-versions/{id}` et `GET /api/bons-sortie/{id}` ne portaient strictement aucun contrôle de périmètre — un identifiant syntaxiquement valide suffisait à consulter n'importe quel document, indépendamment du service ou de l'agent concerné. Corrigé par `FiphService.verifierPerimetreLecture()` / `BonSortieService.verifierPerimetreLecture()` (nouvelles méthodes, package-private ou publiques selon les appelants), appliquées à chaque point de consultation par identifiant. Accorde l'accès au titulaire du document, à tout détenteur d'une habilitation active sur le service concerné (Chargé d'Affaires, personne habilitée, Responsable d'activité — hypothèse documentée dans le Javadoc de `verifierPerimetreLecture` : la lecture n'est pas restreinte au seul niveau de validation en attente, contrairement à la formulation littérale « FIPH en attente à son niveau » de la section 14 pour le Responsable d'activité, ce qui élargit sans jamais retirer un droit déjà accordé par ailleurs) et aux rôles à vision globale (RH, Direction, Administrateur).

**Impression / export PDF (RG-DOC-001 à 008)** : génération HTML → PDF à la demande via `openhtmltopdf` (dépendance déjà présente dans `pom.xml`, jusque-là inutilisée), jamais de fichier conservé. `GET /api/bons-sortie/{id}/pdf` (`BonSortiePdfService`) refuse tout document dont le statut n'est pas `VALIDE` (RG-DOC-001, HTTP 422) ; `GET /api/fiph-versions/{id}/pdf` (`FiphVersionPdfService`) refuse toute version dont le statut n'est pas `VALIDEE_DEFINITIVEMENT` (RG-DOC-003, HTTP 422). Chaque génération réussie est journalisée dans `evenement_audit` (`IMPRESSION_BON_SORTIE`, `TELECHARGEMENT_PDF_FIPH` — types déjà présents dans `TypeActionAudit` depuis un travail préparatoire antérieur, jusque-là jamais déclenchés) ; l'échec technique de rendu suit le circuit d'erreur générique (message générique côté client, trace complète journalisée côté serveur, RG-DOC section 13.7).

**Historique et export d'audit (section 24)** : `GET /api/audit/fiph/{fiphId}` (`AuditHistoryService`) reconstitue l'historique complet d'une FIPH en fusionnant les événements portés par la FIPH elle-même et par chacune de ses versions successives, triés chronologiquement — même périmètre de lecture que la consultation de la FIPH. L'export (`GET /api/audit/fiph/{fiphId}/export/csv|pdf`) est explicitement restreint par le texte source à la Direction, la RH et l'Administrateur (`@PreAuthorize`).

**Droits RH en lecture seule (RG-HAB-005, section 25)** : la charpente (habilitation `service` nullable pour un périmètre global, exclusivité RH dans `HabilitationService.validerExclusiviteRh`) existait déjà depuis le socle ; ce travail complète la lecture globale (FIPH, FIPHVersion, bon de sortie) et confirme qu'aucun endpoint d'écriture n'admet le rôle RH (`@PreAuthorize` par liste blanche de rôles, jamais par liste noire).

**Journalisation des accès refusés (section 26.4)** : `GlobalExceptionHandler` journalise désormais (log applicatif, délibérément distinct de la table `evenement_audit` — exigence explicite du texte source) toute tentative refusée, qu'elle provienne d'un contrôle grossier par rôle (`@PreAuthorize`) ou d'un contrôle fin de périmètre (`ForbiddenOperationException`), avec utilisateur, méthode HTTP et URI. Corrige au passage une référence Javadoc vers une classe `AccessDeniedAuditListener` jamais implémentée.

**Tests** : `AuditPdfDroitsRhIT` (HTTP réel via MockMvc) — anti-IDOR (bon de sortie, FIPH, FIPHVersion), gating PDF avant/après le statut requis (avec vérification de la signature binaire `%PDF-`), lecture globale RH avec refus d'écriture, historique d'audit non vide, export CSV/PDF réservé à Direction/RH/Administrateur (403 pour un Chargé d'Affaires). **1/1 réussi** au moment de son écriture, aux côtés du test de bout en bout FIPH et de la suite MFA alors en place (10/10 au total) — cette dernière a depuis été remplacée par `AuthentificationSimpleIT`, voir section L.

**Correction d'outillage notable** : `mvn test` sans argument ne découvrait, avant ce travail, **aucune** classe de test du projet — `maven-surefire-plugin` n'était pas explicitement configuré dans `pom.xml` et ses motifs d'inclusion par défaut (`**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`) ne couvrent jamais la convention `**/*IT.java` (réservée par convention à `maven-failsafe-plugin`, absent de ce module) utilisée par toutes les classes de test de ce projet. La commande réussissait silencieusement (code de sortie 0) en n'exécutant strictement rien, ce qui avait auparavant conduit à interpréter à tort d'anciens rapports Surefire laissés par des exécutions ciblées (`-Dtest=NomDeClasse`) comme la preuve d'une suite complète passante. Corrigé en déclarant explicitement `maven-surefire-plugin` avec `**/*IT.java` dans ses inclusions ; réexécution complète vérifiée après correction (10/10, horodatage des rapports confirmé).

## L. Suppression de l'authentification à deux facteurs (décision du 2026-08-17)

À la demande explicite de l'utilisateur, le second facteur d'authentification (code à usage unique envoyé par e-mail, mis en place et rendu obligatoire pour tous les comptes plus tôt le même jour — voir section J) a été **entièrement retiré**, sans mécanisme de repli ni indicateur d'activation par compte. L'authentification repose désormais uniquement sur l'identifiant (ou l'adresse e-mail) et le mot de passe, en un seul appel.

**Nouveau parcours** : `POST /api/auth/login` vérifie l'identifiant/e-mail et le mot de passe et émet immédiatement un jeton d'accès et un cookie de rafraîchissement — exactement le comportement qu'avait `POST /mfa/verifier` auparavant, désormais fusionné dans `login()`. Un mot de passe incorrect (`BadCredentialsException`) ou un compte désactivé/verrouillé (`AccountStatusException`, catégorie qui n'était auparavant jamais atteinte séparément de la vérification MFA) sont tous deux refusés et journalisés comme échec de connexion — le statut du compte reste vérifié à chaque tentative, la suppression du second facteur n'a retiré aucun contrôle d'autorisation.

**Nouveauté non liée au MFA, introduite à cette occasion** : la page de connexion demande désormais « Login ou e-mail », conformément à la spécification — `UserDetailsServiceImpl.loadUserByUsername` recherche par identifiant puis, à défaut, par adresse e-mail (`UtilisateurRepository.findByEmail`, déjà présent mais jusque-là inutilisé pour l'authentification).

| Élément | Avant (section J) | Après |
|---|---|---|
| Package `security.mfa` | `EmailOtpService`, `OtpChallenge` (entité + repository), `MfaException`, `EmailService`, `EmailEnvoiException` | Package supprimé intégralement |
| **DTO** `security.dto` | `LoginResponse{challengeId, expiresInSecondes}`, `MfaVerifyRequest`, `RenvoyerCodeRequest` | `LoginResponse` supprimé ; `login()` renvoie directement `TokenResponse{jetonAcces, expiresInSecondes}` (déjà utilisé par `/refresh`) ; `MfaVerifyRequest`/`RenvoyerCodeRequest` supprimés |
| **Contrôleur** `AuthController` | `login()` → challenge ; `verifierMfa()` ; `renvoyerCode()` | `login()` authentifie et émet le jeton en un seul appel ; `verifierMfa()`/`renvoyerCode()` supprimés |
| **Table** `otp_challenge` | Portait le code hashé, l'expiration, les tentatives, le compteur de renvois | Supprimée par migration `V9__suppression_mfa.sql` (migration immuable V2/V6 jamais modifiée rétroactivement — principe déjà appliqué pour `mfa_actif` en V6) ; `users.email` conservé intact (adresse de contact, identifiant alternatif de connexion) |
| **`TypeActionAudit`** | + `MFA_CODE_ENVOYE`, `MFA_VALIDATION_REUSSIE`, `MFA_VALIDATION_ECHEC` | Constantes retirées (colonne `evenement_audit.action` étant un simple `VARCHAR`, aucune migration nécessaire pour ce retrait) |
| **Config Spring Security** | `/api/auth/mfa/verifier`, `/api/auth/mfa/renvoyer` publics | Retirés de `SecurityConfig` ; seuls `/login`, `/refresh`, `/logout` restent publics |
| **Config applicative** | `spring.mail.*` (SMTP Gmail), `app.security.mfa.*` (TTL, longueur, tentatives, anti-abus renvoi) | Blocs retirés de `application.yml`, `application-local.yml(.example)`, `application-test.yml` |
| **Dépendance** `pom.xml` | `spring-boot-starter-mail` | Retirée (plus aucun consommateur : usage confirmé exclusivement MFA avant suppression) |
| **`@EnableScheduling`** (`SgbfBackendApplication`) | Justifié par la purge périodique des challenges MFA expirés (au sein d'`EmailOtpService`) | Retiré : aucune méthode `@Scheduled` ne subsiste dans le code une fois `EmailOtpService` supprimé |
| **React** `LoginPage` | Deux écrans (mot de passe, puis code) ; boutons « Renvoyer le code », « Retour à la connexion » ; comptes à rebours d'expiration/anti-abus | Écran unique : « Login ou e-mail » + « Mot de passe » + « Se connecter », redirection directe vers le tableau de bord |
| **React** `AuthContext` | `challengeMfaEnCours`, `expirationCodeMs`, `validerCodeMfa`, `renvoyerCode`, `annulerVerification` | Retirés ; `connexion()` authentifie et charge l'utilisateur courant en une seule séquence |
| **CSS** | `.indication-expiration`, `.message-info`, `.lien-secondaire` (styles de l'écran de code) | Retirées (plus aucun consommateur) |
| **Tests** | `AuthentificationDeuxEtapesIT` (8 scénarios MFA) | Supprimée ; remplacée par `AuthentificationSimpleIT` (voir ci-dessous) |

**Sécurité conservée** : RBAC et périmètre par habilitation strictement inchangés — aucune vérification de rôle, d'habilitation ou de périmètre (`verifierPerimetreGestionnaire`, `verifierPerimetreLecture`, RG-HAB-004, anti-IDOR RG-SEC-002) n'a été touchée par ce travail, qui porte exclusivement sur l'étape d'authentification elle-même. Un compte désactivé ou verrouillé reste strictement refusé (vérifié par test).

**Tests** : suite d'intégration `AuthentificationSimpleIT` (HTTP réel via MockMvc, base MySQL de test dédiée) couvrant explicitement les six scénarios demandés — connexion réussie par identifiant, connexion réussie par adresse e-mail, mot de passe incorrect, utilisateur inexistant, compte désactivé, compte verrouillé — ainsi que la conservation des autorisations par rôle après connexion (un compte sans habilitation reçoit 403 sur une action réservée à un rôle métier, un compte habilité passe le contrôle) et le rejet des API protégées pour un appel non authentifié ou porteur d'un jeton invalide. **9/9 réussis**, aux côtés des suites déjà en place (FIPH, audit/PDF/RH), soit **11/11** au total sur l'ensemble de la suite backend, réexécutée intégralement après retrait du MFA pour confirmer l'absence de régression sur les modules Mission, Bon de sortie et FIPH.

Vérifié également en conditions réelles, au-delà des tests automatisés : appel direct `curl` contre le backend en profil `local` (base `sgbf_db`) confirmant l'émission immédiate d'un jeton d'accès dès `POST /api/auth/login`, puis son acceptation par `GET /api/auth/me` — et, côté frontend, rendu visuel confirmé de l'écran de connexion réduit aux seuls champs « Login ou e-mail » / « Mot de passe » / « Se connecter » (compilation TypeScript strict sans erreur). Le clic-à-clic complet dans le navigateur intégré à l'environnement de développement n'a pu être mené à son terme : le bac à sable réseau de cet outil de prévisualisation ne peut pas atteindre le backend lancé localement (échec `ERR_CONNECTION_REFUSED` sur tout appel `/api/*` proxié par Vite, y compris après redémarrage du serveur de développement et essais de plusieurs adresses cibles) — limitation d'outillage de l'environnement, sans lien avec le code applicatif, déjà validé par ailleurs via les tests HTTP réels ci-dessus.
