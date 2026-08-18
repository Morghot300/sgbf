import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./styles/tokens.css";
import "./index.css";

/**
 * Client TanStack Query unique pour toute l'application : cache des lectures
 * (`useQuery`) et invalidation apres ecriture (`useMutation`). `retry: false`
 * volontairement - une reponse HTTP 403/404/422 est un etat metier legitime
 * (perimetre, regle de gestion), jamais une panne transitoire a rejouer
 * automatiquement.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false },
    mutations: { retry: false },
  },
});

// Point de montage unique de l'application (voir index.html, <div id="root">).
// React.StrictMode n'a aucun effet en production ; en developpement il aide a
// detecter des effets de bord non idempotents (utile ici, l'authentification
// declenche des appels reseau au montage - voir auth/AuthContext.tsx).
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
);
