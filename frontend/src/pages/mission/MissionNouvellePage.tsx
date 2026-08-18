import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { creerMission } from "../../api/missionApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerChantiers, listerCodesHN } from "../../api/referentielApi";

const schema = z.object({
  codeHNId: z.string().min(1, "Le code mission est obligatoire."),
  chantierId: z.string().min(1, "Le chantier est obligatoire."),
  dateDebutPrevue: z.string().min(1, "La date de début est obligatoire."),
  dateFinPrevue: z.string().min(1, "La date de fin est obligatoire."),
});
type Formulaire = z.infer<typeof schema>;

export default function MissionNouvellePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const chantiers = useQuery({ queryKey: ["chantiers"], queryFn: listerChantiers });
  const codesHN = useQuery({ queryKey: ["codes-hn"], queryFn: listerCodesHN });

  const { register, handleSubmit, formState: { errors } } = useForm<Formulaire>({ resolver: zodResolver(schema) });

  const creation = useMutation({
    mutationFn: (v: Formulaire) => creerMission({
      codeHNId: Number(v.codeHNId), chantierId: Number(v.chantierId),
      dateDebutPrevue: v.dateDebutPrevue, dateFinPrevue: v.dateFinPrevue, missionPrecedenteId: null,
    }),
    onSuccess: (creee) => {
      void queryClient.invalidateQueries({ queryKey: ["missions"] });
      navigate(`/missions/${creee.id}`, { replace: true });
    },
  });

  return (
    <div>
      <h1>Nouvelle mission</h1>
      <form className="formulaire" onSubmit={handleSubmit((v) => creation.mutate(v))}>
        <label htmlFor="chantierId">Chantier</label>
        <select id="chantierId" {...register("chantierId")}>
          <option value="">— Sélectionner —</option>
          {chantiers.data?.map((c) => <option key={c.id} value={c.id}>{c.codeAffaire} — {c.libelle}</option>)}
        </select>
        {errors.chantierId && <p role="alert">{errors.chantierId.message}</p>}

        <label htmlFor="codeHNId">Code mission (Code HN)</label>
        <select id="codeHNId" {...register("codeHNId")}>
          <option value="">— Sélectionner —</option>
          {codesHN.data?.map((c) => <option key={c.id} value={c.id}>{c.code} — {c.libelle}</option>)}
        </select>
        {errors.codeHNId && <p role="alert">{errors.codeHNId.message}</p>}

        <label htmlFor="dateDebutPrevue">Date de début prévue</label>
        <input id="dateDebutPrevue" type="date" {...register("dateDebutPrevue")} />
        {errors.dateDebutPrevue && <p role="alert">{errors.dateDebutPrevue.message}</p>}

        <label htmlFor="dateFinPrevue">Date de fin prévue</label>
        <input id="dateFinPrevue" type="date" {...register("dateFinPrevue")} />
        {errors.dateFinPrevue && <p role="alert">{errors.dateFinPrevue.message}</p>}

        {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, "Impossible de créer la mission.")}</p>}

        <button type="submit" disabled={creation.isPending}>{creation.isPending ? "Création..." : "Créer la mission"}</button>
      </form>
    </div>
  );
}
