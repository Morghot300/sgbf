import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import logoSnef from "../assets/brand/logo_snef.png";
import { Icone } from "./Icone";
import { MenuTheme } from "./MenuTheme";
import NotificationCenter from "./NotificationCenter";
import { SelecteurLangue } from "./SelecteurLangue";
import { VisiteGuidee, type EtapeVisiteGuidee } from "./VisiteGuidee";
import { LIBELLES_STATUT_BON_SORTIE, type StatutBonSortie } from "../types/bonSortie";
import { LIBELLES_CATEGORIE_FIPH, type CategorieFiph } from "../types/fiph";
import { LIBELLES_STATUT_MISSION, type StatutMission } from "../types/mission";

const CLE_VISITE_GUIDEE_VUE = "sgbf-visite-guidee-vue";

const CATEGORIES_FIPH: CategorieFiph[] = [
  "BROUILLONS", "VISEES", "VALIDEES_NIVEAU_2", "VALIDEES_NIVEAU_3", "VALIDEES_DEFINITIVEMENT", "REJETEES",
];
const STATUTS_BON_SORTIE: StatutBonSortie[] = ["BROUILLON", "VISE", "VALIDE"];
const STATUTS_MISSION: StatutMission[] = ["PLANIFIEE", "EN_COURS", "INTERROMPUE", "TERMINEE"];

/**
 * Ossature commune a tout ecran authentifie (evolution du 2026-10-12,
 * inspiree de l'application SNEF "Gestion Administrative" fournie en
 * reference visuelle - sidebar pleine hauteur avec le lockup de marque en
 * tete, barre superieure allegee scopee au contenu plutot qu'un bandeau
 * pleine largeur) : menu lateral de navigation + barre de contenu (theme,
 * langue, notifications, utilisateur), avec le contenu de la page courante
 * rendu via `<Outlet />` (routes imbriquees, voir `App.tsx`).
 *
 * <p>Les liens de navigation sont affiches a tous - c'est le controle serveur
 * (403 sur l'appel API concerne, voir Javadoc de `ProtectedRoute`) qui reste
 * la seule barriere reelle ; masquer un lien n'est qu'un confort d'usage.
 * Seul le lien « Administration » est conditionne a `aLeRole("ADMINISTRATEUR")`,
 * pour eviter d'exposer un module entierement inutilisable a qui n'y a pas acces.
 *
 * <p>Menu volontairement limite aux modules REELLEMENT presents dans
 * l'application (section 19 : "adapter les menus aux fonctionnalites
 * reellement presentes") - pas de rubriques "Agents"/"Services"/"Rapports"
 * suggerees par l'exemple du document source mais sans route/page reelle.
 *
 * <p>Sur petit ecran (section 22), le menu lateral se replie derriere un
 * bouton bascule plutot que de disparaitre : {@code sidebarOuverte} pilote
 * une classe CSS, jamais un demontage du DOM (les liens restent atteignables
 * au clavier/lecteur d'ecran meme replies).
 *
 * <p><strong>Portee de la traduction (section langue, evolution du
 * 2026-10-12)</strong> : seuls les libelles de CETTE ossature sont traduits
 * (voir {@code i18n.ts}) - les pages metier (Bons de sortie, FIPH,
 * Missions, Administration) restent en francais quelle que soit la langue
 * choisie, a traduire dans un increment ulterieur.
 *
 * <p><strong>Visite guidee (evolution du 2026-10-12)</strong> : orientation
 * rapide dans l'ossature (menu lateral + barre de contenu), PAS un doublon
 * du manuel utilisateur Markdown (voir documentation/manuel-utilisateur/) -
 * son role est la decouvrabilite ("ou se trouve X"), le manuel reste la
 * reference pour le detail metier de chaque module. Se lance automatiquement
 * a la toute premiere connexion (memorisee via localStorage, meme principe
 * que le theme/la langue) et peut etre rejouee a tout moment via le bouton
 * "?" de la barre de contenu. Les etapes cotes {@code [data-tour="..."]}
 * sont posees directement sur les elements reels de cette ossature plutot
 * que dupliquees - jamais de copie visuelle a part qui pourrait diverger.
 */
