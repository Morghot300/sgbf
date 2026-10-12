import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { VisiteGuidee, type EtapeVisiteGuidee } from "./VisiteGuidee";

/**
 * Moteur de visite guidee (voir Javadoc du composant) - verifie ici sans
 * session authentifiee (jsdom ne peut pas se connecter au backend reel) :
 * la logique de navigation entre etapes, la fermeture (bouton, voile,
 * Echap) et les etats premiere/derniere etape. Le rendu visuel reel
 * (positionnement de la bulle, anneau de mise en evidence) a ete verifie
 * manuellement dans le navigateur (voir rapport de mission) - jsdom ne
 * fournit pas de moteur de mise en page reel (`getBoundingClientRect`
 * retourne des zeros), donc non re-teste ici.
 */
const ETAPES: EtapeVisiteGuidee[] = [
  { cible: null, titre: "Bienvenue", texte: "Premiere etape." },
  { cible: '[data-tour="x"]', titre: "Deuxieme", texte: "Deuxieme etape." },
  { cible: null, titre: "Fin", texte: "Derniere etape." },
];

describe("VisiteGuidee", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  function rendre(etapeIndex: number, onEtapeChange = vi.fn(), onFermer = vi.fn()) {
    const resultat = render(
      <VisiteGuidee etapes={ETAPES} etapeIndex={etapeIndex} onEtapeChange={onEtapeChange} onFermer={onFermer} />,
    );
    act(() => { vi.advanceTimersByTime(250); });
    return { ...resultat, onEtapeChange, onFermer };
  }

  it("affiche la premiere etape sans bouton Précédent, avec le bon compteur", () => {
    rendre(0);
    expect(screen.getByText("Bienvenue")).toBeInTheDocument();
    expect(screen.getByText("Premiere etape.")).toBeInTheDocument();
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Précédent" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Suivant" })).toBeInTheDocument();
  });

  it("le clic sur Suivant avance d'une etape", async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { onEtapeChange } = rendre(0);
    await utilisateur.click(screen.getByRole("button", { name: "Suivant" }));
    expect(onEtapeChange).toHaveBeenCalledWith(1);
  });

  it("le clic sur Précédent recule d'une etape (absent sur la premiere)", async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { onEtapeChange } = rendre(1);
    await utilisateur.click(screen.getByRole("button", { name: "Précédent" }));
    expect(onEtapeChange).toHaveBeenCalledWith(0);
  });

  it("la derniere etape affiche Terminer (pas Suivant) et ferme la visite", async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { onFermer } = rendre(2);
    expect(screen.queryByRole("button", { name: "Suivant" })).not.toBeInTheDocument();
    await utilisateur.click(screen.getByRole("button", { name: "Terminer" }));
    expect(onFermer).toHaveBeenCalledTimes(1);
  });

  it("le bouton de fermeture (X) ferme la visite", async () => {
    const utilisateur = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { onFermer } = rendre(0);
    await utilisateur.click(screen.getByRole("button", { name: "Fermer la visite guidée" }));
    expect(onFermer).toHaveBeenCalledTimes(1);
  });

  it("la touche Echap ferme la visite", () => {
    const { onFermer } = rendre(0);
    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    });
    expect(onFermer).toHaveBeenCalledTimes(1);
  });

  it("les fleches clavier naviguent entre les etapes", () => {
    const { onEtapeChange } = rendre(1);
    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
    });
    expect(onEtapeChange).toHaveBeenCalledWith(2);
    act(() => {
      document.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }));
    });
    expect(onEtapeChange).toHaveBeenCalledWith(0);
  });
});
