#!/usr/bin/env python3
"""
tools/generate_brand_assets.py

Genere tous les derives du logo a partir de la source unique
frontend/src/assets/brand/logo_snef.png - jamais retouches a la main
(mission charte graphique, etape 1).

AVERTISSEMENT DE QUALITE (a documenter dans la note de validation) : la
source est un PNG raster de 389x129 px, pas un SVG vectoriel (voir
tools/brand_extract.py pour le detail de cette deviation). Les derives
generes ICI en agrandissement (logo-192.png, logo-512.png : hauteur source
129px -> 192/512px) seront donc necessairement plus doux/moins nets qu'un
export direct depuis un vectoriel. A regenerer depuis un SVG officiel des
qu'il sera fourni par la communication SNEF, sans changer ce script.

Derives generes :
  frontend/public/brand/logo-192.png            (PWA, cote long = 192px)
  frontend/public/brand/logo-512.png            (PWA, cote long = 512px)
  frontend/public/brand/favicon-32.png          (favicon, 32x32, logo centre sur fond transparent)
  backend/src/main/resources/static/brand/logo-email.png  (e-mails, largeur 320px)
"""
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).parent.parent
SOURCE = RACINE / "frontend" / "src" / "assets" / "brand" / "logo_snef.png"

CIBLES = [
    (RACINE / "frontend" / "public" / "brand" / "logo-192.png", "hauteur", 192),
    (RACINE / "frontend" / "public" / "brand" / "logo-512.png", "hauteur", 512),
    (RACINE / "backend" / "src" / "main" / "resources" / "static" / "brand" / "logo-email.png", "largeur", 320),
]

FAVICON = RACINE / "frontend" / "public" / "brand" / "favicon-32.png"
FAVICON_TAILLE = 32


def redimensionner(image: Image.Image, dimension: str, valeur: int) -> Image.Image:
    ratio = valeur / (image.height if dimension == "hauteur" else image.width)
    nouvelle_taille = (round(image.width * ratio), round(image.height * ratio))
    return image.resize(nouvelle_taille, Image.LANCZOS)


def main() -> None:
    if not SOURCE.exists():
        print(f"ERREUR : source introuvable : {SOURCE}", file=sys.stderr)
        sys.exit(1)

    source = Image.open(SOURCE).convert("RGBA")

    for chemin_cible, dimension, valeur in CIBLES:
        chemin_cible.parent.mkdir(parents=True, exist_ok=True)
        redimensionnee = redimensionner(source, dimension, valeur)
        redimensionnee.save(chemin_cible, "PNG")
        print(f"Genere : {chemin_cible}  ({redimensionnee.width}x{redimensionnee.height})")

    # Favicon : logo centre sur un canevas carre transparent, avec une marge
    # (zone de protection provisoire) pour eviter que les lettres ne touchent
    # les bords a une taille aussi reduite.
    FAVICON.parent.mkdir(parents=True, exist_ok=True)
    marge = 4
    cible_hauteur = FAVICON_TAILLE - 2 * marge
    logo_reduit = redimensionner(source, "hauteur", cible_hauteur)
    if logo_reduit.width > FAVICON_TAILLE - 2 * marge:
        logo_reduit = redimensionner(logo_reduit, "largeur", FAVICON_TAILLE - 2 * marge)
    canevas = Image.new("RGBA", (FAVICON_TAILLE, FAVICON_TAILLE), (0, 0, 0, 0))
    position = ((FAVICON_TAILLE - logo_reduit.width) // 2, (FAVICON_TAILLE - logo_reduit.height) // 2)
    canevas.paste(logo_reduit, position, logo_reduit)
    canevas.save(FAVICON, "PNG")
    print(f"Genere : {FAVICON}  ({FAVICON_TAILLE}x{FAVICON_TAILLE})")


if __name__ == "__main__":
    main()
