import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SelectionPersonnesABord } from "./SelectionPersonnesABord";
import { ajouterPersonnesABordEnLot, listerAgentsEligibles } from "../../api/bonSortieApi";

vi.mock("../../api/bonSortieApi", () => ({
  listerAgentsEligibles: vi.fn(),
  ajouterPersonnesABordEnLot: vi.fn(),
}));
const listerSimule = vi.mocked(listerAgentsEligibles);
const ajouterSimule = vi.mocked(ajouterPersonnesABordEnLot);

/**
 * Selection multiple des personnes a bord (evolution du 2026-08-19, Lot 4) -
 * remplace l'ancienne saisie d'un identifiant numerique. Le perimetre est
 * suppose deja calcule par le serveur (voir tests backend
 * EvolutionWorkflowBonSortieIT#lot4_*) : ce composant se contente
 * d'afficher, filtrer, selectionner et soumettre en lot ce que le serveur
 * lui a fourni.
 */
describe("SelectionPersonnesABord", () => {
  beforeEach(() => {
    listerSimule.mockReset();
    ajouterSimule.mockReset();
  });

  function rendre() {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={queryClient}>
        <SelectionPersonnesABord bonSortieId={8} onAjoutReussi={vi.fn()} />
      </QueryClientProvider>,
    );
  }

  it("affiche le personnel eligible renvoye par le serveur", async () => {
    listerSimule.mockResolvedValue([
      { id: 1, nomComplet: "Alice Ateba", matricule: "MAT001", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
      { id: 2, nomComplet: "Bruno Bikoro", matricule: "MAT002", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
    ]);
    rendre();

    expect(await screen.findByText("Alice Ateba")).toBeInTheDocument();
    expect(screen.getByText("Bruno Bikoro")).toBeInTheDocument();
    expect(screen.getByText("0 personne(s) sélectionnée(s)")).toBeInTheDocument();
  });

  it("selection individuelle : coche une personne, met a jour le compteur, active le bouton d'ajout", async () => {
    listerSimule.mockResolvedValue([
      { id: 1, nomComplet: "Alice Ateba", matricule: "MAT001", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
    ]);
    const utilisateur = userEvent.setup();
    rendre();

    await screen.findByText("Alice Ateba");
    const bouton = screen.getByRole("button", { name: "Ajouter la sélection" });
    expect(bouton).toBeDisabled();

    await utilisateur.click(screen.getByLabelText("Sélectionner Alice Ateba"));
    expect(screen.getByText("1 personne(s) sélectionnée(s)")).toBeInTheDocument();
    expect(bouton).toBeEnabled();
  });

  it("« tout sélectionner » ne coche que le resultat filtre par la recherche", async () => {
    listerSimule.mockResolvedValue([
      { id: 1, nomComplet: "Alice Ateba", matricule: "MAT001", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
      { id: 2, nomComplet: "Bruno Bikoro", matricule: "MAT002", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
    ]);
    const utilisateur = userEvent.setup();
    rendre();

    await screen.findByText("Alice Ateba");
    await utilisateur.type(screen.getByLabelText("Rechercher une personne par nom ou matricule"), "Alice");
    expect(screen.queryByText("Bruno Bikoro")).not.toBeInTheDocument();

    await utilisateur.click(screen.getByLabelText("Tout sélectionner"));
    expect(screen.getByText("1 personne(s) sélectionnée(s)")).toBeInTheDocument();
  });

  it("ajout en lot : soumet exactement les identifiants selectionnes, puis reinitialise la selection", async () => {
    listerSimule.mockResolvedValue([
      { id: 1, nomComplet: "Alice Ateba", matricule: "MAT001", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
      { id: 2, nomComplet: "Bruno Bikoro", matricule: "MAT002", serviceLibelle: "Telecom", statutCompte: "ACTIF", dejaAffecteMemeCreneau: false },
    ]);
    ajouterSimule.mockResolvedValue([]);
    const utilisateur = userEvent.setup();
    rendre();

    await screen.findByText("Alice Ateba");
    await utilisateur.click(screen.getByLabelText("Sélectionner Alice Ateba"));
    await utilisateur.click(screen.getByRole("button", { name: "Ajouter la sélection" }));

    await waitFor(() => expect(ajouterSimule).toHaveBeenCalledWith(8, { agentIds: [1] }));
    await waitFor(() => expect(screen.getByText("0 personne(s) sélectionnée(s)")).toBeInTheDocument());
  });

  it("etat d'erreur : affiche un message et un bouton pour reessayer", async () => {
    listerSimule.mockRejectedValue({ isAxiosError: true, response: { data: { detail: "Erreur serveur." } } });
    rendre();

    expect(await screen.findByRole("alert")).toHaveTextContent("Erreur serveur.");
    expect(screen.getByRole("button", { name: "Réessayer" })).toBeInTheDocument();
  });

  it("etat vide : aucune personne eligible", async () => {
    listerSimule.mockResolvedValue([]);
    rendre();

    expect(await screen.findByText(/Aucune personne du service n'est disponible/)).toBeInTheDocument();
  });
});
