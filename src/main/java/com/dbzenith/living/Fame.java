package com.dbzenith.living;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A player's name in the world (docs/LIVING_WORLD.md): fame grows with what people hear of them (beating known
 * fighters, defending a town) and it shows in how townsfolk greet them, what they say and who wants to test them.
 * Crossing a tier is news (a rumor heard where it happened, then everywhere).
 */
public final class Fame {
    public enum Tier {
        UNKNOWN(0), HEARD_OF(30), KNOWN(100), FAMOUS(300), LEGEND(800);

        public final int min;

        Tier(int min) {
            this.min = min;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Tier of(int fame) {
            Tier t = UNKNOWN;
            for (Tier x : values()) if (fame >= x.min) t = x;
            return t;
        }
    }

    private Fame() {}

    /** Fame changed: a new tier is news, heard first in the nearest town. */
    static void check(MinecraftServer server, LivingData ld, UUID player, String name, long now) {
        int fame = ld.fame(player);
        Tier t = Tier.of(fame);
        if (t == Tier.UNKNOWN || ld.fameTier(player) >= t.ordinal()) return;
        ld.setFameTier(player, t.ordinal());
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        Settlement near = p == null ? null : ld.nearest(p.level().dimension().location().toString(), p.blockPosition(), 512);
        Rumors.add(ld, Rumor.Kind.PLAYER_FAMOUS, player, p == null ? name : p.getGameProfile().getName(), null, "", t.id(),
                near == null ? null : near.id, now, near == null ? List.of() : Residents.of(near));
    }

    /** Adds fame (and tells the world if it crossed a tier). */
    public static void add(MinecraftServer server, ServerPlayer p, int amount, long now) {
        LivingData ld = LivingData.get(server);
        ld.addFame(p.getUUID(), amount);
        check(server, ld, p.getUUID(), p.getGameProfile().getName(), now);
    }
}
