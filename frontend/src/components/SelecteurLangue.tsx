import { useTranslation } from "react-i18next";

/**
 * Sélecteur de langue FR/EN (évolution du 2026-10-12, référence visuelle
 * SNEF "Gestion Administrative" - affichage "FR EN" dans la barre de
 * contenu). Change uniquement `i18n.language` : le texte déjà traduit se
 * met à jour immédiatement (voir `i18n.ts` pour la portée actuelle de la
 * traduction - ossature de l'application uniquement à ce stade).
 */
export function SelecteurLangue() {
  const { i18n } = useTranslation();

  return (
    <div className="selecteur-langue" role="group" aria-label="Langue / Language">
      <button
        type="button"
        className={i18n.language === "fr" ? "selecteur-langue__option--actif" : "selecteur-langue__option"}
        onClick={() => void i18n.changeLanguage("fr")}
        aria-pressed={i18n.language === "fr"}
      >
        FR
      </button>
      <button
        type="button"
        className={i18n.language === "en" ? "selecteur-langue__option--actif" : "selecteur-langue__option"}
        onClick={() => void i18n.changeLanguage("en")}
        aria-pressed={i18n.language === "en"}
      >
        EN
      </button>
    </div>
  );
}
