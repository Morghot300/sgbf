import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { historiqueAuditFiph, telechargerAuditCsv, telechargerAuditPdf } from "../../api/auditApi";
import {
  completerPointage, creerNouvelleVersionFiph, definirDateFinFiph, listerValidations, listerVersionsFiph,
  modifierMissionFiph, obtenirFiph, obtenirFiphVersion, ouvrirPdfFiphVersion, priseEnMainSuperAdminFiph, signerFiph,
  soumettreFiph, validerFiph,
} from "../../api/fiphApi";
import { extraireMessageErreur } from "../../api/httpClient";
import { listerMissions } from "../../api/missionApi";
import { EtatAsync } from "../../components/EtatAsync";
import { BadgeStatutFiph } from "../../components/StatutBadge";
import { useAuth } from "../../auth/AuthContext";
import {
  LIBELLES_JOUR_SEMAINE, LIBELLES_STATUT_FIPH, NIVEAU_VALIDATION_ATTENDU, estFiphModifiable, estPointageModifiable,
  type DecisionValidation, type PointageDto,
} from "../../types/fiph";

/**
 * Cycle de vie complet d'une FIPH : complement des heures, signature de
 * l'emetteur, soumission, validation a trois niveaux, telechargement PDF,
 * nouvelle version post-validation (section 12, 21 du document source).
 *
 * Les boutons d'action visibles cote client sont un confort d'usage, pas la
 * securite reelle : chaque action reste entierement revalidee cote serveur
 * (role, perimetre, separation des responsabilites RG-HAB-004) - un clic sur
 * un bouton affiche a tort echoue simplement avec un message d'erreur clair.
 */
