import type { AxiosAdapter, AxiosResponse } from "axios";
import { afterEach, describe, expect, it } from "vitest";
import { definirJetonAcces, extraireMessageErreur, httpClient } from "./httpClient";

/**
 * Rejoue exactement le bug reel corrige le 2026-08-26 (decouvert en
 * verification live dans un vrai navigateur, jamais repere avant faute de
 * test dedie ici) : ce backend ne renvoie jamais 401 pour une ressource
 * protegee (uniquement 403, y compris pour "/auth/me" - voir Javadoc de
 * l'intercepteur dans httpClient.ts), et l'ancienne garde excluait a tort
 * "/auth/me" du rafraichissement automatique au seul motif qu'elle commence
 * par "/auth/". Consequence reelle : la restauration silencieuse de session
 * au demarrage de l'application (et tout appel API sur un jeton expire) ne
 * fonctionnait JAMAIS - un simple rechargement de page deconnectait
 * l'utilisateur malgre un cookie de rafraichissement pourtant toujours valide.
 */
describe("intercepteur de reponse - rafraichissement automatique sur 403", () => {
  const adapterOriginal = httpClient.defaults.adapter;

  afterEach(() => {
    httpClient.defaults.adapter = adapterOriginal;
    definirJetonAcces(null, null);
  });

  function reponse(status: number, data: unknown, config: Parameters<AxiosAdapter>[0]): AxiosResponse {
    return { status, statusText: String(status), data, headers: {}, config } as AxiosResponse;
  }

  it("un 403 sur /auth/me declenche un rafraichissement puis rejoue la requete avec le nouveau jeton", async () => {
    definirJetonAcces("jeton-expire", 900);
    let appelsMe = 0;
    let appelsRefresh = 0;

    httpClient.defaults.adapter = (async (config) => {
      if (config.url === "/auth/refresh") {
        appelsRefresh++;
        return reponse(200, { jetonAcces: "jeton-frais", expiresInSecondes: 900 }, config);
      }
      if (config.url === "/auth/me") {
        appelsMe++;
        if (config.headers?.Authorization === "Bearer jeton-frais") {
          return reponse(200, { id: 1, identifiant: "test" }, config);
        }
        const erreur = { response: reponse(403, { detail: "Access Denied" }, config), config, isAxiosError: true };
        return Promise.reject(erreur);
      }
      throw new Error("URL inattendue dans ce test : " + config.url);
    }) as AxiosAdapter;

    const resultat = await httpClient.get("/auth/me");

    expect(resultat.data).toEqual({ id: 1, identifiant: "test" });
    expect(appelsRefresh).toBe(1);
    expect(appelsMe).toBe(2); // premier appel (403, jeton perime) + rejeu apres rafraichissement
  });

  it("n'entre jamais en boucle : un 403 sur /auth/login ou /auth/refresh eux-memes n'est jamais rejoue", async () => {
    let appelsRefresh = 0;

    httpClient.defaults.adapter = (async (config) => {
      if (config.url === "/auth/login") {
        const erreur = { response: reponse(403, { detail: "improbable" }, config), config, isAxiosError: true };
        return Promise.reject(erreur);
      }
      if (config.url === "/auth/refresh") {
        appelsRefresh++;
        const erreur = { response: reponse(403, { detail: "refresh invalide" }, config), config, isAxiosError: true };
        return Promise.reject(erreur);
      }
      throw new Error("URL inattendue dans ce test : " + config.url);
    }) as AxiosAdapter;

    await expect(httpClient.post("/auth/login", {})).rejects.toBeTruthy();
    expect(appelsRefresh).toBe(0); // jamais tente depuis /auth/login lui-meme
  });

  it("un appel qui echoue definitivement (rafraichissement lui-meme en echec) propage l'erreur d'origine", async () => {
    definirJetonAcces("jeton-expire", 900);

    httpClient.defaults.adapter = (async (config) => {
      if (config.url === "/auth/refresh") {
        const erreur = { response: reponse(403, {}, config), config, isAxiosError: true };
        return Promise.reject(erreur);
      }
      if (config.url === "/fiph") {
        const erreur = { response: reponse(403, { detail: "Access Denied" }, config), config, isAxiosError: true };
        return Promise.reject(erreur);
      }
      throw new Error("URL inattendue dans ce test : " + config.url);
    }) as AxiosAdapter;

    await expect(httpClient.get("/fiph")).rejects.toMatchObject({ response: { status: 403 } });
  });
});

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
