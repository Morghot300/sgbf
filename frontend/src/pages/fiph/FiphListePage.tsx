import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { creerFiphManuelle, listerFiph } from "../../api/fiphApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerServices } from "../../api/referentielApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutFiph } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_STATUT_FIPH, type StatutFiphVersion } from "../../types/fiph";

const STATUTS = Object.keys(LIBELLES_STATUT_FIPH) as StatutFiphVersion[];

/**
 * Liste des FIPH visibles (perimetre applique cote serveur), avec filtres
 * combinables date/periode/statut/service (evolution du 2026-08-18) - le
 * filtrage est realise cote backend, pas en chargeant tout puis en filtrant
 * en React.
 */
export default function FiphListePage() {
  const [filtres, setFiltres] = useState({ date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "" });
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const requete = useQuery({
    queryKey: ["fiph", filtres],
    queryFn: () => listerFiph({
      date: filtres.date || undefined,
      dateDebut: filtres.dateDebut || undefined,
      dateFin: filtres.dateFin || undefined,
      statut: filtres.statut || undefined,
      serviceId: filtres.serviceId ? Number(filtres.serviceId) : undefined,
    }),
  });
  const { aLeRole } = useAuth();

  function reinitialiserFiltres() {
    setFiltres({ date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "" });
  }

  return (
    <div>
      <h1>FIPH — Fiches Individuelles de Pointage Hebdomadaire</h1>

      <section className="barre-filtres">
        <label htmlFor="filtreDateFiph">Date exacte</label>
        <input id="filtreDateFiph" type="date" value={filtres.date} onChange={(e) => setFiltres({ ...filtres, date: e.target.value, dateDebut: "", dateFin: "" })} />
        <label htmlFor="filtreDateDebutFiph">Du</label>
        <input id="filtreDateDebutFiph" type="date" value={filtres.dateDebut} onChange={(e) => setFiltres({ ...filtres, dateDebut: e.target.value, date: "" })} />
        <label htmlFor="filtreDateFinFiph">Au</label>
        <input id="filtreDateFinFiph" type="date" value={filtres.dateFin} onChange={(e) => setFiltres({ ...filtres, dateFin: e.target.value, date: "" })} />
        <label htmlFor="filtreStatutFiph">Statut</label>
        <select id="filtreStatutFiph" value={filtres.statut} onChange={(e) => setFiltres({ ...filtres, statut: e.target.value })}>
          <option value="">— Tous —</option>
          {STATUTS.map((s) => <option key={s} value={s}>{LIBELLES_STATUT_FIPH[s]}</option>)}
        </select>
        <label htmlFor="filtreServiceFiph">Service</label>
        <select id="filtreServiceFiph" value={filtres.serviceId} onChange={(e) => setFiltres({ ...filtres, serviceId: e.target.value })}>
          <option value="">— Tous —</option>
          {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <button type="button" onClick={reinitialiserFiltres}>Réinitialiser les filtres</button>
      </section>

      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => {
          if (liste.length === 0) {
            return <p>Aucune FIPH ne correspond à ces critères.</p>;
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
  const [valeurs, setValeurs] = useState({ agentId: "", dateDebut: "", dateFin: "" });
  const [erreur, setErreur] = useState<string | null>(null);

  const creation = useMutation({
    mutationFn: () => creerFiphManuelle({
      agentId: Number(valeurs.agentId), dateDebut: valeurs.dateDebut, dateFin: valeurs.dateFin || null,
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
      <p className="dashboard-accueil">
        La date de fin est optionnelle : la période reste "ouverte" tant qu'elle n'est pas définie (ajustable ensuite),
        mais elle devra être renseignée avant toute soumission au circuit de validation.
      </p>
      {erreur && <p role="alert">{erreur}</p>}
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <label htmlFor="agentId">Identifiant de l'agent</label>
        <input id="agentId" type="number" value={valeurs.agentId} onChange={(e) => setValeurs({ ...valeurs, agentId: e.target.value })} required />
        <label htmlFor="dateDebut">Date de début</label>
        <input id="dateDebut" type="date" value={valeurs.dateDebut} onChange={(e) => setValeurs({ ...valeurs, dateDebut: e.target.value })} required />
        <label htmlFor="dateFin">Date de fin (optionnelle)</label>
        <input id="dateFin" type="date" value={valeurs.dateFin} onChange={(e) => setValeurs({ ...valeurs, dateFin: e.target.value })} />
        <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer"}</button>
      </form>
    </section>
  );
}
