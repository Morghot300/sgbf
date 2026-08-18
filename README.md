# SGBF — Système de Gestion des Bons de sortie et des FIPH

Application interne SNEF Cameroun SA — Groupe SNEF, remplaçant le circuit papier du Bon de sortie VH/personnel et de la Fiche Individuelle de Pointage Hebdomadaire (FIPH) par un flux numérique où la validation du bon de sortie déclenche automatiquement la génération de la FIPH.

> **Statut** : tous les modules métier du document source sont implémentés et vérifiés de bout en bout (backend + frontend) — voir [état d'avancement](#14-état-davancement) pour le détail exact de ce qui reste à faire avant une mise en production. Analyse complète : [documentation/01-analyse-fonctionnelle.md](documentation/01-analyse-fonctionnelle.md) et [documentation/02-architecture-technique.md](documentation/02-architecture-technique.md).

## 1. Présentation

Le système couvre : le bon de sortie (visa + validation, personnes à bord), la génération et le préremplissage automatiques de la FIPH, son complément, sa signature et son circuit de validation à trois niveaux (Chargé d'Affaires, Responsable d'activité, Direction), le versionnement post-validation, la gestion des missions et de leurs interruptions, les habilitations, l'audit non-répudiable, l'accès en lecture seule de la RH, l'impression/export documentaire (PDF, CSV), et une interface web complète pour l'ensemble de ces modules.

L'authentification est **simple** (identifiant ou e-mail + mot de passe, un seul appel) — un second facteur par code e-mail a été mis en place puis intégralement retiré le 2026-08-17 à la demande explicite du donneur d'ordre ; voir [analyse fonctionnelle §L](documentation/01-analyse-fonctionnelle.md).

## 2. Fonctionnalités

Voir la section D de [l'analyse fonctionnelle](documentation/01-analyse-fonctionnelle.md) pour le détail par module, et le [manuel utilisateur](documentation/manuel-utilisateur/) pour le mode d'emploi écran par écran :

- [Se connecter](documentation/manuel-utilisateur/01-connexion.md)
- [Bon de sortie](documentation/manuel-utilisateur/02-bon-de-sortie.md)
- [FIPH](documentation/manuel-utilisateur/03-fiph.md)
- [Missions et affectations](documentation/manuel-utilisateur/04-missions.md)
- [Administration](documentation/manuel-utilisateur/05-administration.md)

## 3. Architecture

React (TypeScript) ↔ API REST Spring Boot (Java 21) ↔ MySQL 8. Détail complet : [documentation/02-architecture-technique.md](documentation/02-architecture-technique.md) ; référence de tous les points d'entrée REST : [documentation/documentation-technique/api-reference.md](documentation/documentation-technique/api-reference.md) ; schéma général : [documentation/diagrammes/01-architecture.md](documentation/diagrammes/01-architecture.md).

```
FIPH/
├── backend/              Spring Boot (Maven) — API REST
├── frontend/              React + TypeScript (Vite) — SPA
│   ├── src/styles/tokens.css   Système de tokens CSS (charte graphique)
│   └── src/assets/brand/       Logo source
├── tools/                Scripts Python de charte graphique (extraction de couleurs, audit WCAG, génération des dérivés du logo)
├── database/
│   ├── migrations/       Miroir des migrations Flyway (backend/src/main/resources/db/migration)
│   └── scripts/           Scripts de provisionnement (création DB/utilisateur)
├── documentation/
│   ├── manuel-utilisateur/       Un guide par module
│   ├── documentation-technique/  Référence API REST
│   ├── diagrammes/               Architecture, workflow bon de sortie → FIPH (Mermaid)
│   └── charte-graphique-note-validation.md
└── README.md
```

## 4. Prérequis

| Outil | Version utilisée | Vérifié |
|---|---|---|
| JDK | 21 (LTS) | ✅ |
| Apache Maven | 3.9.9 | ✅ (voir §7) |
| Node.js | 22.14.0 | ✅ (voir §12 si absent du `PATH`) |
| npm | 11.7.0 | ✅ |
| MySQL Server | 8.0.41 | ✅ |
| Python | 3.13+ avec Pillow | Optionnel — uniquement pour régénérer les dérivés du logo (`tools/`, voir [tools/README.md](tools/README.md)) |

## 5. Installation

```bash
git clone <repo> && cd FIPH
```

### Backend

```bash
cd backend
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml
# renseigner DB_PASSWORD, JWT_SECRET dans application-local.yml (jamais commit)
```

### Frontend

```bash
cd frontend
npm install
```

## 6. Configuration MySQL

La base `sgbf_db` et le compte applicatif `ITadmin` (privilèges limités à cette base uniquement, distinct de `root`) sont provisionnés par [database/scripts/00_create_db_and_user.sql](database/scripts/00_create_db_and_user.sql), à exécuter une fois avec un compte administrateur MySQL :

```bash
mysql -u root -p < database/scripts/00_create_db_and_user.sql
```

Les identifiants applicatifs sont ensuite fournis au backend exclusivement via `backend/src/main/resources/application-local.yml` (non versionné, profil Spring `local`) — jamais en clair dans le code ou la documentation utilisateur.

## 7. Lancement backend

Le script `mvn.cmd` officiel de l'installation Maven de ce poste échoue dans PowerShell (voir §12) ; utiliser le wrapper fourni :

```powershell
cd backend
.\mvnw.ps1 "-Dspring-boot.run.profiles=local" spring-boot:run
```

L'API est alors disponible sur `http://localhost:8080`, documentation OpenAPI sur `http://localhost:8080/api/docs`.

## 8. Lancement frontend

```bash
cd frontend
npm run dev
```

Application sur `http://localhost:5173` (proxy `/api` vers le backend en développement).

## 9. Comptes de démonstration (environnement de développement local)

Trois comptes existent dans la base de développement locale `sgbf_db` — créés ponctuellement pour les besoins des tests manuels de cette machine, **pas par une migration ou un script versionné** :

| Identifiant | Rôle | Périmètre |
|---|---|---|
| `admin` | Administrateur | — |
| `ca.mbarga` | Chargé d'Affaires | Service Travaux Publics |
| `agent.mbarga` | Aucun rôle métier | Agent simple |

Leurs mots de passe ne sont **jamais** documentés en clair ici, y compris pour un environnement de développement — un secret committé reste dans l'historique Git même après avoir été changé. Pour recréer des comptes de test en local, utiliser `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` dans `application-local.yml` (compte administrateur créé automatiquement au premier démarrage, voir `security/BootstrapAdminSeeder.java`), ou `POST /api/utilisateurs` une fois connecté avec ce compte.

## 10. Tests

Les tests d'intégration backend utilisent une base MySQL de test dédiée, distincte de `sgbf_db` (à créer une fois) :

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS sgbf_test_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON sgbf_test_db.* TO 'ITadmin'@'localhost';"
```

```bash
cd backend && .\mvnw.ps1 test
cd frontend && npm run test
```

État actuel, vérifié réellement (pas seulement compilé) :

| Suite | Résultat |
|---|---|
| Backend — `AuthentificationSimpleIT` (connexion par identifiant/e-mail, mauvais mot de passe, utilisateur inexistant, compte désactivé/verrouillé, autorisations par rôle conservées, API protégées sans jeton) | 9/9 |
| Backend — `FiphWorkflowIT` (bon de sortie → génération FIPH → complément → signature → soumission → validation aux 3 niveaux → nouvelle version) | 1/1 |
| Backend — `AuditPdfDroitsRhIT` (anti-IDOR, PDF conditionné au statut, lecture RH globale sans écriture, export CSV/PDF réservé) | 1/1 |
| Frontend — Vitest (`httpClient`, `StatutBadge` × 4 énumérations, `ProtectedRoute`/`RouteAvecRole`, `AuthContext`, `LoginPage`) | 42/42 |

## 11. Structure du projet

Voir §3 ci-dessus et le détail des paquets backend en [documentation/02-architecture-technique.md](documentation/02-architecture-technique.md).

## 12. Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `mvn` introuvable / erreur `Usage: java` | Maven absent du `PATH`, script `mvn.cmd` défaillant sur ce poste | Utiliser `backend\mvnw.ps1` |
| `npm`/`node` introuvable dans un terminal | Node installé via `nvm` (`C:\Users\Utilisateur\AppData\Local\nvm\v22.14.0`), absent du `PATH` par défaut de certains terminaux | Ajouter ce dossier au `PATH` de la session, ou utiliser un terminal où `nvm` a déjà été initialisé |
| `Communications link failure` au démarrage backend | Service `MySQL80` arrêté | `Start-Service MySQL80` (PowerShell administrateur) |
| Flyway échoue sur `V3__evenement_audit.sql` : *"You do not have the SUPER privilege and binary logging is enabled"* | MySQL refuse la création de `TRIGGER` à un compte sans `SUPER` tant que `log_bin_trust_function_creators` est désactivé | `SET PERSIST log_bin_trust_function_creators = 1;` en root **une seule fois** (déjà inclus dans `database/scripts/00_create_db_and_user.sql`) — ne jamais accorder `SUPER` à `ITadmin` pour contourner |
| Démarrage backend : `Unable to establish loopback connection` / `SocketException: Invalid argument: connect` | Bug d'environnement JDK 21 sur ce poste : le connecteur Tomcat NIO par défaut (`WEPollSelectorImpl`) échoue à créer son pipe de réveil interne via un socket de domaine Unix | Déjà contourné par `config/TomcatConfig.java`, qui force le connecteur NIO2 (`Http11Nio2Protocol`) |
| `Port 8080 was already in use` au lancement backend | Une instance précédente tourne encore en arrière-plan | Identifier le processus (`Get-NetTCPConnection -LocalPort 8080`) et l'arrêter avant de relancer |
| 401 sur tous les appels API | Token JWT expiré (15 min) | Le frontend déclenche automatiquement le rafraîchissement via le cookie `refresh` ; sinon se reconnecter |
| 403 sur une ressource dont l'identifiant est valide | Comportement attendu (anti-IDOR, RG-SEC-002) | Vérifier le périmètre de l'habilitation de l'utilisateur, pas l'existence de la ressource |
| `npm run lint` échoue avec *"ESLint couldn't find a configuration file"* | Aucun fichier de configuration ESLint n'a jamais été committé dans ce projet, malgré les dépendances présentes dans `package.json` | Non résolu — voir §14. `tsc -b` (exécuté par `npm run build`) reste le filet de sécurité de typage réel en attendant |

## 13. Sécurité

RBAC + périmètre par habilitation, contrôlé côté serveur uniquement ; JWT access court + refresh en cookie `httpOnly`/`Secure` ; authentification simple (identifiant/e-mail + mot de passe, sans étape supplémentaire — voir [manuel utilisateur](documentation/manuel-utilisateur/01-connexion.md) et [analyse fonctionnelle §L](documentation/01-analyse-fonctionnelle.md)) ; verrouillage optimiste (RG-SEC-001) ; anti-IDOR (RG-SEC-002, vérifié par test sur bon de sortie/FIPH/FIPHVersion) ; validation et assainissement systématiques des entrées (RG-SEC-003) ; journal d'audit append-only (garanti à la fois par l'application et par des déclencheurs base de données) ; journalisation technique de tout accès refusé (section 26.4). Détail complet en [documentation/01-analyse-fonctionnelle.md, section H](documentation/01-analyse-fonctionnelle.md#h-sécurité-section-26).

## 14. État d'avancement

**Fait et vérifié** : les 8 modules métier (socle/authentification, missions, bon de sortie, FIPH, audit/PDF/droits RH, frontend complet, charte graphique, tests) — voir le détail module par module dans [l'analyse fonctionnelle](documentation/01-analyse-fonctionnelle.md) (sections A à L).

**Limites connues, à traiter avant une mise en production réelle** :
- **Aucun dépôt Git n'est initialisé** dans ce répertoire à ce stade — tout le travail décrit ici vit uniquement sur le système de fichiers local.
- **Pas de configuration ESLint** malgré les dépendances installées (voir §12) — `tsc -b` (strict) reste le seul filet de sécurité de typage automatisé côté frontend.
- **Logo SNEF en PNG raster (389×129 px)**, pas en SVG vectoriel officiel — voir [note de validation charte graphique](documentation/charte-graphique-note-validation.md) pour le détail et les points à faire confirmer par la communication SNEF.
- **Fédération LDAP/SSO non implémentée** (le document source la demande « si disponible » — aucun annuaire d'entreprise accessible depuis l'environnement de développement).
- Aucun test de bout en bout piloté par navigateur (Playwright/Cypress) — la couverture actuelle est unitaire/intégration (backend via HTTP réel + base MySQL de test, frontend via Vitest + Testing Library).
