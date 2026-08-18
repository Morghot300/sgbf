import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { creerAgent, lierUtilisateurAgent, listerAgents, listerUtilisateurs } from "../../api/identiteApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerServices } from "../../api/referentielApi";
import AdminNav from "../../components/AdminNav";
import { EtatAsync } from "../../components/EtatAsync";

/** Referentiel RH des agents (section 18) - creation et rattachement a un compte applicatif, reserves a l'Administrateur. */
export default function AdminAgentsPage() {
  const queryClient = useQueryClient();
  const agents = useQuery({ queryKey: ["agents"], queryFn: listerAgents });
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const utilisateurs = useQuery({ queryKey: ["utilisateurs"], queryFn: listerUtilisateurs });
  const [erreur, setErreur] = useState<string | null>(null);
  const [nouveau, setNouveau] = useState({ matricule: "", nom: "", prenom: "", serviceId: "" });
  const [liaison, setLiaison] = useState<Record<number, string>>({});

  const creation = useMutation({
    mutationFn: () => creerAgent({ matricule: nouveau.matricule, nom: nouveau.nom, prenom: nouveau.prenom, serviceId: Number(nouveau.serviceId) }),
    onSuccess: () => {
      setNouveau({ matricule: "", nom: "", prenom: "", serviceId: "" });
      void queryClient.invalidateQueries({ queryKey: ["agents"] });
    },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de créer cet agent.")),
  });

  const liaisonCompte = useMutation({
    mutationFn: ({ agentId, utilisateurId }: { agentId: number; utilisateurId: number }) => lierUtilisateurAgent(agentId, utilisateurId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["agents"] }),
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de rattacher ce compte à cet agent.")),
  });

  return (
    <div>
      <h1>Administration</h1>
      <AdminNav />
      <h2>Agents</h2>
      {erreur && <p role="alert">{erreur}</p>}

      <EtatAsync chargement={agents.isLoading} erreur={agents.error} donnees={agents.data}>
        {(liste) => (
          <table className="tableau">
            <thead><tr><th>Matricule</th><th>Nom</th><th>Service</th><th>Compte applicatif</th></tr></thead>
            <tbody>
              {liste.map((a) => (
                <tr key={a.id}>
                  <td>{a.matricule}</td>
                  <td>{a.nomComplet}</td>
                  <td>{a.serviceLibelle}</td>
                  <td>
                    {a.possedeCompte ? "Rattaché" : (
                      <span className="formulaire-ligne">
                        <select value={liaison[a.id] ?? ""} onChange={(e) => setLiaison({ ...liaison, [a.id]: e.target.value })}>
                          <option value="">— Compte —</option>
                          {utilisateurs.data?.map((u) => <option key={u.id} value={u.id}>{u.identifiant}</option>)}
                        </select>
                        <button
                          type="button"
                          disabled={!liaison[a.id] || liaisonCompte.isPending}
                          onClick={() => liaisonCompte.mutate({ agentId: a.id, utilisateurId: Number(liaison[a.id]) })}
                        >
                          Rattacher
                        </button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>

      <section>
        <h2>Créer un agent</h2>
        <form className="formulaire" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
          <label htmlFor="matricule">Matricule</label>
          <input id="matricule" value={nouveau.matricule} onChange={(e) => setNouveau({ ...nouveau, matricule: e.target.value })} required maxLength={20} />
          <label htmlFor="nom">Nom</label>
          <input id="nom" value={nouveau.nom} onChange={(e) => setNouveau({ ...nouveau, nom: e.target.value })} required maxLength={100} />
          <label htmlFor="prenom">Prénom</label>
          <input id="prenom" value={nouveau.prenom} onChange={(e) => setNouveau({ ...nouveau, prenom: e.target.value })} required maxLength={100} />
          <label htmlFor="serviceId">Service</label>
          <select id="serviceId" value={nouveau.serviceId} onChange={(e) => setNouveau({ ...nouveau, serviceId: e.target.value })} required>
            <option value="">— Sélectionner —</option>
            {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
          </select>
          <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer l'agent"}</button>
        </form>
      </section>
    </div>
  );
}
