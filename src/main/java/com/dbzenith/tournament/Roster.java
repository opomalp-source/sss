package com.dbzenith.tournament;

import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;

import java.util.List;
import java.util.Locale;

/**
 * The fighters who enter the World Martial Arts Tournament (CX-17c), each with their own look and style. {@code seed}
 * is how they fare when two of them meet and nobody is watching (higher wins more often); Mr. Satan is the reigning
 * champion and is always drawn into the far half of the bracket.
 */
public enum Roster {
    MR_SATAN(6, List.of()),
    SPOPOVICH(5, List.of(Techniques.KI_BLAST)),
    PINTAR(4, List.of()),
    JEWEL(3, List.of(Techniques.KI_BLAST)),
    NAM(3, List.of()),
    RANFAN(2, List.of()),
    YAMU(2, List.of(Techniques.KI_BLAST));

    private final int seed;
    private final List<Technique> techniques;

    Roster(int seed, List<Technique> techniques) {
        this.seed = seed;
        this.techniques = techniques;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int seed() {
        return seed;
    }

    public List<Technique> techniques() {
        return techniques;
    }

    public String nameKey() {
        return "entity.dbzenith." + id();
    }

    public static Roster byId(String id) {
        for (Roster r : values()) if (r.id().equals(id)) return r;
        return null;
    }
}
