import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { modifierBonSortie } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMissions } from "../../api/missionApi";
import { listerVehicules } from "../../api/referentielApi";
import { LIBELLES_MOYEN_UTILISE, type BonSortieDto, type MoyenUtilise } from "../../types/bonSortie";

const schema = z.object({
  missionId: z.string().optional(),
  moyenUtilise: z.enum(["OMNIUM_SERVICE", "PERSONNEL", "TAXI", "AUTRE"]),
  precisionVehicule: z.string().max(200).optional(),
  vehiculeId: z.string().optional(),
  lt: z.string().max(20).optional(),
  kilometrage: z.coerce.number().int().min(0, "Le kilométrage doit être positif ou nul."),
  dateSortie: z.string().min(1, "La date de sortie est obligatoire."),
  heureSortie: z.string().min(1, "L'heure de sortie est obligatoire."),
  heureRetour: z.string().optional(),
  lieu: z.string().min(1, "La destination est obligatoire.").max(150),
  codeAffaireSaisi: z.string().min(1, "Le code affaire est obligatoire.").max(30),
  motifSortie: z.string().min(1, "Le motif est obligatoire.").max(500),
}).refine(
  (valeurs) => valeurs.moyenUtilise !== "AUTRE" || !!valeurs.precisionVehicule?.trim(),
  { message: "Veuillez préciser le véhicule utilisé.", path: ["precisionVehicule"] },
);
type Formulaire = z.infer<typeof schema>;

function normaliserHeure(heure: string): string {
  return heure.length === 5 ? `${heure}:00` : heure;
}

/**
 * Correction des champs d'un bon de sortie (évolution du 2026-08-26 —
 * "ajoute la correction des bon de sortie", étendue le 2026-08-27 — brief
 * "Evolution du module Bon de Sortie", section 10-12) : repliée par défaut
 * pour ne pas alourdir la fiche d'un formulaire complet en permanence — un
 * seul bouton bascule son ouverture.
 *
 * <p>Reste possible même une fois le bon VALIDE (RG-VER-001 désormais
 * inversée sur décision explicite) — le serveur restreint alors la
 * correction au seul gestionnaire du service (Chargé d'Affaires/personne
 * habilitée) ou au Super Administrateur, et refuse si une FIPH déjà générée
 * à partir de ce bon est validée définitivement (RG-BS-011). Ce composant ne
 * fait que refléter ce que le serveur acceptera, sans dupliquer cette
 * logique — un clic refusé échoue simplement avec un message clair.
 */
