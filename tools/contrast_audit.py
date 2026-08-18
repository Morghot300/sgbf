#!/usr/bin/env python3
"""
tools/contrast_audit.py

Calcule le ratio de contraste WCAG 2.1 de chaque couleur de marque sur fond
blanc (#FFFFFF), et, si insuffisant, calcule automatiquement la variante
conforme la plus proche a teinte (H) et saturation (S) constantes, en
assombrissant la luminosite (L) par recherche dichotomique jusqu'a franchir
le seuil (recherche exacte, pas d'approximation par pas fixe).

Produit deux tokens par couleur de marque, conformement a l'etape 3 de la
mission :
  --brand-N       : couleur brute (aplats, logo, fonds) - JAMAIS pour du texte < 18px
  --brand-N-text  : variante conforme AAA/AA, utilisable pour texte/liens/icones

Formules de luminance relative et de ratio de contraste : WCAG 2.1 SC 1.4.3
(texte normal, seuil 4.5:1) et SC 1.4.11 (composants d'interface / grand
texte, seuil 3:1).

Couleurs de marque en entree : sortie de tools/brand_extract.py sur
frontend/src/assets/brand/logo_snef.png (executee le 2026-08-17) - voir le
rapport joint pour la justification du choix des deux valeurs retenues
(brand-1 = noir d'encre des lettres S/E/F, brand-2 = teal du "n" ; les autres
couleurs detectees par le script sont des artefacts d'anticrenelage sur les
bords des lettres, pas des teintes de marque distinctes).
"""
import colorsys

COULEURS_MARQUE = [
    ("brand-1", "#1D1D1B", "noir d'encre (lettres S, E, F)"),
    ("brand-2", "#0095A9", "teal (lettre \"n\")"),
]

SEUIL_TEXTE_NORMAL = 4.5
SEUIL_GRAND_TEXTE_ET_UI = 3.0
BLANC = (255, 255, 255)


def hex_vers_rgb(hex_coul: str) -> tuple[float, float, float]:
    hex_coul = hex_coul.lstrip("#")
    return tuple(int(hex_coul[i:i + 2], 16) for i in (0, 2, 4))


def rgb_vers_hex(rgb: tuple[float, float, float]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*[max(0, min(255, round(c))) for c in rgb])


def luminance_relative(rgb: tuple[float, float, float]) -> float:
    def canal(c: float) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b)


def ratio_contraste(rgb1: tuple[float, float, float], rgb2: tuple[float, float, float]) -> float:
    l1, l2 = luminance_relative(rgb1), luminance_relative(rgb2)
    plus_clair, plus_sombre = max(l1, l2), min(l1, l2)
    return (plus_clair + 0.05) / (plus_sombre + 0.05)


def variante_conforme(rgb: tuple[float, float, float], seuil: float) -> tuple[tuple[float, float, float], float]:
    """
    Assombrit la couleur (L decroissante, H/S constants) par dichotomie
    jusqu'au seuil de contraste sur blanc.

    La dichotomie vise `seuil + MARGE_ARRONDI`, pas `seuil` exactement : le
    resultat flottant est ensuite arrondi a l'entier le plus proche pour
    produire un hex CSS valide (r:02X etc.), ce qui peut faire perdre
    jusqu'a ~0.01 de ratio. Sans cette marge, une couleur calculee a
    exactement 4.50 peut se retrouver a 4.49 une fois arrondie - constate
    empiriquement sur --brand-2-text (voir rapport de mission).
    """
    MARGE_ARRONDI = 0.03
    r, g, b = rgb
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    if ratio_contraste(rgb, BLANC) >= seuil:
        return rgb, l * 100
    l_bas, l_haut = 0.0, l
    for _ in range(50):
        l_milieu = (l_bas + l_haut) / 2
        r2, g2, b2 = colorsys.hls_to_rgb(h, l_milieu, s)
        if ratio_contraste((r2 * 255, g2 * 255, b2 * 255), BLANC) >= seuil + MARGE_ARRONDI:
            l_bas = l_milieu
        else:
            l_haut = l_milieu
    r2, g2, b2 = colorsys.hls_to_rgb(h, l_bas, s)
    return (r2 * 255, g2 * 255, b2 * 255), l_bas * 100


