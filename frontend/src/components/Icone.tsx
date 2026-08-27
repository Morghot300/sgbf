/**
 * Petit jeu d'icônes SVG monochromes minimal (évolution du 2026-08-27, brief
 * "Evolution du module FIPH", section 20) — aucune bibliothèque d'icônes
 * n'était installée dans le projet avant cette évolution ; plutôt que d'en
 * ajouter une (nouvelle dépendance, empreinte de bundle) pour un usage aussi
 * restreint, ce module fournit un ensemble cohérent et unique de tracés
 * simples, tous sur `currentColor` (héritent donc automatiquement des tokens
 * de couleur déjà en place dans la navigation - jamais de couleur en dur).
 *
 * <p>Traits fins (stroke, pas de remplissage plein) pour rester sobre et
 * professionnel, cohérents avec le ton général de l'application (section 21 :
 * "plus brillante" ne veut jamais dire surchargée).
 */
type NomIcone = "tableauDeBord" | "document" | "fiph" | "mission" | "agents" | "service" | "rapports" | "administration" | "chevron";

const TRACES: Record<NomIcone, string> = {
  tableauDeBord: "M3 13h7V3H3v10Zm0 8h7v-6H3v6Zm11 0h7V11h-7v10Zm0-18v6h7V3h-7Z",
  document: "M6 2h9l5 5v15a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Zm8 1.5V8h4.5M8 12h8M8 16h8M8 8h3",
  fiph: "M6 2h9l5 5v15a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Zm8 1.5V8h4.5M8 12.5h3M13 12.5h3M8 16h3M13 16h3",
  mission: "M5 11l2-5a2 2 0 0 1 2-1.3h6A2 2 0 0 1 17 6l2 5M4 17v2a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-1h10v1a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-2M4 11h16v6H4v-6ZM7.5 14h.01M16.5 14h.01",
  agents: "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7-1a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM3 20v-1a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v1M16 14a4.5 4.5 0 0 1 4.5 4.5V20",
  service: "M4 21V8l8-5 8 5v13M9 21v-6h6v6M4 21h16",
  rapports: "M4 20V10M10 20V4M16 20v-7M4 20h16",
  administration: "M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm8-3.5-1.6-.9a6.9 6.9 0 0 0-.6-1.4l.5-1.8-1.7-1.7-1.8.5a6.9 6.9 0 0 0-1.4-.6L12 4l-1.4 1.6a6.9 6.9 0 0 0-1.4.6l-1.8-.5-1.7 1.7.5 1.8a6.9 6.9 0 0 0-.6 1.4L4 12l1.6.9c.1.5.3 1 .6 1.4l-.5 1.8 1.7 1.7 1.8-.5c.4.3.9.5 1.4.6L12 20l1.4-1.6c.5-.1 1-.3 1.4-.6l1.8.5 1.7-1.7-.5-1.8c.3-.4.5-.9.6-1.4L20 12Z",
  chevron: "M9 6l6 6-6 6",
};

export function Icone({ nom, taille = 18 }: { nom: NomIcone; taille?: number }) {
  return (
    <svg
      width={taille}
      height={taille}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={TRACES[nom]} />
    </svg>
  );
}
