import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  BadgeStatutAffectation, BadgeStatutBonSortie, BadgeStatutFiph, BadgeStatutMission,
} from "./StatutBadge";
import { LIBELLES_STATUT_BON_SORTIE, type StatutBonSortie } from "../types/bonSortie";
import { LIBELLES_STATUT_FIPH, type StatutFiphVersion } from "../types/fiph";
import { LIBELLES_STATUT_AFFECTATION, LIBELLES_STATUT_MISSION, type StatutAffectation, type StatutMission } from "../types/mission";

/**
 * Mission charte graphique, critere d'acceptation 4 (WCAG 1.4.1) : "Aucun
 * statut n'est communique par la seule couleur - le libelle textuel est
 * conserve." Ces tests verifient, pour CHAQUE valeur des quatre enumerations
 * de statut de l'application, que le libelle textuel est effectivement
 * present dans le DOM rendu - pas seulement une classe CSS de couleur.
 */
describe("BadgeStatutBonSortie", () => {
  it.each(Object.keys(LIBELLES_STATUT_BON_SORTIE) as StatutBonSortie[])(
    "affiche le libelle textuel pour le statut %s",
    (statut) => {
      render(<BadgeStatutBonSortie statut={statut} libelle={LIBELLES_STATUT_BON_SORTIE[statut]} />);
      expect(screen.getByText(LIBELLES_STATUT_BON_SORTIE[statut])).toBeInTheDocument();
    },
  );
});

describe("BadgeStatutFiph", () => {
  it.each(Object.keys(LIBELLES_STATUT_FIPH) as StatutFiphVersion[])(
    "affiche le libelle textuel pour le statut %s",
    (statut) => {
      render(<BadgeStatutFiph statut={statut} libelle={LIBELLES_STATUT_FIPH[statut]} />);
      expect(screen.getByText(LIBELLES_STATUT_FIPH[statut])).toBeInTheDocument();
    },
  );
});

describe("BadgeStatutMission", () => {
  it.each(Object.keys(LIBELLES_STATUT_MISSION) as StatutMission[])(
    "affiche le libelle textuel pour le statut %s",
    (statut) => {
      render(<BadgeStatutMission statut={statut} libelle={LIBELLES_STATUT_MISSION[statut]} />);
      expect(screen.getByText(LIBELLES_STATUT_MISSION[statut])).toBeInTheDocument();
    },
  );
});

describe("BadgeStatutAffectation", () => {
  it.each(Object.keys(LIBELLES_STATUT_AFFECTATION) as StatutAffectation[])(
    "affiche le libelle textuel pour le statut %s",
    (statut) => {
      render(<BadgeStatutAffectation statut={statut} libelle={LIBELLES_STATUT_AFFECTATION[statut]} />);
      expect(screen.getByText(LIBELLES_STATUT_AFFECTATION[statut])).toBeInTheDocument();
    },
  );
});
