/**
 * Fichier d'amorcage de la suite de tests (vite.config.ts, `test.setupFiles`).
 *
 * L'import ci-dessous etend l'interface `Assertion` de Vitest avec les
 * matchers de `@testing-library/jest-dom` (`toBeInTheDocument`,
 * `toHaveTextContent`, etc.) - point d'entree `/vitest` recommande par la
 * bibliotheque pour Vitest 2.x, sans configuration TypeScript supplementaire.
 *
 * Le nettoyage automatique du DOM entre chaque test (`afterEach(cleanup)`)
 * est deja assure par `@testing-library/react` des lors que `afterEach` est
 * un global (voir `test.globals: true` dans vite.config.ts) - rien a faire
 * ici de plus.
 *
 * L'import de `../i18n` (evolution du 2026-10-12) initialise i18next pour
 * toute la suite - sans lui, chaque composant utilisant `useTranslation()`
 * (AppLayout, LoginPage, ChampMotDePasse...) emettrait un avertissement
 * "NO_I18NEXT_INSTANCE" a chaque rendu de test (comportement de repli
 * d'i18next, pas un echec de test, mais un bruit evitable).
 */
import "@testing-library/jest-dom/vitest";
import "../i18n";
