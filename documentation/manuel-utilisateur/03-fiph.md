# Manuel d'utilisation — FIPH (Fiche Individuelle de Pointage Hebdomadaire)

> S'adresse à l'agent titulaire, au Chargé d'Affaires / personne habilitée (complément, soumission), au Responsable d'activité (validation niveau 3), à la Direction (validation niveau 4) et à la RH (consultation globale en lecture seule).

## Consulter une FIPH

**Accès** — Menu **FIPH**, liste des fiches visibles selon votre périmètre. Cliquez sur une ligne pour ouvrir le détail.

La fiche affiche : les informations de l'agent et de la période, le tableau des pointages journaliers, le total d'heures normales/supplémentaires, les validations déjà enregistrées, et l'historique d'audit complet.

## Compléter le pointage

**Objectif** — Renseigner ou corriger les heures normales et supplémentaires de chaque jour.

**Accès** — Fiche FIPH, tableau des pointages — les champs ne sont modifiables que si la version est encore au statut **Brouillon** ou **En complément**, et seulement pour un Chargé d'Affaires ou une personne habilitée du service concerné.

**Étapes** — Modifiez les valeurs **Heures normales** / **Heures sup.** de la ligne concernée, puis cliquez sur **Enregistrer** sur cette même ligne.

**Résultat attendu** — La ligne est mise à jour, le total en pied de tableau est recalculé, et la fiche passe (la première fois) au statut **En complément**.

## Signer, soumettre et faire valider

**Objectif** — Faire progresser la FIPH jusqu'à sa validation définitive.

**Étapes**
1. **Signature** — L'agent titulaire clique sur **Signer (je suis l'agent titulaire)**. La FIPH passe au statut **Signée** ; les données de mission de chaque ligne sont alors figées.
2. **Soumission** — Un Chargé d'Affaires ou une personne habilitée clique sur **Soumettre au circuit de validation**. Statut **Soumise**.
3. **Validation niveau 2** (Chargé d'Affaires) puis **niveau 3** (Responsable d'activité) puis **niveau 4** (Direction, définitive) : à chaque étape, la personne habilitée au niveau courant voit apparaître un encart **Décision de validation** avec trois boutons — **Valider**, **Retourner pour correction**, **Rejeter**.

**Résultat attendu** — Après la validation de niveau 4, la FIPH passe au statut **Validée définitivement** ; une empreinte d'intégrité (SHA-256) est calculée et affichée, garantissant que le contenu ne pourra plus être modifié en place.

**Cas particuliers**
- **RG-HAB-004 (séparation des responsabilités)** : la personne qui a créé, complété ou modifié une FIPH ne peut jamais la valider elle-même, à aucun niveau — même en cumulant plusieurs habilitations. Le serveur refuse la tentative avec un message explicite.
- Un **retour pour correction** ramène la fiche au statut **En complément** : le pointage redevient modifiable, une nouvelle signature et une nouvelle soumission seront nécessaires.
- Un **commentaire est obligatoire** pour un rejet ou un retour pour correction (laissé vide, le serveur refuse l'action).

## Télécharger le PDF

**Accès** — Fiche FIPH, bouton **Télécharger le PDF** — visible uniquement une fois la version au statut **Validée définitivement**.

**Résultat attendu** — Le PDF, cohérent avec l'empreinte d'intégrité affichée, est téléchargé.

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
