# Outils de charte graphique (SGBF / SNEF)

Trois scripts Python, executes dans cet ordre pour toute mise a jour de la charte graphique. Aucune valeur de couleur ni aucun derive du logo n'est retouche a la main - tout passe par ces scripts.

## Pre-requis

```bash
python -m pip install Pillow
```

## 1. Extraction de la palette

```bash
python tools/brand_extract.py
```

Charge `frontend/src/assets/brand/logo_snef.png`, quantifie les couleurs dominantes par median cut (pixels alpha < 200 ignores), et affiche chaque couleur avec sa couverture et sa position HSL. Sert a identifier les primitives de marque a reporter dans `frontend/src/styles/tokens.css` (couche 1).

## 2. Audit de contraste WCAG

```bash
python tools/contrast_audit.py
```

Calcule le ratio WCAG 2.1 de chaque couleur de marque sur fond blanc, genere une variante `-text` conforme (>= 4.5:1) si besoin, puis **verifie les paires texte/fond reellement utilisees dans l'interface** (badges de statut, liens, boutons - copiees depuis `tokens.css`). Se termine par `ECHEC : ...` si une paire est sous le seuil - ne jamais livrer de token dont la sortie de ce script indique un echec.

Si les valeurs de `PAIRES_INTERFACE` dans le script divergent de `tokens.css` (apres une modification manuelle des tokens), mettre a jour ce tableau avant de relancer.

## 3. Generation des derives du logo

```bash
python tools/generate_brand_assets.py
```

Genere, a partir de la seule source `frontend/src/assets/brand/logo_snef.png` :

| Fichier | Usage |
|---|---|
| `frontend/public/brand/logo-192.png` | PWA (manifest, cote long 192px) |
| `frontend/public/brand/logo-512.png` | PWA (manifest, cote long 512px) |
| `frontend/public/brand/favicon-32.png` | Favicon (32x32, logo centre sur fond transparent) |
| `backend/src/main/resources/static/brand/logo-email.png` | Logo inline (CID) des futurs e-mails transactionnels, largeur 320px |

## Deviation par rapport a la source vectorielle

Les trois scripts ci-dessus supposaient a l'origine une source SVG vectorielle (`frontend/src/assets/brand/logo_snef.svg`), rendue en bitmap avant extraction. **Aucun fichier SVG n'a pu etre obtenu** : les deux premieres pieces jointes transmises n'etaient pas exploitables (une image en realite au format HTML, une page de resultats Google Images), seul un PNG raster (389x129 px) a finalement ete fourni avec succes. Les trois scripts chargent donc ce PNG directement.

**Consequence concrete** : `logo-512.png` est un agrandissement x4 d'une source de 129px de haut - visiblement moins net qu'un export direct depuis un vectoriel. A regenerer (memes commandes, aucun changement de script necessaire) des qu'un SVG officiel sera fourni par la communication SNEF - voir la note de validation jointe a la mission.
