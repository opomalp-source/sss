package com.dbzenith.combat.engine;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The special meter (CX-19 phase 4): bars of 100 (three by default), filled by landing and taking blows, perfect guards
 * and vanishes; spent on supers (a bar) and ultimates (all of it). Only players have one.
 */
public final class SpecialMeter {
    public static final double BAR = 100;

    private SpecialMeter() {}

    public static boolean enabled() {
        try {
            return DBZConfig.SERVER.specialMeter.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static double max() {
        try {
            return BAR * DBZConfig.SERVER.specialBars.get();
        } catch (IllegalStateException e) {
            return BAR * 3;
        }
    }

    public static void gain(LivingEntity e, double amount) {
        if (amount <= 0 || !(e instanceof Player p)) return;
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d != null) d.setSpecial(d.getSpecial() + amount);
    }

    /** Spends {@code amount} if there is that much (creative players always can). */
    public static boolean spend(Player p, PlayerData d, double amount) {
        if (amount <= 0 || !enabled() || p.getAbilities().instabuild) return true;
        if (d.getSpecial() + 1e-6 < amount) return false;
        d.setSpecial(d.getSpecial() - amount);
        return true;
    }
}
