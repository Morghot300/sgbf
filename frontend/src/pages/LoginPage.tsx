import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { extraireMessageErreur } from "../api/httpClient";
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
      setErreur(extraireMessageErreur(erreurCaptee, "Identifiant ou mot de passe incorrect."));
    } finally {
      setEnCours(false);
    }
  }

  return (
    <main className="page-login">
      <img src={logoSnef} alt="SNEF" className="logo-lockup" />
      <h1>SGBF - Connexion</h1>
      <form onSubmit={soumettre}>
        <label htmlFor="identifiant">Login ou e-mail</label>
        <input
          id="identifiant"
          type="text"
          autoComplete="username"
          value={identifiant}
          onChange={(e) => setIdentifiant(e.target.value)}
          required
          autoFocus
        />

        <label htmlFor="mot-de-passe">Mot de passe</label>
        <input
          id="mot-de-passe"
          type="password"
          autoComplete="current-password"
          value={motDePasse}
          onChange={(e) => setMotDePasse(e.target.value)}
          required
        />

        {erreur && <p role="alert">{erreur}</p>}
        <button type="submit" disabled={enCours}>
          {enCours ? "Connexion..." : "Se connecter"}
        </button>
      </form>
    </main>
  );
}
