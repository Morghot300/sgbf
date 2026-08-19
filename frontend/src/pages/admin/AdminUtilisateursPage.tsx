import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Fragment, useState } from "react";
import {
  ajouterCompteApplicatif, attribuerHabilitation, changerServiceHabilitation, changerStatutCompte, creerUtilisateur,
  listerHabilitationsUtilisateur, modifierEmail, modifierIdentifiant, modifierIdentite, modifierServiceUtilisateur,
  reinitialiserMotDePasse, rechercherUtilisateurs, retirerHabilitation,
} from "../../api/identiteApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerServices } from "../../api/referentielApi";
import { useAuth } from "../../auth/AuthContext";
import AdminNav from "../../components/AdminNav";
import { ChampMotDePasse } from "../../components/ChampMotDePasse";
import { EtatAsync } from "../../components/EtatAsync";
import {
  LIBELLES_ROLE, ROLES_PERIMETRE_GLOBAL, ROLES_SERVICE_EXCLUSIF, type CodeRoleMetier, type HabilitationDto,
  type StatutCompte, type UtilisateurDto,
} from "../../types/identite";

const ROLES: CodeRoleMetier[] = ["AGENT", "CHARGE_AFFAIRES", "PERSONNE_HABILITEE", "RESPONSABLE_ACTIVITE", "DIRECTION", "RH", "ADMINISTRATEUR", "SUPER_ADMINISTRATEUR"];
const STATUTS: StatutCompte[] = ["ACTIF", "VERROUILLE", "DESACTIVE"];

