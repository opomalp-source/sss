package com.dbzenith.nemesis;

import java.util.Locale;

/**
 * Where a character stands with someone (a player, or another character). Not a ladder: it is read from two numbers
 * that events push around, so a rival can become a friend and an ally an enemy.
 * <ul>
 *   <li><b>affinity</b> -100..100: contempt and hatred below zero, respect and friendship above;</li>
 *   <li><b>rivalry</b> 0..100: how much the two measure themselves against each other.</li>
 * </ul>
 * {@link #of} maps them (and a couple of facts: met at all, trained together) to a state; a new state must clear its
 * boundary by a margin before it replaces the old one ({@link #next}), so it doesn't flicker.
 */
public enum Relationship {
    STRANGER, NEUTRAL, RESPECTFUL, COMPETITIVE_RIVAL, BITTER_RIVAL, SWORN_ENEMY, FRIENDLY_RIVAL, ALLY, TRAINING_PARTNER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Relationship byId(String id) {
        for (Relationship r : values()) if (r.id().equalsIgnoreCase(id)) return r;
        return null;
    }

    /** Whether a character in this state fights the player on sight. */
    public boolean hostile() {
        return this == BITTER_RIVAL || this == SWORN_ENEMY || this == COMPETITIVE_RIVAL || this == NEUTRAL || this == STRANGER;
    }

    /** Whether it is on the player's side (never attacks them, may help them). */
    public boolean friendly() {
        return this == ALLY || this == TRAINING_PARTNER;
    }

    /** A rival of any kind. */
    public boolean rival() {
        return this == COMPETITIVE_RIVAL || this == BITTER_RIVAL || this == SWORN_ENEMY || this == FRIENDLY_RIVAL;
    }

    /** The state these numbers point to. */
    public static Relationship of(double affinity, double rivalry, int encounters, int spars) {
        if (encounters <= 0) return STRANGER;
        if (affinity >= 60) return spars >= 2 ? TRAINING_PARTNER : rivalry >= 40 ? FRIENDLY_RIVAL : ALLY;
        if (affinity >= 25) return rivalry >= 30 ? FRIENDLY_RIVAL : RESPECTFUL;
        if (affinity <= -60 && rivalry >= 45) return SWORN_ENEMY;
        if (affinity <= -20 && rivalry >= 25) return BITTER_RIVAL;
        if (rivalry >= 30) return COMPETITIVE_RIVAL;
        return NEUTRAL;
    }

    /** The next state, with hysteresis: the numbers must point past the boundary by {@code margin} to change. */
    public static Relationship next(Relationship current, double affinity, double rivalry, int encounters, int spars) {
        Relationship target = of(affinity, rivalry, encounters, spars);
        if (current == null || current == STRANGER || target == current) return target;
        double m = 6;
        Relationship lo = of(affinity - m, rivalry - m, encounters, spars), hi = of(affinity + m, rivalry + m, encounters, spars);
        Relationship mixed1 = of(affinity - m, rivalry + m, encounters, spars), mixed2 = of(affinity + m, rivalry - m, encounters, spars);
        // only change if the numbers sit clearly inside the new state: all four nudged readings agree on it
        return lo == target && hi == target && mixed1 == target && mixed2 == target ? target : current;
    }
}
