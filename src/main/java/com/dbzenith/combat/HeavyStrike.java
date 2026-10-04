package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Charged heavy hit: hold to charge, release to arm; the next melee hit within the window is multiplied. */
public final class HeavyStrike {
    private HeavyStrike() {}

    /** Called on key release. Returns the armed multiplier, or 0 if nothing was armed. */
    public static double release(ServerPlayer player, PlayerData data) {
        int held = data.releaseHeavyCharge();
        if (held < 0) return 0;
        DBZConfig.Server c = DBZConfig.SERVER;
        double frac = Math.min(1.0, held / (double) c.heavyMaxChargeTicks.get());
        double cost = c.heavyStaminaCost.get() * Math.max(0.2, frac);
        if (data.getStamina() < cost) return 0;
        data.setStamina(data.getStamina() - cost);
        double mult = c.heavyMinMultiplier.get() + (c.heavyMaxMultiplier.get() - c.heavyMinMultiplier.get()) * frac;
        long now = player.level().getGameTime();
        data.armHeavy(mult, now + c.heavyArmedTicks.get());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.3f, 1.6f + (float) frac * 0.4f);
        return mult;
    }
}
