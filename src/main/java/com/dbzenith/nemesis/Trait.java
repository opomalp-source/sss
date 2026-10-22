package com.dbzenith.nemesis;

import com.dbzenith.npc.ai.Temper;

import java.util.Locale;

/**
 * A named character's traits, on top of its {@link com.dbzenith.npc.ai.Personality} (arrogant, honorable, aggressive,
 * cowardly... are personalities; these are what experience adds). Each shifts the eight fighting traits the combat AI
 * weighs ({@link #shift}) and changes how the character lives between fights (training, grudges, friendships):
 * <ul>
 *   <li><b>vengeful</b>: trains obsessively after defeats, holds grudges, ambushes;</li>
 *   <li><b>intelligent</b>: learns an opponent's habits faster and keeps more of them between fights;</li>
 *   <li><b>competitive</b>: seeks rematches, gains rivalry fast;</li>
 *   <li><b>loyal</b>: takes an ally's defeat personally, comes to an ally's aid;</li>
 *   <li><b>calm</b>: steadier, slower to hate;</li>
 *   <li><b>friendly</b>: quicker to respect and befriend;</li>
 *   <li><b>ambitious</b>: trains harder, chases reputation and new forms;</li>
 *   <li><b>curious</b>: wanders, asks, collects rumors; <b>shy</b>: keeps to itself; <b>greedy</b>: haggles, wants paying.</li>
 * </ul>
 */
public enum Trait {
    //                          aggr  caut  pati  pride honor cour  reck  disc
    VENGEFUL(new Temper.Shift(0.10, -0.05, 0.0, 0.10, -0.10, 0.10, 0.05, 0.0)),
    INTELLIGENT(new Temper.Shift(0.0, 0.05, 0.10, 0.0, 0.0, 0.0, -0.10, 0.20)),
    COMPETITIVE(new Temper.Shift(0.10, 0.0, -0.05, 0.05, 0.05, 0.10, 0.0, 0.0)),
    LOYAL(new Temper.Shift(0.0, 0.0, 0.0, 0.0, 0.20, 0.10, 0.0, 0.05)),
    CALM(new Temper.Shift(-0.05, 0.10, 0.20, -0.05, 0.05, 0.0, -0.20, 0.10)),
    FRIENDLY(new Temper.Shift(-0.10, 0.0, 0.05, -0.10, 0.15, 0.0, 0.0, 0.0)),
    AMBITIOUS(new Temper.Shift(0.05, 0.0, 0.0, 0.10, 0.0, 0.10, 0.05, 0.05)),
    // the living world's temperaments (docs/LIVING_WORLD.md): they colour conversations and daily life
    CURIOUS(new Temper.Shift(0.0, -0.05, -0.05, 0.0, 0.0, 0.05, 0.05, 0.0)),
    SHY(new Temper.Shift(-0.10, 0.10, 0.05, -0.05, 0.0, -0.10, -0.05, 0.0)),
    GREEDY(new Temper.Shift(0.05, 0.0, 0.0, 0.05, -0.15, 0.0, 0.0, 0.0));

    public final Temper.Shift shift;

    Trait(Temper.Shift shift) {
        this.shift = shift;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Trait byId(String id) {
        for (Trait t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return null;
    }
}
