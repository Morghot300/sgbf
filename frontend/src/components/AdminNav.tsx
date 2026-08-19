import { NavLink } from "react-router-dom";

/** Sous-navigation des ecrans d'administration (reserves a l'Administrateur, voir App.tsx). */
export default function AdminNav() {
  return (
    <nav className="admin-nav">
      <NavLink to="/administration" end>Personnel et habilitations</NavLink>
      <NavLink to="/administration/referentiels">Référentiels</NavLink>
    </nav>
  );
}
