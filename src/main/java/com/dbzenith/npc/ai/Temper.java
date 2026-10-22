package com.dbzenith.npc.ai;

import java.util.Collection;

/**
 * The traits a fighter actually fights with: its {@link Personality} (the one personality system), adjusted by the
 * Nemesis traits it has picked up ({@code nemesis.Trait}: vengeful, loyal, calm...). Same eight traits as a
 * personality, each 0..1. Ordinary fighters have no traits, so their temper is their personality's.
 */
public final class Temper {
    public final Personality kind;
    public final double aggression, caution, patience, pride, honor, courage, recklessness, discipline;

    public Temper(Personality kind, double aggression, double caution, double patience, double pride, double honor,
                  double courage, double recklessness, double discipline) {
        this.kind = kind;
        this.aggression = clamp(aggression);
        this.caution = clamp(caution);
        this.patience = clamp(patience);
        this.pride = clamp(pride);
        this.honor = clamp(honor);
        this.courage = clamp(courage);
        this.recklessness = clamp(recklessness);
        this.discipline = clamp(discipline);
    }

    public static Temper of(Personality p) {
        return new Temper(p, p.aggression, p.caution, p.patience, p.pride, p.honor, p.courage, p.recklessness, p.discipline);
    }

    /** One adjustment of the eight traits (a Nemesis trait's). */
    public record Shift(double aggression, double caution, double patience, double pride, double honor, double courage,
                        double recklessness, double discipline) {}

    /** The personality with these shifts added. */
    public static Temper of(Personality p, Collection<Shift> shifts) {
        double a = p.aggression, c = p.caution, pa = p.patience, pr = p.pride, h = p.honor, co = p.courage, r = p.recklessness, d = p.discipline;
        for (Shift s : shifts) {
            a += s.aggression;
            c += s.caution;
            pa += s.patience;
            pr += s.pride;
            h += s.honor;
            co += s.courage;
            r += s.recklessness;
            d += s.discipline;
        }
        return new Temper(p, a, c, pa, pr, h, co, r, d);
    }

    public FightStyle style() {
        return kind.style();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
