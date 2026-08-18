# Diagramme — Architecture générale

```mermaid
flowchart TB
    subgraph Client["Navigateur"]
        SPA["React + TypeScript (Vite)\nTanStack Query, React Hook Form, Zod"]
    end

    subgraph Backend["Backend — Spring Boot 3.3 / Java 21"]
        Security["Filtre JWT + Spring Security\n(RBAC + périmètre par habilitation)"]
        Controllers["Contrôleurs REST\n/api/auth, /api/bons-sortie, /api/fiph,\n/api/fiph-versions, /api/missions,\n/api/affectations-mission, /api/utilisateurs,\n/api/habilitations, /api/agents,\n/api/referentiels, /api/audit"]
        Services["Services métier\n(règles de gestion, transactions,\npérimètre fin, séparation des responsabilités)"]
        Pdf["PdfRenderer\n(openhtmltopdf, génération à la demande)"]
    end

    subgraph DB["MySQL 8"]
        Flyway["Schéma piloté par Flyway\n(V1 à V9, historique immuable)"]
        Triggers["Déclencheurs SQL\n(append-only, immutabilité FIPHVersion figée)"]
    end

    SPA -- "HTTPS + jeton d'accès (mémoire)\ncookie httpOnly (rafraîchissement)" --> Security
    Security --> Controllers --> Services
    Services --> Flyway
    Services --> Pdf
    Flyway --- Triggers
```

## Notes

- Le jeton d'accès (JWT, 15 min) est conservé uniquement en mémoire côté client — jamais dans `localStorage`. Le jeton de rafraîchissement (7 jours) est posé en cookie `httpOnly`/`Secure`/`SameSite=Strict`, restreint à `/api/auth`.
- Authentification simple depuis le 2026-08-17 (identifiant/e-mail + mot de passe, un seul appel — voir [analyse fonctionnelle §L](../01-analyse-fonctionnelle.md)).
- Aucune donnée n'est mise en cache côté serveur pour les documents PDF : chaque téléchargement régénère le document à la demande à partir de l'état persisté (section 13.5 du document source).
- Détail complet des paquets et des choix techniques : [02-architecture-technique.md](../02-architecture-technique.md).
