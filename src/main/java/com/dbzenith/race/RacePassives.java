package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Per-tick racial passives and the signature mechanics that aren't simple multipliers:
 * Saiyan Zenkai, Android/Cyborg ki absorption, hunger and breathing rules.
 * Regen multipliers are applied in {@code KiTicker}; TP weights in {@code StatCalculator}.
 */
public final class RacePassives {
    private static final Attribute[] ZENKAI_ATTRIBUTES = {Attribute.STRENGTH, Attribute.DEXTERITY, Attribute.CONSTITUTION, Attribute.KI_POWER};

    private RacePassives() {}

    public static RaceTraits traits(PlayerData data) {
        return Races.of(data.getRace());
    }

    public static void tick(ServerPlayer player, PlayerData data, long now) {
        RaceTraits t = traits(data);
        if (t.noHunger() && now % 20 == 0) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5f);
        }
        if (t.slowHunger() && now % 400 == 0 && player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
        }
        if (t.breathless() && player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (t.staminaless()) {
            data.setStamina(data.getDerived().maxStamina());
        }
        if (t.zenkaiPercent() > 0) zenkai(player, data, t, now);
    }

    /**
     * Zenkai: dropping below {@code zenkaiTriggerPercent} body arms it; recovering to {@code zenkaiRecoverPercent}
     * (by regen, a Senzu Bean, anything) permanently raises the combat attributes, once per cooldown.
     */
    static void zenkai(ServerPlayer player, PlayerData data, RaceTraits t, long now) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double max = data.getDerived().maxBody();
        if (max <= 0) return;
        double percent = 100.0 * data.getBody() / max;
        if (percent < c.zenkaiTriggerPercent.get()) {
            data.setZenkaiArmed(true);
        } else if (data.isZenkaiArmed() && percent >= c.zenkaiRecoverPercent.get()) {
            if (now - data.getLastZenkai() < c.zenkaiCooldownTicks.get()) {
                data.setZenkaiArmed(false);
                return;
            }
            int cap = c.attributeHardCap.get();
            for (Attribute a : ZENKAI_ATTRIBUTES) {
                int v = data.getAttribute(a);
                data.setAttribute(a, Math.min(cap, v + Math.max(1, (int) Math.round(v * t.zenkaiPercent()))));
            }
            data.recordZenkai(now);
            data.recomputeIfStale();
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 1, 0.5, 0.3);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.7f);
            player.displayClientMessage(Component.translatable("message.dbzenith.zenkai"), true);
        }
    }

    /**
     * A ki attack hits a player. Returns the fraction of the damage that still gets through, and banks the rest
     * as ki for absorbing races (all of it during an Energy Absorb window).
     */
    public static double absorbKiHit(PlayerData victim, double rawDamage, long now) {
        double fraction = now <= victim.getAbsorbUntil() ? 1.0 : traits(victim).kiAbsorb();
        if (fraction <= 0) return 1.0;
        victim.setKi(victim.getKi() + rawDamage * fraction);
        return 1.0 - fraction;
    }
}
