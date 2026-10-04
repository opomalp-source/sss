package com.dbzenith.ki;

import com.dbzenith.combat.BodyHealth;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;

/**
 * Per-tick server update of a player's pools: charging, regeneration, flight drain, combo decay,
 * and the body/health mirror. Called from {@code PlayerDataEvents.tick}.
 */
public final class KiTicker {
    /** Default aura color until forms/races define their own (Phase 2/3). */
    private static final Vector3f AURA_COLOR = new Vector3f(0.85f, 0.95f, 1.0f);

    private KiTicker() {}

    public static void tick(ServerPlayer player, PlayerData data) {
        if (!player.isAlive()) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        data.recomputeIfStale();
        BodyHealth.adoptExternalChanges(player, data);
        DerivedStats s = data.getDerived();

        data.tickCombo(now, c.comboWindowTicks.get());

        if (data.isCharging()) {
            if (data.getStamina() <= 0) {
                data.setCharging(false);
            } else {
                int t = data.tickCharge();
                data.setKi(data.getKi() + perTick(s.maxKi(), c.chargeKiPercentPerSecond.get()));
                data.setStamina(data.getStamina() - perTick(s.maxStamina(), c.chargeStaminaPercentPerSecond.get()));
                if (t % c.chargeReleaseStepTicks.get() == 0) data.setReleasePercent(data.getReleasePercent() + 1);
                if (t % c.tpChargeTrainingInterval.get() == 0) {
                    data.addTrainingProgress(StatCalculator.scaleTpGain(data, c.tpPerChargeInterval.get()));
                }
                if (t % 3 == 0) aura(level, player);
                if (t % 20 == 1) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
                            SoundSource.PLAYERS, 0.8f, 1.6f);
                }
            }
        } else if (!(data.isFlying() && player.getAbilities().flying)) {
            data.setKi(data.getKi() + perTick(s.maxKi(), c.kiRegenPercentPerSecond.get()));
        }

        if (!data.isCharging() && !data.isGuarding()) {
            data.setStamina(data.getStamina() + perTick(s.maxStamina(), c.staminaRegenPercentPerSecond.get()));
        }
        if (now - data.getLastDamagedTick() > c.bodyRegenDelayTicks.get()) {
            data.setBody(data.getBody() + perTick(s.maxBody(), c.bodyRegenPercentPerSecond.get()));
        }

        FlightHandler.tick(player, data);
        BodyHealth.mirror(player, data);
    }

    private static double perTick(double max, double percentPerSecond) {
        return max * percentPerSecond / 100.0 / 20.0;
    }

    private static void aura(ServerLevel level, ServerPlayer player) {
        level.sendParticles(new DustParticleOptions(AURA_COLOR, 1.4f),
                player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.45, 0.9, 0.45, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.4, 0.1, 0.4, 0.05);
    }
}
