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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
            }
        } else if (!(data.isFlying() && player.getAbilities().flying) && !data.isTransformed()
                && !com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            // No passive ki regen while transformed: forms are sustained by charging and mastery.
            data.setKi(data.getKi() + perTick(s.maxKi(), c.kiRegenPercentPerSecond.get()) * race.kiRegenMultiplier() * com.dbzenith.race.RacialSkills.factor(data, com.dbzenith.race.RacialSkill.Stat.KI_REGEN)
                    * (data.isMeditating() ? c.meditationKiRegenMultiplier.get() : 1.0) * com.dbzenith.race.Alignment.kiRegenMultiplier(data));
        }

        com.dbzenith.combat.GuardRules.tick(data, now);
        if (now % 20 == 0) com.dbzenith.race.Milestones.tick(player, data);
        if (now % 20 == 0) com.dbzenith.transform.GodKi.tickSecond(player, data);
        com.dbzenith.race.RacialSkillEffects.tick(player, data, now);
        com.dbzenith.transform.Kaioken.tick(player, data, now);
        com.dbzenith.combat.CombatMoves.tick(player, data, now);
        data.tickRisingCharge();
        boolean fighting = now - data.getLastCombatTick() < 200;                      // ten seconds since the last blow
        int before = data.getCombatTicks();
        data.setCombatTicks(fighting ? before + 1 : 0);
        if (com.dbzenith.transform.Forms.byId(data.getFormId()).rising() > 0 && (fighting ? before % 20 == 0 : before > 0)) data.invalidateDerived();
        if (!data.isCharging() && !data.isGuarding()) {
            data.setStamina(data.getStamina() + perTick(s.maxStamina(), c.staminaRegenPercentPerSecond.get()) * race.staminaRegenMultiplier()
                    * com.dbzenith.world.Needs.staminaRegenMultiplier(data));
        }
        if (now - data.getLastDamagedTick() > c.bodyRegenDelayTicks.get() * race.regenDelayFactor()) {
            data.setBody(data.getBody() + perTick(s.maxBody(), c.bodyRegenPercentPerSecond.get()) * race.regenMultiplier()
                    * com.dbzenith.race.RacialSkills.factor(data, com.dbzenith.race.RacialSkill.Stat.BODY_REGEN));
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
        if (now % 20 == 0) com.dbzenith.world.Needs.tick(player, data);
        if (now % 20 == 0) com.dbzenith.world.Family.tick(player, data);
        if (now % 20 == 0) GreatApe.tick(player, data);
        FormHandler.tick(player, data, now);
        FlightHandler.tick(player, data);
        BodyHealth.mirror(player, data);
    }

    private static double perTick(double max, double percentPerSecond) {
        return max * percentPerSecond / 100.0 / 20.0;
    }
}