/** Gestion des comptes applicatifs et de leurs habilitations (RG-HAB-001 à 006) - reservee a l'Administrateur. */
export default function AdminUtilisateursPage() {
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  // Un Administrateur standard ne doit jamais se voir proposer SUPER_ADMINISTRATEUR comme cible de filtre ou
  // de role a attribuer (evolution du 2026-08-19, section 1) - purement un confort d'affichage, le backend
  // bloque de toute facon toute tentative reelle (voir HabilitationService.validerAttributionSuperAdministrateur
  // et UtilisateurService.verifierAccesCompteCible), mais l'interface ne doit meme pas laisser deviner l'option.
  const rolesSelectionnables = aLeRole("SUPER_ADMINISTRATEUR") ? ROLES : ROLES.filter((r) => r !== "SUPER_ADMINISTRATEUR");
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
  const [nouveau, setNouveau] = useState({
    nom: "", prenom: "", matricule: "", avecCompte: true,
    identifiant: "", email: "", motDePasse: "", serviceId: "",
  });

  function reinitialiserFiltres() {
    setFiltres({ terme: "", serviceId: "", role: "", statut: "" });
  }

  const creation = useMutation({
    mutationFn: () => creerUtilisateur({
      nom: nouveau.nom,
      prenom: nouveau.prenom,
      matricule: nouveau.matricule || null,
      identifiant: nouveau.avecCompte ? nouveau.identifiant : null,
      email: nouveau.avecCompte ? nouveau.email : null,
      motDePasse: nouveau.avecCompte ? nouveau.motDePasse : null,
      serviceId: nouveau.serviceId ? Number(nouveau.serviceId) : null,
    }),
    onSuccess: () => {
      setNouveau({ nom: "", prenom: "", matricule: "", avecCompte: true, identifiant: "", email: "", motDePasse: "", serviceId: "" });
      void queryClient.invalidateQueries({ queryKey: ["utilisateurs"] });
    },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible de créer cette personne.")),
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
      <h2>Personnel et habilitations</h2>
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
          {rolesSelectionnables.map((r) => <option key={r} value={r}>{LIBELLES_ROLE[r]}</option>)}
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
            <thead><tr><th>Nom complet</th><th>Matricule</th><th>Identifiant</th><th>E-mail</th><th>Service</th><th>Statut</th><th></th><th></th></tr></thead>
            <tbody>
              {liste.length === 0 && <tr><td colSpan={8}>Aucune personne ne correspond à ces critères.</td></tr>}
              {liste.map((u) => (
                <Fragment key={u.id}>
                  <tr>
                    <td>{u.nomComplet ?? "—"}</td>
                    <td>{u.matricule ?? "—"}</td>
                    <td>{u.identifiant ?? <em>Aucun compte</em>}</td>
                    <td>{u.email ?? "—"}</td>
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
                      <td colSpan={8}>
                        <PanneauHabilitations utilisateurId={u.id} services={services.data ?? []} roles={rolesSelectionnables} onErreur={setErreur} />
                      </td>
                    </tr>
                  )}
                  {modificationOuverte === u.id && (
                    <tr>
                      <td colSpan={8}>
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
        <h2>Créer une personne</h2>
        <p>
          Toute personne utilisant l'application est un membre du personnel rattaché à un service. Un compte
          applicatif (identifiant/mot de passe) est optionnel : une personne sans accès direct au système peut
          exister dans ce référentiel (son Bon de Sortie et sa FIPH sont alors gérés pour son compte par un tiers
          habilité), et un compte pourra lui être ajouté plus tard.
        </p>
        <form className="formulaire" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
          <label htmlFor="nom">Nom</label>
          <input id="nom" value={nouveau.nom} onChange={(e) => setNouveau({ ...nouveau, nom: e.target.value })} required maxLength={100} />
          <label htmlFor="prenom">Prénom</label>
          <input id="prenom" value={nouveau.prenom} onChange={(e) => setNouveau({ ...nouveau, prenom: e.target.value })} required maxLength={100} />
          <label htmlFor="matricule">Matricule (facultatif)</label>
          <input id="matricule" value={nouveau.matricule} onChange={(e) => setNouveau({ ...nouveau, matricule: e.target.value })} maxLength={20} />
          <label htmlFor="serviceId">Service (facultatif)</label>
          <select id="serviceId" value={nouveau.serviceId} onChange={(e) => setNouveau({ ...nouveau, serviceId: e.target.value })}>
            <option value="">— Non renseigné —</option>
            {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
          </select>

          <label htmlFor="avecCompte">
            <input
              id="avecCompte"
              type="checkbox"
              checked={nouveau.avecCompte}
              onChange={(e) => setNouveau({ ...nouveau, avecCompte: e.target.checked })}
            />
            {" "}Cette personne dispose d'un compte applicatif
          </label>
          {nouveau.avecCompte && (
            <>
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
            </>
          )}
          <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer"}</button>
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
  utilisateur: UtilisateurDto;
  services: { id: number; libelle: string }[];
  onErreur: (message: string) => void;
  onSucces: () => void;
}) {
  const [nom, setNom] = useState(utilisateur.nom ?? "");
  const [prenom, setPrenom] = useState(utilisateur.prenom ?? "");
  const [matricule, setMatricule] = useState(utilisateur.matricule ?? "");
  const [identifiant, setIdentifiant] = useState(utilisateur.identifiant ?? "");
  const [email, setEmail] = useState(utilisateur.email ?? "");
  const [serviceId, setServiceId] = useState(utilisateur.serviceId != null ? String(utilisateur.serviceId) : "");
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState("");
  const [nouveauCompte, setNouveauCompte] = useState({ identifiant: "", email: "", motDePasse: "" });

  const majIdentite = useMutation({
    mutationFn: () => modifierIdentite(utilisateur.id, { nom, prenom, matricule: matricule || null }),
    onSuccess: onSucces,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de modifier l'identité.")),
  });
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
  const ajoutCompte = useMutation({
    mutationFn: () => ajouterCompteApplicatif(utilisateur.id, {
      nom: utilisateur.nom ?? "", prenom: utilisateur.prenom ?? "", matricule: utilisateur.matricule,
      identifiant: nouveauCompte.identifiant, email: nouveauCompte.email, motDePasse: nouveauCompte.motDePasse,
      serviceId: utilisateur.serviceId,
    }),
    onSuccess: onSucces,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible d'ajouter un compte applicatif.")),
  });

  return (
    <div className="panneau-imbrique">
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majIdentite.mutate(); }}>
        <label htmlFor={`nom-${utilisateur.id}`}>Nom</label>
        <input id={`nom-${utilisateur.id}`} value={nom} onChange={(e) => setNom(e.target.value)} maxLength={100} required />
        <label htmlFor={`prenom-${utilisateur.id}`}>Prénom</label>
        <input id={`prenom-${utilisateur.id}`} value={prenom} onChange={(e) => setPrenom(e.target.value)} maxLength={100} required />
        <label htmlFor={`matricule-${utilisateur.id}`}>Matricule</label>
        <input id={`matricule-${utilisateur.id}`} value={matricule} onChange={(e) => setMatricule(e.target.value)} maxLength={20} />
        <button type="submit" disabled={majIdentite.isPending}>Enregistrer</button>
      </form>
      {utilisateur.possedeCompteApplicatif ? (
        <>
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
        </>
      ) : (
        <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); ajoutCompte.mutate(); }}>
          <em>Cette personne ne dispose pas encore d'un compte applicatif.</em>
          <label htmlFor={`nouvel-identifiant-${utilisateur.id}`}>Identifiant</label>
          <input id={`nouvel-identifiant-${utilisateur.id}`} value={nouveauCompte.identifiant}
                 onChange={(e) => setNouveauCompte({ ...nouveauCompte, identifiant: e.target.value })} maxLength={60} required />
          <label htmlFor={`nouvel-email-${utilisateur.id}`}>E-mail</label>
          <input id={`nouvel-email-${utilisateur.id}`} type="email" value={nouveauCompte.email}
                 onChange={(e) => setNouveauCompte({ ...nouveauCompte, email: e.target.value })} maxLength={150} required />
          <ChampMotDePasse
            id={`nouveau-mdp-${utilisateur.id}`}
            libelle="Mot de passe initial (12 caractères minimum)"
            valeur={nouveauCompte.motDePasse}
            onChange={(v) => setNouveauCompte({ ...nouveauCompte, motDePasse: v })}
            autoComplete="new-password"
            required
            minLength={12}
          />
          <button type="submit" disabled={ajoutCompte.isPending}>Ajouter un compte applicatif</button>
        </form>
      )}
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); majService.mutate(); }}>
        <label htmlFor={`service-${utilisateur.id}`}>Service</label>
        <select id={`service-${utilisateur.id}`} value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
          <option value="">— Non renseigné —</option>
          {services.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <button type="submit" disabled={majService.isPending}>Enregistrer</button>
      </form>
    </div>
  );
}

