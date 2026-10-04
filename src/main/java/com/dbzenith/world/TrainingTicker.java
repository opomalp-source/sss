package com.dbzenith.world;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.item.TrainingWeightsItem;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * Training environment, once per server tick per player: gravity strain and slowdown, the training multiplier
 * (gravity x weights x Time Chamber) used by every TP gain, TP for moving under gravity, and meditation.
 */
public final class TrainingTicker {
    private static final UUID GRAVITY_SLOW_ID = UUID.fromString("2c8e9b4a-5d1f-4e7a-9b36-1a0c7d4e8f55");

    private TrainingTicker() {}

    /** Gravity a character shrugs off: 1 + (STR + CON) x tolerancePerPoint. */
    public static double tolerance(PlayerData data) {
        int points = data.getAttribute(Attribute.STRENGTH) + data.getAttribute(Attribute.CONSTITUTION);
        return 1.0 + points * DBZConfig.SERVER.gravityTolerancePerPoint.get();
    }

    public static double gravityMultiplier(double g) {
        DBZConfig.Server c = DBZConfig.SERVER;
        return Math.min(c.gravityMaxTrainingMultiplier.get(), 1.0 + Math.max(0, g - 1) * c.gravityTrainingBonus.get());
    }

    public static void tick(ServerPlayer player, PlayerData data, long now) {
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean chamber = TimeChamber.isIn(player);
        if (chamber) data.applyGravity(c.chamberGravity.get(), now + 20);
        Planet planet = Planet.of(player.level());
        if (planet != null && planet.gravity() > 1) data.applyGravity(planet.gravity(), now + 20);
        data.expireGravity(now);
        double g = data.getGravity(now);
        double excess = Math.max(0, g - tolerance(data));

        setSlow(player, Math.min(0.8, excess * c.gravitySlowPerExcess.get()));
        if (excess > 0 && !player.getAbilities().invulnerable && !data.isOnCooldown("gravity_strain", now)) {
            data.setCooldown("gravity_strain", now + 20);
            // Once per second: each health drop flashes the player red on clients, so per-tick strain would flicker constantly.
            double perSecond = Math.min(5.0, excess * c.gravityBodyDamagePercentPerExcess.get());
            double loss = data.getDerived().maxBody() * perSecond / 100.0;
            data.setBody(Math.max(1, data.getBody() - loss)); // gravity strains, it never kills
            data.setLastDamagedTick(now);
        }

        double weights = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TrainingWeightsItem w ? w.trainingFactor() : 1.0;
        data.setTrainingMultiplier(gravityMultiplier(g) * weights * (chamber ? c.chamberTrainingMultiplier.get() : 1.0));

        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0025 || player.getAbilities().flying;
        if (g > 1 && moving && now % 20 == 0) {
            data.addTrainingProgress(StatCalculator.scaleTpGain(data, c.tpPerSecondMovingUnderGravity.get() * g));
        }

        boolean still = player.isShiftKeyDown() && player.onGround() && !data.isCharging()
                && player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4;
        int t = data.tickMeditation(still);
        data.setMeditating(t >= c.meditationStartTicks.get());
        if (data.isMeditating()) {
            if (now % 20 == 0) data.addTrainingProgress(StatCalculator.scaleTpGain(data, c.tpPerMeditationSecond.get()));
            if (now % 10 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.5, 0.6, 0.5, 0.4);
            }
        }
    }

    private static void setSlow(ServerPlayer player, double slow) {
        AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(GRAVITY_SLOW_ID);
        double amount = -slow;
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-4) return;
        if (current != null) inst.removeModifier(GRAVITY_SLOW_ID);
        if (slow > 0) inst.addTransientModifier(new AttributeModifier(GRAVITY_SLOW_ID, "dbzenith gravity", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
