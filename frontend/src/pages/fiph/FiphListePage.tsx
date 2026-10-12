import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { creerFiphManuelle, listerFiph, listerPersonnelDuServicePourFiph } from "../../api/fiphApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMissions } from "../../api/missionApi";
import { listerServices } from "../../api/referentielApi";
import { EtatAsync } from "../../components/EtatAsync";
import { Icone } from "../../components/Icone";
import { SelectionPersonnelService } from "../../components/SelectionPersonnelService";
import { BadgeStatutFiph } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import {
  LIBELLES_CATEGORIE_FIPH, LIBELLES_STATUT_FIPH, STATUTS_PAR_CATEGORIE, type CategorieFiph,
  type ResultatCreationFiphDto, type StatutFiphVersion,
} from "../../types/fiph";

const STATUTS = Object.keys(LIBELLES_STATUT_FIPH) as StatutFiphVersion[];

/**
 * Liste des FIPH visibles (perimetre applique cote serveur), avec filtres
 * combinables date/periode/statut/service/mission (evolution du 2026-08-18,
 * etendue le 2026-08-27) - le filtrage est realise cote backend, pas en
 * chargeant tout puis en filtrant en React.
 *
 * <p>Le parametre d'URL {@code categorie} (section 16-18 : sous-menus du
 * menu lateral) presélectionne un regroupement de statuts reellement
 * atteignables - voir {@code STATUTS_PAR_CATEGORIE}. Reste combinable avec
 * les autres filtres (nom, service, mission...).
 */
