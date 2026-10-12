import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Icone } from "./Icone";
import { useTheme, type Theme } from "../theme";

const OPTIONS: { theme: Theme; icone: "soleil" | "lune" | "verre"; libelle: string }[] = [
  { theme: "clair", icone: "soleil", libelle: "Mode clair" },
  { theme: "sombre", icone: "lune", libelle: "Mode sombre" },
  { theme: "verre", icone: "verre", libelle: "Mode verre" },
];

const ICONE_PAR_THEME: Record<Theme, "soleil" | "lune" | "verre"> = { clair: "soleil", sombre: "lune", verre: "verre" };

/**
 * Sélecteur de thème à 3 options (évolution du 2026-10-12, référence
 * visuelle SNEF "Gestion Administrative" - menu "Mode clair / Mode sombre /
 * Mode verre"). Remplace la simple bascule clair/sombre de la première
 * passe : un bouton à deux états ne peut pas représenter un choix à trois
 * valeurs sans un comportement surprenant (quel état suivant depuis
 * "verre" ?) - un menu déroulant explicite était nécessaire dès l'ajout du
 * mode verre.
 */
export function MenuTheme() {
  const [theme, definirTheme] = useTheme();
  const { t } = useTranslation();
  const [ouvert, setOuvert] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ouvert) return;
    function surClicExterieur(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOuvert(false);
    }
    function surEchap(e: KeyboardEvent) {
      if (e.key === "Escape") setOuvert(false);
    }
    document.addEventListener("mousedown", surClicExterieur);
    document.addEventListener("keydown", surEchap);
    return () => {
      document.removeEventListener("mousedown", surClicExterieur);
      document.removeEventListener("keydown", surEchap);
    };
  }, [ouvert]);

  return (
    <div className="menu-theme" ref={ref}>
      <button
        type="button"
        className="app-topbar__bascule-theme"
        onClick={() => setOuvert((v) => !v)}
        aria-label={t("Choisir le thème")}
        aria-haspopup="menu"
        aria-expanded={ouvert}
        title={t(OPTIONS.find((o) => o.theme === theme)!.libelle)}
      >
        <Icone nom={ICONE_PAR_THEME[theme]} />
      </button>
      {ouvert && (
        <div className="menu-theme__panneau" role="menu">
          {OPTIONS.map((option) => (
            <button
              key={option.theme}
              type="button"
              role="menuitemradio"
              aria-checked={theme === option.theme}
              className="menu-theme__option"
              onClick={() => { definirTheme(option.theme); setOuvert(false); }}
            >
              <Icone nom={option.icone} />
              <span>{t(option.libelle)}</span>
              {theme === option.theme && <Icone nom="coche" taille={14} />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
