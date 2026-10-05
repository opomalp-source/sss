package com.dbzenith.stats;

import java.util.Locale;

/**
 * Playable races. Phase 0 only stores the choice; racial passives, growth weighting and
 * transformation lines are implemented in Phase 3 (see TODO.md).
 */
public enum Race {
    HUMAN,
    SAIYAN,
    HALF_SAIYAN,
    NAMEKIAN,
    FROST_DEMON,
    MAJIN,
    ANDROID,
    CYBORG,
    VAMPIRE,
    BIO_ANDROID,
    TUFFLE,
    GEN_ALIEN,
    CORE_PERSON;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "race.dbzenith." + id();
    }

    public static Race byId(String id) {
        for (Race r : values()) {
            if (r.id().equals(id)) return r;
        }
        return HUMAN;
    }
}
