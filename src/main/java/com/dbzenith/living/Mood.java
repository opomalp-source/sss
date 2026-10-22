package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * How a resident feels right now: passing, unlike its personality. Events set it for a while (a fight nearby worries
 * it, a blow angers it, a good conversation pleases it, a famous visitor excites it, a long day tires it); then it
 * settles back to its temperament's resting mood. It colours what it says (conversations test it) and what it does (an
 * angry or worried resident won't chat; a tired one goes home early).
 */
public enum Mood {
    HAPPY, RELAXED, EXCITED, WORRIED, ANNOYED, ANGRY, TIRED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "living.dbzenith.mood." + id();
    }

    public static Mood byId(String id) {
        for (Mood m : values()) if (m.id().equalsIgnoreCase(id)) return m;
        return null;
    }

    /** Willing to stop and talk. */
    public boolean sociable() {
        return this != ANGRY && this != WORRIED;
    }

    /** Its mood now (its resting one when nothing has stirred it, tired late at night). */
    public static Mood of(NamedFighter c, Level level) {
        Mood m = byId(c.mood);
        if (m != null && level.getGameTime() < c.moodUntil) return m;
        if (Schedules.hour(level) >= 22 || Schedules.hour(level) < 5) return TIRED;
        var t = c.temper();
        return t.aggression > 0.7 ? ANNOYED : t.caution > 0.7 ? RELAXED : HAPPY;
    }

    /** Sets a mood for {@code ticks}. */
    public static void set(NamedFighter c, Mood m, long now, long ticks) {
        c.mood = m.id();
        c.moodUntil = now + ticks;
    }
}
