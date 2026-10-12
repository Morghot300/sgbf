import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

/**
 * Bascule clair/sombre (évolution du 2026-10-12, inspirée de l'application
 * SNEF "Gestion Administrative" fournie en référence visuelle) — appliquée
 * via l'attribut `data-theme` sur `<html>`, lu par les overrides de
 * `styles/tokens.css` (`:root[data-theme="dark"]`). Préférence mémorisée
 * dans `localStorage` (confort d'usage uniquement, jamais une donnée
 * serveur) ; à défaut, respecte la préférence système
 * (`prefers-color-scheme`) au premier chargement.
 *
 * <p>Porté par un contexte React (et non un simple hook local) : la page de
 * connexion (`/login`, hors de {@code AppLayout}) doit respecter le même
 * thème mémorisé que le reste de l'application — un hook local invoqué
 * séparément dans deux composants créerait deux états indépendants,
 * désynchronisés dès qu'on bascule depuis l'un des deux (bug réel identifié
 * lors de la vérification visuelle de cette évolution).
 */
export type Theme = "clair" | "sombre";

const CLE_STOCKAGE = "sgbf-theme";

function themeInitial(): Theme {
  try {
    const memorise = localStorage.getItem(CLE_STOCKAGE);
    if (memorise === "clair" || memorise === "sombre") return memorise;
  } catch {
    // Stockage indisponible (navigation privée, quota...) - repli silencieux sur la préférence système.
  }
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "sombre" : "clair";
}

const ThemeContext = createContext<[Theme, () => void] | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(themeInitial);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme === "sombre" ? "dark" : "light");
    try {
      localStorage.setItem(CLE_STOCKAGE, theme);
    } catch {
      // Confort d'usage uniquement - une écriture échouée ne doit jamais bloquer l'affichage.
    }
  }, [theme]);

  function basculer() {
    setTheme((t) => (t === "sombre" ? "clair" : "sombre"));
  }

  return <ThemeContext.Provider value={[theme, basculer]}>{children}</ThemeContext.Provider>;
}

export function useTheme(): [Theme, () => void] {
  const contexte = useContext(ThemeContext);
  if (!contexte) {
    throw new Error("useTheme() doit être appelé à l'intérieur de <ThemeProvider>.");
  }
  return contexte;
}
