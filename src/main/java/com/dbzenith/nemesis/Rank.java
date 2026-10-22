package com.dbzenith.nemesis;

import java.util.Locale;

/**
 * A character's standing among fighters (Shadow of War's hierarchy), from its reputation, which is earned and lost
 * by what it does (victories, tournaments, rivalries, new forms; defeats and retreats cost) and is separate from its
 * power: a strong recluse can be unknown, a cunning weakling famous.
 */
public enum Rank {
    UNKNOWN(0), RISING(60), EXPERIENCED(160), ELITE(360), LEGENDARY(700);

    public final int from;

    Rank(int from) {
        this.from = from;
    }

    public static Rank of(int reputation) {
        Rank r = UNKNOWN;
        for (Rank k : values()) if (reputation >= k.from) r = k;
        return r;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "nemesis.dbzenith.rank." + id();
    }
}
