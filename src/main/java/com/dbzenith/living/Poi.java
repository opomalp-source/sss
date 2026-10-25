package com.dbzenith.living;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * A point of interest in a settlement: a place a resident can actually walk to and stand at (they are set where the
 * settlement's buildings put a floor, and dropped if residents keep failing to reach them).
 */
public record Poi(Kind kind, BlockPos pos, String label) {
    public enum Kind {
        /** Where residents sleep and spend evenings (each home houses two). */
        HOME,
        /** A shop counter (merchants stand behind it); {@link #MARKET} spots are where customers browse. */
        SHOP, MARKET,
        /** A restaurant's counter and tables. */
        TAVERN,
        /** A training floor. */
        DOJO,
        /** A public gathering spot. */
        PLAZA,
        /** A point on the guards' round. */
        PATROL,
        /** Where roads leave town (travelers come and go here). */
        GATE,
        /** A laboratory bench (scientists). */
        LAB;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("k", kind.id());
        t.putLong("p", pos.asLong());
        t.putString("l", label);
        return t;
    }

    static Poi load(CompoundTag t) {
        Kind k;
        try {
            k = Kind.valueOf(t.getString("k").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Poi(k, BlockPos.of(t.getLong("p")), t.getString("l"));
    }
}
