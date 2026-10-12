import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { creerMission } from "../../api/missionApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerChantiers, listerCodesHN } from "../../api/referentielApi";

/**
 * Evolution du 2026-08-26 - "les mission et code mission ne seront pas des
 * liste deroulante mais une zone texte ou on ajoutera des mission au clavier" :
 * le chantier et le code mission sont saisis librement (avec suggestion des
 * codes déjà existants via une liste d'autocomplétion native du navigateur -
 * <datalist>) plutôt que choisis dans une liste déroulante figée. Taper un
 * code déjà existant le réutilise tel quel ; taper un code inédit le crée à
 * la volée côté serveur (voir Javadoc de `MissionService#creerMission`).
 */
function construireSchema(t: TFunction) {
  return z.object({
    codeChantier: z.string().min(1, t("Le code chantier est obligatoire.")).max(30),
    libelleChantier: z.string().max(150).optional(),
    codeMission: z.string().min(1, t("Le code mission est obligatoire.")).max(30),
    libelleCodeMission: z.string().max(150).optional(),
    dateDebutPrevue: z.string().min(1, t("La date de début est obligatoire.")),
    dateFinPrevue: z.string().min(1, t("La date de fin est obligatoire.")),
  });
}
type Formulaire = z.infer<ReturnType<typeof construireSchema>>;

export default function MissionNouvellePage() {
  const { t } = useTranslation();
  const schema = useMemo(() => construireSchema(t), [t]);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const chantiers = useQuery({ queryKey: ["chantiers"], queryFn: listerChantiers });
  const codesHN = useQuery({ queryKey: ["codes-hn"], queryFn: listerCodesHN });

  const { register, handleSubmit, control, formState: { errors } } = useForm<Formulaire>({ resolver: zodResolver(schema) });
  const codeChantierSaisi = useWatch({ control, name: "codeChantier" });
  const codeMissionSaisi = useWatch({ control, name: "codeMission" });
  const chantierExistant = chantiers.data?.some((c) => c.codeAffaire === codeChantierSaisi);
  const codeMissionExistant = codesHN.data?.some((c) => c.code === codeMissionSaisi);

  const creation = useMutation({
    mutationFn: (v: Formulaire) => creerMission({
      codeChantier: v.codeChantier,
      libelleChantier: v.libelleChantier?.trim() || null,
      codeMission: v.codeMission,
      libelleCodeMission: v.libelleCodeMission?.trim() || null,
      dateDebutPrevue: v.dateDebutPrevue,
      dateFinPrevue: v.dateFinPrevue,
      missionPrecedenteId: null,
    }),
    onSuccess: (creee) => {
      void queryClient.invalidateQueries({ queryKey: ["missions"] });
      void queryClient.invalidateQueries({ queryKey: ["chantiers"] });
      void queryClient.invalidateQueries({ queryKey: ["codes-hn"] });
      navigate(`/missions/${creee.id}`, { replace: true });
    },
  });

  return (
    <div>
      <h1>{t("Nouvelle mission")}</h1>
      <form className="formulaire" onSubmit={handleSubmit((v) => creation.mutate(v))}>
        <label htmlFor="codeChantier">{t("Code chantier")}</label>
        <input id="codeChantier" type="text" maxLength={30} list="chantiers-existants" {...register("codeChantier")} />
        <datalist id="chantiers-existants">
          {chantiers.data?.map((c) => <option key={c.id} value={c.codeAffaire}>{c.libelle}</option>)}
        </datalist>
        {errors.codeChantier && <p role="alert">{errors.codeChantier.message}</p>}

        {codeChantierSaisi && !chantierExistant && (
          <>
            <label htmlFor="libelleChantier">{t("Libellé du chantier (nouveau code - facultatif, repris du code sinon)")}</label>
            <input id="libelleChantier" type="text" maxLength={150} {...register("libelleChantier")} />
          </>
        )}

        <label htmlFor="codeMission">{t("Code mission (Code HN)")}</label>
        <input id="codeMission" type="text" maxLength={30} list="codes-mission-existants" {...register("codeMission")} />
        <datalist id="codes-mission-existants">
          {codesHN.data?.map((c) => <option key={c.id} value={c.code}>{c.libelle}</option>)}
        </datalist>
        {errors.codeMission && <p role="alert">{errors.codeMission.message}</p>}

        {codeMissionSaisi && !codeMissionExistant && (
          <>
            <label htmlFor="libelleCodeMission">{t("Libellé du code mission (nouveau code - facultatif, repris du code sinon)")}</label>
            <input id="libelleCodeMission" type="text" maxLength={150} {...register("libelleCodeMission")} />
          </>
        )}

        <label htmlFor="dateDebutPrevue">{t("Date de début prévue")}</label>
        <input id="dateDebutPrevue" type="date" {...register("dateDebutPrevue")} />
        {errors.dateDebutPrevue && <p role="alert">{errors.dateDebutPrevue.message}</p>}

        <label htmlFor="dateFinPrevue">{t("Date de fin prévue")}</label>
        <input id="dateFinPrevue" type="date" {...register("dateFinPrevue")} />
        {errors.dateFinPrevue && <p role="alert">{errors.dateFinPrevue.message}</p>}

        {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, t("Impossible de créer la mission."))}</p>}

        <button type="submit" disabled={creation.isPending}>{creation.isPending ? t("Création...") : t("Créer la mission")}</button>
      </form>
    </div>
  );
}
