import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { creerFiphManuelle, listerFiph } from "../../api/fiphApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutFiph } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_STATUT_FIPH } from "../../types/fiph";

export default function FiphListePage() {
  const requete = useQuery({ queryKey: ["fiph"], queryFn: listerFiph });
  const { aLeRole } = useAuth();

  return (
    <div>
      <h1>FIPH — Fiches Individuelles de Pointage Hebdomadaire</h1>
      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => {
          if (liste.length === 0) {
            return <p>Aucune FIPH visible pour le moment.</p>;
          }
          const triees = [...liste].sort((a, b) => (b.annee - a.annee) || (b.numeroSemaine - a.numeroSemaine));
          return (
            <table className="tableau">
              <thead>
                <tr><th>Agent</th><th>Service</th><th>Semaine</th><th>Origine</th><th>Version</th><th>Statut</th><th></th></tr>
              </thead>
              <tbody>
                {triees.map((f) => (
                  <tr key={f.id}>
                    <td>{f.agentNomComplet} ({f.agentMatricule})</td>
                    <td>{f.serviceLibelle}</td>
                    <td>S{f.numeroSemaine} / {f.annee}</td>
                    <td>{f.origine === "BON_SORTIE" ? "Bon de sortie" : "Manuelle"}</td>
                    <td>v{f.versionCouranteNumero}</td>
                    <td><BadgeStatutFiph statut={f.statut} libelle={LIBELLES_STATUT_FIPH[f.statut]} /></td>
                    <td><Link to={`/fiph/${f.id}`}>Ouvrir</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          );
        }}
      </EtatAsync>

      {(aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE")) && <SectionCreationManuelle />}
    </div>
  );
}

/**
 * Creation manuelle d'une FIPH (Code Service, RG-FIPH-004) pour un agent non
 * concerne par une mission durant la periode - reservee au Charge d'Affaires
 * et a la personne habilitee (@PreAuthorize cote backend).
 */
function SectionCreationManuelle() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [valeurs, setValeurs] = useState({ agentId: "", annee: String(new Date().getFullYear()), numeroSemaine: "" });
  const [erreur, setErreur] = useState<string | null>(null);

  const creation = useMutation({
    mutationFn: () => creerFiphManuelle({
      agentId: Number(valeurs.agentId), annee: Number(valeurs.annee), numeroSemaine: Number(valeurs.numeroSemaine),
    }),
    onSuccess: (creee) => {
      void queryClient.invalidateQueries({ queryKey: ["fiph"] });
      navigate(`/fiph/${creee.id}`);
    },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de créer cette FIPH manuelle.")),
  });

  return (
    <section>
      <h2>Créer une FIPH manuelle (Code Service)</h2>
      <p className="dashboard-accueil">Pour un agent non concerné par une mission durant la période (section 2 du document source).</p>
      {erreur && <p role="alert">{erreur}</p>}
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <label htmlFor="agentId">Identifiant de l'agent</label>
        <input id="agentId" type="number" value={valeurs.agentId} onChange={(e) => setValeurs({ ...valeurs, agentId: e.target.value })} required />
        <label htmlFor="annee">Année</label>
        <input id="annee" type="number" min={2020} max={2100} value={valeurs.annee} onChange={(e) => setValeurs({ ...valeurs, annee: e.target.value })} required />
        <label htmlFor="numeroSemaine">Semaine (1 à 53, ISO 8601)</label>
        <input id="numeroSemaine" type="number" min={1} max={53} value={valeurs.numeroSemaine} onChange={(e) => setValeurs({ ...valeurs, numeroSemaine: e.target.value })} required />
        <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer"}</button>
      </form>
    </section>
  );
}
