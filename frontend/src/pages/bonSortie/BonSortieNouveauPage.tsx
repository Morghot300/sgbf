import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { creerBonSortie } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerVehicules } from "../../api/referentielApi";
import { LIBELLES_MOYEN_UTILISE, type MoyenUtilise } from "../../types/bonSortie";

/**
 * Formulaire de creation d'un bon de sortie (section 3, RG-BS-001/002).
 * L'agent titulaire n'est PAS un champ du formulaire : il est resolu cote
 * serveur a partir de l'utilisateur authentifie (creation en libre-service -
 * voir Javadoc de `BonSortieController`).
 */
const schema = z.object({
  moyenUtilise: z.enum(["OMNIUM_SERVICE", "PERSONNEL", "TAXI"]),
  vehiculeId: z.string().optional(),
  lt: z.string().max(20).optional(),
  kilometrage: z.coerce.number().int().min(0, "Le kilométrage doit être positif ou nul."),
  dateSortie: z.string().min(1, "La date de sortie est obligatoire."),
  heureSortie: z.string().min(1, "L'heure de sortie est obligatoire."),
  lieu: z.string().min(1, "La destination est obligatoire.").max(150),
  codeAffaireSaisi: z.string().min(1, "Le code affaire est obligatoire.").max(30),
  motifSortie: z.string().min(1, "Le motif est obligatoire.").max(500),
});
type Formulaire = z.infer<typeof schema>;

export default function BonSortieNouveauPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const vehicules = useQuery({ queryKey: ["vehicules"], queryFn: listerVehicules });

  const { register, handleSubmit, formState: { errors } } = useForm<Formulaire>({
    resolver: zodResolver(schema),
    defaultValues: { moyenUtilise: "OMNIUM_SERVICE", kilometrage: 0 },
  });

  const creation = useMutation({
    mutationFn: (valeurs: Formulaire) => creerBonSortie({
      vehiculeId: valeurs.vehiculeId ? Number(valeurs.vehiculeId) : null,
      moyenUtilise: valeurs.moyenUtilise,
      lt: valeurs.lt || null,
      kilometrage: valeurs.kilometrage,
      dateSortie: valeurs.dateSortie,
      heureSortie: valeurs.heureSortie.length === 5 ? `${valeurs.heureSortie}:00` : valeurs.heureSortie,
      lieu: valeurs.lieu,
      codeAffaireSaisi: valeurs.codeAffaireSaisi,
      motifSortie: valeurs.motifSortie,
    }),
    onSuccess: (cree) => {
      void queryClient.invalidateQueries({ queryKey: ["bons-sortie"] });
      navigate(`/bons-sortie/${cree.id}`, { replace: true });
    },
  });

  return (
    <div>
      <h1>Nouveau bon de sortie</h1>
      <form className="formulaire" onSubmit={handleSubmit((valeurs) => creation.mutate(valeurs))}>
        <label htmlFor="moyenUtilise">Moyen utilisé</label>
        <select id="moyenUtilise" {...register("moyenUtilise")}>
          {(Object.keys(LIBELLES_MOYEN_UTILISE) as MoyenUtilise[]).map((m) => (
            <option key={m} value={m}>{LIBELLES_MOYEN_UTILISE[m]}</option>
          ))}
        </select>

        <label htmlFor="vehiculeId">Véhicule (facultatif)</label>
        <select id="vehiculeId" {...register("vehiculeId")}>
          <option value="">— Non renseigné —</option>
          {vehicules.data?.map((v) => (
            <option key={v.id} value={v.id}>{v.immatriculation}</option>
          ))}
        </select>

        <label htmlFor="lt">Immatriculation (LT) si non listée ci-dessus</label>
        <input id="lt" type="text" maxLength={20} {...register("lt")} />

        <label htmlFor="kilometrage">Kilométrage</label>
        <input id="kilometrage" type="number" min={0} {...register("kilometrage")} />
        {errors.kilometrage && <p role="alert">{errors.kilometrage.message}</p>}

        <label htmlFor="dateSortie">Date de sortie</label>
        <input id="dateSortie" type="date" {...register("dateSortie")} />
        {errors.dateSortie && <p role="alert">{errors.dateSortie.message}</p>}

        <label htmlFor="heureSortie">Heure de sortie</label>
        <input id="heureSortie" type="time" {...register("heureSortie")} />
        {errors.heureSortie && <p role="alert">{errors.heureSortie.message}</p>}

        <label htmlFor="lieu">Destination</label>
        <input id="lieu" type="text" maxLength={150} {...register("lieu")} />
        {errors.lieu && <p role="alert">{errors.lieu.message}</p>}

        <label htmlFor="codeAffaireSaisi">Code affaire</label>
        <input id="codeAffaireSaisi" type="text" maxLength={30} {...register("codeAffaireSaisi")} />
        {errors.codeAffaireSaisi && <p role="alert">{errors.codeAffaireSaisi.message}</p>}

        <label htmlFor="motifSortie">Motif de sortie</label>
        <textarea id="motifSortie" maxLength={500} {...register("motifSortie")} />
        {errors.motifSortie && <p role="alert">{errors.motifSortie.message}</p>}

        {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, "Impossible de créer le bon de sortie.")}</p>}

        <button type="submit" disabled={creation.isPending}>
          {creation.isPending ? "Création..." : "Créer le bon de sortie"}
        </button>
      </form>
    </div>
  );
}
