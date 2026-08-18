import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import { recupererUtilisateurCourant, seConnecter, seDeconnecter } from "../api/authApi";
import { definirJetonAcces } from "../api/httpClient";
import type { UtilisateurCourant } from "../types/auth";

vi.mock("../api/authApi");
vi.mock("../api/httpClient", async (importerOriginal) => ({
  ...(await importerOriginal<typeof import("../api/httpClient")>()),
  definirJetonAcces: vi.fn(),
}));

const UTILISATEUR_TEST: UtilisateurCourant = {
  id: 1, identifiant: "ca.mbarga", email: "ca.mbarga@sgbf.local",
  serviceId: 2, serviceLibelle: "Travaux Publics", rolesActifs: ["CHARGE_AFFAIRES"],
};

function enveloppe({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

/**
 * Contexte d'authentification simple (2026-08-17, sans MFA) : `connexion()`
 * enchaine directement seConnecter -> definirJetonAcces ->
 * recupererUtilisateurCourant, en un seul appel utilisateur - c'est
 * precisement ce comportement, coeur de la suppression du second facteur,
 * qui est verifie ici.
 */
describe("AuthProvider / useAuth", () => {
  beforeEach(() => {
    vi.mocked(seConnecter).mockReset();
    vi.mocked(seDeconnecter).mockReset();
    vi.mocked(recupererUtilisateurCourant).mockReset();
    vi.mocked(definirJetonAcces).mockReset();
  });

  it("tente une session silencieuse au montage : utilisateur charge si un cookie de rafraichissement valide existe", async () => {
    vi.mocked(recupererUtilisateurCourant).mockResolvedValue(UTILISATEUR_TEST);
    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });

    expect(result.current.chargementInitial).toBe(true);
    await waitFor(() => expect(result.current.chargementInitial).toBe(false));
    expect(result.current.utilisateur).toEqual(UTILISATEUR_TEST);
  });

  it("premier acces sans session : reste non-authentifie sans jamais lever d'erreur visible", async () => {
    vi.mocked(recupererUtilisateurCourant).mockRejectedValue({ isAxiosError: true, response: { status: 403 } });
    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });

    await waitFor(() => expect(result.current.chargementInitial).toBe(false));
    expect(result.current.utilisateur).toBeNull();
  });

  it("connexion() : authentifie en un seul appel, pose le jeton, puis charge l'utilisateur courant", async () => {
    vi.mocked(recupererUtilisateurCourant)
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 403 } }) // appel au montage : aucune session
      .mockResolvedValueOnce(UTILISATEUR_TEST); // appel declenche par connexion()
    vi.mocked(seConnecter).mockResolvedValue({ jetonAcces: "jeton.de.test", expiresInSecondes: 900 });

    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });
    await waitFor(() => expect(result.current.chargementInitial).toBe(false));

    await act(async () => {
      await result.current.connexion("ca.mbarga", "MotDePasseTest123!");
    });

    expect(seConnecter).toHaveBeenCalledWith("ca.mbarga", "MotDePasseTest123!");
    expect(definirJetonAcces).toHaveBeenCalledWith("jeton.de.test", 900);
    expect(result.current.utilisateur).toEqual(UTILISATEUR_TEST);
  });

  it("connexion() : un mot de passe incorrect propage l'erreur et ne modifie pas l'etat authentifie", async () => {
    vi.mocked(recupererUtilisateurCourant).mockRejectedValue({ isAxiosError: true, response: { status: 403 } });
    vi.mocked(seConnecter).mockRejectedValue({
      isAxiosError: true, response: { status: 401, data: { detail: "Identifiant ou mot de passe incorrect." } },
    });

    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });
    await waitFor(() => expect(result.current.chargementInitial).toBe(false));

    await expect(
      act(async () => { await result.current.connexion("ca.mbarga", "mauvais-mot-de-passe"); }),
    ).rejects.toBeDefined();

    expect(definirJetonAcces).not.toHaveBeenCalled();
    expect(result.current.utilisateur).toBeNull();
  });

  it("deconnexion() : nettoie l'etat local meme si l'appel serveur echoue (perte reseau)", async () => {
    vi.mocked(recupererUtilisateurCourant).mockResolvedValue(UTILISATEUR_TEST);
    vi.mocked(seDeconnecter).mockRejectedValue(new Error("reseau coupe"));

    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });
    await waitFor(() => expect(result.current.utilisateur).toEqual(UTILISATEUR_TEST));

    await act(async () => {
      await result.current.deconnexion().catch(() => undefined);
    });

    expect(definirJetonAcces).toHaveBeenCalledWith(null, null);
    expect(result.current.utilisateur).toBeNull();
  });

  it("aLeRole() : reflete exactement les roles actifs de l'utilisateur, jamais une supposition", async () => {
    vi.mocked(recupererUtilisateurCourant).mockResolvedValue(UTILISATEUR_TEST);
    const { result } = renderHook(() => useAuth(), { wrapper: enveloppe });

    await waitFor(() => expect(result.current.utilisateur).toEqual(UTILISATEUR_TEST));
    expect(result.current.aLeRole("CHARGE_AFFAIRES")).toBe(true);
    expect(result.current.aLeRole("ADMINISTRATEUR")).toBe(false);
  });
});
