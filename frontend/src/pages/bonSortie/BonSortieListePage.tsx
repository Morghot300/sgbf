import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { listerBonsSortie } from "../../api/bonSortieApi";
import { listerServices } from "../../api/referentielApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutBonSortie } from "../../components/StatutBadge";
import { LIBELLES_MOYEN_UTILISE, LIBELLES_STATUT_BON_SORTIE, type StatutBonSortie } from "../../types/bonSortie";

const STATUTS: StatutBonSortie[] = ["BROUILLON", "VISE", "VALIDE"];

/**
 * Liste des bons de sortie visibles par l'utilisateur courant (perimetre
 * applique cote serveur), avec filtres combinables date/periode/statut/
 * service (evolution du 2026-08-18) - le filtrage est realise cote backend
 * (parametres de requete), pas en chargeant tout puis en filtrant en React.
 */
export default function BonSortieListePage() {
  const [filtres, setFiltres] = useState({ date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "" });
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const requete = useQuery({
    queryKey: ["bons-sortie", filtres],
    queryFn: () => listerBonsSortie({
      date: filtres.date || undefined,
      dateDebut: filtres.dateDebut || undefined,
      dateFin: filtres.dateFin || undefined,
      statut: filtres.statut || undefined,
      serviceId: filtres.serviceId ? Number(filtres.serviceId) : undefined,
    }),
  });

  function reinitialiserFiltres() {
    setFiltres({ date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "" });
  }

  return (
    <div>
      <div className="page-entete">
        <h1>Bons de sortie</h1>
        <Link to="/bons-sortie/nouveau" className="bouton-principal">Nouveau bon de sortie</Link>
      </div>

      <section className="barre-filtres">
        <label htmlFor="filtreDate">Date exacte</label>
        <input id="filtreDate" type="date" value={filtres.date} onChange={(e) => setFiltres({ ...filtres, date: e.target.value, dateDebut: "", dateFin: "" })} />
        <label htmlFor="filtreDateDebut">Du</label>
        <input id="filtreDateDebut" type="date" value={filtres.dateDebut} onChange={(e) => setFiltres({ ...filtres, dateDebut: e.target.value, date: "" })} />
        <label htmlFor="filtreDateFin">Au</label>
        <input id="filtreDateFin" type="date" value={filtres.dateFin} onChange={(e) => setFiltres({ ...filtres, dateFin: e.target.value, date: "" })} />
        <label htmlFor="filtreStatutBs">Statut</label>
        <select id="filtreStatutBs" value={filtres.statut} onChange={(e) => setFiltres({ ...filtres, statut: e.target.value })}>
          <option value="">— Tous —</option>
          {STATUTS.map((s) => <option key={s} value={s}>{LIBELLES_STATUT_BON_SORTIE[s]}</option>)}
        </select>
        <label htmlFor="filtreServiceBs">Service</label>
        <select id="filtreServiceBs" value={filtres.serviceId} onChange={(e) => setFiltres({ ...filtres, serviceId: e.target.value })}>
          <option value="">— Tous —</option>
          {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <button type="button" onClick={reinitialiserFiltres}>Réinitialiser les filtres</button>
      </section>

      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => {
          if (liste.length === 0) {
            return <p>Aucun bon de sortie ne correspond à ces critères.</p>;
          }
          const parDateDecroissante = [...liste].sort((a, b) => b.dateSortie.localeCompare(a.dateSortie));
          return (
            <table className="tableau">
              <thead>
                <tr>
                  <th>Référence</th><th>Agent</th><th>Date</th><th>Moyen</th><th>Motif</th><th>Statut</th><th></th>
                </tr>
              </thead>
              <tbody>
                {parDateDecroissante.map((bs) => (
                  <tr key={bs.id}>
                    <td>#{bs.id}</td>
                    <td>{bs.agentNomComplet}</td>
                    <td>{bs.dateSortie}</td>
                    <td>{LIBELLES_MOYEN_UTILISE[bs.moyenUtilise]}</td>
                    <td>{bs.motifSortie}</td>
                    <td><BadgeStatutBonSortie statut={bs.statut} libelle={LIBELLES_STATUT_BON_SORTIE[bs.statut]} /></td>
                    <td><Link to={`/bons-sortie/${bs.id}`}>Ouvrir</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          );
        }}
      </EtatAsync>
    </div>
  );
}