export default function FiphDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const fiphId = Number(id);
  const queryClient = useQueryClient();
  const { aLeRole } = useAuth();
  const [erreurAction, setErreurAction] = useState<string | null>(null);
  const [motifNouvelleVersion, setMotifNouvelleVersion] = useState("");
  const [commentaireDecision, setCommentaireDecision] = useState("");
  const [commentairePriseEnMain, setCommentairePriseEnMain] = useState("");
  const [pointagesModifies, setPointagesModifies] = useState<Record<number, { heuresNormales: string; heuresSup: string }>>({});
  const [dateFinSaisie, setDateFinSaisie] = useState("");
  const [motifDateFin, setMotifDateFin] = useState("");
  const [modificationMissionOuverte, setModificationMissionOuverte] = useState(false);
  const [missionSaisie, setMissionSaisie] = useState("");

  const fiph = useQuery({ queryKey: ["fiph-detail", fiphId], queryFn: () => obtenirFiph(fiphId) });
  const versionId = fiph.data?.versionCouranteId;

  const version = useQuery({
    queryKey: ["fiph-version", versionId],
    queryFn: () => obtenirFiphVersion(versionId as number),
    enabled: versionId !== undefined,
  });
  const validations = useQuery({
    queryKey: ["fiph-validations", versionId],
    queryFn: () => listerValidations(versionId as number),
    enabled: versionId !== undefined,
  });
  const historique = useQuery({
    queryKey: ["fiph-audit", fiphId],
    queryFn: () => historiqueAuditFiph(fiphId),
  });
  const versions = useQuery({
    queryKey: ["fiph-toutes-versions", fiphId],
    queryFn: () => listerVersionsFiph(fiphId),
  });

  function invalider() {
    void queryClient.invalidateQueries({ queryKey: ["fiph-detail", fiphId] });
    void queryClient.invalidateQueries({ queryKey: ["fiph-version", versionId] });
    void queryClient.invalidateQueries({ queryKey: ["fiph-validations", versionId] });
    void queryClient.invalidateQueries({ queryKey: ["fiph-audit", fiphId] });
    void queryClient.invalidateQueries({ queryKey: ["fiph-toutes-versions", fiphId] });
    void queryClient.invalidateQueries({ queryKey: ["fiph"] });
  }
  function gererErreur(e: unknown, messageParDefaut: string) {
    setErreurAction(extraireMessageErreur(e, messageParDefaut));
  }

  const enregistrerPointage = useMutation({
    mutationFn: (p: PointageDto) => completerPointage(versionId as number, {
      datePointage: p.datePointage,
      heuresNormales: Number(pointagesModifies[p.id]?.heuresNormales ?? p.heuresNormales),
      heuresSup: Number(pointagesModifies[p.id]?.heuresSup ?? p.heuresSup),
    }),
    onSuccess: invalider,
    onError: (e) => gererErreur(e, t("Impossible d'enregistrer ce pointage.")),
  });
  const signer = useMutation({
    mutationFn: () => signerFiph(versionId as number),
    onSuccess: invalider,
    onError: (e) => gererErreur(e, t("Impossible de signer cette FIPH.")),
  });
  const soumettre = useMutation({
    mutationFn: () => soumettreFiph(versionId as number),
    onSuccess: invalider,
    onError: (e) => gererErreur(e, t("Impossible de soumettre cette FIPH.")),
  });
  const valider = useMutation({
    mutationFn: (decision: DecisionValidation) => validerFiph(versionId as number, NIVEAU_VALIDATION_ATTENDU[version.data!.statutVersion] as number, {
      decision, commentaire: commentaireDecision || null,
    }),
    onSuccess: () => { setCommentaireDecision(""); invalider(); },
    onError: (e) => gererErreur(e, t("Impossible d'enregistrer cette décision de validation.")),
  });
  const nouvelleVersion = useMutation({
    mutationFn: () => creerNouvelleVersionFiph(fiphId, { motifModification: motifNouvelleVersion }),
    onSuccess: () => { setMotifNouvelleVersion(""); invalider(); },
    onError: (e) => gererErreur(e, t("Impossible de créer une nouvelle version.")),
  });
  const priseEnMain = useMutation({
    mutationFn: () => priseEnMainSuperAdminFiph(versionId as number, { commentaire: commentairePriseEnMain }),
    onSuccess: () => { setCommentairePriseEnMain(""); invalider(); },
    onError: (e) => gererErreur(e, t("Impossible d'effectuer la prise en main exceptionnelle de cette FIPH.")),
  });
  const definirDateFin = useMutation({
    mutationFn: () => definirDateFinFiph(versionId as number, { dateFin: dateFinSaisie, motifModification: motifDateFin || null }),
    onSuccess: () => { setMotifDateFin(""); invalider(); },
    onError: (e) => gererErreur(e, t("Impossible de définir la date de fin de la période.")),
  });
  const missions = useQuery({ queryKey: ["missions"], queryFn: listerMissions, enabled: modificationMissionOuverte });
  const modifierMission = useMutation({
    mutationFn: () => modifierMissionFiph(fiphId, { missionId: missionSaisie ? Number(missionSaisie) : null }),
    onSuccess: () => { setModificationMissionOuverte(false); invalider(); },
    onError: (e) => gererErreur(e, t("Impossible de modifier la mission de cette FIPH.")),
  });

  // Role litteral, jamais herite via la hierarchie Spring Security (voir Javadoc de aLeRole) : contrairement a
  // aLeRole("ADMINISTRATEUR"), qui accepte aussi un Super Administrateur par confort d'affichage, la prise en
  // main exceptionnelle est une fonctionnalite strictement reservee au Super Administrateur (evolution du
  // 2026-08-19, section 15) - jamais accessible a un Administrateur standard, meme via ce meme ecran.
  const estSuperAdministrateur = aLeRole("SUPER_ADMINISTRATEUR");
  const peutCompleter = aLeRole("CHARGE_AFFAIRES") || aLeRole("PERSONNE_HABILITEE");
  // Meme perimetre que le backend (FiphController.modifierMission) : CA/PH du service, RH et Super
  // Administrateur a perimetre global (section 6-9 : "creation OU modification d'une FIPH").
  const peutModifierMission = peutCompleter || aLeRole("RH") || estSuperAdministrateur;
  const niveauAttendu = version.data ? NIVEAU_VALIDATION_ATTENDU[version.data.statutVersion] : undefined;
  const roleRequisParNiveau: Record<number, string> = { 2: "CHARGE_AFFAIRES", 3: "RESPONSABLE_ACTIVITE", 4: "DIRECTION" };
  const peutValiderNiveauCourant = niveauAttendu !== undefined
    && (aLeRole(roleRequisParNiveau[niveauAttendu]) || (niveauAttendu === 2 && aLeRole("PERSONNE_HABILITEE")));

  return (
    <div>
      <h1>{t("FIPH")} #{fiphId}</h1>
      <EtatAsync chargement={fiph.isLoading} erreur={fiph.error} donnees={fiph.data}>
        {(f) => (
          <table className="fiche">
            <tbody>
              <tr><th>{t("Agent")}</th><td>{f.agentNomComplet} ({f.agentMatricule})</td></tr>
              <tr><th>{t("Service")}</th><td>{f.serviceLibelle}</td></tr>
              <tr>
                <th>{t("Date de début")}</th>
                <td>
                  {f.dateDebutPeriode}{" "}
                  <span className="note">
                    ({f.origine === "BON_SORTIE" ? t("automatique, issue du bon de sortie") : t("saisie à la création")} — {t("non modifiable")})
                  </span>
                </td>
              </tr>
              <tr>
                <th>{t("Origine")}</th>
                <td>
                  {f.origine === "BON_SORTIE" && f.bonSortieId
                    ? <>{t("Générée depuis le")} <Link to={`/bons-sortie/${f.bonSortieId}`}>{t("bon de sortie")} #{f.bonSortieId}</Link></>
                    : f.origine === "BON_SORTIE" ? t("Générée depuis un bon de sortie") : t("Créée manuellement (Code Service)")}
                </td>
              </tr>
              <tr>
                <th>{t("Mission")}</th>
                <td>
                  {f.missionCodeHN ? `${f.missionCodeHN} — ${f.missionChantierLibelle}` : t("Non renseignée")}
                  {peutModifierMission && (
                    <>
                      {" "}
                      <button
                        type="button"
                        onClick={() => { setMissionSaisie(f.missionId ? String(f.missionId) : ""); setModificationMissionOuverte((v) => !v); }}
                      >
                        {modificationMissionOuverte ? t("Annuler") : t("Modifier")}
                      </button>
                    </>
                  )}
                </td>
              </tr>
            </tbody>
          </table>
        )}
      </EtatAsync>

      {fiph.data?.avertissementMission && (
        <p role="alert" className="avertissement">{fiph.data.avertissementMission}</p>
      )}

      {/* Section 6-9 : la mission reste modifiable APRES la creation, jamais seulement a la creation
          (anomalie identifiee et corrigee - la premiere passe de cette evolution n'avait couvert que
          la creation). Repliee par defaut (meme principe que CorrectionBonSortie) pour ne pas
          surcharger la fiche d'un formulaire visible en permanence. */}
      {modificationMissionOuverte && (
        <section className="panneau-imbrique">
          <h3>{t("Modifier la mission")}</h3>
          <label htmlFor="missionSaisie">{t("Code Mission")}</label>
          <select id="missionSaisie" value={missionSaisie} onChange={(e) => setMissionSaisie(e.target.value)}>
            <option value="">{t("— Non renseignée —")}</option>
            {missions.data?.map((m) => (
              <option key={m.id} value={m.id}>{m.codeHN} — {m.chantierLibelle} ({m.dateDebutPrevue} → {m.dateFinPrevue})</option>
            ))}
          </select>
          <div className="barre-actions">
            <button type="button" onClick={() => modifierMission.mutate()} disabled={modifierMission.isPending}>
              {modifierMission.isPending ? t("Enregistrement...") : t("Confirmer")}
            </button>
          </div>
        </section>
      )}

      {fiph.data?.origine === "BON_SORTIE" && (
        <p className="note">
          {t("Le visa de l'agent titulaire est acquis automatiquement dès la validation du bon de sortie déclencheur : aucune signature supplémentaire ne lui est demandée pour cette FIPH.")}
        </p>
      )}

      {erreurAction && <p role="alert">{erreurAction}</p>}

      <EtatAsync chargement={version.isLoading} erreur={version.error} donnees={version.data}>
        {(v) => (
          <>
            <h2>{t("Version")} {v.numeroVersion} <BadgeStatutFiph statut={v.statutVersion} libelle={t(LIBELLES_STATUT_FIPH[v.statutVersion])} /></h2>

            <table className="fiche">
              <tbody>
                <tr><th>{t("Date de fin")}</th><td>{v.dateFinPeriode ?? t("Non encore définie")}</td></tr>
                <tr><th>{t("Nombre de jours")}</th><td>{v.pointages.length}</td></tr>
              </tbody>
            </table>

            {v.avertissementPeriode && <p role="alert" className="avertissement">{v.avertissementPeriode}</p>}

            {peutCompleter && (
              <section>
                <h3>{v.dateFinPeriode ? t("Modifier la date de fin") : t("Définir la date de fin")}</h3>
                <label htmlFor="dateFin">{t("Date de fin")}</label>
                <input id="dateFin" type="date" min={fiph.data?.dateDebutPeriode} value={dateFinSaisie}
                       onChange={(e) => setDateFinSaisie(e.target.value)} />
                {v.statutVersion === "VALIDEE_DEFINITIVEMENT" && (
                  <>
                    <label htmlFor="motifDateFin">{t("Motif de la modification (obligatoire, FIPH déjà validée définitivement)")}</label>
                    <textarea id="motifDateFin" maxLength={500} value={motifDateFin} onChange={(e) => setMotifDateFin(e.target.value)} />
                  </>
                )}
                <button
                  type="button"
                  onClick={() => definirDateFin.mutate()}
                  disabled={definirDateFin.isPending || !dateFinSaisie
                    || (v.statutVersion === "VALIDEE_DEFINITIVEMENT" && !motifDateFin.trim())}
                >
                  {definirDateFin.isPending ? t("Enregistrement...") : t("Confirmer la date de fin")}
                </button>
              </section>
            )}

            <table className="tableau">
              <thead>
                <tr><th>{t("Jour")}</th><th>{t("Date")}</th><th>{t("Heures normales")}</th><th>{t("Heures sup.")}</th><th>{t("Mission / service")}</th>{estPointageModifiable(v.statutVersion) && peutCompleter && <th></th>}</tr>
              </thead>
              <tbody>
                {v.pointages.map((p) => {
                  const modifiable = estPointageModifiable(v.statutVersion) && peutCompleter;
                  const valeurs = pointagesModifies[p.id] ?? { heuresNormales: String(p.heuresNormales), heuresSup: String(p.heuresSup) };
                  return (
                    <tr key={p.id}>
                      <td>{t(LIBELLES_JOUR_SEMAINE[p.jourSemaine])}</td>
                      <td>{p.datePointage}</td>
                      <td>
                        {modifiable
                          ? <input type="number" step="0.5" min="0" value={valeurs.heuresNormales}
                              onChange={(e) => setPointagesModifies((m) => ({ ...m, [p.id]: { ...valeurs, heuresNormales: e.target.value } }))} />
                          : p.heuresNormales}
                      </td>
                      <td>
                        {modifiable
                          ? <input type="number" step="0.5" min="0" value={valeurs.heuresSup}
                              onChange={(e) => setPointagesModifies((m) => ({ ...m, [p.id]: { ...valeurs, heuresSup: e.target.value } }))} />
                          : p.heuresSup}
                      </td>
                      <td>{p.codeMission ?? p.codeService ?? "—"}</td>
                      {modifiable && (
                        <td><button type="button" onClick={() => enregistrerPointage.mutate(p)} disabled={enregistrerPointage.isPending}>{t("Enregistrer")}</button></td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr><th colSpan={2}>{t("Total")}</th><th>{v.totalHN}</th><th>{v.totalHS}</th><th colSpan={2}></th></tr>
              </tfoot>
            </table>

            <div className="barre-actions">
              {estFiphModifiable(v.statutVersion) && (
                <button type="button" onClick={() => signer.mutate()} disabled={signer.isPending}>
                  {signer.isPending ? t("Signature...") : t("Signer (je suis l'agent titulaire)")}
                </button>
              )}
              {v.statutVersion === "SIGNEE" && peutCompleter && (
                <button type="button" onClick={() => soumettre.mutate()} disabled={soumettre.isPending}>
                  {soumettre.isPending ? t("Soumission...") : t("Soumettre au circuit de validation")}
                </button>
              )}
              <button type="button" onClick={() => void ouvrirPdfFiphVersion(v.id)}>
                {v.statutVersion === "VALIDEE_DEFINITIVEMENT" ? t("Télécharger le PDF") : t("Prévisualiser (PDF)")}
              </button>
            </div>

            {niveauAttendu !== undefined && peutValiderNiveauCourant && (
              <section>
                <h3>{t("Décision de validation — niveau")} {niveauAttendu}</h3>
                <label htmlFor="commentaire">{t("Commentaire (obligatoire pour un rejet ou un retour pour correction)")}</label>
                <textarea id="commentaire" maxLength={500} value={commentaireDecision} onChange={(e) => setCommentaireDecision(e.target.value)} />
                <div className="barre-actions">
                  <button type="button" onClick={() => valider.mutate("VALIDEE")} disabled={valider.isPending}>{t("Valider")}</button>
                  <button type="button" onClick={() => valider.mutate("RETOUR_POUR_CORRECTION")} disabled={valider.isPending}>{t("Retourner pour correction")}</button>
                  <button type="button" onClick={() => valider.mutate("REJETEE")} disabled={valider.isPending}>{t("Rejeter")}</button>
                </div>
              </section>
            )}

            {estSuperAdministrateur && v.statutVersion !== "VALIDEE_DEFINITIVEMENT" && (
              <section className="panneau-alerte">
                <h3>{t("Prise en main exceptionnelle (Super Administrateur)")}</h3>
                <p className="note">
                  {t("Fait progresser cette FIPH jusqu'à « Validée définitivement » en franchissant les niveaux de validation restants, hors du circuit normal. Chaque étape franchie de cette façon reste tracée distinctement d'une validation normale (voir la colonne « Type » du tableau des validations ci-dessous) et journalisée dans l'audit — cette possibilité n'efface jamais la traçabilité du processus normal.")}
                </p>
                <label htmlFor="commentairePriseEnMain">{t("Justification (obligatoire, ex. « Responsable indisponible — continuité du processus de validation »)")}</label>
                <textarea
                  id="commentairePriseEnMain"
                  maxLength={500}
                  value={commentairePriseEnMain}
                  onChange={(e) => setCommentairePriseEnMain(e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => priseEnMain.mutate()}
                  disabled={priseEnMain.isPending || !commentairePriseEnMain.trim()}
                >
                  {priseEnMain.isPending ? t("Prise en main...") : t("Prendre en main et valider jusqu'au bout")}
                </button>
              </section>
            )}

            {v.statutVersion === "VALIDEE_DEFINITIVEMENT" && peutCompleter && (
              <section>
                <h3>{t("Créer une nouvelle version (correction post-validation)")}</h3>
                <label htmlFor="motif">{t("Motif de la modification")}</label>
                <textarea id="motif" maxLength={500} value={motifNouvelleVersion} onChange={(e) => setMotifNouvelleVersion(e.target.value)} />
                <button type="button" onClick={() => nouvelleVersion.mutate()} disabled={nouvelleVersion.isPending || !motifNouvelleVersion.trim()}>
                  {nouvelleVersion.isPending ? t("Création...") : t("Créer une nouvelle version")}
                </button>
              </section>
            )}

            <EtatAsync chargement={versions.isLoading} erreur={versions.error} donnees={versions.data}>
              {(liste) => liste.length > 1 && (
                <section>
                  <h3>{t("Historique des versions")}</h3>
                  <p className="note">
                    {t("Cette FIPH a fait l'objet d'au moins une correction post-validation (RG-VER-001 à 007) : chaque version précédente reste consultable et intacte, avec sa propre empreinte d'intégrité.")}
                  </p>
                  <table className="tableau tableau--compact">
                    <thead><tr><th>{t("Version")}</th><th>{t("Statut")}</th><th>{t("Créée le")}</th><th>{t("Motif")}</th><th>{t("Empreinte")}</th></tr></thead>
                    <tbody>
                      {liste.map((vers) => (
                        <tr key={vers.id} style={vers.id === v.id ? { fontWeight: 600 } : undefined}>
                          <td>{vers.numeroVersion}{vers.id === v.id ? ` (${t("courante")})` : ""}</td>
                          <td><BadgeStatutFiph statut={vers.statutVersion} libelle={t(LIBELLES_STATUT_FIPH[vers.statutVersion])} /></td>
                          <td>{vers.dateCreation}</td>
                          <td>{vers.motifModification ?? "—"}</td>
                          <td>{vers.empreinteIntegrite ? `${vers.empreinteIntegrite.slice(0, 12)}…` : "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              )}
            </EtatAsync>

            <EtatAsync chargement={validations.isLoading} erreur={validations.error} donnees={validations.data}>
              {(liste) => liste.length > 0 && (
                <section>
                  <h3>{t("Validations")}</h3>
                  <table className="tableau">
                    <thead><tr><th>{t("Niveau")}</th><th>{t("Validateur")}</th><th>{t("Type")}</th><th>{t("Décision")}</th><th>{t("Date")}</th><th>{t("Commentaire")}</th></tr></thead>
                    <tbody>
                      {liste.map((val) => (
                        <tr key={val.id}>
                          <td>{val.niveauValidation}</td>
                          <td>{val.utilisateurIdentifiant}</td>
                          <td>
                            {val.priseEnMainSuperAdmin
                              ? <span className="badge badge--attente">{t("Prise en main Super Admin")}</span>
                              : t("Normale")}
                          </td>
                          <td>{val.decision}</td>
                          <td>{val.dateValidation}</td>
                          <td>{val.commentaire ?? "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              )}
            </EtatAsync>
          </>
        )}
      </EtatAsync>

      <EtatAsync chargement={historique.isLoading} erreur={historique.error} donnees={historique.data}>
        {(evenements) => evenements.length > 0 && (
          <section>
            <h3>{t("Historique d'audit")}</h3>
            {(aLeRole("DIRECTION") || aLeRole("RH") || aLeRole("ADMINISTRATEUR")) && (
              <div className="barre-actions">
                <button type="button" onClick={() => void telechargerAuditCsv(fiphId)}>{t("Exporter en CSV")}</button>
                <button type="button" onClick={() => void telechargerAuditPdf(fiphId)}>{t("Exporter en PDF")}</button>
              </div>
            )}
            <table className="tableau tableau--compact">
              <thead><tr><th>{t("Date")}</th><th>{t("Action")}</th><th>{t("Utilisateur")}</th></tr></thead>
              <tbody>
                {evenements.map((ev) => (
                  <tr key={ev.id}>
                    <td>{ev.dateAction}</td>
                    <td>{ev.action}</td>
                    <td>{ev.utilisateur ?? t("système")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      </EtatAsync>
    </div>
  );
}
