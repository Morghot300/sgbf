import type { StatutBonSortie } from "../types/bonSortie";
import type { StatutFiphVersion } from "../types/fiph";
import type { StatutAffectation, StatutMission } from "../types/mission";

/**
 * Badge de statut generique. Les couleurs d'etat (succes/alerte/erreur/info)
 * sont volontairement independantes de toute charte de marque (elles ne
 * doivent jamais dependre du logo - un document refuse reste rouge quel que
 * soit l'habillage visuel de l'application) : elles restent donc definies
 * ici en dur, via des classes semantiques, jusqu'a ce qu'un systeme de
 * tokens CSS complet soit mis en place.
 *
 * Le libelle textuel est TOUJOURS affiche a cote de la couleur (jamais la
 * couleur seule) - conformite WCAG 1.4.1 (« l'usage de la couleur »).
 */

type Variante = "neutre" | "attente" | "succes" | "danger" | "info";

const VARIANTE_BON_SORTIE: Record<StatutBonSortie, Variante> = {
  BROUILLON: "neutre",
  VISE: "attente",
  VALIDE: "succes",
};

const VARIANTE_FIPH: Record<StatutFiphVersion, Variante> = {
  BROUILLON: "neutre",
  EN_COMPLEMENT: "attente",
  SIGNEE: "attente",
  SOUMISE: "attente",
  VALIDEE_NIVEAU_2: "info",
  VALIDEE_NIVEAU_3: "info",
  VALIDEE_DEFINITIVEMENT: "succes",
  REJETEE: "danger",
  RETOUR_POUR_CORRECTION: "danger",
  ANNULEE: "danger",
  EN_REVISION: "attente",
};

const VARIANTE_MISSION: Record<StatutMission, Variante> = {
  PLANIFIEE: "neutre",
  EN_COURS: "info",
  INTERROMPUE: "danger",
  TERMINEE: "succes",
};

const VARIANTE_AFFECTATION: Record<StatutAffectation, Variante> = {
  ACTIVE: "info",
  INTERROMPUE: "danger",
  TERMINEE: "succes",
  TRANSFEREE: "neutre",
};

function Badge({ variante, libelle }: { variante: Variante; libelle: string }) {
  return (
    <span className={`badge badge--${variante}`}>
      {libelle}
    </span>
  );
}

export function BadgeStatutBonSortie({ statut, libelle }: { statut: StatutBonSortie; libelle: string }) {
  return <Badge variante={VARIANTE_BON_SORTIE[statut]} libelle={libelle} />;
}

export function BadgeStatutFiph({ statut, libelle }: { statut: StatutFiphVersion; libelle: string }) {
  return <Badge variante={VARIANTE_FIPH[statut]} libelle={libelle} />;
}

export function BadgeStatutMission({ statut, libelle }: { statut: StatutMission; libelle: string }) {
  return <Badge variante={VARIANTE_MISSION[statut]} libelle={libelle} />;
}

export function BadgeStatutAffectation({ statut, libelle }: { statut: StatutAffectation; libelle: string }) {
  return <Badge variante={VARIANTE_AFFECTATION[statut]} libelle={libelle} />;
}
