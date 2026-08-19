import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Fragment, useState } from "react";
import {
  attribuerHabilitation, changerStatutCompte, creerUtilisateur, listerHabilitationsUtilisateur,
  modifierEmail, modifierIdentifiant, modifierServiceUtilisateur, reinitialiserMotDePasse,
  rechercherUtilisateurs, retirerHabilitation,
} from "../../api/identiteApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerServices } from "../../api/referentielApi";
import AdminNav from "../../components/AdminNav";
import { ChampMotDePasse } from "../../components/ChampMotDePasse";
import { EtatAsync } from "../../components/EtatAsync";
import {
  LIBELLES_ROLE, ROLES_PERIMETRE_GLOBAL, type CodeRoleMetier, type StatutCompte,
} from "../../types/identite";

const ROLES: CodeRoleMetier[] = ["AGENT", "CHARGE_AFFAIRES", "PERSONNE_HABILITEE", "RESPONSABLE_ACTIVITE", "DIRECTION", "RH", "ADMINISTRATEUR", "SUPER_ADMINISTRATEUR"];
const STATUTS: StatutCompte[] = ["ACTIF", "VERROUILLE", "DESACTIVE"];

/** Gestion des comptes applicatifs et de leurs habilitations (RG-HAB-001 à 006) - reservee a l'Administrateur. */
export default function AdminUtilisateursPage() {
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const [filtres, setFiltres] = useState({ terme: "", serviceId: "", role: "", statut: "" });
  const utilisateurs = useQuery({
    queryKey: ["utilisateurs", filtres],
    queryFn: () => rechercherUtilisateurs({
      terme: filtres.terme || undefined,
      serviceId: filtres.serviceId ? Number(filtres.serviceId) : undefined,
      role: filtres.role || undefined,
      statut: (filtres.statut || undefined) as StatutCompte | undefined,
    }),
  });
  const [utilisateurOuvert, setUtilisateurOuvert] = useState<number | null>(null);
  const [modificationOuverte, setModificationOuverte] = useState<number | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [nouveau, setNouveau] = useState({ identifiant: "", email: "", motDePasse: "", serviceId: "" });

  function reinitialiserFiltres() {
    setFiltres({ terme: "", serviceId: "", role: "", statut: "" });
  }

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

      <section className="barre-filtres">
        <label htmlFor="filtreTerme">Rechercher (identifiant, e-mail, nom)</label>
        <input id="filtreTerme" type="text" value={filtres.terme} onChange={(e) => setFiltres({ ...filtres, terme: e.target.value })} />
        <label htmlFor="filtreService">Service</label>
        <select id="filtreService" value={filtres.serviceId} onChange={(e) => setFiltres({ ...filtres, serviceId: e.target.value })}>
          <option value="">— Tous —</option>
          {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <label htmlFor="filtreRole">Rôle</label>
        <select id="filtreRole" value={filtres.role} onChange={(e) => setFiltres({ ...filtres, role: e.target.value })}>
          <option value="">— Tous —</option>
          {ROLES.map((r) => <option key={r} value={r}>{LIBELLES_ROLE[r]}</option>)}
        </select>
        <label htmlFor="filtreStatut">Statut</label>
        <select id="filtreStatut" value={filtres.statut} onChange={(e) => setFiltres({ ...filtres, statut: e.target.value })}>
          <option value="">— Tous —</option>
          {STATUTS.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
        <button type="button" onClick={reinitialiserFiltres}>Réinitialiser les filtres</button>
      </section>

      <EtatAsync chargement={utilisateurs.isLoading} erreur={utilisateurs.error} donnees={utilisateurs.data}>
        {(liste) => (
          <table className="tableau">
            <thead><tr><th>Identifiant</th><th>E-mail</th><th>Service</th><th>Statut</th><th></th><th></th></tr></thead>
            <tbody>
              {liste.length === 0 && <tr><td colSpan={6}>Aucun compte ne correspond à ces critères.</td></tr>}
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
                    <td>
                      <button type="button" onClick={() => setModificationOuverte(modificationOuverte === u.id ? null : u.id)}>
                        {modificationOuverte === u.id ? "Fermer" : "Modifier"}
                      </button>
                    </td>
                  </tr>
                  {utilisateurOuvert === u.id && (
                    <tr>
                      <td colSpan={6}>
                        <PanneauHabilitations utilisateurId={u.id} services={services.data ?? []} onErreur={setErreur} />
                      </td>
                    </tr>
                  )}
                  {modificationOuverte === u.id && (
                    <tr>
                      <td colSpan={6}>
                        <PanneauModificationCompte
                          utilisateur={u}
                          services={services.data ?? []}
                          onErreur={setErreur}
                          onSucces={() => void queryClient.invalidateQueries({ queryKey: ["utilisateurs"] })}
                        />
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
          <ChampMotDePasse
            id="motDePasse"
            libelle="Mot de passe initial (12 caractères minimum)"
            valeur={nouveau.motDePasse}
            onChange={(v) => setNouveau({ ...nouveau, motDePasse: v })}
            autoComplete="new-password"
            required
            minLength={12}
          />
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

/**
 * Correction administrative d'un compte (section 7-8 de l'évolution du
 * 2026-08-18) : identifiant, e-mail, service, réinitialisation du mot de
 * passe. Chaque action est indépendante (formulaires distincts) et
 * intégralement revalidée côté serveur (unicité, format) - un message
 * d'erreur clair y est toujours affiché en cas de refus.
 */
function PanneauModificationCompte({ utilisateur, services, onErreur, onSucces }: {
  utilisateur: { id: number; identifiant: string; email: string; serviceId: number | null };
  services: { id: number; libelle: string }[];
  onErreur: (message: string) => void;
  onSucces: () => void;
}) {
  const [identifiant, setIdentifiant] = useState(utilisateur.identifiant);
  const [email, setEmail] = useState(utilisateur.email);
  const [serviceId, setServiceId] = useState(utilisateur.serviceId != null ? String(utilisateur.serviceId) : "");
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState("");

  const majIdentifiant = useMutation({
    mutationFn: () => modifierIdentifiant(utilisateur.id, identifiant),
    onSuccess: onSucces,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de modifier l'identifiant.")),
  });
  const majEmail = useMutation({
    mutationFn: () => modifierEmail(utilisateur.id, email),
    onSuccess: onSucces,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de modifier l'adresse e-mail.")),
  });
  const majService = useMutation({
    mutationFn: () => modifierServiceUtilisateur(utilisateur.id, serviceId ? Number(serviceId) : null),
    onSuccess: onSucces,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de modifier le service.")),
  });
  const majMotDePasse = useMutation({
    mutationFn: () => reinitialiserMotDePasse(utilisateur.id, nouveauMotDePasse),
    onSuccess: () => { setNouveauMotDePasse(""); onSucces(); },
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de réinitialiser le mot de passe.")),
  });

  return (
    <div className="panneau-imbrique">
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majIdentifiant.mutate(); }}>
        <label htmlFor={`identifiant-${utilisateur.id}`}>Identifiant</label>
        <input id={`identifiant-${utilisateur.id}`} value={identifiant} onChange={(e) => setIdentifiant(e.target.value)} maxLength={60} required />
        <button type="submit" disabled={majIdentifiant.isPending || identifiant === utilisateur.identifiant}>Enregistrer</button>
      </form>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majEmail.mutate(); }}>
        <label htmlFor={`email-${utilisateur.id}`}>E-mail</label>
        <input id={`email-${utilisateur.id}`} type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={150} required />
        <button type="submit" disabled={majEmail.isPending || email === utilisateur.email}>Enregistrer</button>
      </form>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majService.mutate(); }}>
        <label htmlFor={`service-${utilisateur.id}`}>Service</label>
        <select id={`service-${utilisateur.id}`} value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
          <option value="">— Non renseigné —</option>
          {services.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <button type="submit" disabled={majService.isPending}>Enregistrer</button>
      </form>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majMotDePasse.mutate(); }}>
        <ChampMotDePasse
          id={`motDePasse-${utilisateur.id}`}
          libelle="Nouveau mot de passe (12 caractères minimum)"
          valeur={nouveauMotDePasse}
          onChange={setNouveauMotDePasse}
          autoComplete="new-password"
          required
          minLength={12}
        />
        <button type="submit" disabled={majMotDePasse.isPending || nouveauMotDePasse.length < 12}>Réinitialiser le mot de passe</button>
      </form>
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
