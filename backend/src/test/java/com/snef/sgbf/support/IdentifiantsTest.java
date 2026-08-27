package com.snef.sgbf.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fabrique de suffixes uniques pour les jeux de donnees des tests d'integration.
 *
 * <p><strong>Pourquoi&nbsp;:</strong> chaque IT tirait historiquement son suffixe
 * via {@link System#nanoTime()} puis le tronquait ({@code suffixe % 100_000L})
 * pour tenir dans la colonne {@code users.matricule} (VARCHAR(20), contrainte
 * unique {@code uq_users_matricule}). Lorsque la suite complete tourne, deux
 * {@code @BeforeEach} pouvaient tomber dans la meme fenetre de 100&nbsp;000&nbsp;ns
 * (resolution de {@code nanoTime()} sur certaines plateformes, GC, ordonnancement
 * OS...) et produire le meme matricule court&nbsp;: MySQL rejetait alors
 * l'insertion ({@code Duplicate entry 'EMT39200' for key 'users.uq_users_matricule'})
 * et faisait echouer, de facon non deterministe, un test sans aucun rapport avec
 * une {@code DataIntegrityViolationException}.
 *
 * <p><strong>Solution&nbsp;:</strong> un compteur {@link AtomicLong} partage par
 * toute la JVM de test (surefire&nbsp;: {@code forkCount=1}, {@code reuseForks=true})
 * est initialise une seule fois sur {@code nanoTime()} — pour ne pas entrer en
 * collision avec les lignes deja commitees par une execution precedente — puis
 * incremente de&nbsp;1 a chaque appel. Deux suffixes d'un meme run different donc
 * toujours d'au moins&nbsp;1, et leurs versions courtes ({@code % 100_000L})
 * restent distinctes tant qu'un run n'enchaine pas 100&nbsp;000 jeux de donnees,
 * ce qui n'arrive pas.
 */
public final class IdentifiantsTest {

    private static final AtomicLong COMPTEUR = new AtomicLong(System.nanoTime());

    private IdentifiantsTest() {
    }

    /**
     * Retourne un suffixe {@code long} strictement croissant, unique au sein
     * d'une execution de tests. Remplace {@code System.nanoTime()} dans les
     * {@code @BeforeEach} qui construisent des jeux de donnees.
     */
    public static long prochainSuffixe() {
        return COMPTEUR.incrementAndGet();
    }
}
