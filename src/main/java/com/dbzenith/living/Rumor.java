package com.dbzenith.living;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/**
 * Something people talk about, made from a real event (a character's memory, an attack on a town): who it is about
 * ({@code subject}, a character or player id, and the name it goes by), who else ({@code object}), a detail (a form, a
 * place), where it happened (the nearest settlement) and when. Residents who know it carry its id; it spreads by
 * conversation and by travelers.
 */
public record Rumor(UUID id, Kind kind, UUID subject, String subjectName, UUID object, String objectName, String detail, UUID origin, long tick) {
    public enum Kind {
        BEAT_PLAYER, LOST_TO_PLAYER, HUMILIATED, SURVIVED_DEATH, NEW_FORM, TOURNAMENT_WIN, DUEL, RETREATED, TOWN_ATTACKED,
        PLAYER_FAMOUS, STRONG_FIGHTER, TRAINING_HARD,
        /** Town life: someone moved in; someone died. */
        NEWCOMER, MOURNING,
        /** A named fighter died for good. */
        FELL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.id());
        if (subject != null) t.putUUID("subject", subject);
        t.putString("sname", subjectName);
        if (object != null) t.putUUID("object", object);
        t.putString("oname", objectName);
        t.putString("detail", detail);
        if (origin != null) t.putUUID("origin", origin);
        t.putLong("tick", tick);
        return t;
    }

    static Rumor load(CompoundTag t) {
        Kind k;
        try {
            k = Kind.valueOf(t.getString("kind").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Rumor(t.getUUID("id"), k, t.hasUUID("subject") ? t.getUUID("subject") : null, t.getString("sname"),
                t.hasUUID("object") ? t.getUUID("object") : null, t.getString("oname"), t.getString("detail"),
                t.hasUUID("origin") ? t.getUUID("origin") : null, t.getLong("tick"));
    }
}
