# Référence API REST — SGBF

Vue d'ensemble de tous les points d'entrée exposés par le backend (base `/api`). Le contrat exact (champs des DTO, validations) est porté par le code source (`backend/src/main/java/com/snef/sgbf/**/dto/`, `**/controller/`) et par la documentation OpenAPI générée automatiquement sur `http://localhost:8080/api/docs` — ce document sert de vue d'ensemble navigable, pas de source de vérité.

Sauf mention contraire, chaque endpoint exige un jeton d'accès valide (`Authorization: Bearer ...`) ; le contrôle fin de périmètre (service, titulaire) est le plus souvent appliqué en service, au-delà du simple contrôle de rôle indiqué ci-dessous.

## Authentification — `/api/auth`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| POST | `/login` | public | Identifiant/e-mail + mot de passe → jeton d'accès + cookie de rafraîchissement, en un seul appel (authentification simple, section L de l'analyse fonctionnelle) |
| POST | `/refresh` | public (cookie) | Renouvelle le jeton d'accès via le cookie `refreshToken` |
| POST | `/logout` | authentifié | Révoque tous les jetons de rafraîchissement de l'utilisateur |
| GET | `/me` | authentifié | Identité et rôles actifs de l'utilisateur porteur du jeton |

## Bon de sortie — `/api/bons-sortie`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/` | authentifié | Liste des bons de sortie visibles (périmètre appliqué en service) |
| GET | `/{id}` | authentifié | Détail (anti-IDOR : périmètre vérifié) |
| GET | `/{id}/pdf` | authentifié | PDF — uniquement si statut `VALIDE` (RG-DOC-001) |
| POST | `/` | authentifié | Création (libre-service, agent = utilisateur courant) |
| PUT | `/{id}/retour` | authentifié | Renseigne l'heure de retour |
| POST | `/{id}/viser` | authentifié (titulaire, vérifié en service) | Visa niveau 1 |
| POST | `/{id}/valider` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Validation niveau 2 — déclenche la génération FIPH |

### Personnes à bord — `/api/bons-sortie/{bonSortiePrincipalId}/personnes-a-bord`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/` | authentifié | Liste des personnes à bord d'un bon de sortie principal |
| POST | `/` | authentifié (vérifié en service) | Ajoute une personne à bord |
| DELETE | `/{associationId}` | authentifié (vérifié en service) | Retire une personne à bord |

## FIPH — `/api/fiph`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/` | authentifié | Liste des FIPH visibles (périmètre) |
| GET | `/{id}` | authentifié | Détail (anti-IDOR) |
| POST | `/manuelle` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Création manuelle (Code Service, RG-FIPH-004) |

## Versions de FIPH — `/api/fiph-versions`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/{id}` | authentifié | Détail d'une version |
| GET | `/fiph/{fiphId}` | authentifié | Toutes les versions d'une FIPH |
| GET | `/{id}/validations` | authentifié | Décisions de validation enregistrées |
| GET | `/{id}/pdf` | authentifié | PDF — uniquement si `VALIDEE_DEFINITIVEMENT` (RG-DOC-003) |
| PUT | `/{id}/pointage` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Complète une ligne de pointage |
| POST | `/{id}/signer` | authentifié (titulaire, vérifié en service) | Signature de l'émetteur |
| POST | `/{id}/soumettre` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Soumission au circuit |
| POST | `/{id}/valider/{niveau}` | selon niveau (2 : CA/PH, 3 : Responsable d'activité, 4 : Direction) | Décision de validation (RG-HAB-004 vérifiée) |
| POST | `/fiph/{fiphId}/nouvelle-version` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Nouvelle version post-validation (RG-VER-001 à 007) |

## Missions — `/api/missions`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/` | authentifié | Liste des missions |
| GET | `/{id}` | authentifié | Détail |
| GET | `/{id}/historique` | authentifié | Chaîne des missions liées par prolongation |
| POST | `/` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Création |

## Affectations — `/api/affectations-mission`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/?missionId=` | authentifié | Affectations d'une mission |
| GET | `/{id}` | authentifié | Détail |
| POST | `/` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Affecte un agent |
| POST | `/{id}/interrompre` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Interrompt (clôture + motif, jamais de modification en place) |
| POST | `/{id}/reaffecter` | `CHARGE_AFFAIRES`, `PERSONNE_HABILITEE` | Réaffecte vers une nouvelle mission |

## Identité et habilitations

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET/POST | `/api/agents`, `/api/agents/recherche?terme=` | authentifié (lecture), `ADMINISTRATEUR` (écriture) | Référentiel RH des agents |
| PUT | `/api/agents/{id}/utilisateur/{utilisateurId}` | `ADMINISTRATEUR` | Rattache un compte à un agent |
| GET/POST | `/api/utilisateurs` | `ADMINISTRATEUR` (contrôleur entier) | Comptes applicatifs |
| PUT | `/api/utilisateurs/{id}/statut/{statut}` | `ADMINISTRATEUR` | Change le statut (ACTIF/VERROUILLE/DESACTIVE) |
| GET/POST/DELETE | `/api/habilitations/**` | `ADMINISTRATEUR` (contrôleur entier) | Attribution/retrait d'habilitations (RG-HAB-001 à 006) |

## Référentiels — `/api/referentiels`

Lecture ouverte à tout utilisateur authentifié, écriture réservée à `ADMINISTRATEUR` : `/services`, `/chantiers`, `/codes-hn`, `/vehicules` (GET+POST), `/motifs-interruption` (GET seul).

## Audit — `/api/audit`

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/fiph/{fiphId}` | authentifié (même périmètre que la FIPH) | Historique complet (FIPH + toutes ses versions) |
| GET | `/fiph/{fiphId}/export/csv` | `DIRECTION`, `RH`, `ADMINISTRATEUR` | Export CSV |
| GET | `/fiph/{fiphId}/export/pdf` | `DIRECTION`, `RH`, `ADMINISTRATEUR` | Export PDF |

## Format des erreurs

Toute erreur suit RFC 7807 (`ProblemDetail`) : `{ type, title, status, detail, instance }`, avec `codeRegle` supplémentaire pour une violation de règle de gestion (`BusinessRuleViolationException`, HTTP 422). Le frontend lit systématiquement `detail` (`api/httpClient.ts`, `extraireMessageErreur`) plutôt que d'afficher un message générique.
