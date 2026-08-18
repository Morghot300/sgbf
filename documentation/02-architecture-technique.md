# Architecture technique — SGBF (Système de Gestion des Bons de sortie et FIPH)

## 1. Vue d'ensemble

```
┌─────────────────────────┐        HTTPS / REST JSON        ┌──────────────────────────────┐
│   Frontend (React + TS)  │  ───────────────────────────▶  │  Backend (Spring Boot 3 / 21) │
│   Vite, React Router,    │  ◀───────────────────────────  │  Controller → Service →       │
│   TanStack Query, RHF    │     JWT access (Authorization)  │  Repository → Entity (JPA)    │
└─────────────────────────┘     + refresh (cookie httpOnly)  └──────────────┬────────────────┘
                                                                              │ JDBC (HikariCP)
                                                                              ▼
                                                              ┌──────────────────────────────┐
                                                              │   MySQL 8.0 — schéma sgbf_db  │
                                                              │   Flyway (migrations versionnées) │
                                                              └──────────────────────────────┘
```

Application name : **SGBF**. Base : `sgbf_db`. Compte applicatif : `ITadmin` (privilèges limités à `sgbf_db`, sans droit `GRANT`, sans accès aux autres schémas — voir [database/scripts/00_create_db_and_user.sql](../database/scripts/00_create_db_and_user.sql)).

## 2. Backend — structure des paquets

```
com.snef.sgbf
├── config/            # SecurityConfig, CorsConfig, OpenApiConfig, JacksonConfig
├── security/          # JwtService, JwtAuthFilter, UserDetailsServiceImpl, PasswordConfig, AuthController
├── common/
│   ├── exception/      # ApiException hierarchy, GlobalExceptionHandler (RFC 7807)
│   ├── audit/           # EvenementAudit entity, AuditService, AuditController (historique/export)
│   ├── pdf/             # PdfRenderer (HTML -> PDF a la demande), HtmlUtils, DocumentPdf
│   └── validation/      # Validateurs personnalisés (ex: exclusivité code mission/service)
├── referentiel/        # Service, Vehicule, Chantier, CodeHN, MotifInterruptionMission
├── identite/            # Utilisateur, Agent, RoleMetier, Habilitation
├── mission/             # Mission, AffectationMission (+ interruption/réaffectation)
├── bonsortie/           # BonSortie, BonSortiePersonne, BonSortiePdfService
└── fiph/                # FIPH, FIPHVersion, Pointage, Validation, Signature, FiphVersionPdfService
```

Aucune entité `DocumentGenere` : la génération PDF (bon de sortie, FIPH) est une opération de lecture pure, sans aucun état persisté (voir §3, choix "Génération PDF à la demande").

Chaque module suit la même stratification : `*.controller` (REST, DTO in/out), `*.service` (logique métier, transactions), `*.repository` (Spring Data JPA), `*.entity` (JPA), `*.dto` (records Java), `*.mapper` (MapStruct).

## 3. Choix techniques justifiés

