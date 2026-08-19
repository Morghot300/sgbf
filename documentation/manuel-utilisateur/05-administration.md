# Manuel d'utilisation — Administration

> Réservé au rôle **Administrateur** (et au **Super Administrateur**, qui hérite de tous ses droits — voir la section dédiée en fin de document). Chaque appel API concerné reste protégé côté serveur (`@PreAuthorize` et hiérarchie de rôles Spring Security) : même en accédant directement à une URL d'administration, un compte sans ce rôle se voit systématiquement refuser l'accès — jamais seulement un bouton masqué côté React.

## Personnel et habilitations

> **Évolution du 2026-08-19** — Toute personne utilisant l'application est obligatoirement un membre du personnel : il n'existe plus d'écran séparé « Agents » ni de rattachement en deux étapes. Une seule fiche par personne réunit son identité RH (nom, prénom, matricule) et, lorsqu'elle en dispose, son compte applicatif (identifiant, e-mail, mot de passe) — le compte reste optionnel : une personne de terrain peut exister dans ce référentiel sans jamais se connecter elle-même, son Bon de Sortie et sa FIPH étant alors gérés pour son compte par un tiers habilité (Chargé d'Affaires, Personne habilitée, Administrateur ou Super Administrateur).

**Accès** — Menu **Administration** (n'apparaît que pour un Administrateur ou un Super Administrateur) → onglet **Personnel et habilitations**.

### Rechercher et filtrer

**Objectif** — Retrouver rapidement une personne parmi toutes celles existantes.

**Accès** — En haut de la page, barre de filtres.

**Étapes** — Combinez librement :
- **Rechercher** : identifiant, e-mail, nom, prénom ou matricule (insensible à la casse) ;
- **Service** : ne montre que les personnes rattachées à ce service ;
- **Rôle** : ne montre que les personnes détenant une habilitation active de ce rôle ;
- **Statut** : ACTIF / VERROUILLE / DESACTIVE.

Le bouton **Réinitialiser les filtres** efface tous les critères. Le filtrage est effectué côté serveur, pas en chargeant toutes les personnes puis en filtrant dans le navigateur.

### Créer une personne

**Étapes** — Renseignez le **nom**, le **prénom**, le **matricule** (facultatif) et le **service** (facultatif — voir plus bas). Cochez **Cette personne dispose d'un compte applicatif** pour lui donner directement un accès : renseignez alors l'**identifiant**, l'**e-mail** et un **mot de passe initial** (12 caractères minimum). Laissez la case décochée pour une personne sans accès direct. Cliquez sur **Créer**.

**Résultat attendu** — La personne est créée au statut **ACTIF**, sans aucune habilitation — à attribuer séparément (voir ci-dessous). Le service reste obligatoire dès qu'un rôle à périmètre non global (Chargé d'Affaires, Personne habilitée, Responsable d'Activité…) lui sera attribué ; le serveur refuse toute habilitation de ce type sans service, indépendamment de ce qui a été saisi à la création de la personne elle-même.

### Ajouter un compte applicatif à une personne existante

**Objectif** — Permettre à une personne créée sans accès de se connecter elle-même par la suite (visa de ses propres bons de sortie, signature de ses FIPH).

**Accès** — Bouton **Modifier** sur la ligne de la personne concernée.

**Étapes** — Le panneau affiche *« Cette personne ne dispose pas encore d'un compte applicatif »* : renseignez l'**identifiant**, l'**e-mail** et un **mot de passe initial**, puis cliquez sur **Ajouter un compte applicatif**.

**Résultat attendu** — La personne peut désormais se connecter, viser ses propres bons de sortie et signer ses FIPH.

### Changer le statut d'un compte

**Étapes** — Dans le tableau, changez la valeur du menu déroulant **Statut** sur la ligne concernée (ACTIF / VERROUILLE / DESACTIVE).

**Résultat attendu** — Effet immédiat : un compte VERROUILLE ou DESACTIVE ne peut plus se connecter, même avec des identifiants corrects ; ses éventuels jetons de rafraîchissement déjà émis sont également révoqués.

### Corriger une personne (identité, identifiant, e-mail, mot de passe, service) — évolution du 2026-08-18/2026-08-19

**Objectif** — Corriger une erreur de saisie ou un changement administratif (mariage, faute de frappe, oubli du service à la création…), sans devoir recréer la personne.

**Accès** — Bouton **Modifier** sur la ligne de la personne concernée.

**Étapes** — Le panneau qui s'ouvre propose plusieurs corrections indépendantes, chacune avec son propre bouton **Enregistrer** :
- **Nom / prénom / matricule** — identité RH de la personne ;
- **Identifiant** — refusé si déjà attribué à un autre compte (message explicite), uniquement si la personne dispose d'un compte ;
- **E-mail** — refusé si déjà attribué, ou si le format n'est pas valide, uniquement si la personne dispose d'un compte ;
- **Service** — peut être retiré (laisser « Non renseigné ») pour une personne à périmètre global (RH, Administrateur, Super Administrateur) ;
- **Réinitialiser le mot de passe** — nouveau mot de passe (12 caractères minimum), avec la case **Afficher le mot de passe** pour le relire avant validation, uniquement si la personne dispose d'un compte.

**Résultat attendu** — Le mot de passe saisi n'est jamais conservé ni journalisé en clair : seul son empreinte cryptographique (hash) est enregistrée, exactement comme à la création d'un compte. Toute session déjà ouverte avec l'ancien mot de passe est invalidée (les jetons de rafraîchissement du compte sont révoqués) — une reconnexion avec le nouveau mot de passe est nécessaire.

### Attribuer, retirer, ou réaffecter une habilitation

**Objectif** — Associer une personne à un rôle métier sur un périmètre (service), l'en retirer, ou la réaffecter à un autre service (RG-HAB-001 à 007).

**Étapes**
1. Cliquez sur **Habilitations** sur la ligne de la personne concernée pour dérouler le panneau.
2. Choisissez le **rôle** et, sauf pour RH ou Administrateur (périmètre global, sans service), le **service**.
3. Cliquez sur **Attribuer**.
4. Pour retirer une habilitation active, cliquez sur **Retirer** sur la ligne correspondante.
5. Pour un Chargé d'Affaires, une Personne habilitée ou un Responsable d'Activité déjà en poste, utilisez plutôt **Changer de service** (menu déroulant + bouton **Changer** sur sa ligne) : une seule action tracée qui remplace un retrait suivi d'une nouvelle attribution.

**Cas particuliers**
- **RG-HAB-005 (exclusivité RH)** : une personne détenant l'habilitation RH ne peut cumuler aucune autre habilitation, et réciproquement — le serveur refuse la tentative avec un message explicite.
- **RG-HAB-007 (un seul service par rôle, évolution du 2026-08-19)** : un Chargé d'Affaires, une Personne habilitée ou un Responsable d'Activité ne peut détenir qu'une seule habilitation active de ce rôle à la fois — sur un seul service. Une seconde attribution du même rôle est refusée ; utilisez **Changer de service** pour réaffecter la personne. La Direction reste exempte de cette règle (portée transverse).

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
PERSONNEL (chaque personne = un utilisateur/agent)
```
