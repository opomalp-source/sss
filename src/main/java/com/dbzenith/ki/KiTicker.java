package com.dbzenith.ki;

import com.dbzenith.combat.BodyHealth;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.RacePassives;
import com.dbzenith.world.TimeChamber;
import com.dbzenith.world.TrainingTicker;
import com.dbzenith.race.RaceTraits;
import com.dbzenith.race.Races;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.SpeedModifiers;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.GreatApe;
import com.dbzenith.transform.Overdrive;
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
    private KiTicker() {}

    public static void tick(ServerPlayer player, PlayerData data) {
        if (!player.isAlive()) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        data.recomputeIfStale();
        BodyHealth.adoptExternalChanges(player, data);
        DerivedStats s = data.getDerived();
        RaceTraits race = Races.of(data.getRace());

        data.tickCombo(now, c.comboWindowTicks.get());
        SpeedModifiers.apply(player, s);
        if (data.getHeavyArmedMultiplier() > 0 && !data.isHeavyArmed(now)) data.consumeHeavy(now); // expired
        int heavy = data.tickHeavyCharge();
        if (heavy > 0 && heavy % 4 == 0) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.1, player.getZ(), 3, 0.3, 0.2, 0.3, 0.1);
        }

        if (data.isCharging()) {
            if (data.getStamina() <= 0 || com.dbzenith.registry.ModEffects.isKiSealed(player)) {
                data.setCharging(false);
            } else {
                int t = data.tickCharge();
                data.setKi(data.getKi() + perTick(s.maxKi(), c.chargeKiPercentPerSecond.get()));
                data.setStamina(data.getStamina() - perTick(s.maxStamina(), c.chargeStaminaPercentPerSecond.get()));
                if (t % c.chargeReleaseStepTicks.get() == 0) data.setReleasePercent(data.getReleasePercent() + 1);
                if (t % c.tpChargeTrainingInterval.get() == 0) {
                    data.addTrainingProgress(StatCalculator.scaleTpGain(data, c.tpPerChargeInterval.get()));
                }
                if (t % 3 == 0) aura(level, player, data);
                if (t % 20 == 1) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
                            SoundSource.PLAYERS, 0.8f, 1.6f);
                }
            }
        } else if (!(data.isFlying() && player.getAbilities().flying) && !data.isTransformed()
                && !com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            // No passive ki regen while transformed: forms are sustained by charging and mastery.
            data.setKi(data.getKi() + perTick(s.maxKi(), c.kiRegenPercentPerSecond.get()) * race.kiRegenMultiplier()
                    * (data.isMeditating() ? c.meditationKiRegenMultiplier.get() : 1.0) * com.dbzenith.race.Alignment.kiRegenMultiplier(data));
        }

        if (!data.isCharging() && !data.isGuarding()) {
            data.setStamina(data.getStamina() + perTick(s.maxStamina(), c.staminaRegenPercentPerSecond.get()) * race.staminaRegenMultiplier());
        }
        if (now - data.getLastDamagedTick() > c.bodyRegenDelayTicks.get() * race.regenDelayFactor()) {
            data.setBody(data.getBody() + perTick(s.maxBody(), c.bodyRegenPercentPerSecond.get()) * race.regenMultiplier());
        }

        com.dbzenith.item.GiArmorItem.updateBonus(player, data);
        if (now % 20 == 0) com.dbzenith.item.ScouterItem.checkOverload(player);
        TrainingTicker.tick(player, data, now);
        if (now % 20 == 0) TimeChamber.tick(player, data, now);
        if (now % 20 == 0) com.dbzenith.quest.QuestManager.tick(player, data);
        if (now % 20 == 0) com.dbzenith.world.LifeSim.tick(player, data);
        data.tickMajin(now);
        RacePassives.tick(player, data, now);
        if (now % 20 == 0) com.dbzenith.race.TailRules.tick(player, data);
        if (now % 20 == 0) GreatApe.tick(player, data);
        FormHandler.tick(player, data, now);
        Overdrive.tick(player, data, now);
        FlightHandler.tick(player, data);
        BodyHealth.mirror(player, data);
    }

    private static double perTick(double max, double percentPerSecond) {
        return max * percentPerSecond / 100.0 / 20.0;
    }

    private static void aura(ServerLevel level, ServerPlayer player, PlayerData data) {
        int c = Aura.color(data);
        Vector3f rgb = new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
        level.sendParticles(new DustParticleOptions(rgb, 1.4f),
                player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.45, 0.9, 0.45, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.4, 0.1, 0.4, 0.05);
    }
}
