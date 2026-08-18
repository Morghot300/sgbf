import { useId, useState } from "react";

/**
 * Champ mot de passe avec case a cocher "Afficher le mot de passe" (bascule
 * entre `type="password"` et `type="text"`) - demande explicite des
 * utilisateurs lors des premiers tests reels de l'application.
 *
 * Masque par defaut a chaque montage (jamais de memorisation du choix entre
 * deux ecrans ou deux sessions) : un champ affiche en clair reste un choix
 * ponctuel de l'utilisateur pour l'ecran en cours, jamais un reglage
 * persistant qui pourrait rester actif a son insu sur un poste partage.
 */
export function ChampMotDePasse({
  id, valeur, onChange, autoComplete, required, minLength, libelle = "Mot de passe",
}: {
  id: string;
  valeur: string;
  onChange: (valeur: string) => void;
  autoComplete?: string;
  required?: boolean;
  minLength?: number;
  libelle?: string;
}) {
  const [visible, setVisible] = useState(false);
  const idCase = useId();

  return (
    <>
      <label htmlFor={id}>{libelle}</label>
      <input
        id={id}
        type={visible ? "text" : "password"}
        autoComplete={autoComplete}
        value={valeur}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        minLength={minLength}
      />
      <label htmlFor={idCase} className="case-a-cocher">
        <input
          id={idCase}
          type="checkbox"
          checked={visible}
          onChange={(e) => setVisible(e.target.checked)}
        />
        Afficher le mot de passe
      </label>
    </>
  );
}
