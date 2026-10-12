import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  creerChantier, creerCodeHN, creerService, creerVehicule, listerChantiers, listerCodesHN,
  listerServices, listerVehicules,
} from "../../api/referentielApi";
import { extraireMessageErreur } from "../../api/httpClient";
import AdminNav from "../../components/AdminNav";
import { EtatAsync } from "../../components/EtatAsync";
import { Icone } from "../../components/Icone";
import type { TypeVehicule } from "../../types/referentiel";

/** Referentiels partages (services, chantiers, codes mission, vehicules) - creation reservee a l'Administrateur. */
export default function AdminReferentielsPage() {
  const { t } = useTranslation();
  return (
    <div>
      <h1>{t("Administration")}</h1>
      <AdminNav />
      <h2>{t("Référentiels")}</h2>
      <SectionServices />
      <SectionChantiers />
      <SectionCodesHN />
      <SectionVehicules />
    </div>
  );
}

function SectionServices() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices });
  const [valeurs, setValeurs] = useState({ codeService: "", libelle: "" });
  const [erreur, setErreur] = useState<string | null>(null);
  const creation = useMutation({
    mutationFn: () => creerService(valeurs),
    onSuccess: () => { setValeurs({ codeService: "", libelle: "" }); void queryClient.invalidateQueries({ queryKey: ["services"] }); },
    onError: (e) => setErreur(extraireMessageErreur(e, t("Impossible de créer ce service."))),
  });
  return (
    <section>
      <h2>{t("Services")}</h2>
      {erreur && <p role="alert">{erreur}</p>}
      <EtatAsync chargement={services.isLoading} erreur={services.error} donnees={services.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>{t("Code")}</th><th>{t("Libellé")}</th></tr></thead>
            <tbody>{liste.map((s) => <tr key={s.id}><td>{s.codeService}</td><td>{s.libelle}</td></tr>)}</tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <input placeholder={t("Code")} value={valeurs.codeService} onChange={(e) => setValeurs({ ...valeurs, codeService: e.target.value })} required maxLength={20} />
        <input placeholder={t("Libellé")} value={valeurs.libelle} onChange={(e) => setValeurs({ ...valeurs, libelle: e.target.value })} required maxLength={150} />
        <button type="submit" disabled={creation.isPending}><Icone nom="plus" taille={16} />{t("Ajouter")}</button>
      </form>
    </section>
  );
}

function SectionChantiers() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const chantiers = useQuery({ queryKey: ["chantiers"], queryFn: listerChantiers });
  const [valeurs, setValeurs] = useState({ codeAffaire: "", libelle: "" });
  const [erreur, setErreur] = useState<string | null>(null);
  const creation = useMutation({
    mutationFn: () => creerChantier(valeurs),
    onSuccess: () => { setValeurs({ codeAffaire: "", libelle: "" }); void queryClient.invalidateQueries({ queryKey: ["chantiers"] }); },
    onError: (e) => setErreur(extraireMessageErreur(e, t("Impossible de créer ce chantier."))),
  });
  return (
    <section>
      <h2>{t("Chantiers")}</h2>
      {erreur && <p role="alert">{erreur}</p>}
      <EtatAsync chargement={chantiers.isLoading} erreur={chantiers.error} donnees={chantiers.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>{t("Code affaire")}</th><th>{t("Libellé")}</th></tr></thead>
            <tbody>{liste.map((c) => <tr key={c.id}><td>{c.codeAffaire}</td><td>{c.libelle}</td></tr>)}</tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <input placeholder={t("Code affaire")} value={valeurs.codeAffaire} onChange={(e) => setValeurs({ ...valeurs, codeAffaire: e.target.value })} required maxLength={30} />
        <input placeholder={t("Libellé")} value={valeurs.libelle} onChange={(e) => setValeurs({ ...valeurs, libelle: e.target.value })} required maxLength={150} />
        <button type="submit" disabled={creation.isPending}><Icone nom="plus" taille={16} />{t("Ajouter")}</button>
      </form>
    </section>
  );
}

