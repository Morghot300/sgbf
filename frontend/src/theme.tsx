import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

/**
 * Choix de thème clair/sombre/verre (évolution du 2026-10-12, inspirée de
 * l'application SNEF "Gestion Administrative" fournie en référence
 * visuelle - menu "Mode clair / Mode sombre / Mode verre") — appliqué via
 * l'attribut `data-theme` sur `<html>`, lu par les overrides de
 * `styles/tokens.css` (`:root[data-theme="dark"]` / `[data-theme="glass"]`).
 * Préférence mémorisée dans `localStorage` (confort d'usage uniquement,
 * jamais une donnée serveur) ; à défaut, respecte la préférence système
 * (`prefers-color-scheme`) au premier chargement (jamais "verre" par
 * défaut - un thème décoratif ne doit jamais s'imposer sans choix explicite).
 *
 * <p>Porté par un contexte React (et non un simple hook local) : la page de
 * connexion (`/login`, hors de {@code AppLayout}) doit respecter le même
 * thème mémorisé que le reste de l'application — un hook local invoqué
 * séparément dans deux composants créerait deux états indépendants,
 * désynchronisés dès qu'on bascule depuis l'un des deux (bug réel identifié
 * lors de la vérification visuelle de la première passe de cette évolution).
 */
export type Theme = "clair" | "sombre" | "verre";

const CLE_STOCKAGE = "sgbf-theme";
const THEMES_VALIDES: Theme[] = ["clair", "sombre", "verre"];

const ATTRIBUT_PAR_THEME: Record<Theme, string> = {
  clair: "light",
  sombre: "dark",
  verre: "glass",
};

function themeInitial(): Theme {
  try {
    const memorise = localStorage.getItem(CLE_STOCKAGE);
    if (THEMES_VALIDES.includes(memorise as Theme)) return memorise as Theme;
  } catch {
    // Stockage indisponible (navigation privée, quota...) - repli silencieux sur la préférence système.
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "sombre" : "clair";
}

const ThemeContext = createContext<[Theme, (theme: Theme) => void] | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(themeInitial);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", ATTRIBUT_PAR_THEME[theme]);
    try {
      localStorage.setItem(CLE_STOCKAGE, theme);
    } catch {
      // Confort d'usage uniquement - une écriture échouée ne doit jamais bloquer l'affichage.
    }
  }, [theme]);

  return <ThemeContext.Provider value={[theme, setTheme]}>{children}</ThemeContext.Provider>;
}

export function useTheme(): [Theme, (theme: Theme) => void] {
  const contexte = useContext(ThemeContext);
  if (!contexte) {
    throw new Error("useTheme() doit être appelé à l'intérieur de <ThemeProvider>.");
  }
  return contexte;
}
