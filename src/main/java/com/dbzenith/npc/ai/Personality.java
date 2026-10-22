package com.dbzenith.npc.ai;

import java.util.Locale;

/**
 * How an NPC fighter is inclined to fight: the one personality system for NPCs (a future Nemesis feature reads it from
 * here; see docs/COMBAT_AI.md). Each is a set of traits from 0 to 1 that the utility scores weigh:
 * <ul>
 *   <li><b>aggression</b>: closes in and keeps up the pressure;</li>
 *   <li><b>caution</b>: guards, keeps distance, transforms early when outmatched;</li>
 *   <li><b>patience</b>: waits for openings, strafes, charges ki when it can;</li>
 *   <li><b>pride</b>: holds back its transformations until it is hurt (arrogance);</li>
 *   <li><b>honor</b>: leaves the downed alone, protects allies, does not gang up from behind;</li>
 *   <li><b>courage</b>: how long before it retreats;</li>
 *   <li><b>recklessness</b>: ignores danger, spends everything;</li>
 *   <li><b>discipline</b>: manages ki and stamina, powers down when a form is not needed.</li>
 * </ul>
 */
public enum Personality {
    //           aggr  caut  pati  pride honor cour  reck  disc
    AGGRESSIVE(0.85, 0.30, 0.30, 0.50, 0.40, 0.70, 0.50, 0.40),
    DEFENSIVE(0.30, 0.80, 0.70, 0.30, 0.60, 0.60, 0.15, 0.70),
    TACTICAL(0.50, 0.60, 0.70, 0.30, 0.50, 0.60, 0.20, 0.85),
    ARROGANT(0.70, 0.20, 0.40, 0.95, 0.30, 0.80, 0.50, 0.40),
    HONORABLE(0.55, 0.50, 0.60, 0.50, 0.95, 0.80, 0.25, 0.70),
    COWARDLY(0.35, 0.90, 0.40, 0.20, 0.20, 0.15, 0.20, 0.50),
    BALANCED(0.50, 0.50, 0.50, 0.50, 0.50, 0.60, 0.30, 0.60),
    RECKLESS(0.95, 0.10, 0.10, 0.60, 0.40, 0.90, 0.95, 0.15),
    PATIENT(0.35, 0.60, 0.95, 0.40, 0.60, 0.70, 0.10, 0.85);

    public final double aggression, caution, patience, pride, honor, courage, recklessness, discipline;

    Personality(double aggression, double caution, double patience, double pride, double honor, double courage, double recklessness, double discipline) {
        this.aggression = aggression;
        this.caution = caution;
        this.patience = patience;
        this.pride = pride;
        this.honor = honor;
        this.courage = courage;
        this.recklessness = recklessness;
        this.discipline = discipline;
    }

    /** The melee style this temperament fights with. */
    public FightStyle style() {
        return switch (this) {
            case AGGRESSIVE, RECKLESS, ARROGANT -> FightStyle.AGGRESSIVE;
            case DEFENSIVE, COWARDLY, PATIENT -> FightStyle.DEFENSIVE;
            case TACTICAL -> FightStyle.TACTICAL;
            default -> FightStyle.BALANCED;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Personality byId(String id) {
        for (Personality p : values()) if (p.id().equalsIgnoreCase(id)) return p;
        return null;
    }
}
