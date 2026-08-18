import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Fragment, useState } from "react";
import {
  attribuerHabilitation, changerStatutCompte, creerUtilisateur, listerHabilitationsUtilisateur,
  listerUtilisateurs, retirerHabilitation,
} from "../../api/identiteApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerServices } from "../../api/referentielApi";
import AdminNav from "../../components/AdminNav";
import { EtatAsync } from "../../components/EtatAsync";
import {
  LIBELLES_ROLE, ROLES_PERIMETRE_GLOBAL, type CodeRoleMetier, type StatutCompte,
} from "../../types/identite";

const ROLES: CodeRoleMetier[] = ["AGENT", "CHARGE_AFFAIRES", "PERSONNE_HABILITEE", "RESPONSABLE_ACTIVITE", "DIRECTION", "RH", "ADMINISTRATEUR"];
const STATUTS: StatutCompte[] = ["ACTIF", "VERROUILLE", "DESACTIVE"];

/** Gestion des comptes applicatifs et de leurs habilitations (RG-HAB-001 à 006) - reservee a l'Administrateur. */
export default function AdminUtilisateursPage() {
  const queryClient = useQueryClient();
  const utilisateurs = useQuery({ queryKey: ["utilisateurs"], queryFn: listerUtilisateurs });
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const [utilisateurOuvert, setUtilisateurOuvert] = useState<number | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [nouveau, setNouveau] = useState({ identifiant: "", email: "", motDePasse: "", serviceId: "" });

  const creation = useMutation({
    mutationFn: () => creerUtilisateur({
      identifiant: nouveau.identifiant, email: nouveau.email, motDePasse: nouveau.motDePasse,
      serviceId: nouveau.serviceId ? Number(nouveau.serviceId) : null,
    }),
    onSuccess: () => {
      setNouveau({ identifiant: "", email: "", motDePasse: "", serviceId: "" });
      void queryClient.invalidateQueries({ queryKey: ["utilisateurs"] });
    },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de créer ce compte.")),
  });

  const changementStatut = useMutation({
    mutationFn: ({ id, statut }: { id: number; statut: StatutCompte }) => changerStatutCompte(id, statut),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["utilisateurs"] }),
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de changer le statut de ce compte.")),
  });

  return (
    <div>
      <h1>Administration</h1>
      <AdminNav />
      <h2>Comptes et habilitations</h2>
      {erreur && <p role="alert">{erreur}</p>}

      <EtatAsync chargement={utilisateurs.isLoading} erreur={utilisateurs.error} donnees={utilisateurs.data}>
        {(liste) => (
          <table className="tableau">
            <thead><tr><th>Identifiant</th><th>E-mail</th><th>Service</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              {liste.map((u) => (
                <Fragment key={u.id}>
                  <tr>
                    <td>{u.identifiant}</td>
                    <td>{u.email}</td>
                    <td>{u.serviceLibelle ?? "—"}</td>
                    <td>
                      <select value={u.statutCompte} onChange={(e) => changementStatut.mutate({ id: u.id, statut: e.target.value as StatutCompte })}>
                        {STATUTS.map((s) => <option key={s} value={s}>{s}</option>)}
                      </select>
                    </td>
                    <td>
                      <button type="button" onClick={() => setUtilisateurOuvert(utilisateurOuvert === u.id ? null : u.id)}>
                        {utilisateurOuvert === u.id ? "Fermer" : "Habilitations"}
                      </button>
                    </td>
                  </tr>
                  {utilisateurOuvert === u.id && (
                    <tr>
                      <td colSpan={5}>
                        <PanneauHabilitations utilisateurId={u.id} services={services.data ?? []} onErreur={setErreur} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>

      <section>
        <h2>Créer un compte</h2>
        <form className="formulaire" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
          <label htmlFor="identifiant">Identifiant</label>
          <input id="identifiant" value={nouveau.identifiant} onChange={(e) => setNouveau({ ...nouveau, identifiant: e.target.value })} required maxLength={60} />
          <label htmlFor="email">E-mail</label>
          <input id="email" type="email" value={nouveau.email} onChange={(e) => setNouveau({ ...nouveau, email: e.target.value })} required maxLength={150} />
          <label htmlFor="motDePasse">Mot de passe initial (12 caractères minimum)</label>
          <input id="motDePasse" type="password" value={nouveau.motDePasse} onChange={(e) => setNouveau({ ...nouveau, motDePasse: e.target.value })} required minLength={12} />
          <label htmlFor="serviceId">Service (facultatif)</label>
          <select id="serviceId" value={nouveau.serviceId} onChange={(e) => setNouveau({ ...nouveau, serviceId: e.target.value })}>
            <option value="">— Non renseigné —</option>
            {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
          </select>
          <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer le compte"}</button>
        </form>
      </section>
    </div>
  );
}

function PanneauHabilitations({ utilisateurId, services, onErreur }: {
  utilisateurId: number;
  services: { id: number; libelle: string }[];
  onErreur: (message: string) => void;
}) {
  const queryClient = useQueryClient();
  const habilitations = useQuery({ queryKey: ["habilitations", utilisateurId], queryFn: () => listerHabilitationsUtilisateur(utilisateurId) });
  const [nouvelleHabilitation, setNouvelleHabilitation] = useState<{ role: CodeRoleMetier; serviceId: string }>({ role: "AGENT", serviceId: "" });

  function invalider() {
    void queryClient.invalidateQueries({ queryKey: ["habilitations", utilisateurId] });
  }
  const attribution = useMutation({
    mutationFn: () => attribuerHabilitation({
      utilisateurId,
      roleMetierCode: nouvelleHabilitation.role,
      serviceId: ROLES_PERIMETRE_GLOBAL.has(nouvelleHabilitation.role) ? null : Number(nouvelleHabilitation.serviceId),
      dateDebut: new Date().toISOString().slice(0, 10),
      dateFin: null,
    }),
    onSuccess: invalider,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible d'attribuer cette habilitation.")),
  });
  const retrait = useMutation({
    mutationFn: (id: number) => retirerHabilitation(id),
    onSuccess: invalider,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de retirer cette habilitation.")),
  });

  const perimetreGlobal = ROLES_PERIMETRE_GLOBAL.has(nouvelleHabilitation.role);

  return (
    <div className="panneau-imbrique">
      <EtatAsync chargement={habilitations.isLoading} erreur={habilitations.error} donnees={habilitations.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>Rôle</th><th>Service</th><th>Actif</th><th></th></tr></thead>
            <tbody>
              {liste.map((h) => (
                <tr key={h.id}>
                  <td>{LIBELLES_ROLE[h.roleMetierCode]}</td>
                  <td>{h.serviceLibelle ?? "Global"}</td>
                  <td>{h.actif ? "Oui" : "Non"}</td>
                  <td>{h.actif && <button type="button" onClick={() => retrait.mutate(h.id)}>Retirer</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); attribution.mutate(); }}>
        <select value={nouvelleHabilitation.role} onChange={(e) => setNouvelleHabilitation({ ...nouvelleHabilitation, role: e.target.value as CodeRoleMetier })}>
          {ROLES.map((r) => <option key={r} value={r}>{LIBELLES_ROLE[r]}</option>)}
        </select>
        {!perimetreGlobal && (
          <select value={nouvelleHabilitation.serviceId} onChange={(e) => setNouvelleHabilitation({ ...nouvelleHabilitation, serviceId: e.target.value })} required>
            <option value="">— Service —</option>
            {services.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
          </select>
        )}
        <button type="submit" disabled={attribution.isPending || (!perimetreGlobal && !nouvelleHabilitation.serviceId)}>Attribuer</button>
      </form>
    </div>
  );
}