| Choix | Alternative écartée | Justification |
|---|---|---|
| Spring Boot 3.3.x / Java 21 (LTS déjà installé) | Java 17 | Version LTS la plus récente disponible sur le poste ; aucune dépendance du projet n'exige une version antérieure |
| MapStruct pour les mappers Entity↔DTO | Mapping manuel | Élimine le code répétitif sans magie runtime (génération à la compilation), cohérent avec l'exigence « pas de code dupliqué » |
| Flyway pour les migrations | Hibernate `ddl-auto=update` | Le brief exige des scripts SQL versionnés et des contraintes fortes (CHECK, triggers) que `ddl-auto` ne sait pas exprimer ; Flyway donne un historique auditable du schéma, cohérent avec l'exigence de non-répudiation |
| JWT stateless (access 15 min + refresh 7 j, refresh en cookie `httpOnly`/`Secure`/`SameSite=Strict`) | Session serveur classique | Architecture REST/SPA explicitement demandée (§9) ; le document source demande une « expiration de session après inactivité » — obtenue par la durée de vie courte de l'access token + rotation du refresh token, sans état serveur à répliquer |
Fédération LDAP/SSO non implémentée en phase 1 | Fédération LDAP/SSO immédiate | Aucun annuaire d'entreprise n'est accessible depuis ce poste de développement ; le document source dit lui-même « si disponible » (§26.1). L'authentification reste un `UserDetailsService` interne, remplaçable plus tard sans changer le reste du système (hypothèse documentée, réversible — point I.5) |
| Verrouillage optimiste (`@Version` JPA = `lockVersion`) | Verrou pessimiste | Exigé explicitement par RG-SEC-001 et justifié par le document source lui-même (§26.7) |
| MySQL triggers pour interdire l'UPDATE sur `fiph_version` figée et le DELETE sur les tables append-only | Contrôle applicatif seul | Le brief et le document source (§20.1, §26.3) demandent explicitement une double garantie base de données + application |
| Génération PDF à la demande (openhtmltopdf, gabarits HTML/CSS rendus en PDF), sans stockage fichier | PDFBox bas niveau ; table `DocumentGenere` | Choix de conception déjà tranché et justifié dans le document source (§13.5, approche A) — repris tel quel ; openhtmltopdf préféré à un dessin PDFBox bas niveau pour des gabarits tabulaires plus lisibles et plus faciles à faire évoluer |
| Authentification simple (identifiant/e-mail + mot de passe, un seul appel) | MFA par e-mail obligatoire pour tout compte ; avant cela, MFA conditionnel par compte (`mfaActif`) ; avant cela, TOTP (RFC 6238) | Trois décisions successives du 2026-08-17, chacune remplaçant entièrement la précédente (pas de coexistence) : TOTP envisagé puis remplacé par un code e-mail (compte Gmail de service), rendu obligatoire pour tous les comptes, puis intégralement supprimé le même jour à la demande explicite de l'utilisateur — voir analyse fonctionnelle §L pour le détail complet de la suppression |
| Profil Spring `local` (`application-local.yml`, non versionne) pour les secrets de dev | Bibliotheque tierce de chargement de `.env` | Testee et abandonnee (`spring-dotenv` ne resolvait pas fiablement les placeholders dans cet environnement) au profit d'un mecanisme Spring Boot natif, sans dependance supplementaire ni comportement tiers a diagnostiquer - solution explicitement prevue par le brief (section 4 : "variables d'environnement ou fichier de configuration local non versionne") |
| TypeScript pour le frontend | JavaScript | Modèle de domaine riche (19 entités, machine d'états à 11 statuts, RBAC fin) ; le typage statique réduit directement la classe d'erreurs la plus coûteuse ici (mauvais statut envoyé à une API sensible) — répond à l'exigence explicite de « robustesse » du brief |
| Vite + React Router + TanStack Query + React Hook Form + Zod | Create React App | CRA est déprécié ; Zod partage les schémas de validation avec les DTO backend (miroir, jamais source de vérité — la validation serveur reste seule autoritaire, §11 du brief) |
| Tests : JUnit 5 + Mockito + Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`) ; Vitest + React Testing Library | Testcontainers MySQL | Docker n'est pas installé sur ce poste ; les tests d'intégration backend utilisent une base MySQL de test dédiée (`sgbf_test_db`) plutôt qu'un conteneur, pour rester fidèles au moteur cible (H2 aurait un comportement CHECK/trigger différent de MySQL) |

## 4. Stratégie de gestion des erreurs

Toute erreur applicative retourne un corps JSON conforme à **RFC 7807 (Problem Details)** : `type`, `title`, `status`, `detail`, `instance`, plus un champ `errors[]` pour les violations de validation champ par champ. Un `@RestControllerAdvice` central traduit :
- `MethodArgumentNotValidException` → 400, détail par champ ;
- `AccessDeniedException` → 403, journalisé comme tentative refusée (§26.4) — jamais 404 (RG-SEC-002) ;
- `OptimisticLockingFailureException` → 409, message explicite invitant à recharger (§26.7) ;
- exceptions métier dédiées (`FiphDejaValideeException`, `IncoherenceAffectationException`, etc.) → 422 ;
- toute exception non prévue → 500 générique, sans fuite de détail technique, journalisée côté serveur.

## 5. Stratégie de validation

- **Backend, seul niveau autoritaire** (§11) : Bean Validation (`jakarta.validation`) sur les DTO d'entrée, contraintes `CHECK` en base pour les invariants critiques (exclusivité Code Mission/Code Service, motif obligatoire), validateurs métier dédiés dans la couche service pour les règles transverses (ex. RG-FIPH-025 cohérence affectation/pointage).
- **Frontend** : Zod (miroir des contraintes serveur) pour un retour immédiat à l'utilisateur — jamais considéré comme suffisant.

## 6. Stratégie de journalisation

- **Technique** (SLF4J + Logback, format JSON en production) : erreurs, performance, démarrage — aucune donnée métier sensible.
- **Fonctionnelle/audit** (`evenement_audit`, append-only, table dédiée) : toute action significative au sens de RG-FIPH-016, avec `entiteType`/`entiteId` polymorphes, `valeurAvant`/`valeurApres`, horodatage serveur exclusif. C'est la source unique de la non-répudiation (§23, §24).
- Séparation stricte des deux journaux : le premier sert au diagnostic, le second constitue une preuve.

## 7. Modèle relationnel — voir [documentation/diagrammes](diagrammes/) pour le détail entité par entité et [database/migrations](../database/migrations/) pour le DDL versionné correspondant.

## 8. Environnement de développement — état constaté sur ce poste

| Outil | État | Détail |
|---|---|---|
| JDK | ✅ prêt | 21.0.11 LTS, `JAVA_HOME` = `C:\Program Files\Java\jdk-21.0.11` |
| Maven | ✅ prêt (contournement appliqué) | 3.9.9 installé (`C:\Users\Utilisateur\Maven\apache-maven`), absent du `PATH`. Le script `mvn.cmd` officiel échoue silencieusement dans cet environnement (retombe sur `java` sans classpath) ; un wrapper fiable [`backend/mvnw.ps1`](../backend/mvnw.ps1) invoquant directement le lanceur Plexus Classworlds a été fourni et vérifié fonctionnel |
| Node.js / npm | ✅ prêt | v22.14.0 / npm 11.7.0, disponibles via `nvm` (`C:\Users\Utilisateur\AppData\Local\nvm\v22.14.0`), absents du `PATH` par défaut |
| MySQL Server | ✅ prêt | 8.0.41, service Windows `MySQL80` déjà démarré |
| Base applicative | ✅ créée | `sgbf_db`, utilisateur dédié `ITadmin` (privilèges limités, voir §9) |
| VS Code | ✅ installé | `AppData\Local\Programs\Microsoft VS Code` |
| Eclipse (JEE) | ✅ installé | `C:\Users\Utilisateur\eclipse\jee-2025-09` |
| MySQL Workbench | ✅ installé | `C:\Program Files\MySQL\MySQL Workbench 8.0` |
| Docker | ❌ absent | Tests d'intégration adaptés en conséquence (voir §3, ligne Tests) |
| Démarrage backend de bout en bout | ✅ vérifié le 2026-08-17 | Flyway (9 migrations), Hibernate, Spring Security, JWT, authentification simple, RBAC : testés via appels HTTP réels (login, `/auth/me`, endpoint protégé, endpoint réservé à l'Administrateur) |

Deux particularités de ce poste, sans impact sur un déploiement standard, contournées dans le code/scripts (jamais par un affaiblissement de la sécurité) :
- **MySQL refuse la création de `TRIGGER`** à un compte sans privilège `SUPER` tant que `log_bin_trust_function_creators` est désactivé (erreur 1419) — résolu par `SET PERSIST log_bin_trust_function_creators = 1` en root, une fois (`database/scripts/00_create_db_and_user.sql`), plutôt que d'accorder `SUPER` à `ITadmin`.
- **Le connecteur Tomcat NIO par défaut échoue au démarrage** sur ce poste (JDK 21 / Windows : `WEPollSelectorImpl` ne parvient pas à établir son pipe de réveil interne via socket de domaine Unix) — résolu en forçant le connecteur NIO2 (`config/TomcatConfig.java`), un connecteur Tomcat standard et pleinement supporté, pas un contournement fragile.

Aucune bibliothèque superflue n'est ajoutée : chaque dépendance backend/frontend listée dans les `pom.xml` / `package.json` du projet est justifiée dans le tableau §3 ci-dessus.

## 9. Sécurité des secrets

Les identifiants MySQL fournis dans le brief (`root`, `ITadmin`) sont des secrets de développement et **ne figurent dans aucun fichier versionné** : `backend/src/main/resources/application.yml` référence uniquement `${DB_USERNAME}` / `${DB_PASSWORD}`, résolues via `backend/src/main/resources/application-local.yml` (profil Spring `local`, ajouté à `.gitignore`) en développement local, et via de vraies variables d'environnement en production. Le manuel utilisateur final ne mentionne aucun identifiant technique.
