# Manuel d'utilisation — Administration

> Réservé au rôle **Administrateur**. Chaque appel API concerné reste protégé côté serveur (`@PreAuthorize` sur la classe entière des contrôleurs concernés) : même en accédant directement à une URL d'administration, un compte sans ce rôle se voit systématiquement refuser l'accès.

## Comptes et habilitations

**Accès** — Menu **Administration** (n'apparaît que pour un Administrateur) → onglet **Comptes et habilitations**.

### Créer un compte

**Étapes** — Renseignez l'**identifiant**, l'**e-mail**, un **mot de passe initial** (12 caractères minimum) et, le cas échéant, le **service** de rattachement. Cliquez sur **Créer le compte**.

**Résultat attendu** — Le compte est créé au statut **ACTIF**, sans aucune habilitation — à attribuer séparément (voir ci-dessous).

### Changer le statut d'un compte

**Étapes** — Dans le tableau des comptes, changez la valeur du menu déroulant **Statut** sur la ligne concernée (ACTIF / VERROUILLE / DESACTIVE).

**Résultat attendu** — Effet immédiat : un compte VERROUILLE ou DESACTIVE ne peut plus se connecter, même avec des identifiants corrects.

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