export function CorrectionBonSortie({ bonSortie, onCorrigeAvecSucces }: {
  bonSortie: BonSortieDto;
  onCorrigeAvecSucces: () => void;
}) {
  const [ouvert, setOuvert] = useState(false);
  const queryClient = useQueryClient();
  const vehicules = useQuery({ queryKey: ["vehicules"], queryFn: listerVehicules, enabled: ouvert });
  const missions = useQuery({ queryKey: ["missions"], queryFn: listerMissions, enabled: ouvert });

  const { register, handleSubmit, control, setValue, formState: { errors } } = useForm<Formulaire>({
    resolver: zodResolver(schema),
    defaultValues: {
      missionId: bonSortie.missionSelectionneeId ? String(bonSortie.missionSelectionneeId) : "",
      moyenUtilise: bonSortie.moyenUtilise,
      precisionVehicule: bonSortie.precisionVehicule ?? "",
      vehiculeId: bonSortie.vehiculeId ? String(bonSortie.vehiculeId) : "",
      lt: bonSortie.lt ?? "",
      kilometrage: bonSortie.kilometrage,
      dateSortie: bonSortie.dateSortie,
      heureSortie: bonSortie.heureSortie.slice(0, 5),
      heureRetour: bonSortie.heureRetour?.slice(0, 5) ?? "",
      lieu: bonSortie.lieu,
      codeAffaireSaisi: bonSortie.codeAffaireSaisi,
      motifSortie: bonSortie.motifSortie,
    },
  });
  const moyenUtiliseChoisi = useWatch({ control, name: "moyenUtilise" });
  // Code affaire et Code Mission designent la meme reference (confirme le 2026-08-27) : voir la
  // meme logique dans BonSortieNouveauPage.
  const missionIdChoisie = useWatch({ control, name: "missionId" });
  const missionChoisie = missions.data?.find((m) => String(m.id) === missionIdChoisie);
  useEffect(() => {
    if (missionChoisie) {
      setValue("codeAffaireSaisi", missionChoisie.codeHN, { shouldValidate: true });
    }
  }, [missionChoisie, setValue]);

  const correction = useMutation({
    mutationFn: (valeurs: Formulaire) => modifierBonSortie(bonSortie.id, {
      missionId: valeurs.missionId ? Number(valeurs.missionId) : null,
      vehiculeId: valeurs.vehiculeId ? Number(valeurs.vehiculeId) : null,
      moyenUtilise: valeurs.moyenUtilise,
      precisionVehicule: valeurs.moyenUtilise === "AUTRE" ? (valeurs.precisionVehicule?.trim() || null) : null,
      lt: valeurs.lt || null,
      kilometrage: valeurs.kilometrage,
      dateSortie: valeurs.dateSortie,
      heureSortie: normaliserHeure(valeurs.heureSortie),
      heureRetour: valeurs.heureRetour ? normaliserHeure(valeurs.heureRetour) : null,
      lieu: valeurs.lieu,
      codeAffaireSaisi: valeurs.codeAffaireSaisi,
      motifSortie: valeurs.motifSortie,
      lockVersion: bonSortie.lockVersion,
    }),
    onSuccess: () => {
      setOuvert(false);
      void queryClient.invalidateQueries({ queryKey: ["bon-sortie", bonSortie.id] });
      void queryClient.invalidateQueries({ queryKey: ["bons-sortie"] });
      onCorrigeAvecSucces();
    },
  });

  if (!ouvert) {
    return (
      <button type="button" onClick={() => setOuvert(true)}>Corriger le bon de sortie</button>
    );
  }

  return (
    <section>
      <h2>Corriger le bon de sortie</h2>
      {bonSortie.statut === "VALIDE" && (
        <p role="alert" className="avertissement">
          Ce bon de sortie est déjà validé. Réservé au Chargé d'Affaires/à la personne habilitée du service ou au
          Super Administrateur — la modification sera enregistrée dans l'historique (auteur, date, anciennes et
          nouvelles valeurs).
        </p>
      )}
      <form className="formulaire" onSubmit={handleSubmit((valeurs) => correction.mutate(valeurs))}>
        <label htmlFor="c-missionId">Code Mission (facultatif)</label>
        <select id="c-missionId" {...register("missionId")}>
          <option value="">— Non renseigné —</option>
          {missions.data?.map((m) => (
            <option key={m.id} value={m.id}>{m.codeHN} — {m.chantierLibelle} ({m.dateDebutPrevue} → {m.dateFinPrevue})</option>
          ))}
        </select>

        <label htmlFor="c-moyenUtilise">Moyen utilisé</label>
        <select id="c-moyenUtilise" {...register("moyenUtilise")}>
          {(Object.keys(LIBELLES_MOYEN_UTILISE) as MoyenUtilise[]).map((m) => (
            <option key={m} value={m}>{LIBELLES_MOYEN_UTILISE[m]}</option>
          ))}
        </select>

        {moyenUtiliseChoisi === "AUTRE" && (
          <>
            <label htmlFor="c-precisionVehicule">Préciser le véhicule</label>
            <input id="c-precisionVehicule" type="text" maxLength={200} {...register("precisionVehicule")} />
            {errors.precisionVehicule && <p role="alert">{errors.precisionVehicule.message}</p>}
          </>
        )}

        <label htmlFor="c-vehiculeId">Véhicule (facultatif)</label>
        <select id="c-vehiculeId" {...register("vehiculeId")}>
          <option value="">— Non renseigné —</option>
          {vehicules.data?.map((v) => (
            <option key={v.id} value={v.id}>{v.immatriculation}</option>
          ))}
        </select>

        <label htmlFor="c-lt">Immatriculation (LT) si non listée ci-dessus</label>
        <input id="c-lt" type="text" maxLength={20} {...register("lt")} />

        <label htmlFor="c-kilometrage">Kilométrage</label>
        <input id="c-kilometrage" type="number" min={0} {...register("kilometrage")} />
        {errors.kilometrage && <p role="alert">{errors.kilometrage.message}</p>}

        <label htmlFor="c-dateSortie">Date de sortie</label>
        <input id="c-dateSortie" type="date" {...register("dateSortie")} />
        {errors.dateSortie && <p role="alert">{errors.dateSortie.message}</p>}

        <label htmlFor="c-heureSortie">Heure de sortie</label>
        <input id="c-heureSortie" type="time" {...register("heureSortie")} />
        {errors.heureSortie && <p role="alert">{errors.heureSortie.message}</p>}

        <label htmlFor="c-heureRetour">Heure de retour (facultative)</label>
        <input id="c-heureRetour" type="time" {...register("heureRetour")} />

        <label htmlFor="c-lieu">Destination</label>
        <input id="c-lieu" type="text" maxLength={150} {...register("lieu")} />
        {errors.lieu && <p role="alert">{errors.lieu.message}</p>}

        <label htmlFor="c-codeAffaireSaisi">Code affaire{missionChoisie ? " (= Code Mission ci-dessus)" : ""}</label>
        <input id="c-codeAffaireSaisi" type="text" maxLength={30} readOnly={!!missionChoisie} {...register("codeAffaireSaisi")} />
        {missionChoisie && <p className="note">Rempli automatiquement à partir de la mission choisie — même référence.</p>}
        {errors.codeAffaireSaisi && <p role="alert">{errors.codeAffaireSaisi.message}</p>}

        <label htmlFor="c-motifSortie">Motif de sortie</label>
        <textarea id="c-motifSortie" maxLength={500} {...register("motifSortie")} />
        {errors.motifSortie && <p role="alert">{errors.motifSortie.message}</p>}

        {correction.isError && <p role="alert">{extraireMessageErreur(correction.error, "Impossible d'enregistrer ces corrections.")}</p>}

        <div className="barre-actions">
          <button type="submit" disabled={correction.isPending}>
            {correction.isPending ? "Enregistrement..." : "Enregistrer les corrections"}
          </button>
          <button type="button" onClick={() => setOuvert(false)} disabled={correction.isPending}>Annuler</button>
        </div>
      </form>
    </section>
  );
}
