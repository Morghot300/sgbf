import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import AppLayout from "./components/AppLayout";
import DashboardPage from "./pages/DashboardPage";
import LoginPage from "./pages/LoginPage";
import BonSortieDetailPage from "./pages/bonSortie/BonSortieDetailPage";
import BonSortieListePage from "./pages/bonSortie/BonSortieListePage";
import BonSortieNouveauPage from "./pages/bonSortie/BonSortieNouveauPage";
import FiphDetailPage from "./pages/fiph/FiphDetailPage";
import FiphListePage from "./pages/fiph/FiphListePage";
import MissionDetailPage from "./pages/mission/MissionDetailPage";
import MissionListePage from "./pages/mission/MissionListePage";
import MissionNouvellePage from "./pages/mission/MissionNouvellePage";
import AdminReferentielsPage from "./pages/admin/AdminReferentielsPage";
import AdminUtilisateursPage from "./pages/admin/AdminUtilisateursPage";
import { ProtectedRoute, RouteAvecRole } from "./routes/ProtectedRoute";

/**
 * Racine de l'application : fournisseur d'authentification (voir
 * `auth/AuthContext.tsx`) et arborescence complete des routes. Toute route
 * authentifiee est enveloppee dans {@link ProtectedRoute} puis rendue a
 * l'interieur de {@link AppLayout} (en-tete + navigation communs) ; les
 * ecrans d'administration ajoutent {@link RouteAvecRole} par-dessus, le
 * controle reel restant toujours cote serveur (403 sur l'appel API concerne).
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<DashboardPage />} />

            <Route path="/bons-sortie" element={<BonSortieListePage />} />
            <Route path="/bons-sortie/nouveau" element={<BonSortieNouveauPage />} />
            <Route path="/bons-sortie/:id" element={<BonSortieDetailPage />} />

            <Route path="/fiph" element={<FiphListePage />} />
            <Route path="/fiph/:id" element={<FiphDetailPage />} />

            <Route path="/missions" element={<MissionListePage />} />
            <Route path="/missions/nouvelle" element={<MissionNouvellePage />} />
            <Route path="/missions/:id" element={<MissionDetailPage />} />

            <Route
              path="/administration"
              element={<RouteAvecRole role="ADMINISTRATEUR"><AdminUtilisateursPage /></RouteAvecRole>}
            />
            <Route
              path="/administration/referentiels"
              element={<RouteAvecRole role="ADMINISTRATEUR"><AdminReferentielsPage /></RouteAvecRole>}
            />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
