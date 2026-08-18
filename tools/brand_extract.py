#!/usr/bin/env python3
"""
tools/brand_extract.py

Extrait les couleurs de marque dominantes du logo source, par quantification
median cut (Pillow, Image.quantize(method=MEDIANCUT)), en ignorant les
pixels quasi transparents (alpha < 200).

DEVIATION DOCUMENTEE PAR RAPPORT A LA MISSION : celle-ci demandait un rendu
SVG -> bitmap. Aucun fichier SVG vectoriel n'a pu etre obtenu malgre deux
tentatives (une image en realite HTML, une page de resultats Google Images) ;
seul un PNG raster (389x129, palette 8 bits) a finalement ete transmis avec
succes par l'utilisateur. Ce script charge donc CE PNG directement comme
source, sans etape de rendu vectoriel. A adapter (ajout d'un rendu SVG en
amont, par ex. via cairosvg) des qu'un fichier vectoriel officiel sera fourni
par la communication SNEF - voir la note de validation livree avec cette
mission.
"""
import colorsys
import sys
from pathlib import Path

from PIL import Image

SOURCE = Path(__file__).parent.parent / "frontend" / "src" / "assets" / "brand" / "logo_snef.png"
ALPHA_MIN = 200
NB_COULEURS = 6


def main() -> None:
    if not SOURCE.exists():
        print(f"ERREUR : source introuvable : {SOURCE}", file=sys.stderr)
        sys.exit(1)

    image = Image.open(SOURCE).convert("RGBA")
    pixels = list(image.getdata())
    pixels_opaques = [(r, g, b) for (r, g, b, a) in pixels if a >= ALPHA_MIN]

    if not pixels_opaques:
        print("ERREUR : aucun pixel suffisamment opaque trouve (alpha >= 200).", file=sys.stderr)
        sys.exit(1)

    total = len(pixels_opaques)

    # Reconstruit une image ne contenant que les pixels opaques retenus, pour
    # que la quantification ne soit jamais influencee par un fond transparent.
    image_opaque = Image.new("RGB", (total, 1))
    image_opaque.putdata(pixels_opaques)
    quantifiee = image_opaque.quantize(colors=NB_COULEURS, method=Image.MEDIANCUT)
    palette = quantifiee.getpalette()[: NB_COULEURS * 3]
    histogramme = quantifiee.histogram()[:NB_COULEURS]

    resultats = []
    for i in range(NB_COULEURS):
        compte = histogramme[i]
        if compte == 0:
            continue
        r, g, b = palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2]
        hex_coul = f"#{r:02X}{g:02X}{b:02X}"
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        couverture = compte / total * 100
        resultats.append((couverture, hex_coul, h * 360, s * 100, l * 100))

    resultats.sort(reverse=True)

    print(f"Source : {SOURCE}")
    print(f"Dimensions : {image.width}x{image.height}")
    print(f"Pixels opaques analyses (alpha >= {ALPHA_MIN}) : {total} / {len(pixels)}")
    print()
    print(f"{'Hex':<10}{'Couverture':<14}{'H (deg)':<10}{'S (%)':<8}{'L (%)':<8}")
    for couverture, hex_coul, h, s, l in resultats:
        print(f"{hex_coul:<10}{couverture:>6.2f} %      {h:>6.1f}    {s:>6.1f}  {l:>6.1f}")

    # Les deux teintes chromatiques (S > 10%, exclut noir/blanc/gris quasi
    # neutres) les plus couvrantes alimentent la couche 1 des tokens.
    chromatiques = [r for r in resultats if r[3] > 10]
    print()
    print("Teintes chromatiques dominantes retenues pour la couche 1 des tokens :")
    for couverture, hex_coul, h, s, l in chromatiques[:2]:
        print(f"  {hex_coul}  (couverture {couverture:.2f} %, H={h:.1f} S={s:.1f} L={l:.1f})")

    neutres = [r for r in resultats if r[3] <= 10]
    print()
    print("Couleurs quasi neutres (S <= 10%, hors charte - noir/blanc/gris du logo) :")
    for couverture, hex_coul, h, s, l in neutres:
        print(f"  {hex_coul}  (couverture {couverture:.2f} %)")


if __name__ == "__main__":
    main()
