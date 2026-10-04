package com.dbzenith.transform;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Overdrive: a stackable kaio-style multiplier on STR/DEX/KI_POWER. Drains body and stamina while active
 * (less with mastery) and costs body on the way out (backlash). Only usable in forms that allow it.
 */
public final class Overdrive {
    public static final int RED = 0xFF3A2A;

    private Overdrive() {}

    /** Raises the level by one. */
    public static boolean raise(ServerPlayer player) {
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !player.isAlive()) return false;
        if (com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.ki_sealed"), true);
            return false;
        }
        Form form = Forms.byId(data.getFormId());
        if (!form.allowsOverdrive()) {
            player.displayClientMessage(Component.translatable("message.dbzenith.overdrive_form"), true);
            return false;
        }
        int max = FormMath.maxOverdriveLevel(data);
        if (max <= 0) {
            player.displayClientMessage(Component.translatable("message.dbzenith.overdrive_locked",
                    DBZConfig.SERVER.overdriveUnlockLevel.get()), true);
            return false;
        }
        if (data.getOverdriveLevel() >= max) {
            player.displayClientMessage(Component.translatable("message.dbzenith.overdrive_max"), true);
            return false;
        }
        data.setOverdriveLevel(data.getOverdriveLevel() + 1);
        data.recomputeIfStale();
        FormHandler.burst(player.serverLevel(), player, RED, 25);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
        player.displayClientMessage(Component.translatable("message.dbzenith.overdrive",
                String.format("%.0f", FormMath.overdriveMultiplier(data))), true);
        return true;
    }

    /** Ends overdrive; with {@code backlash} the body pays for the multiplier used. */
    public static void stop(ServerPlayer player, PlayerData data, boolean backlash) {
        if (data.getOverdriveLevel() <= 0) return;
        double mult = FormMath.overdriveMultiplier(data);
        data.setOverdriveLevel(0);
        data.recomputeIfStale();
        if (backlash) {
            double loss = data.getDerived().maxBody() * DBZConfig.SERVER.overdriveBacklashPercent.get() * (mult - 1) / 100.0;
            data.setBody(Math.max(1, data.getBody() - loss)); // backlash never kills
            data.setLastDamagedTick(player.level().getGameTime());
        }
    }

    public static void tick(ServerPlayer player, PlayerData data, long gameTime) {
        if (data.getOverdriveLevel() <= 0) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        double over = FormMath.overdriveMultiplier(data) - 1.0;
        double masteryFactor = 1.0 - 0.6 * data.getMastery(FormMath.OVERDRIVE_MASTERY) / 100.0;
        double bodyDrain = data.getDerived().maxBody() * c.overdriveBodyDrainPercent.get() * over / 100.0 / 20.0 * masteryFactor;
        double staDrain = data.getDerived().maxStamina() * c.overdriveStaminaDrainPercent.get() * over / 100.0 / 20.0 * masteryFactor;
        data.setBody(data.getBody() - bodyDrain);
        data.setStamina(data.getStamina() - staDrain);
        data.setLastDamagedTick(gameTime); // no body regen while straining
        if (gameTime % 20 == 0) {
            double gain = c.masteryGainPerSecond.get() * data.getDerived().spiritModifier() * Math.max(1, data.getOverdriveLevel());
            data.setMastery(FormMath.OVERDRIVE_MASTERY, data.getMastery(FormMath.OVERDRIVE_MASTERY) + gain);
        }
        double bodyPercent = 100.0 * data.getBody() / Math.max(1, data.getDerived().maxBody());
        if (bodyPercent < c.overdriveMinBodyPercent.get() || data.getStamina() <= 0) {
            stop(player, data, true);
            player.displayClientMessage(Component.translatable("message.dbzenith.overdrive_ended"), true);
        }
    }

    /** Off-key handler. */
    public static void off(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(data -> stop(player, data, true));
    }
}
