package com.dbzenith.combat;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.stats.Race;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Blood (the knocked-out rework), in each race's own colour, as in the show: red for Earthlings and Saiyans, purple for
 * Namekians and Frieza's race, pink goo for Majins, dark oil and sparks for androids. Spills go to clients as an
 * {@link ImpactPacket#BLOOD} impact (colour, amount, and the style in the flags); they draw falling drops and splats.
 */
public final class Blood {
    /** Styles, in an impact's flags: plain blood, thick goo (Majins), oil with sparks (machines), blood with sparks (cyborgs). */
    public static final int PLAIN = 0, GOO = 1, OIL = 2, SPARKS = 3;

    private Blood() {}

    /** A race's blood colour. */
    public static int color(Race race) {
        if (race == null) return 0x8E0B0B;
        return switch (race) {
            case NAMEKIAN -> 0x7A2BA8;                                          // Piccolo's violet
            case FROST_DEMON -> 0x8A2C9E;                                       // Frieza's purple
            case MAJIN -> 0xF06FB0;                                             // Buu's pink
            case ANDROID -> 0x26262C;                                           // oil
            case BIO_ANDROID -> 0x5E2A8E;                                       // Cell's purple
            case VAMPIRE -> 0x4E0410;                                           // near black
            case GEN_ALIEN -> 0x3E8E2A;                                         // green
            case CORE_PERSON -> 0xC07A14;                                       // amber
            default -> 0x8E0B0B;                                                // Earthlings, Saiyans, half-Saiyans, Tuffles, cyborgs
        };
    }

    public static int style(Race race) {
        if (race == null) return PLAIN;
        return switch (race) {
            case MAJIN -> GOO;
            case ANDROID -> OIL;
            case CYBORG -> SPARKS;
            default -> PLAIN;
        };
    }

    /** What runs in this fighter's veins: a player's race, or an NPC's by what it is. */
    public static Race race(LivingEntity e) {
        if (e instanceof Player p) return ModCapabilities.get(p).map(PlayerData::getRace).orElse(null);
        String id = net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath();
        if (id.contains("namek")) return Race.NAMEKIAN;
        if (id.contains("frieza") || id.contains("frost") || id.contains("cooler")) return Race.FROST_DEMON;
        if (id.contains("majin") || id.contains("buu")) return Race.MAJIN;
        if (id.contains("android")) return Race.ANDROID;
        if (id.contains("cell")) return Race.BIO_ANDROID;
        if (id.contains("saibaman")) return Race.GEN_ALIEN;
        return Race.HUMAN;
    }

    public static int color(LivingEntity e) {
        return color(race(e));
    }

    public static int style(LivingEntity e) {
        return style(race(e));
    }

    /** Spills {@code amount} drops (about) of this fighter's blood at {@code at}, flung a little outward. */
    public static void spill(LivingEntity e, Vec3 at, int amount) {
        if (!(e.level() instanceof ServerLevel sl) || amount <= 0) return;
        ImpactPacket.at(at, new Vec3(0, 1, 0), ImpactPacket.BLOOD, amount, color(e), e.getId()).withFeel(style(e) << 4, 0).send(sl);
    }

    /** A spurt in a direction (a fresh wound): drops thrown along {@code dir}. */
    public static void spurt(LivingEntity e, Vec3 at, Vec3 dir, int amount) {
        if (!(e.level() instanceof ServerLevel sl) || amount <= 0) return;
        ImpactPacket.at(at, dir, ImpactPacket.BLOOD, amount, color(e), e.getId()).withFeel(style(e) << 4 | 1, 0).send(sl);
    }
}
