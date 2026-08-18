package com.snef.sgbf.fiph.entity;

import java.time.DayOfWeek;

/** Jour de la semaine d'une ligne {@link Pointage} (section 4.1 : "detail journalier du lundi au dimanche"). */
public enum JourSemaine {
    LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI, DIMANCHE;

    public static JourSemaine depuis(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}
