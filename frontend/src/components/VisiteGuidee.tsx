import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { Icone } from "./Icone";

export type EtapeVisiteGuidee = {
  /** Selecteur CSS de l'element a mettre en evidence, ou `null` pour une etape centree (introduction/conclusion). */
  cible: string | null;
  titre: string;
  texte: string;
  /** Regroupement logique (ex. "sidebar") - permet a l'appelant de preparer l'etape avant la mesure (voir AppLayout.tsx). */
  groupe?: "sidebar";
};

/**
 * Moteur generique de visite guidee (évolution du 2026-10-12, "ajoute un tuto
 * pour guider les utilisateurs" - visite guidee interactive plutot qu'un
 * doublon du manuel utilisateur Markdown deja existant, voir
 * documentation/manuel-utilisateur/).
 *
 * <p>Composant volontairement DECOUPLE du contenu applicatif : ne connait ni
 * les routes, ni les roles, ni la sidebar - reçoit une liste d'etapes deja
 * filtrees et un index courant pilotes par l'appelant (voir AppLayout.tsx,
 * qui gere en plus l'ouverture de la sidebar mobile pour les etapes qui la
 * ciblent). Rendu via portail dans {@code document.body} pour echapper a
 * tout `overflow`/`z-index` d'un conteneur parent (sidebar, barre de
 * contenu...).
 *
 * <p>Bloque volontairement toute interaction avec la page pendant la visite
 * (voile plein ecran avec `pointer-events: auto`) plutot que de laisser
 * cliquer l'element mis en evidence : une visite guidee multi-page (sidebar
 * puis barre de contenu) ne peut pas rester coherente si l'utilisateur
 * navigue ailleurs au milieu - voir Javadoc d'AppLayout pour le detail des
 * etapes. L'anneau de mise en evidence est purement visuel (jamais une
 * vraie "fenetre" decoupee dans le voile), volontairement plus simple et
 * plus robuste qu'un decoupage par `clip-path`.
 */
export function VisiteGuidee({ etapes, etapeIndex, onEtapeChange, onFermer }: {
  etapes: EtapeVisiteGuidee[];
  etapeIndex: number;
  onEtapeChange: (index: number) => void;
  onFermer: () => void;
}) {
  const { t } = useTranslation();
  const [rectCible, setRectCible] = useState<DOMRect | null>(null);
  const [stylePanneau, setStylePanneau] = useState<{ top: number; left: number; visible: boolean }>({ top: 0, left: 0, visible: false });
  const panneauRef = useRef<HTMLDivElement>(null);
  const etape = etapes[etapeIndex];

  // Mesure la cible de l'etape courante - delai court volontaire (laisse le temps a une transition
  // CSS en cours, ex. ouverture de la sidebar mobile par l'appelant, de se terminer avant de
  // mesurer une position qui serait sinon capturee en plein milieu de l'animation).
  useLayoutEffect(() => {
    let annule = false;
    const minuteur = window.setTimeout(() => {
      if (annule) return;
      if (!etape.cible) {
        setRectCible(null);
        return;
      }
      const element = document.querySelector(etape.cible);
      if (!element) {
        setRectCible(null);
        return;
      }
      element.scrollIntoView({ block: "nearest", behavior: "auto" });
      setRectCible(element.getBoundingClientRect());
    }, 200);
    return () => {
      annule = true;
      window.clearTimeout(minuteur);
    };
  }, [etape]);

  const recalculerPanneau = useCallback(() => {
    const panneau = panneauRef.current;
    if (!panneau) return;
    const taille = panneau.getBoundingClientRect();
    const marge = 16;
    let top: number;
    let left: number;
    if (rectCible) {
      const espaceBas = window.innerHeight - rectCible.bottom;
      const placerAuDessus = espaceBas < taille.height + 24 && rectCible.top > taille.height + 24;
      top = placerAuDessus ? rectCible.top - taille.height - 12 : rectCible.bottom + 12;
      left = rectCible.left + rectCible.width / 2 - taille.width / 2;
    } else {
      top = window.innerHeight / 2 - taille.height / 2;
      left = window.innerWidth / 2 - taille.width / 2;
    }
    left = Math.min(Math.max(left, marge), window.innerWidth - taille.width - marge);
    top = Math.min(Math.max(top, marge), window.innerHeight - taille.height - marge);
    setStylePanneau({ top, left, visible: true });
  }, [rectCible]);

  useLayoutEffect(() => {
    setStylePanneau((s) => ({ ...s, visible: false }));
    recalculerPanneau();
  }, [recalculerPanneau, etapeIndex]);

  useEffect(() => {
    window.addEventListener("resize", recalculerPanneau);
    return () => window.removeEventListener("resize", recalculerPanneau);
  }, [recalculerPanneau]);

  useEffect(() => {
    panneauRef.current?.focus();
  }, [etapeIndex]);

  useEffect(() => {
    function surTouche(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onFermer();
      } else if (e.key === "ArrowRight" && etapeIndex < etapes.length - 1) {
        onEtapeChange(etapeIndex + 1);
      } else if (e.key === "ArrowLeft" && etapeIndex > 0) {
        onEtapeChange(etapeIndex - 1);
      }
    }
    document.addEventListener("keydown", surTouche);
    return () => document.removeEventListener("keydown", surTouche);
  }, [etapeIndex, etapes.length, onEtapeChange, onFermer]);

  const estPremier = etapeIndex === 0;
  const estDernier = etapeIndex === etapes.length - 1;

  return createPortal(
    <div className="visite-guidee">
      <div className="visite-guidee__voile" onClick={onFermer} />
      {rectCible && (
        <div
          className="visite-guidee__surbrillance"
          style={{
            top: rectCible.top - 6,
            left: rectCible.left - 6,
            width: rectCible.width + 12,
            height: rectCible.height + 12,
          }}
        />
      )}
      <div
        className="visite-guidee__panneau"
        style={{ top: stylePanneau.top, left: stylePanneau.left, visibility: stylePanneau.visible ? "visible" : "hidden" }}
        ref={panneauRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={t("Visite guidée")}
      >
        <button type="button" className="visite-guidee__fermer" onClick={onFermer} aria-label={t("Fermer la visite guidée")}>
          <Icone nom="fermer" taille={16} />
        </button>
        <p className="visite-guidee__compteur">{etapeIndex + 1} / {etapes.length}</p>
        <h3>{etape.titre}</h3>
        <p>{etape.texte}</p>
        <div className="barre-actions">
          {!estPremier && (
            <button type="button" className="bouton-secondaire" onClick={() => onEtapeChange(etapeIndex - 1)}>
              {t("Précédent")}
            </button>
          )}
          <span className="visite-guidee__espace" />
          {!estDernier ? (
            <button type="button" onClick={() => onEtapeChange(etapeIndex + 1)}>{t("Suivant")}</button>
          ) : (
            <button type="button" onClick={onFermer}>{t("Terminer")}</button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