function SectionCodesHN() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const codesHN = useQuery({ queryKey: ["codes-hn"], queryFn: listerCodesHN });
  const chantiers = useQuery({ queryKey: ["chantiers"], queryFn: listerChantiers });
  const [valeurs, setValeurs] = useState({ code: "", libelle: "", chantierId: "" });
  const [erreur, setErreur] = useState<string | null>(null);
  const creation = useMutation({
    mutationFn: () => creerCodeHN({ code: valeurs.code, libelle: valeurs.libelle, chantierId: Number(valeurs.chantierId) }),
    onSuccess: () => { setValeurs({ code: "", libelle: "", chantierId: "" }); void queryClient.invalidateQueries({ queryKey: ["codes-hn"] }); },
    onError: (e) => setErreur(extraireMessageErreur(e, t("Impossible de créer ce code mission."))),
  });
  return (
    <section>
      <h2>{t("Codes mission (Code HN)")}</h2>
      {erreur && <p role="alert">{erreur}</p>}
      <EtatAsync chargement={codesHN.isLoading} erreur={codesHN.error} donnees={codesHN.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>{t("Code")}</th><th>{t("Libellé")}</th><th>{t("Chantier")}</th></tr></thead>
            <tbody>{liste.map((c) => <tr key={c.id}><td>{c.code}</td><td>{c.libelle}</td><td>{c.chantierLibelle}</td></tr>)}</tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <input placeholder={t("Code")} value={valeurs.code} onChange={(e) => setValeurs({ ...valeurs, code: e.target.value })} required maxLength={30} />
        <input placeholder={t("Libellé")} value={valeurs.libelle} onChange={(e) => setValeurs({ ...valeurs, libelle: e.target.value })} required maxLength={150} />
        <select value={valeurs.chantierId} onChange={(e) => setValeurs({ ...valeurs, chantierId: e.target.value })} required>
          <option value="">{t("— Chantier —")}</option>
          {chantiers.data?.map((c) => <option key={c.id} value={c.id}>{c.libelle}</option>)}
        </select>
        <button type="submit" disabled={creation.isPending}><Icone nom="plus" taille={16} />{t("Ajouter")}</button>
      </form>
    </section>
  );
}

function SectionVehicules() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const vehicules = useQuery({ queryKey: ["vehicules"], queryFn: listerVehicules });
  const [valeurs, setValeurs] = useState<{ immatriculation: string; type: TypeVehicule }>({ immatriculation: "", type: "OMNIUM_SERVICE" });
  const [erreur, setErreur] = useState<string | null>(null);
  const creation = useMutation({
    mutationFn: () => creerVehicule(valeurs),
    onSuccess: () => { setValeurs({ immatriculation: "", type: "OMNIUM_SERVICE" }); void queryClient.invalidateQueries({ queryKey: ["vehicules"] }); },
    onError: (e) => setErreur(extraireMessageErreur(e, t("Impossible de créer ce véhicule."))),
  });
  return (
    <section>
      <h2>{t("Véhicules")}</h2>
      {erreur && <p role="alert">{erreur}</p>}
      <EtatAsync chargement={vehicules.isLoading} erreur={vehicules.error} donnees={vehicules.data}>
        {(liste) => (
          <table className="tableau tableau--compact">
            <thead><tr><th>{t("Immatriculation")}</th><th>{t("Type")}</th></tr></thead>
            <tbody>{liste.map((v) => <tr key={v.id}><td>{v.immatriculation}</td><td>{v.type === "OMNIUM_SERVICE" ? t("Omnium service") : t("Personnel")}</td></tr>)}</tbody>
          </table>
        )}
      </EtatAsync>
      <form className="formulaire-ligne" onSubmit={(e) => { e.preventDefault(); creation.mutate(); }}>
        <input placeholder={t("Immatriculation")} value={valeurs.immatriculation} onChange={(e) => setValeurs({ ...valeurs, immatriculation: e.target.value })} required maxLength={20} />
        <select value={valeurs.type} onChange={(e) => setValeurs({ ...valeurs, type: e.target.value as TypeVehicule })}>
          <option value="OMNIUM_SERVICE">{t("Omnium service")}</option>
          <option value="PERSONNEL">{t("Personnel")}</option>
        </select>
        <button type="submit" disabled={creation.isPending}><Icone nom="plus" taille={16} />{t("Ajouter")}</button>
      </form>
    </section>
  );
}
