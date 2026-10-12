import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";

/** Sous-navigation des ecrans d'administration (reserves a l'Administrateur, voir App.tsx). */
export default function AdminNav() {
  const { t } = useTranslation();
  return (
    <nav className="admin-nav">
      <NavLink to="/administration" end>{t("Personnel et habilitations")}</NavLink>
      <NavLink to="/administration/referentiels">{t("Référentiels")}</NavLink>
    </nav>
  );
}
