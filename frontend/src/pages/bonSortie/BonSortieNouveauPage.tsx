import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ajouterPersonnesABordEnLot, creerBonSortie } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMissions } from "../../api/missionApi";
import { listerVehicules } from "../../api/referentielApi";
import { useAuth } from "../../auth/AuthContext";
import { LIBELLES_MOYEN_UTILISE, type MoyenUtilise } from "../../types/bonSortie";

/**
 * Formulaire de creation d'un bon de sortie (section 3, RG-BS-001/002).
 *
 * <p>En libre-service (cas habituel), l'agent titulaire n'est pas un champ du
 * formulaire : il est resolu cote serveur a partir de l'utilisateur
 * authentifie. Evolution du 2026-08-27 (brief "Evolution du module Bon de
 * Sortie") : un Charge d'Affaires/une personne habilitee/un Super
 * Administrateur peut desormais indiquer une "Personne principale"
 * differente de lui-meme (creation pour le compte d'un tiers), un "Code
 * Mission" (association directe a une Mission existante, en plus/a la place
 * de la resolution automatique par date), et ajouter directement des
 * personnes a bord des la creation - le controle reel de qui peut faire quoi
 * reste, comme toujours, applique cote serveur, jamais seulement par
 * l'affichage conditionnel de ces champs ici.
 */
const schema = z.object({
  agentId: z.string().optional(),
  missionId: z.string().optional(),
  moyenUtilise: z.enum(["OMNIUM_SERVICE", "PERSONNEL", "TAXI", "AUTRE"]),
  precisionVehicule: z.string().max(200).optional(),
  vehiculeId: z.string().optional(),
  lt: z.string().max(20).optional(),
  kilometrage: z.coerce.number().int().min(0, "Le kilométrage doit être positif ou nul."),
  dateSortie: z.string().min(1, "La date de sortie est obligatoire."),
  heureSortie: z.string().min(1, "L'heure de sortie est obligatoire."),
  lieu: z.string().min(1, "La destination est obligatoire.").max(150),
  codeAffaireSaisi: z.string().min(1, "Le code affaire est obligatoire.").max(30),
  motifSortie: z.string().min(1, "Le motif est obligatoire.").max(500),
}).refine(
  (valeurs) => valeurs.moyenUtilise !== "AUTRE" || !!valeurs.precisionVehicule?.trim(),
  { message: "Veuillez préciser le véhicule utilisé.", path: ["precisionVehicule"] },
);
type Formulaire = z.infer<typeof schema>;

