# Manuel d'utilisation — FIPH (Fiche Individuelle de Pointage Hebdomadaire)

> S'adresse à l'agent titulaire, au Chargé d'Affaires / personne habilitée (complément, validation niveau 2), au Responsable d'activité (validation niveau 3), à la Direction/DG (validation niveau 4, définitive) et à la RH (consultation globale en lecture seule).
>
> **Évolution du 2026-08-18** — depuis une FIPH générée automatiquement à la validation d'un bon de sortie, l'agent titulaire n'a plus besoin de signer sa fiche une seconde fois : son visa est considéré comme déjà acquis (il a déjà visé son bon de sortie avant que le Chargé d'Affaires ne le valide). Le circuit démarre donc directement au Chargé d'Affaires / à la personne habilitée. Voir le chapitre dédié « Évolution du workflow FIPH » pour le détail complet.

## Consulter une FIPH

**Accès** — Menu **FIPH**, liste des fiches visibles selon votre périmètre. Cliquez sur une ligne pour ouvrir le détail.

La fiche affiche : les informations de l'agent et de la période, le tableau des pointages journaliers, le total d'heures normales/supplémentaires, les validations déjà enregistrées, et l'historique d'audit complet.

## Compléter le pointage

**Objectif** — Renseigner ou corriger les heures normales et supplémentaires de chaque jour.

**Accès** — Fiche FIPH, tableau des pointages — réservé au Chargé d'Affaires ou à la personne habilitée du service concerné.

Pour une FIPH générée depuis un bon de sortie, les champs restent modifiables tant que le circuit de validation n'a pas réellement démarré (statuts **Brouillon**, **En complément** ou **Signée** — ce dernier étant désormais le statut de départ habituel, voir plus bas). Pour une FIPH créée manuellement (Code Service), seuls **Brouillon** et **En complément** restent modifiables : la signature de l'agent y reste un acte personnel qui fige le contenu.

**Étapes** — Modifiez les valeurs **Heures normales** / **Heures sup.** de la ligne concernée, puis cliquez sur **Enregistrer** sur cette même ligne.

**Résultat attendu** — La ligne est mise à jour, le total en pied de tableau est recalculé, et la fiche passe (la première fois) au statut **En complément**.

## Faire valider la FIPH

**Objectif** — Faire progresser la FIPH jusqu'à sa validation définitive.

**Pour une FIPH générée depuis un bon de sortie** (cas le plus fréquent) : le visa de l'agent titulaire est **déjà acquis automatiquement** dès la précréation de la fiche (il a déjà visé son bon de sortie avant que le Chargé d'Affaires ne le valide — la fiche démarre directement au statut **Signée**). Aucune signature ne lui est redemandée. Le Chargé d'Affaires ou la personne habilitée ("Responsable désigné") peut valider directement au niveau 2, sans étape de soumission séparée à effectuer au préalable.

**Pour une FIPH créée manuellement** (Code Service) : l'agent titulaire doit encore la signer lui-même (bouton **Signer (je suis l'agent titulaire)**), puis un Chargé d'Affaires ou une personne habilitée la soumet (bouton **Soumettre au circuit de validation**) avant de pouvoir la valider au niveau 2.

**Étapes du circuit de validation** (identiques pour les deux cas à partir d'ici) :
1. **Niveau 2** — le Chargé d'Affaires **ou** la personne habilitée du service (un seul des deux suffit) clique sur **Valider**. Statut **Validée niveau 2**.
2. **Niveau 3** — le Responsable d'activité valide. Statut **Validée niveau 3**.
3. **Niveau 4** — le Directeur (DG) valide, de façon définitive. Statut **Validée définitivement**.

À chaque étape, la personne habilitée au niveau courant voit apparaître un encart **Décision de validation** avec trois boutons — **Valider**, **Retourner pour correction**, **Rejeter**.

**Cas particulier — le Chargé d'Affaires est aussi Responsable d'activité** : si la même personne détient les deux habilitations sur le service concerné, rien ne l'empêche d'effectuer successivement les validations de niveau 2 puis de niveau 3 elle-même ; chacune des deux décisions reste néanmoins enregistrée et tracée séparément dans la section **Validations** de la fiche.

**Résultat attendu** — Après la validation de niveau 4, la FIPH passe au statut **Validée définitivement** ; une empreinte d'intégrité (SHA-256) est calculée et affichée, garantissant que le contenu ne pourra plus être modifié en place.

**Cas particuliers**
- **RG-HAB-004 (séparation des responsabilités)** : la personne qui a **complété ou modifié le pointage** d'une FIPH ne peut jamais la valider elle-même, à aucun niveau — même en cumulant plusieurs habilitations. Le serveur refuse la tentative avec un message explicite. (Le seul fait d'avoir validé le bon de sortie déclencheur, en revanche, n'empêche pas de valider ensuite la FIPH générée automatiquement.)
- Un **retour pour correction** ramène la fiche au statut **En complément** : le pointage redevient modifiable, une nouvelle validation complète du circuit sera nécessaire.
- Un **commentaire est obligatoire** pour un rejet ou un retour pour correction (laissé vide, le serveur refuse l'action).

## Imprimer / télécharger le PDF

**Accès** — Fiche FIPH, bouton **Télécharger le PDF** — visible uniquement une fois la version au statut **Validée définitivement**.

**Résultat attendu** — Comme pour le bon de sortie, le document s'ouvre dans un nouvel onglet (en-tête stylisé, logo SNEF) et la boîte de dialogue d'impression de votre navigateur s'ouvre automatiquement — imprimante par défaut, imprimante réseau, ou enregistrement en fichier PDF sur le poste. Le contenu est cohérent avec l'empreinte d'intégrité (SHA-256) affichée sur la fiche.

## Créer une nouvelle version (correction post-validation)

**Objectif** — Corriger une FIPH déjà validée définitivement (ex. suite à une interruption de mission découverte tardivement).

**Accès** — Fiche FIPH, section **Créer une nouvelle version**, visible uniquement au statut **Validée définitivement** pour un Chargé d'Affaires ou une personne habilitée.

**Étapes** — Saisissez un **motif de modification** (obligatoire), puis cliquez sur **Créer une nouvelle version**.

**Résultat attendu** — Une nouvelle version est créée au statut **Brouillon**, avec le même contenu que la précédente ; celle-ci reste consultable et inchangée. La nouvelle version doit repasser par l'ensemble du circuit de validation.

## Créer une FIPH manuelle

**Objectif** — Couvrir un agent non concerné par une mission durant la période (Code Service).

**Accès** — Bas de la page **FIPH** (liste), section **Créer une FIPH manuelle (Code Service)** — visible uniquement pour un Chargé d'Affaires ou une personne habilitée.

**Étapes** — Renseignez l'**identifiant de l'agent**, l'**année** et le **numéro de semaine** (1 à 53, ISO 8601), puis cliquez sur **Créer**.

**Résultat attendu** — La FIPH est créée au statut Brouillon et vous êtes redirigé vers sa fiche.

## Consulter l'historique et exporter l'audit

**Accès** — Fiche FIPH, section **Historique d'audit** (visible à tous ceux qui peuvent déjà consulter la fiche). Les boutons **Exporter en CSV** et **Exporter en PDF** n'apparaissent que pour la Direction, la RH et l'Administrateur (section 24 du document source).