def main() -> None:
    print(f"{'Couleur':<10}{'Description':<32}{'Ratio/blanc':<14}{'Conforme AA (4.5:1)?':<22}")
    tokens_css: list[tuple[str, str]] = []

    for nom, hex_coul, description in COULEURS_MARQUE:
        rgb = hex_vers_rgb(hex_coul)
        ratio = ratio_contraste(rgb, BLANC)
        conforme = ratio >= SEUIL_TEXTE_NORMAL
        print(f"{hex_coul:<10}{description:<32}{ratio:<14.2f}{'Oui' if conforme else 'Non':<22}")
        tokens_css.append((f"--{nom}", hex_coul))

        if conforme:
            hex_texte = hex_coul
            ratio_texte = ratio
        else:
            rgb_texte, l_pourcent = variante_conforme(rgb, SEUIL_TEXTE_NORMAL)
            hex_texte = rgb_vers_hex(rgb_texte)
            ratio_texte = ratio_contraste(rgb_texte, BLANC)
            print(f"  -> --{nom}-text : {hex_texte}  (L ramenee a {l_pourcent:.1f} %, ratio {ratio_texte:.2f}:1)")
        tokens_css.append((f"--{nom}-text", hex_texte))

    print()
    print("--- Tokens CSS generes (couche 1 - primitives, a coller dans tokens.css) ---")
    for nom, hex_coul in tokens_css:
        print(f"{nom}: {hex_coul};")

    verifier_paires_reelles()


# Paires texte/fond REELLEMENT utilisees dans l'interface (badges de statut,
# etats), telles que definies dans styles/tokens.css - le critere
# d'acceptation 2 de la mission ("toute paire texte/fond... 4.5:1 minimum")
# porte sur ces paires effectivement affichees, pas sur chaque couleur
# isolement contre du blanc pur (une couleur de texte conforme sur blanc ne
# le reste pas forcement sur un fond tinte).
PAIRES_INTERFACE = [
    ("Badge neutre",   "#56646D", "#EEF1F3"),
    ("Badge attente",  "#8A5A00", "#FCEFD6"),
    ("Badge info",     "#0B5FA5", "#E7F1FB"),
    ("Badge succes",   "#1E7A34", "#E6F4E9"),
    ("Badge danger",   "#B3261E", "#FBE9E8"),
    ("Texte principal sur surface carte",   "#16191C", "#FFFFFF"),
    ("Texte secondaire sur surface carte",  "#56646D", "#FFFFFF"),
    ("Texte principal sur surface page",    "#16191C", "#F7F8F9"),
    ("Lien (brand-2-text) sur surface carte", "#008294", "#FFFFFF"),
    ("Bouton principal (texte sur fond d'action)", "#FFFFFF", "#1D1D1B"),
]


def verifier_paires_reelles() -> None:
    print()
    print("--- Verification des paires texte/fond reellement utilisees (critere d'acceptation 2) ---")
    print(f"{'Paire':<45}{'Ratio':<10}{'Seuil':<8}{'Conforme?'}")
    echecs = []
    for nom, texte, fond in PAIRES_INTERFACE:
        ratio = ratio_contraste(hex_vers_rgb(texte), hex_vers_rgb(fond))
        conforme = ratio >= SEUIL_TEXTE_NORMAL
        if not conforme:
            echecs.append(nom)
        print(f"{nom:<45}{ratio:<10.2f}{SEUIL_TEXTE_NORMAL:<8}{'Oui' if conforme else 'NON - A CORRIGER'}")

    print()
    if echecs:
        print(f"ECHEC : {len(echecs)} paire(s) sous le seuil de 4.5:1 : {', '.join(echecs)}")
    else:
        print("Toutes les paires verifiees atteignent au moins 4.5:1 (texte normal).")


if __name__ == "__main__":
    main()