export default function BonSortieNouveauPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  const vehicules = useQuery({ queryKey: ["vehicules"], queryFn: listerVehicules });
  const peutChoisirPersonnePrincipale = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE") || aLeRole("SUPER_ADMINISTRATEUR");
  const missions = useQuery({ queryKey: ["missions"], queryFn: listerMissions });
  const [personnesABord, setPersonnesABord] = useState<string[]>([]);

  const { register, handleSubmit, control, formState: { errors } } = useForm<Formulaire>({
    resolver: zodResolver(schema),
    defaultValues: { moyenUtilise: "OMNIUM_SERVICE", kilometrage: 0 },
  });
  const moyenUtiliseChoisi = useWatch({ control, name: "moyenUtilise" });

  const creation = useMutation({
    mutationFn: async (valeurs: Formulaire) => {
      const bonCree = await creerBonSortie({
        agentId: valeurs.agentId ? Number(valeurs.agentId) : null,
        missionId: valeurs.missionId ? Number(valeurs.missionId) : null,
        vehiculeId: valeurs.vehiculeId ? Number(valeurs.vehiculeId) : null,
        moyenUtilise: valeurs.moyenUtilise,
        precisionVehicule: valeurs.moyenUtilise === "AUTRE" ? (valeurs.precisionVehicule?.trim() || null) : null,
        lt: valeurs.lt || null,
        kilometrage: valeurs.kilometrage,
        dateSortie: valeurs.dateSortie,
        heureSortie: valeurs.heureSortie.length === 5 ? `${valeurs.heureSortie}:00` : valeurs.heureSortie,
        lieu: valeurs.lieu,
        codeAffaireSaisi: valeurs.codeAffaireSaisi,
        motifSortie: valeurs.motifSortie,
      });
      // Personnes a bord ajoutees juste apres la creation (evolution du 2026-08-27) - reutilise
      // exactement le meme mecanisme (transactionnel, idempotent) que l'ajout post-creation deja
      // existant, sans dupliquer cette logique cote serveur.
      const agentIds = personnesABord.map((v) => Number(v)).filter((n) => Number.isInteger(n) && n > 0);
      if (agentIds.length > 0) {
        await ajouterPersonnesABordEnLot(bonCree.id, { agentIds });
      }
      return bonCree;
    },
    onSuccess: (cree) => {
      void queryClient.invalidateQueries({ queryKey: ["bons-sortie"] });
      navigate(`/bons-sortie/${cree.id}`, { replace: true });
    },
  });

  return (
    <div>
      <h1>Nouveau bon de sortie</h1>
      <form className="formulaire" onSubmit={handleSubmit((valeurs) => creation.mutate(valeurs))}>
        {peutChoisirPersonnePrincipale && (
          <>
            <label htmlFor="agentId">Personne principale (facultatif — laisser vide pour créer votre propre bon)</label>
            <input id="agentId" type="number" min={1} {...register("agentId")} />
            <p className="dashboard-accueil">
              Identifiant de l'agent pour lequel ce bon de sortie est établi. Réservé au Chargé d'Affaires/à la
              personne habilitée du même service, ou au Super Administrateur — vérifié côté serveur.
            </p>
          </>
        )}

        <label htmlFor="missionId">Code Mission (facultatif)</label>
        <select id="missionId" {...register("missionId")}>
          <option value="">— Non renseigné —</option>
          {missions.data?.map((m) => (
            <option key={m.id} value={m.id}>{m.codeHN} — {m.chantierLibelle} ({m.dateDebutPrevue} → {m.dateFinPrevue})</option>
          ))}
        </select>

        <label htmlFor="moyenUtilise">Moyen utilisé</label>
        <select id="moyenUtilise" {...register("moyenUtilise")}>
          {(Object.keys(LIBELLES_MOYEN_UTILISE) as MoyenUtilise[]).map((m) => (
            <option key={m} value={m}>{LIBELLES_MOYEN_UTILISE[m]}</option>
          ))}
        </select>

        {moyenUtiliseChoisi === "AUTRE" && (
          <>
            <label htmlFor="precisionVehicule">Préciser le véhicule</label>
            <input id="precisionVehicule" type="text" maxLength={200} {...register("precisionVehicule")} />
            {errors.precisionVehicule && <p role="alert">{errors.precisionVehicule.message}</p>}
          </>
        )}

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

        <fieldset>
          <legend>Personnes à bord (facultatif)</legend>
          {personnesABord.map((valeur, index) => (
            <div className="formulaire-ligne" key={index}>
              <label htmlFor={`personneABord-${index}`}>Identifiant de l'agent</label>
              <input
                id={`personneABord-${index}`}
                type="number"
                min={1}
                value={valeur}
                onChange={(e) => setPersonnesABord((liste) => liste.map((v, i) => (i === index ? e.target.value : v)))}
              />
              <button type="button" onClick={() => setPersonnesABord((liste) => liste.filter((_, i) => i !== index))}>
                Retirer
              </button>
            </div>
          ))}
          <button type="button" onClick={() => setPersonnesABord((liste) => [...liste, ""])}>
            Ajouter une personne à bord
          </button>
        </fieldset>

        {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, "Impossible de créer le bon de sortie.")}</p>}

        <button type="submit" disabled={creation.isPending}>
          {creation.isPending ? "Création..." : "Créer le bon de sortie"}
        </button>
      </form>
    </div>
  );
}