export default function AppLayout() {
  const { utilisateur, deconnexion, aLeRole } = useAuth();
  const { t } = useTranslation();
  const [sidebarOuverte, setSidebarOuverte] = useState(false);
  const [visiteOuverte, setVisiteOuverte] = useState(false);
  const [etapeVisiteIndex, setEtapeVisiteIndex] = useState(0);
  const estAdministrateur = aLeRole("ADMINISTRATEUR");

  const etapesVisite = useMemo<EtapeVisiteGuidee[]>(() => {
    const etapes: EtapeVisiteGuidee[] = [
      {
        cible: null,
        titre: t("Bienvenue dans SGBF"),
        texte: t("Cette courte visite vous présente les grandes zones de l'application. Vous pourrez la revoir à tout moment depuis le bouton d'aide, en haut de l'écran."),
      },
      {
        cible: '[data-tour="nav-tableau-de-bord"]', groupe: "sidebar",
        titre: t("Tableau de bord"), texte: t("Votre page d'accueil : un aperçu de vos bons de sortie en cours et des FIPH qui attendent une action de votre part."),
      },
      {
        cible: '[data-tour="nav-bons-sortie"]', groupe: "sidebar",
        titre: t("Bons de sortie"), texte: t("Créez et suivez vos bons de sortie. Un sous-menu déroulant permet de filtrer directement par statut (brouillon, visé, validé)."),
      },
      {
        cible: '[data-tour="nav-fiph"]', groupe: "sidebar",
        titre: t("FIPH"), texte: t("Vos fiches individuelles de pointage hebdomadaire : complétez vos heures, signez et suivez le circuit de validation."),
      },
      {
        cible: '[data-tour="nav-missions"]', groupe: "sidebar",
        titre: t("Missions"), texte: t("La liste des missions et des affectations du personnel, avec leur période et leur statut."),
      },
    ];
    if (estAdministrateur) {
      etapes.push({
        cible: '[data-tour="nav-administration"]', groupe: "sidebar",
        titre: t("Administration"), texte: t("Réservé aux administrateurs : gestion du personnel, des habilitations et des référentiels partagés (services, chantiers, véhicules...)."),
      });
    }
    etapes.push(
      {
        cible: '[data-tour="topbar-langue"]',
        titre: t("Choix de la langue"), texte: t("Basculez l'interface en français ou en anglais à tout moment."),
      },
      {
        cible: '[data-tour="topbar-theme"]',
        titre: t("Choix du thème"), texte: t("Trois apparences disponibles : claire, sombre, ou « verre » (sombre translucide)."),
      },
      {
        cible: '[data-tour="topbar-notifications"]',
        titre: t("Notifications"), texte: t("Un badge indique vos notifications non lues : validations en attente, décisions sur vos documents, etc."),
      },
      {
        cible: '[data-tour="topbar-utilisateur"]',
        titre: t("Votre compte"), texte: t("Votre identifiant et votre service sont toujours rappelés ici. Le bouton à droite permet de vous déconnecter."),
      },
      {
        cible: null,
        titre: t("Vous êtes prêt(e) !"), texte: t("Pour le détail de chaque module, consultez le manuel utilisateur. Vous pouvez revoir cette visite à tout moment via le bouton d'aide."),
      },
    );
    return etapes;
  }, [t, estAdministrateur]);

  useEffect(() => {
    try {
      if (!localStorage.getItem(CLE_VISITE_GUIDEE_VUE)) {
        setVisiteOuverte(true);
      }
    } catch {
      // Stockage indisponible (navigation privée, quota...) - pas de lancement automatique, le
      // bouton d'aide reste de toute façon disponible pour lancer la visite manuellement.
    }
  }, []);

  function ouvrirVisite() {
    setEtapeVisiteIndex(0);
    setVisiteOuverte(true);
  }
  function fermerVisite() {
    setVisiteOuverte(false);
    setSidebarOuverte(false);
    try {
      localStorage.setItem(CLE_VISITE_GUIDEE_VUE, "1");
    } catch {
      // Confort d'usage uniquement (voir plus haut) - une écriture échouée ne doit jamais bloquer.
    }
  }
  function changerEtapeVisite(index: number) {
    setEtapeVisiteIndex(index);
    setSidebarOuverte(etapesVisite[index]?.groupe === "sidebar");
  }

  return (
    <div className="app-shell">
      <nav className={`app-sidebar${sidebarOuverte ? " app-sidebar--ouverte" : ""}`}>
        <div className="app-sidebar__marque">
          <img src={logoSnef} alt="SNEF" className="logo-lockup logo-lockup--sidebar" />
          <span>SGBF</span>
        </div>

        <NavLink to="/" end onClick={() => setSidebarOuverte(false)} data-tour="nav-tableau-de-bord">
          <Icone nom="tableauDeBord" /> {t("Tableau de bord")}
        </NavLink>

        <div className="app-sidebar__groupe">
          <NavLink to="/bons-sortie" onClick={() => setSidebarOuverte(false)} data-tour="nav-bons-sortie">
            <Icone nom="document" /> {t("Bons de sortie")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {STATUTS_BON_SORTIE.map((statut) => (
              <li key={statut}>
                <NavLink to={`/bons-sortie?statut=${statut}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_STATUT_BON_SORTIE[statut])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="app-sidebar__groupe">
          <NavLink to="/fiph" end onClick={() => setSidebarOuverte(false)} data-tour="nav-fiph">
            <Icone nom="fiph" /> {t("FIPH")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {CATEGORIES_FIPH.map((categorie) => (
              <li key={categorie}>
                <NavLink to={`/fiph?categorie=${categorie}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_CATEGORIE_FIPH[categorie])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        <div className="app-sidebar__groupe">
          <NavLink to="/missions" onClick={() => setSidebarOuverte(false)} data-tour="nav-missions">
            <Icone nom="mission" /> {t("Missions")}
          </NavLink>
          <ul className="app-sidebar__sous-menu">
            {STATUTS_MISSION.map((statut) => (
              <li key={statut}>
                <NavLink to={`/missions?statut=${statut}`} onClick={() => setSidebarOuverte(false)}>
                  {t(LIBELLES_STATUT_MISSION[statut])}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>

        {estAdministrateur && (
          <div className="app-sidebar__groupe">
            <NavLink to="/administration" end onClick={() => setSidebarOuverte(false)} data-tour="nav-administration">
              <Icone nom="administration" /> {t("Administration")}
            </NavLink>
            <ul className="app-sidebar__sous-menu">
              <li><NavLink to="/administration" end onClick={() => setSidebarOuverte(false)}>{t("Personnel et habilitations")}</NavLink></li>
              <li><NavLink to="/administration/referentiels" onClick={() => setSidebarOuverte(false)}>{t("Référentiels")}</NavLink></li>
            </ul>
          </div>
        )}
      </nav>

      <div className="app-body">
        <header className="app-topbar">
          <button
            type="button"
            className="app-topbar__bascule-menu"
            onClick={() => setSidebarOuverte((v) => !v)}
            aria-label={sidebarOuverte ? t("Fermer le menu") : t("Ouvrir le menu")}
            aria-expanded={sidebarOuverte}
          >
            <Icone nom="menu" />
          </button>
          <div className="app-topbar__espace" />
          <button type="button" className="app-topbar__aide" onClick={ouvrirVisite} title={t("Revoir la visite guidée")}>
            <Icone nom="aide" />
          </button>
          <span data-tour="topbar-langue"><SelecteurLangue /></span>
          <span data-tour="topbar-theme"><MenuTheme /></span>
          <span data-tour="topbar-notifications"><NotificationCenter /></span>
          <div className="app-topbar__utilisateur" data-tour="topbar-utilisateur">
            <span className="app-topbar__utilisateur-nom">{utilisateur?.identifiant}</span>
            {utilisateur?.serviceLibelle && <span className="app-topbar__utilisateur-service">{utilisateur.serviceLibelle}</span>}
          </div>
          <button type="button" className="app-topbar__deconnexion" onClick={() => void deconnexion()} title={t("Se déconnecter")}>
            <Icone nom="deconnexion" />
          </button>
        </header>
        <main className="app-content">
          <Outlet />
        </main>
      </div>
      {visiteOuverte && (
        <VisiteGuidee
          etapes={etapesVisite}
          etapeIndex={etapeVisiteIndex}
          onEtapeChange={changerEtapeVisite}
          onFermer={fermerVisite}
        />
      )}
    </div>
  );
}
