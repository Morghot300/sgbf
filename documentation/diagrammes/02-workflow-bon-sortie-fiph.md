# Diagramme — Du bon de sortie à la FIPH validée définitivement

Parcours complet décrit section 12.2 du document source, tel qu'implémenté (et vérifié de bout en bout par `FiphWorkflowIT`).

```mermaid
flowchart TD
    A["Agent : crée le bon de sortie\n(BROUILLON)"] --> B["Agent : vise\n(VISE — niveau 1)"]
    B --> C["Chargé d'Affaires / personne habilitée : valide\n(VALIDE — niveau 2)"]
    C -->|"déclenche automatiquement\n(RG-BS-007, RG-FIPH-001)"| D["FIPH générée ou enrichie\n(BROUILLON)"]
    C -->|"pour chaque personne à bord"| C2["Bon de sortie individuel\ngénéré automatiquement"]
    C2 -.-> D

    D --> E["CA / personne habilitée : complète le pointage\n(EN_COMPLEMENT)"]
    E --> F["Agent titulaire : signe\n(SIGNEE — données de mission figées)"]
    F --> G["CA / personne habilitée : soumet\n(SOUMISE)"]
    G --> H["Chargé d'Affaires : valide niveau 2\n(VALIDEE_NIVEAU_2)"]
    H --> I["Responsable d'activité : valide niveau 3\n(VALIDEE_NIVEAU_3)"]
    I --> J["Direction : valide niveau 4\n(VALIDEE_DEFINITIVEMENT)\nempreinte SHA-256 calculée"]

    J -->|"correction nécessaire"| K["Nouvelle version\n(motif obligatoire, RG-VER-002)\nrepart de BROUILLON"]
    K -.-> E

    H -.->|"rejet ou retour pour correction"| E
    I -.->|"rejet ou retour pour correction"| E
    J -.->|"jamais modifiée en place"| J
```

## Règles clés illustrées

- **RG-HAB-004 (séparation des responsabilités)** : la personne qui a créé/complété une version ne peut jamais la valider, à aucun niveau — vérifié via le journal d'audit, pas par une simple case à cocher.
- **RG-VER-001 (immuabilité)** : une fois `VALIDEE_DEFINITIVEMENT`, la version est figée en base (déclencheur SQL en plus du contrôle applicatif) — toute correction passe exclusivement par une nouvelle version, jamais une modification en place.
- Le retour en arrière (rejet / retour pour correction) ramène toujours la version au statut `EN_COMPLEMENT`, jamais à `BROUILLON` — le pointage déjà saisi n'est pas perdu.
