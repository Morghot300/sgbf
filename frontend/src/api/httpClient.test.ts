import { describe, expect, it } from "vitest";
import { extraireMessageErreur } from "./httpClient";

/**
 * `extraireMessageErreur` est le seul point de traduction d'une erreur API
 * en message affichable (voir Javadoc de la fonction) : un bug ici affiche
 * un message trompeur a l'utilisateur pour TOUTE erreur de l'application,
 * d'ou la couverture dediee.
 */
describe("extraireMessageErreur", () => {
  it("reprend le champ `detail` RFC 7807 renvoye par le backend", () => {
    const erreurAxios = {
      isAxiosError: true,
      response: { data: { detail: "Ce compte est desactive ou verrouille. Contactez votre administrateur." } },
    };
    expect(extraireMessageErreur(erreurAxios, "message par defaut")).toBe(
      "Ce compte est desactive ou verrouille. Contactez votre administrateur.",
    );
  });

  it("retombe sur le message par defaut si la reponse Axios n'a pas de champ `detail`", () => {
    const erreurAxios = { isAxiosError: true, response: { data: {} } };
    expect(extraireMessageErreur(erreurAxios, "message par defaut")).toBe("message par defaut");
  });

  it("retombe sur le message par defaut si la reponse Axios n'a aucune donnee (ex. reseau coupe)", () => {
    const erreurAxios = { isAxiosError: true, response: undefined };
    expect(extraireMessageErreur(erreurAxios, "message par defaut")).toBe("message par defaut");
  });

  it("retombe sur le message par defaut pour une erreur qui n'est pas une erreur Axios", () => {
    expect(extraireMessageErreur(new Error("panne interne"), "message par defaut")).toBe("message par defaut");
    expect(extraireMessageErreur("chaine quelconque", "message par defaut")).toBe("message par defaut");
    expect(extraireMessageErreur(null, "message par defaut")).toBe("message par defaut");
  });

  it("ignore un `detail` vide (chaine vide) et retombe sur le message par defaut", () => {
    const erreurAxios = { isAxiosError: true, response: { data: { detail: "" } } };
    expect(extraireMessageErreur(erreurAxios, "message par defaut")).toBe("message par defaut");
  });
});
