import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import LoginPage from "./LoginPage";
import { useAuth } from "../auth/AuthContext";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
const useAuthSimule = vi.mocked(useAuth);

const navigateSimule = vi.fn();
vi.mock("react-router-dom", async (importerOriginal) => {
  const original = await importerOriginal<typeof import("react-router-dom")>();
  return { ...original, useNavigate: () => navigateSimule };
});

/**
 * Page de connexion (authentification simple, sans MFA - decision du
 * 2026-08-17). Verifie le contrat exact demande a plusieurs reprises cette
 * session : SEULS "Login ou e-mail" + "Mot de passe" + "Se connecter"
 * sont proposes, un succes redirige directement vers le tableau de bord
 * (aucune etape intermediaire), et un echec affiche un message explicite
 * sans jamais rediriger.
 */
describe("LoginPage", () => {
  const connexionSimulee = vi.fn();

  beforeEach(() => {
    connexionSimulee.mockReset();
    navigateSimule.mockReset();
    useAuthSimule.mockReturnValue({ connexion: connexionSimulee } as unknown as ReturnType<typeof useAuth>);
  });

  function rendre() {
    return render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
  }

  it("n'affiche que les trois champs attendus - aucun ecran ou champ lie a un second facteur", () => {
    rendre();
    expect(screen.getByLabelText("Login ou e-mail")).toBeInTheDocument();
    expect(screen.getByLabelText("Mot de passe")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Se connecter" })).toBeInTheDocument();
    expect(screen.queryByText(/code/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/code/i)).not.toBeInTheDocument();
  });

  it("connexion reussie : appelle connexion() avec les valeurs saisies puis redirige directement vers le tableau de bord", async () => {
    connexionSimulee.mockResolvedValue(undefined);
    const utilisateur = userEvent.setup();
    rendre();

    await utilisateur.type(screen.getByLabelText("Login ou e-mail"), "admin");
    await utilisateur.type(screen.getByLabelText("Mot de passe"), "MotDePasseTest123!");
    await utilisateur.click(screen.getByRole("button", { name: "Se connecter" }));

    await waitFor(() => expect(connexionSimulee).toHaveBeenCalledWith("admin", "MotDePasseTest123!"));
    await waitFor(() => expect(navigateSimule).toHaveBeenCalledWith("/", { replace: true }));
  });

  it("mot de passe incorrect : affiche le message d'erreur et ne redirige jamais", async () => {
    connexionSimulee.mockRejectedValue({
      isAxiosError: true,
      response: { data: { detail: "Identifiant ou mot de passe incorrect." } },
    });
    const utilisateur = userEvent.setup();
    rendre();

    await utilisateur.type(screen.getByLabelText("Login ou e-mail"), "admin");
    await utilisateur.type(screen.getByLabelText("Mot de passe"), "mauvais-mot-de-passe");
    await utilisateur.click(screen.getByRole("button", { name: "Se connecter" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Identifiant ou mot de passe incorrect.");
    expect(navigateSimule).not.toHaveBeenCalled();
  });

  it("compte desactive : affiche le message d'erreur specifique renvoye par le serveur", async () => {
    connexionSimulee.mockRejectedValue({
      isAxiosError: true,
      response: { data: { detail: "Ce compte est desactive ou verrouille. Contactez votre administrateur." } },
    });
    const utilisateur = userEvent.setup();
    rendre();

    await utilisateur.type(screen.getByLabelText("Login ou e-mail"), "agent.mbarga");
    await utilisateur.type(screen.getByLabelText("Mot de passe"), "MotDePasseTest123!");
    await utilisateur.click(screen.getByRole("button", { name: "Se connecter" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/desactive ou verrouille/);
    expect(navigateSimule).not.toHaveBeenCalled();
  });
});