function PanneauHabilitations({ utilisateurId, services, roles, onErreur }: {
  utilisateurId: number;
  services: { id: number; libelle: string }[];
  roles: CodeRoleMetier[];
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
  const changementService = useMutation({
    mutationFn: ({ id, nouveauServiceId }: { id: number; nouveauServiceId: number }) => changerServiceHabilitation(id, nouveauServiceId),
    onSuccess: invalider,
    onError: (e) => onErreur(extraireMessageErreur(e, "Impossible de changer le service de cette habilitation.")),
  });

  const perimetreGlobal = ROLES_PERIMETRE_GLOBAL.has(nouvelleHabilitation.role);

  return (
    <div className="panneau-imbrique">
      <EtatAsync chargement={habilitations.isLoading} erreur={habilitations.error} donnees={habilitations.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>Rôle</th><th>Service</th><th>Actif</th><th></th><th></th></tr></thead>
            <tbody>
              {liste.map((h) => (
                <tr key={h.id}>
                  <td>{LIBELLES_ROLE[h.roleMetierCode]}</td>
                  <td>{h.serviceLibelle ?? "Global"}</td>
                  <td>{h.actif ? "Oui" : "Non"}</td>
                  <td>{h.actif && <button type="button" onClick={() => retrait.mutate(h.id)}>Retirer</button>}</td>
                  <td>
                    {h.actif && ROLES_SERVICE_EXCLUSIF.has(h.roleMetierCode) && (
                      <CelluleChangerService
                        habilitation={h}
                        services={services}
                        enCours={changementService.isPending}
                        onValider={(nouveauServiceId) => changementService.mutate({ id: h.id, nouveauServiceId })}
                      />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); attribution.mutate(); }}>
        <select value={nouvelleHabilitation.role} onChange={(e) => setNouvelleHabilitation({ ...nouvelleHabilitation, role: e.target.value as CodeRoleMetier })}>
          {roles.map((r) => <option key={r} value={r}>{LIBELLES_ROLE[r]}</option>)}
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

/**
 * Reaffectation d'une habilitation Charge d'Affaires / Personne habilitée /
 * Responsable d'activité vers un autre service, en une seule action tracée
 * (évolution du 2026-08-19, section 10) - remplace le retrait suivi d'une
 * nouvelle attribution, qui rendrait la reaffectation moins explicite dans
 * l'historique d'audit et laisserait, entre les deux appels, un instant sans
 * aucune habilitation active pour ce rôle.
 */
function CelluleChangerService({ habilitation, services, enCours, onValider }: {
  habilitation: HabilitationDto;
  services: { id: number; libelle: string }[];
  enCours: boolean;
  onValider: (nouveauServiceId: number) => void;
}) {
  const [nouveauServiceId, setNouveauServiceId] = useState("");
  const autresServices = services.filter((s) => s.id !== habilitation.serviceId);

  return (
    <span className="formulaire-ligne">
      <select value={nouveauServiceId} onChange={(e) => setNouveauServiceId(e.target.value)}>
        <option value="">— Changer de service —</option>
        {autresServices.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
      </select>
      <button
        type="button"
        disabled={enCours || !nouveauServiceId}
        onClick={() => onValider(Number(nouveauServiceId))}
      >
        Changer
      </button>
    </span>
  );
}
