import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ajouterPersonnesABordEnLot, creerBonSortie, listerPersonnelDuService } from "../../api/bonSortieApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMissions } from "../../api/missionApi";
import { listerServices, listerVehicules } from "../../api/referentielApi";
import { useAuth } from "../../auth/AuthContext";
import { SelectionPersonnelService } from "../../components/SelectionPersonnelService";
import { LIBELLES_MOYEN_UTILISE, type MoyenUtilise } from "../../types/bonSortie";

/**
 * Formulaire de creation d'un bon de sortie (section 3, RG-BS-001/002),
 * reorganise selon une logique naturelle (evolution du 2026-08-27, brief
 * "Evolution avancee du module Bon de Sortie, Missions et FIPH", section 29) :
 * informations generales -> personne principale -> mission -> vehicule ->
 * personnes a bord -> informations complementaires -> actions.
 *
 * <p>En libre-service (cas habituel), l'agent titulaire n'est pas un champ du
 * formulaire : il est resolu cote serveur a partir de l'utilisateur
 * authentifie. Un Charge d'Affaires/une personne habilitee/un Super
 * Administrateur peut en plus choisir une "Personne principale" differente
 * de lui-meme (creation pour le compte d'un tiers) et des "Personnes a
 * bord" - toutes deux selectionnees par cases a cocher parmi le personnel
 * d'un service, filtrable par nom (jamais une simple saisie libre
 * d'identifiant) - le controle reel de qui peut faire quoi reste, comme
 * toujours, applique cote serveur.
 */
const schema = z.object({
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
  const { aLeRole, utilisateur } = useAuth();
  const vehicules = useQuery({ queryKey: ["vehicules"], queryFn: listerVehicules });
  const peutChoisirPersonnePrincipale = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE") || aLeRole("SUPER_ADMINISTRATEUR");
  const services = useQuery({ queryKey: ["services"], queryFn: listerServices, enabled: peutChoisirPersonnePrincipale });
  const missions = useQuery({ queryKey: ["missions"], queryFn: listerMissions });
  const [serviceId, setServiceId] = useState<string>(utilisateur?.serviceId ? String(utilisateur.serviceId) : "");
  const [personnePrincipale, setPersonnePrincipale] = useState<Set<number>>(new Set());
  const [personnesABord, setPersonnesABord] = useState<Set<number>>(new Set());
  // Service dont le personnel est propose pour les personnes a bord : celui choisi ci-dessus pour
  // la personne principale (CA/PH/Super Admin), ou directement le service du createur sinon (RG-PAB-010 :
  // les personnes a bord appartiennent toujours au meme service que le titulaire du bon).
  const serviceEffectif = peutChoisirPersonnePrincipale
    ? (serviceId ? Number(serviceId) : null)
    : (utilisateur?.serviceId ?? null);

  const { register, handleSubmit, control, formState: { errors } } = useForm<Formulaire>({
    resolver: zodResolver(schema),
    defaultValues: { moyenUtilise: "OMNIUM_SERVICE", kilometrage: 0 },
  });
  const moyenUtiliseChoisi = useWatch({ control, name: "moyenUtilise" });

  const creation = useMutation({
    mutationFn: async (valeurs: Formulaire) => {
      const agentId = [...personnePrincipale][0];
      const bonCree = await creerBonSortie({
        agentId: agentId ?? null,
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
      if (personnesABord.size > 0) {
        await ajouterPersonnesABordEnLot(bonCree.id, { agentIds: [...personnesABord] });
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
            <h2>Personne principale (facultatif)</h2>
            <p className="dashboard-accueil">
              Laissez vide pour créer votre propre bon de sortie. Sélectionnez un service puis une personne pour créer
              ce bon pour le compte d'un tiers — réservé au Chargé d'Affaires/à la personne habilitée du même
              service, ou au Super Administrateur (vérifié côté serveur).
            </p>
            <label htmlFor="serviceId">Service</label>
            <select id="serviceId" value={serviceId} onChange={(e) => { setServiceId(e.target.value); setPersonnePrincipale(new Set()); setPersonnesABord(new Set()); }}>
              <option value="">— Sélectionner —</option>
              {services.data?.map((s) => <option key={s.id} value={s.id}>{s.libelle}</option>)}
            </select>
            <SelectionPersonnelService
              serviceId={serviceId ? Number(serviceId) : null}
              mode="unique"
              selection={personnePrincipale}
              onChange={setPersonnePrincipale}
              chargerPersonnel={listerPersonnelDuService}
            />
          </>
        )}

        <h2>Mission</h2>
        <label htmlFor="missionId">Code Mission (facultatif)</label>
        <select id="missionId" {...register("missionId")}>
          <option value="">— Non renseigné —</option>
          {missions.data?.map((m) => (
            <option key={m.id} value={m.id}>{m.codeHN} — {m.chantierLibelle} ({m.dateDebutPrevue} → {m.dateFinPrevue})</option>
          ))}
        </select>

        <h2>Véhicule</h2>
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

        <h2>Personnes à bord (facultatif)</h2>
        <SelectionPersonnelService
          serviceId={serviceEffectif}
          mode="multiple"
          selection={personnesABord}
          onChange={setPersonnesABord}
          exclureIds={personnePrincipale}
          chargerPersonnel={listerPersonnelDuService}
        />

        {creation.isError && <p role="alert">{extraireMessageErreur(creation.error, "Impossible de créer le bon de sortie.")}</p>}

        <button type="submit" disabled={creation.isPending}>
          {creation.isPending ? "Création..." : "Créer le bon de sortie"}
        </button>
      </form>
    </div>
  );
}
