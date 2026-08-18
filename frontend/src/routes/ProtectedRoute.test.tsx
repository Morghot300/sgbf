import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProtectedRoute, RouteAvecRole } from "./ProtectedRoute";
import { useAuth } from "../auth/AuthContext";

vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));
const useAuthSimule = vi.mocked(useAuth);

function rendreAvecRoutes(elementProtege: ReactElement) {
  return render(
    <MemoryRouter initialEntries={["/protege"]}>
      <Routes>
        <Route path="/login" element={<p>Page de connexion</p>} />
        <Route path="/" element={<p>Tableau de bord</p>} />
        <Route path="/protege" element={elementProtege} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    useAuthSimule.mockReset();
  });

  it("affiche un indicateur de chargement tant que la session initiale n'est pas resolue", () => {
    useAuthSimule.mockReturnValue({ utilisateur: null, chargementInitial: true } as unknown as ReturnType<typeof useAuth>);
    rendreAvecRoutes(<ProtectedRoute><p>Contenu prive</p></ProtectedRoute>);
    expect(screen.getByText("Chargement...")).toBeInTheDocument();
    expect(screen.queryByText("Contenu prive")).not.toBeInTheDocument();
  });

  it("redirige vers /login si aucun utilisateur n'est authentifie", () => {
    useAuthSimule.mockReturnValue({ utilisateur: null, chargementInitial: false } as unknown as ReturnType<typeof useAuth>);
    rendreAvecRoutes(<ProtectedRoute><p>Contenu prive</p></ProtectedRoute>);
    expect(screen.getByText("Page de connexion")).toBeInTheDocument();
    expect(screen.queryByText("Contenu prive")).not.toBeInTheDocument();
  });

  it("affiche le contenu prive une fois l'utilisateur authentifie", () => {
    useAuthSimule.mockReturnValue({
      utilisateur: { id: 1, identifiant: "admin", email: "a@b.c", serviceId: null, serviceLibelle: null, rolesActifs: [] },
      chargementInitial: false,
    } as unknown as ReturnType<typeof useAuth>);
    rendreAvecRoutes(<ProtectedRoute><p>Contenu prive</p></ProtectedRoute>);
    expect(screen.getByText("Contenu prive")).toBeInTheDocument();
  });
});

describe("RouteAvecRole", () => {
  beforeEach(() => {
    useAuthSimule.mockReset();
  });

  it("affiche le contenu si l'utilisateur porte le role requis", () => {
    useAuthSimule.mockReturnValue({ aLeRole: (r: string) => r === "ADMINISTRATEUR" } as unknown as ReturnType<typeof useAuth>);
    rendreAvecRoutes(<RouteAvecRole role="ADMINISTRATEUR"><p>Ecran admin</p></RouteAvecRole>);
    expect(screen.getByText("Ecran admin")).toBeInTheDocument();
  });

  it("redirige vers / si l'utilisateur ne porte pas le role requis - la vraie barriere reste cote serveur", () => {
    useAuthSimule.mockReturnValue({ aLeRole: () => false } as unknown as ReturnType<typeof useAuth>);
    rendreAvecRoutes(<RouteAvecRole role="ADMINISTRATEUR"><p>Ecran admin</p></RouteAvecRole>);
    expect(screen.getByText("Tableau de bord")).toBeInTheDocument();
    expect(screen.queryByText("Ecran admin")).not.toBeInTheDocument();
  });
});
