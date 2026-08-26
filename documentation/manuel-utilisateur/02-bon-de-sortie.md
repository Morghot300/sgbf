# Manuel d'utilisation — Bon de sortie

> S'adresse à tout agent émetteur, Chargé d'Affaires ou personne habilitée. Le contrôle réel des droits reste toujours effectué par le serveur : un bouton visible ne garantit jamais qu'une action réussira si vous n'y êtes pas habilité.

## Filtrer et retrouver un bon de sortie (évolution du 2026-08-18)

**Accès** — Menu **Bons de sortie**, barre de filtres en haut de la liste.

**Étapes** — Combinez librement : **date exacte**, ou une **période** (champs **Du** / **Au**), le **statut** (Brouillon, Visé, Validé), et le **service**. Les critères actifs se cumulent (ex. Service = Littoral **et** Statut = Validé **et** période du 01/08/2026 au 18/08/2026 ne retourne que les bons remplissant simultanément les trois conditions). Le bouton **Réinitialiser les filtres** efface tous les critères d'un coup.

**Résultat attendu** — La liste ne montre que les bons de sortie correspondant à tous les critères sélectionnés, parmi ceux déjà visibles selon votre périmètre (un filtre ne peut jamais faire apparaître un document hors de votre périmètre).

## Créer un bon de sortie

**Objectif** — Déclarer une sortie de véhicule (Omnium service, personnel ou taxi).

**Accès** — Menu **Bons de sortie** → bouton **Nouveau bon de sortie**.

**Étapes**
1. Choisissez le **moyen utilisé** (Omnium service / véhicule personnel / taxi / **autre**). Si vous choisissez **Autre**, un champ **Préciser le véhicule** apparaît et devient obligatoire (ex. « Véhicule de location ») — impossible de valider le formulaire sans le renseigner.
2. Sélectionnez un **véhicule** dans la liste si disponible, ou renseignez son **immatriculation (LT)** manuellement.
3. Renseignez le **kilométrage**, la **date** et l'**heure de sortie**, la **destination**, le **code affaire**, et le **motif** de la sortie.
4. Cliquez sur **Créer le bon de sortie**.

**Résultat attendu** — Le bon de sortie est créé au statut **Brouillon** et vous êtes redirigé vers sa fiche détaillée.

## Viser puis valider un bon de sortie

**Objectif** — Faire progresser le bon de sortie jusqu'au statut **Validé**, seul statut qui déclenche automatiquement la génération de la FIPH correspondante et autorise l'impression.

**Étapes**
1. **Visa (niveau 1)** — L'agent titulaire du bon de sortie ouvre sa fiche et clique sur **Viser (je suis l'agent titulaire)**. Le statut passe à **Visé**. *(Évolution du 2026-08-26)* Un Chargé d'Affaires ou une personne habilitée du service de l'agent peut également viser à sa place, exactement comme pour la validation — utile si l'agent titulaire ne peut pas le faire lui-même.
2. **Validation (niveau 2)** — Un Chargé d'Affaires ou une personne habilitée du service concerné ouvre la même fiche et clique sur **Valider (niveau 2)**. Le statut passe à **Validé**.

**Résultat attendu** — Le bon de sortie passe au statut **Validé** ; la FIPH hebdomadaire de l'agent est générée ou complétée automatiquement, sans action supplémentaire de votre part.

**Cas particuliers**
- Le bouton **Viser** n'apparaît que si le bon de sortie est encore au statut Brouillon ; un clic par un utilisateur qui n'est ni l'agent titulaire, ni un Chargé d'Affaires/une personne habilitée du même service, est refusé par le serveur, même si le bouton était visible.
- Le bouton **Valider (niveau 2)** n'apparaît qu'au statut Visé, et seulement si vous portez une habilitation Chargé d'Affaires ou personne habilitée sur le service de l'agent.

## Gérer les personnes à bord

**Objectif** — Associer d'autres agents transportés au même déplacement (bon de sortie principal uniquement).

**Accès** — Fiche du bon de sortie principal, section **Personnes à bord**.

**Étapes** — Saisissez l'identifiant numérique de l'agent à ajouter, puis cliquez sur **Ajouter**.

**Résultat attendu** — Un bon de sortie individuel est généré automatiquement pour cette personne dès que le bon principal est validé (ou immédiatement si le bon principal l'est déjà), avec sa propre FIPH le cas échéant.

## Imprimer un bon de sortie

**Objectif** — Obtenir le document PDF officiel.

**Accès** — Fiche du bon de sortie, bouton **Imprimer (PDF)** — visible uniquement au statut **Validé**.

**Étapes** — Cliquez sur **Imprimer (PDF)**.

**Résultat attendu** — Le document s'ouvre dans un nouvel onglet, avec l'en-tête stylisé et le logo SNEF, puis la boîte de dialogue d'impression de votre navigateur s'ouvre automatiquement après un court instant. Vous y choisissez vous-même la destination : votre imprimante par défaut (déjà présélectionnée), une autre imprimante réseau disponible sur le poste, ou l'enregistrement en fichier PDF sur le poste (option « Enregistrer au format PDF » de la boîte de dialogue). Le document est régénéré à chaque demande à partir des données actuelles ; l'impression n'a aucun effet sur le contenu ni le statut du bon de sortie.

Si votre navigateur bloque l'ouverture automatique de la boîte de dialogue d'impression (certains bloqueurs de fenêtres pop-up), l'onglet du PDF reste ouvert : utilisez alors le bouton d'impression du lecteur PDF intégré à votre navigateur.

**Messages d'erreur possibles**

| Message | Signification | Que faire |
|---|---|---|
| *Seul un bon de sortie au statut VALIDE peut être imprimé...* | Le bon de sortie n'a pas encore atteint le statut Validé. | Faire viser puis valider le bon de sortie avant de tenter l'impression. |
| *Vous n'êtes pas habilité à consulter ce bon de sortie...* | Le bon de sortie est hors de votre périmètre (autre service, ni titulaire ni gestionnaire). | Contacter le titulaire ou un gestionnaire du service concerné. |
