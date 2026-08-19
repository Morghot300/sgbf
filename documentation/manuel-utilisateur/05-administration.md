# Manuel d'utilisation — Administration

> Réservé au rôle **Administrateur** (et au **Super Administrateur**, qui hérite de tous ses droits — voir la section dédiée en fin de document). Chaque appel API concerné reste protégé côté serveur (`@PreAuthorize` et hiérarchie de rôles Spring Security) : même en accédant directement à une URL d'administration, un compte sans ce rôle se voit systématiquement refuser l'accès — jamais seulement un bouton masqué côté React.

## Comptes et habilitations

**Accès** — Menu **Administration** (n'apparaît que pour un Administrateur ou un Super Administrateur) → onglet **Comptes et habilitations**.

### Rechercher et filtrer les comptes (évolution du 2026-08-18)

**Objectif** — Retrouver rapidement un compte parmi tous ceux existants.

**Accès** — En haut de la page **Comptes et habilitations**, barre de filtres.

**Étapes** — Combinez librement :
- **Rechercher** : identifiant, e-mail, ou nom/prénom de l'agent éventuellement rattaché (insensible à la casse) ;
- **Service** : ne montre que les comptes rattachés à ce service ;
- **Rôle** : ne montre que les comptes détenant une habilitation active de ce rôle ;
- **Statut** : ACTIF / VERROUILLE / DESACTIVE.

Le bouton **Réinitialiser les filtres** efface tous les critères. Le filtrage est effectué côté serveur (paramètres de requête), pas en chargeant tous les comptes puis en filtrant dans le navigateur.

### Créer un compte

**Étapes** — Renseignez l'**identifiant**, l'**e-mail**, un **mot de passe initial** (12 caractères minimum) et, le cas échéant, le **service** de rattachement. Cliquez sur **Créer le compte**.

**Résultat attendu** — Le compte est créé au statut **ACTIF**, sans aucune habilitation — à attribuer séparément (voir ci-dessous).

### Changer le statut d'un compte

**Étapes** — Dans le tableau des comptes, changez la valeur du menu déroulant **Statut** sur la ligne concernée (ACTIF / VERROUILLE / DESACTIVE).

**Résultat attendu** — Effet immédiat : un compte VERROUILLE ou DESACTIVE ne peut plus se connecter, même avec des identifiants corrects ; ses éventuels jetons de rafraîchissement déjà émis sont également révoqués.

### Corriger un compte (identifiant, e-mail, mot de passe, service) — évolution du 2026-08-18

**Objectif** — Corriger une erreur de saisie ou un changement administratif (mariage, faute de frappe, oubli du service à la création…), sans devoir recréer le compte.

**Accès** — Bouton **Modifier** sur la ligne du compte concerné.

**Étapes** — Le panneau qui s'ouvre propose quatre corrections indépendantes, chacune avec son propre bouton **Enregistrer** :
- **Identifiant** — refusé si déjà attribué à un autre compte (message explicite) ;
- **E-mail** — refusé si déjà attribué, ou si le format n'est pas valide ;
- **Service** — peut être retiré (laisser « Non renseigné ») pour un compte à périmètre global (RH, Administrateur, Super Administrateur) ;
- **Réinitialiser le mot de passe** — nouveau mot de passe (12 caractères minimum), avec la case **Afficher le mot de passe** pour le relire avant validation.

**Résultat attendu** — Le mot de passe saisi n'est jamais conservé ni journalisé en clair : seul son empreinte cryptographique (hash) est enregistrée, exactement comme à la création d'un compte. Toute session déjà ouverte avec l'ancien mot de passe est invalidée (les jetons de rafraîchissement du compte sont révoqués) — une reconnexion avec le nouveau mot de passe est nécessaire.

### Attribuer ou retirer une habilitation

**Objectif** — Associer un utilisateur à un rôle métier sur un périmètre (service), ou l'en retirer (RG-HAB-001 à 006).

**Étapes**
1. Cliquez sur **Habilitations** sur la ligne du compte concerné pour dérouler le panneau.
2. Choisissez le **rôle** et, sauf pour RH ou Administrateur (périmètre global, sans service), le **service**.
3. Cliquez sur **Attribuer**.
4. Pour retirer une habilitation active, cliquez sur **Retirer** sur la ligne correspondante.

**Cas particuliers**
- **RG-HAB-005 (exclusivité RH)** : un compte détenant l'habilitation RH ne peut cumuler aucune autre habilitation, et réciproquement — le serveur refuse la tentative avec un message explicite.
- Un utilisateur peut cumuler plusieurs habilitations non-RH (ex. Chargé d'Affaires sur un service et personne habilitée sur un autre).

## Agents

**Accès** — Menu **Administration** → onglet **Agents**.

### Créer un agent

**Étapes** — Renseignez le **matricule**, le **nom**, le **prénom** et le **service**, puis cliquez sur **Créer l'agent**.

### Rattacher un compte applicatif à un agent

**Objectif** — Permettre à un agent de se connecter lui-même à l'application (visa de ses propres bons de sortie, signature de ses FIPH).

**Étapes** — Sur la ligne de l'agent non encore rattaché, sélectionnez un **compte** dans la liste puis cliquez sur **Rattacher**.

**Résultat attendu** — L'agent est marqué « Rattaché » ; son compte applicatif peut désormais viser ses bons de sortie et signer ses FIPH.

## Référentiels

**Accès** — Menu **Administration** → onglet **Référentiels**.

Quatre sections, chacune avec un tableau de consultation et un mini-formulaire de création en bas de section :

| Référentiel | Champs à la création |
|---|---|
| **Services** | Code service, libellé |
| **Chantiers** | Code affaire, libellé |
| **Codes mission (Code HN)** | Code, libellé, chantier de rattachement |
| **Véhicules** | Immatriculation, type (Omnium service / Personnel) |

**Résultat attendu** — Chaque élément créé est immédiatement disponible dans les listes déroulantes des autres modules (création de mission, de bon de sortie, etc.).

## Le moyen utilisé « Autre » sur un bon de sortie (évolution du 2026-08-18)

En plus d'Omnium service, Véhicule personnel et Taxi, le formulaire de création d'un bon de sortie propose désormais **Autre**. En le sélectionnant, un champ **Préciser le véhicule** apparaît et devient **obligatoire** (ex. « Véhicule de location ») : le formulaire refuse la validation tant qu'il est vide, et le serveur applique la même règle indépendamment du frontend — un appel API direct sans précision est également refusé. Taxi, comme avant, n'exige aucune précision supplémentaire.

## Journal d'audit (section 24, évolution du 2026-08-18)

Toute action administrative sensible (création/modification d'un compte, modification de l'identifiant ou de l'e-mail, réinitialisation d'un mot de passe, changement de statut, attribution/retrait d'une habilitation, etc.) est enregistrée dans le journal d'audit, de façon non modifiable : administrateur auteur, date et heure, type d'action, compte ou objet concerné, valeur avant/après lorsque pertinent. **Le mot de passe lui-même n'y figure jamais**, ni en clair ni sous forme de hash — seul le fait qu'une réinitialisation a eu lieu est journalisé.

**Accès** — Fiche d'un bon de sortie ou d'une FIPH, section **Historique d'audit** (visible à tous ceux qui peuvent déjà consulter le document) ; les journaux liés aux comptes eux-mêmes sont consultables via les mêmes principes de traçabilité, réservés à la Direction, la RH et l'Administration selon le contexte (section 24 du document source).

## Super Administrateur (évolution du 2026-08-18)

Un rôle **SUPER_ADMINISTRATEUR** distinct de l'Administrateur existe pour la supervision globale de l'application. Il **hérite automatiquement de tous les droits de l'Administrateur** (contrôlé côté serveur par la hiérarchie de rôles de Spring Security, pas seulement par l'affichage du menu) et dispose en plus d'un droit réservé :

**Seul un titulaire déjà actif du rôle Super Administrateur peut attribuer — ou retirer — l'habilitation Super Administrateur**, à un tiers comme à lui-même. Un Administrateur standard qui tente de se l'auto-attribuer, ou de l'attribuer à quelqu'un d'autre, se voit refuser l'opération par le serveur avec un message explicite, même en appelant l'API directement sans passer par l'écran.

**Hiérarchie des droits** :
```
SUPER_ADMINISTRATEUR
        ↓
ADMINISTRATEUR
        ↓
RESPONSABLES / RÔLES MÉTIER (Chargé d'Affaires, Responsable d'activité, Direction, RH...)
        ↓
UTILISATEURS (agents)
```
