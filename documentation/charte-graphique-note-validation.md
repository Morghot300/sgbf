# Note de validation — Charte graphique SGBF

Points à faire confirmer par la communication SNEF avant tout usage public/production de cette charte.

## 1. Source du logo — priorité haute

Aucun fichier SVG vectoriel officiel n'a pu être obtenu pour cette mission. Deux pièces jointes ont été transmises et se sont révélées inutilisables (une image en réalité au format HTML, une page de résultats Google Images pointant vers `groupware.snef.fr`) ; seul un **PNG raster de 389×129 px** a finalement été fourni avec succès, et sert de source unique à toute la charte actuelle.

**Conséquences concrètes à connaître** :
- Les dérivés agrandis (`logo-512.png`, notamment) sont un agrandissement ×4 de la source — visiblement moins nets qu'un export direct depuis un vectoriel.
- Toute la palette de marque (voir point 2) est donc extraite d'un fichier de qualité web courante, pas d'une définition officielle de couleurs (Pantone, RVB de marque documenté, etc.).

**Demande** : obtenir le SVG (ou, à défaut, un PNG haute résolution ≥ 2000 px de large) officiel auprès de la communication SNEF, puis relancer `tools/generate_brand_assets.py` et `tools/brand_extract.py` sans autre changement.

## 2. Couleurs de marque extraites

| Token | Hex | Usage | Source |
|---|---|---|---|
| `--brand-1` | `#1D1D1B` | Noir d'encre (lettres S, E, F) | Extraction automatique, couverture 13.63 % des pixels opaques |
| `--brand-2` | `#0095A9` | Teal (lettre « n ») | Extraction automatique, couverture 5.78 % des pixels opaques |
| `--brand-2-text` | `#008294` | Variante teal conforme WCAG AA (texte/liens) | Calculée (assombrissement à teinte/saturation constantes) |

**Demande** : confirmer que `#1D1D1B` et `#0095A9` correspondent bien aux couleurs de marque officielles (charte graphique SNEF existante, si elle est documentée par ailleurs) — l'extraction automatique ne peut que mesurer les pixels du fichier fourni, pas garantir qu'il s'agit d'un export fidèle aux valeurs de référence de l'entreprise.

## 3. Zone de protection (clearspace)

Fixée provisoirement à 12 px (`--logo-clearspace` dans `tokens.css`) — **valeur arbitraire de bon sens, non issue d'une charte officielle**. La mission demandait explicitement de ne pas inventer de valeur non justifiée ; celle-ci est donc signalée comme telle plutôt que présentée comme confirmée.

**Demande** : la zone de protection exacte (généralement exprimée en multiple de la hauteur d'une lettre du logo) et la taille minimale d'affichage garantissant la lisibilité.

## 4. Version monochrome / fond sombre

Non traitée dans cette mission (aucun écran actuel de l'application n'utilise de fond sombre). Si une version sombre de l'interface est envisagée à l'avenir, une version du logo adaptée (monochrome blanc, ou variante à contraste inversé) devra être fournie séparément — un simple filtre CSS `invert()` sur le PNG actuel donnerait un résultat non maîtrisé.

## 5. Écrans non appliqués — hors périmètre de cette mission

La mission demandait d'appliquer le lockup logo à l'écran de connexion **et** à l'écran de saisie du code MFA. Le second n'existe plus : le second facteur d'authentification par code e-mail, mis en place le 2026-08-17, a été intégralement retiré la même journée à la demande explicite de l'utilisateur (authentification simple, identifiant/e-mail + mot de passe). Le lockup a donc été appliqué à l'écran de connexion (seul écran non authentifié restant) et à l'en-tête de l'application authentifiée.

De même, la section 7 de la mission (logo inline CID dans les e-mails transactionnels du code MFA) est sans objet : plus aucun e-mail transactionnel n'est envoyé par l'application. Le dérivé `logo-email.png` (320 px) a néanmoins été généré et placé dans `backend/src/main/resources/static/brand/`, prêt à être utilisé si une fonctionnalité de notification par e-mail est réintroduite ultérieurement.
