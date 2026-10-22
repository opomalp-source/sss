package com.dbzenith.nemesis;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/**
 * One thing a character remembers: what happened, when (game time), with whom (a player's or a character's id) and
 * a detail (a technique, a form, a place, a name). Dialogue quotes these; the history screen lists them.
 */
public record Memory(Kind kind, long tick, UUID who, String detail) {
    public enum Kind {
        MET, FOUGHT, WON, LOST, SPARED_BY, SPARED, PLAYER_ESCAPED, RETREATED, SURVIVED_DEATH, SAW_FORM, SAW_TECHNIQUE,
        HELPED_BY, ALLY_DEFEATED, TRAINED, UNLOCKED_FORM, LEARNED_TECHNIQUE, RANK_UP, RANK_DOWN, RELATION_CHANGED,
        NPC_WON, NPC_LOST, TOURNAMENT, BEFRIENDED, BETRAYED, SPARRED, TALKED, RETIRED, DIED, PROMOTED, INTERRUPTED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byId(String id) {
            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("k", kind.id());
        t.putLong("t", tick);
        if (who != null) t.putUUID("w", who);
        if (detail != null && !detail.isEmpty()) t.putString("d", detail);
        return t;
    }

    static Memory load(CompoundTag t) {
        Kind k = Kind.byId(t.getString("k"));
        return k == null ? null : new Memory(k, t.getLong("t"), t.hasUUID("w") ? t.getUUID("w") : null, t.getString("d"));
    }
}
