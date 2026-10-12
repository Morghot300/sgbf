import i18n from "i18next";
import { initReactI18next } from "react-i18next";

/**
 * Choix de langue FR/EN (évolution du 2026-10-12, inspirée de l'application
 * SNEF "Gestion Administrative" fournie en référence visuelle - sélecteur
 * "FR EN" dans la barre de contenu).
 *
 * <p><strong>Portée de cette première passe</strong> : le français reste la
 * langue de TOUTE l'application (chaînes codées en dur dans chaque page,
 * comme c'était déjà le cas avant cette évolution). Seule l'ossature déjà
 * retravaillée cette session (menu latéral, barre de contenu, page de
 * connexion) est effectivement traduite ici, comme démonstration réelle du
 * mécanisme plutôt qu'un sélecteur qui ne changerait rien à l'écran - voir
 * le rapport de fin de mission pour la liste de ce qui reste à traduire
 * (Bons de sortie, FIPH, Missions, Administration : tous leurs libellés,
 * messages de validation et messages d'erreur).
 *
 * <p>Clé de traduction = texte français lui-même (jamais un identifiant
 * arbitraire du type {@code nav.dashboard}) : évite une table de
 * correspondance séparée rien que pour retrouver le français déjà présent
 * dans chaque composant, et permet d'etendre la traduction composant par
 * composant sans tout migrer d'un coup - un texte non encore traduit
 * s'affiche simplement tel quel (comportement de repli natif d'i18next
 * quand aucune traduction n'existe pour la langue active).
 */
const CLE_STOCKAGE = "sgbf-langue";

function langueInitiale(): string {
  try {
    const memorisee = localStorage.getItem(CLE_STOCKAGE);
    if (memorisee === "fr" || memorisee === "en") return memorisee;
  } catch {
    // Stockage indisponible (navigation privée, quota...) - repli silencieux sur le francais.
  }
  return "fr";
}

void i18n.use(initReactI18next).init({
  lng: langueInitiale(),
  fallbackLng: "fr",
  interpolation: { escapeValue: false },
  resources: {
    fr: { translation: {} },
    en: {
      translation: {
        // --- AppLayout : menu lateral ---
        "Tableau de bord": "Dashboard",
        "Bons de sortie": "Outgoing slips",
        "FIPH": "FIPH",
        "Missions": "Missions",
        "Administration": "Administration",
        "Personnel et habilitations": "Staff and permissions",
        "Référentiels": "Reference data",
        "Brouillon": "Draft",
        "Visé": "Endorsed",
        "Validé": "Approved",
        "Planifiée": "Planned",
        "En cours": "In progress",
        "Interrompue": "Interrupted",
        "Terminée": "Completed",
        "Brouillons": "Drafts",
        "Visées (à valider niv. 2)": "Endorsed (pending level-2 approval)",
        "Validées niv. 2 (à valider niv. 3)": "Approved lvl 2 (pending level-3)",
        "Validées niv. 3 (à valider niv. 4)": "Approved lvl 3 (pending level-4)",
        "Validées définitivement": "Fully approved",
        "Rejetées": "Rejected",
        // --- Barre de contenu ---
        "Se déconnecter": "Log out",
        "Ouvrir le menu": "Open menu",
        "Fermer le menu": "Close menu",
        "Passer en mode clair": "Switch to light mode",
        "Passer en mode sombre": "Switch to dark mode",
        "Mode clair": "Light mode",
        "Mode sombre": "Dark mode",
        "Mode verre": "Glass mode",
        // --- Page de connexion ---
        "Connexion": "Sign in",
        "Login ou e-mail": "Username or email",
        "Mot de passe": "Password",
        "Afficher le mot de passe": "Show password",
        "Se connecter": "Sign in",
        "Connexion...": "Signing in...",
        "Identifiant ou mot de passe incorrect.": "Incorrect username or password.",
      },
    },
  },
});

// Memorise le choix de langue (meme principe que src/theme.tsx pour le theme) - sans la
// dependance i18next-browser-languagedetector, inutile pour un seul point de persistance simple.
i18n.on("languageChanged", (langue) => {
  try {
    localStorage.setItem(CLE_STOCKAGE, langue);
  } catch {
    // Confort d'usage uniquement - une ecriture echouee ne doit jamais bloquer l'affichage.
  }
});

export default i18n;
