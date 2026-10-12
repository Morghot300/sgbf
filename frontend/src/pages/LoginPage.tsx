import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { extraireMessageErreur } from "../api/httpClient";
import { ChampMotDePasse } from "../components/ChampMotDePasse";
import { Icone } from "../components/Icone";
import logoSnef from "../assets/brand/logo_snef.png";

/**
 * Page de connexion (section 16 du document source : "S'authentifier - point
 * d'entree commun a tous les acteurs").
 *
 * Depuis le 2026-08-17, la connexion est simple : identifiant (ou e-mail) et
 * mot de passe corrects donnent un acces immediat a l'application, sans
 * aucune seconde etape (le second facteur par code e-mail, introduit puis
 * retire le meme jour, ne subsiste nulle part ici - l'ecran de saisie de
 * code demande par la mission charte graphique, etape 6, n'existe donc plus
 * et n'a pas ete recree).
 */
export default function LoginPage() {
  const { connexion } = useAuth();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [identifiant, setIdentifiant] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);

  async function soumettre(evenement: FormEvent) {
    evenement.preventDefault();
    setErreur(null);
    setEnCours(true);
    try {
      await connexion(identifiant, motDePasse);
      navigate("/", { replace: true });
    } catch (erreurCaptee) {
      setErreur(extraireMessageErreur(erreurCaptee, t("Identifiant ou mot de passe incorrect.")));
    } finally {
      setEnCours(false);
    }
  }

  return (
    <main className="page-login">
      <img src={logoSnef} alt="SNEF" className="logo-lockup" />
      <h1 className="page-login__titre">
        <span className="page-login__titre-accent">SGBF</span> — {t("Connexion")}
      </h1>
      <form onSubmit={soumettre}>
        <label htmlFor="identifiant">{t("Login ou e-mail")}</label>
        <div className="champ-icone">
          <Icone nom="personne" />
          <input
            id="identifiant"
            type="text"
            autoComplete="username"
            value={identifiant}
            onChange={(e) => setIdentifiant(e.target.value)}
            required
            autoFocus
          />
        </div>

        <ChampMotDePasse
          id="mot-de-passe"
          valeur={motDePasse}
          onChange={setMotDePasse}
          autoComplete="current-password"
          required
        />

        {erreur && <p role="alert">{erreur}</p>}
        <button type="submit" disabled={enCours}>
          {enCours ? t("Connexion...") : t("Se connecter")}
        </button>
      </form>
    </main>
  );
}
