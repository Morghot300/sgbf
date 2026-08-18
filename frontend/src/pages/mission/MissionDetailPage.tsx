import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useParams } from "react-router-dom";
import {
  affecterAgent, historiqueMission, interrompreAffectation, listerAffectations, obtenirMission,
} from "../../api/missionApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMotifsInterruption } from "../../api/referentielApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutAffectation, BadgeStatutMission } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_STATUT_AFFECTATION, LIBELLES_STATUT_MISSION } from "../../types/mission";

export default function MissionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const missionId = Number(id);
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  const [erreur, setErreur] = useState<string | null>(null);
  const [agentId, setAgentId] = useState("");
  const [dateDebut, setDateDebut] = useState("");
  const [interruption, setInterruption] = useState<{ affectationId: number; motifCode: string; commentaire: string } | null>(null);

  const mission = useQuery({ queryKey: ["mission", missionId], queryFn: () => obtenirMission(missionId) });
  const affectations = useQuery({ queryKey: ["affectations", missionId], queryFn: () => listerAffectations(missionId) });
  const motifs = useQuery({ queryKey: ["motifs-interruption"], queryFn: listerMotifsInterruption });
  const historique = useQuery({ queryKey: ["mission-historique", missionId], queryFn: () => historiqueMission(missionId) });

  const peutGerer = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE");

  function invalider() {
    void queryClient.invalidateQueries({ queryKey: ["affectations", missionId] });
  }

  const affecter = useMutation({
    mutationFn: () => affecterAgent({ agentId: Number(agentId), missionId, dateDebutAffectation: dateDebut }),
    onSuccess: () => { setAgentId(""); setDateDebut(""); invalider(); },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible d'affecter cet agent.")),
  });
  const interrompre = useMutation({
    mutationFn: () => interrompreAffectation(interruption!.affectationId, {
      motifCode: interruption!.motifCode,
      dateInterruption: new Date().toISOString().slice(0, 10),
      commentaire: interruption!.commentaire || null,
    }),
    onSuccess: () => { setInterruption(null); invalider(); },
    onError: (e) => setErreur(extraireMessageErreur(e, "Impossible d'interrompre cette affectation.")),
  });

  return (
    <div>
      <h1>Mission #{missionId}</h1>
      <EtatAsync chargement={mission.isLoading} erreur={mission.error} donnees={mission.data}>
        {(m) => (
          <table className="fiche">
            <tbody>
              <tr><th>Code mission</th><td>{m.codeHN} — {m.codeHNLibelle}</td></tr>
              <tr><th>Chantier</th><td>{m.chantierLibelle}</td></tr>
              <tr><th>Statut</th><td><BadgeStatutMission statut={m.statut} libelle={LIBELLES_STATUT_MISSION[m.statut]} /></td></tr>
              <tr><th>Période prévue</th><td>{m.dateDebutPrevue} → {m.dateFinPrevue}</td></tr>
              {m.dateFinReelle && <tr><th>Fin réelle</th><td>{m.dateFinReelle}</td></tr>}
            </tbody>
          </table>
        )}
      </EtatAsync>

      {erreur && <p role="alert">{erreur}</p>}

      <h2>Affectations</h2>
      <EtatAsync chargement={affectations.isLoading} erreur={affectations.error} donnees={affectations.data}>
        {(liste) => (
          <table className="tableau">
            <thead><tr><th>Agent</th><th>Début</th><th>Fin</th><th>Statut</th><th>Motif interruption</th>{peutGerer && <th></th>}</tr></thead>
            <tbody>
              {liste.map((a) => (
                <tr key={a.id}>
                  <td>{a.agentNomComplet} ({a.agentMatricule})</td>
                  <td>{a.dateDebutAffectation}</td>
                  <td>{a.dateFinAffectation ?? "—"}</td>
                  <td><BadgeStatutAffectation statut={a.statutAffectation} libelle={LIBELLES_STATUT_AFFECTATION[a.statutAffectation]} /></td>
                  <td>{a.motifInterruptionLibelle ?? "—"}</td>
                  {peutGerer && (
                    <td>
                      {a.statutAffectation === "ACTIVE" && (
                        <button type="button" onClick={() => setInterruption({ affectationId: a.id, motifCode: "", commentaire: "" })}>
                          Interrompre
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </EtatAsync>

      {interruption && (
        <section>
          <h3>Interrompre l'affectation #{interruption.affectationId}</h3>
          <label htmlFor="motifCode">Motif</label>
          <select id="motifCode" value={interruption.motifCode} onChange={(e) => setInterruption({ ...interruption, motifCode: e.target.value })}>
            <option value="">— Sélectionner —</option>
            {motifs.data?.map((mo) => <option key={mo.id} value={mo.code}>{mo.libelle}</option>)}
          </select>
          <label htmlFor="commentaire">Commentaire (obligatoire si motif "Autre")</label>
          <textarea id="commentaire" maxLength={500} value={interruption.commentaire} onChange={(e) => setInterruption({ ...interruption, commentaire: e.target.value })} />
          <div className="barre-actions">
            <button type="button" onClick={() => interrompre.mutate()} disabled={interrompre.isPending || !interruption.motifCode}>Confirmer l'interruption</button>
            <button type="button" onClick={() => setInterruption(null)}>Annuler</button>
          </div>
        </section>
      )}

      {peutGerer && (
        <section>
          <h3>Affecter un agent</h3>
          <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); affecter.mutate(); }}>
            <label htmlFor="agentId">Identifiant de l'agent</label>
            <input id="agentId" type="number" value={agentId} onChange={(e) => setAgentId(e.target.value)} required />
            <label htmlFor="dateDebut">Date de début</label>
            <input id="dateDebut" type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} required />
            <button type="submit" disabled={affecter.isPending}>Affecter</button>
          </form>
        </section>
      )}

      <EtatAsync chargement={historique.isLoading} erreur={historique.error} donnees={historique.data}>
        {(liste) => liste.length > 1 && (
          <section>
            <h2>Historique de la mission</h2>
            <p className="dashboard-accueil">Chaîne des missions liées entre elles par prolongation (mission_precedente_id).</p>
            <table className="tableau tableau--compact">
              <thead><tr><th>Code mission</th><th>Chantier</th><th>Période prévue</th><th>Statut</th></tr></thead>
              <tbody>
                {liste.map((m) => (
                  <tr key={m.id}>
                    <td>{m.codeHN}</td>
                    <td>{m.chantierLibelle}</td>
                    <td>{m.dateDebutPrevue} → {m.dateFinPrevue}</td>
                    <td><BadgeStatutMission statut={m.statut} libelle={LIBELLES_STATUT_MISSION[m.statut]} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      </EtatAsync>
    </div>
  );
}
