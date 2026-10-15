package com.dbzenith.combat;

import com.dbzenith.appearance.RaceCustom;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Race;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The colour of a fighter's skin, as it shows (the limbs rework): what stumps, cut-off limbs, scars and bruises are
 * coloured from, so a Namekian's stump is green and a Frieza-race arm white, never a human tone on everyone.
 * <ul>
 *   <li>a player with their race's look on: the race colour they chose, else their race's own;</li>
 *   <li>a player with a generated body: its tone;</li>
 *   <li>otherwise (their own Minecraft skin, or an NPC): their race's usual colour.</li>
 * </ul>
 */
public final class SkinColor {
    public static final int HUMAN = 0xD9A87A;

    private SkinColor() {}

    /** A race's usual skin colour (the first of its palette). */
    public static int of(Race race, Variant variant) {
        if (race == null) return HUMAN;
        int[] palette = RaceCustom.of(race, variant).skin();                    // (a null variant is fine: only Core People look at it)
        if (palette.length > 0) return palette[0];
        return switch (race) {
            case ANDROID, CYBORG -> 0xE4C2A0;
            default -> HUMAN;
        };
    }

    /** On the server: from the player's data, or the NPC's kind. */
    public static int of(LivingEntity e) {
        if (e instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d == null) return HUMAN;
            if (d.isRaceLook()) return d.getRaceSkinColor() >= 0 ? d.getRaceSkinColor() : of(d.getRace(), d.getVariant());
            if (d.getSkinTone() >= 0) return d.getSkinTone();
            return of(d.getRace(), d.getVariant());
        }
        return npc(e);
    }

    /** An NPC's skin, by what it is. */
    public static int npc(LivingEntity e) {
        String id = net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath();
        if (id.contains("soldier")) return 0x5E9A78;                          // Frieza's soldiers: green-skinned aliens
        if (id.contains("saibaman")) return 0x6A9A3A;
        return of(Blood.race(e), null);
    }
}