export default function FiphListePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const categorieUrl = searchParams.get("categorie") as CategorieFiph | null;
  const [filtres, setFiltres] = useState({
    date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "", nomComplet: "", mission: "",
  });
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const requete = useQuery({
    queryKey: ["fiph", filtres, categorieUrl],
    queryFn: () => listerFiph({
      date: filtres.date || undefined,
      dateDebut: filtres.dateDebut || undefined,
      dateFin: filtres.dateFin || undefined,
      statut: filtres.statut || undefined,
      statuts: categorieUrl ? STATUTS_PAR_CATEGORIE[categorieUrl] : undefined,
      serviceId: filtres.serviceId ? Number(filtres.serviceId) : undefined,
      nomComplet: filtres.nomComplet || undefined,
      mission: filtres.mission || undefined,
    }),
  });
  const { aLeRole } = useAuth();

  function reinitialiserFiltres() {
    setFiltres({ date: "", dateDebut: "", dateFin: "", statut: "", serviceId: "", nomComplet: "", mission: "" });
  }

  return (
    <div>
      <div className="page-entete">
        <h1>{t("FIPH — Fiches Individuelles de Pointage Hebdomadaire")}</h1>
      </div>
      {categorieUrl && (
        <p className="note">
          {t("Catégorie :")} <strong>{t(LIBELLES_CATEGORIE_FIPH[categorieUrl])}</strong> — <Link to="/fiph">{t("voir toutes les FIPH")}</Link>
        </p>
      )}

      <section className="barre-filtres">
        <label htmlFor="filtreDateFiph">{t("Date exacte")}</label>
        <input id="filtreDateFiph" type="date" value={filtres.date} onChange={(e) => setFiltres({ ...filtres, date: e.target.value, dateDebut: "", dateFin: "" })} />
        <label htmlFor="filtreDateDebutFiph">{t("Du")}</label>
        <input id="filtreDateDebutFiph" type="date" value={filtres.dateDebut} onChange={(e) => setFiltres({ ...filtres, dateDebut: e.target.value, date: "" })} />
        <label htmlFor="filtreDateFinFiph">{t("Au")}</label>
        <input id="filtreDateFinFiph" type="date" value={filtres.dateFin} onChange={(e) => setFiltres({ ...filtres, dateFin: e.target.value, date: "" })} />
        <label htmlFor="filtreNomCompletFiph">{t("Nom complet")}</label>
        <div className="champ-icone">
          <Icone nom="recherche" taille={16} />
          <input
            id="filtreNomCompletFiph"
            type="text"
            placeholder={t("Rechercher un agent...")}
            value={filtres.nomComplet}
            onChange={(e) => setFiltres({ ...filtres, nomComplet: e.target.value })}
          />
        </div>
        <label htmlFor="filtreMissionFiph">{t("Mission")}</label>
        <div className="champ-icone">
          <Icone nom="recherche" taille={16} />
          <input
            id="filtreMissionFiph"
            type="text"
            placeholder={t("Code ou nom de la mission...")}
            value={filtres.mission}
            onChange={(e) => setFiltres({ ...filtres, mission: e.target.value })}
          />
        </div>
        {!categorieUrl && (
          <>
            <label htmlFor="filtreStatutFiph">{t("Statut")}</label>
            <select id="filtreStatutFiph" value={filtres.statut} onChange={(e) => setFiltres({ ...filtres, statut: e.target.value })}>
              <option value="">{t("— Tous —")}</option>
              {STATUTS.map((s) => <option key={s} value={s}>{t(LIBELLES_STATUT_FIPH[s])}</option>)}
            </select>
          </>
        )}
        <label htmlFor="filtreServiceFiph">{t("Service")}</label>
        <select id="filtreServiceFiph" value={filtres.serviceId} onChange={(e) => setFiltres({ ...filtres, serviceId: e.target.value })}>
          <option value="">{t("— Tous —")}</option>
          {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
        </select>
        <button type="button" onClick={reinitialiserFiltres}>{t("Réinitialiser les filtres")}</button>
      </section>

      <EtatAsync chargement={requete.isLoading} erreur={requete.error} donnees={requete.data}>
        {(liste) => {
          if (liste.length === 0) {
            return <p>{t("Aucune FIPH ne correspond à ces critères.")}</p>;
          }
          const triees = [...liste].sort((a, b) => (b.annee - a.annee) || (b.numeroSemaine - a.numeroSemaine));
          return (
            <table className="tableau">
              <thead>
                <tr><th>{t("Agent")}</th><th>{t("Service")}</th><th>{t("Mission")}</th><th>{t("Semaine")}</th><th>{t("Origine")}</th><th>{t("Version")}</th><th>{t("Statut")}</th><th></th></tr>
              </thead>
              <tbody>
                {triees.map((f) => (
                  <tr key={f.id}>
                    <td>{f.agentNomComplet} ({f.agentMatricule})</td>
                    <td>{f.serviceLibelle}</td>
                    <td>
                      {f.missionCodeHN ? `${f.missionCodeHN} — ${f.missionChantierLibelle}` : "—"}
                      {f.avertissementMission && <span title={f.avertissementMission}> ⚠</span>}
                    </td>
                    <td>S{f.numeroSemaine} / {f.annee}</td>
                    <td>{f.origine === "BON_SORTIE" ? t("Bon de sortie") : t("Manuelle")}</td>
                    <td>v{f.versionCouranteNumero}</td>
                    <td><BadgeStatutFiph statut={f.statut} libelle={t(LIBELLES_STATUT_FIPH[f.statut])} /></td>
                    <td><Link to={`/fiph/${f.id}`}>{t("Ouvrir")}</Link></td>
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
 * Creation manuelle EN LOT d'une ou plusieurs FIPH (Code Service, RG-FIPH-004
 * - evolution du 2026-08-27, section 2-3-4-14) : le personnel du service est
 * selectionne par cases a cocher (jamais une saisie libre d'identifiant), et
 * une mission peut etre explicitement associee (Code Mission, section 6-7-8).
 * L'echec pour l'un des agents selectionnes (ex. periode deja couverte)
 * n'empeche jamais la creation pour les autres - le resultat distingue
 * clairement les creations reussies des echecs.
 */
function SectionCreationManuelle() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { utilisateur, aLeRole } = useAuth();
  const peutChoisirService = aLeRole("RH") || aLeRole("SUPER_ADMINISTRATEUR");
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices, enabled: peutChoisirService });
  const missions = useQuery({ queryKey: ["missions"], queryFn: listerMissions });
  const [serviceId, setServiceId] = useState<string>(utilisateur?.serviceId ? String(utilisateur.serviceId) : "");
  const serviceEffectif = peutChoisirService ? (serviceId ? Number(serviceId) : null) : (utilisateur?.serviceId ?? null);
  const [agentsSelectionnes, setAgentsSelectionnes] = useState<Set<number>>(new Set());
  const [missionId, setMissionId] = useState("");
  const [dateDebut, setDateDebut] = useState("");
  const [dateFin, setDateFin] = useState("");
  const [resultat, setResultat] = useState<ResultatCreationFiphDto | null>(null);

  const creation = useMutation({
    mutationFn: () => creerFiphManuelle({
      agentIds: [...agentsSelectionnes], dateDebut, dateFin: dateFin || null,
      missionId: missionId ? Number(missionId) : null,
    }),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: ["fiph"] });
      setResultat(res);
      if (res.creees.length === 1 && res.echecs.length === 0) {
        navigate(`/fiph/${res.creees[0].id}`);
      }
    },
  });

  return (
    <section>
      <h2>{t("Créer une ou plusieurs FIPH manuelles (Code Service)")}</h2>
      <p className="dashboard-accueil">{t("Pour un ou plusieurs agents non concernés par un bon de sortie durant la période.")}</p>
      <p className="dashboard-accueil">
        {t("La date de fin est optionnelle : la période reste \"ouverte\" tant qu'elle n'est pas définie (ajustable ensuite), mais elle devra être renseignée avant toute soumission au circuit de validation.")}
      </p>
      {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, t("Impossible de créer ces FIPH."))}</p>}

      <form className="formulaire" onSubmit={(e) => { e.preventDefault(); setResultat(null); creation.mutate(); }}>
        {peutChoisirService && (
          <>
            <label htmlFor="serviceIdFiphManuelle">{t("Service")}</label>
            <select id="serviceIdFiphManuelle" value={serviceId}
                    onChange={(e) => { setServiceId(e.target.value); setAgentsSelectionnes(new Set()); }}>
              <option value="">{t("— Sélectionner —")}</option>
              {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
            </select>
          </>
        )}

        <h3>{t("Personnel du service")}</h3>
        <SelectionPersonnelService
          serviceId={serviceEffectif}
          mode="multiple"
          selection={agentsSelectionnes}
          onChange={setAgentsSelectionnes}
          chargerPersonnel={listerPersonnelDuServicePourFiph}
        />

        <label htmlFor="missionIdFiphManuelle">{t("Code Mission (facultatif)")}</label>
        <select id="missionIdFiphManuelle" value={missionId} onChange={(e) => setMissionId(e.target.value)}>
          <option value="">{t("— Non renseigné —")}</option>
          {missions.data?.map((m) => (
            <option key={m.id} value={m.id}>{m.codeHN} — {m.chantierLibelle} ({m.dateDebutPrevue} → {m.dateFinPrevue})</option>
          ))}
        </select>

        <label htmlFor="dateDebutFiphManuelle">{t("Date de début")}</label>
        <input id="dateDebutFiphManuelle" type="date" value={dateDebut} onChange={(e) => setDateDebut(e.target.value)} required />
        <label htmlFor="dateFinFiphManuelle">{t("Date de fin (optionnelle)")}</label>
        <input id="dateFinFiphManuelle" type="date" value={dateFin} onChange={(e) => setDateFin(e.target.value)} />

        <button type="submit" disabled={creation.isPending || agentsSelectionnes.size === 0}>
          {creation.isPending ? t("Création...") : `${t("Créer")} ${agentsSelectionnes.size > 1 ? `(${agentsSelectionnes.size} ${t("agents")})` : ""}`}
        </button>
      </form>

      {resultat && (
        <div className="panneau-imbrique">
          {resultat.creees.length > 0 && (
            <p>
              ✅ {resultat.creees.length} {t("FIPH créée(s) :")}{" "}
              {resultat.creees.map((f, i) => (
                <span key={f.id}>
                  {i > 0 && ", "}
                  <Link to={`/fiph/${f.id}`}>{f.agentNomComplet}</Link>
                </span>
              ))}
            </p>
          )}
          {resultat.echecs.length > 0 && (
            <div role="alert">
              <p>{resultat.echecs.length} {t("échec(s) :")}</p>
              <ul>
                {resultat.echecs.map((e) => (
                  <li key={e.agentId}>{e.agentNomComplet ?? `${t("Agent")} #${e.agentId}`} — {e.motif}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
