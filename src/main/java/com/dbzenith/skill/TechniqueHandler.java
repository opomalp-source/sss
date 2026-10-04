package com.dbzenith.skill;

import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.transform.Forms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Server-authoritative technique use. Clients only ask; everything is validated here. */
public final class TechniqueHandler {
    private static final double HOMING_RANGE = 32.0;
    private static final double HOMING_CONE_COS = Math.cos(Math.toRadians(35));

    public enum Result { FIRED, COOLDOWN, NOT_ENOUGH_KI, NOT_EQUIPPED, INVALID, STUNNED, SEALED }

    private TechniqueHandler() {}

    public static Result use(ServerPlayer player, Technique technique) {
        return use(player, technique, false);
    }

    /** {@code bypassDeck}: admin/testing path (/dbz technique) that skips the learned + deck check. */
    public static Result use(ServerPlayer player, Technique technique, boolean bypassDeck) {
        if (technique == null || !player.isAlive() || player.isSpectator()) return Result.INVALID;
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !Forms.byId(data.getFormId()).allowsTechniques()) return Result.INVALID;
        if (com.dbzenith.registry.ModEffects.isStunned(player)) return Result.STUNNED;
        if (com.dbzenith.registry.ModEffects.isKiSealed(player)) return Result.SEALED;
        if (!bypassDeck && !(data.knows(technique.id()) && data.deckView().contains(technique.id()))) return Result.NOT_EQUIPPED;
        data.recomputeIfStale();

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (data.isOnCooldown(technique.id(), now)) return Result.COOLDOWN;
        double cost = DamageCalculator.kiCost(data, technique.kiCost());
        if (!player.getAbilities().instabuild && data.getKi() < cost) return Result.NOT_ENOUGH_KI;

        if (technique.style() == Technique.Style.SELF) {
            if (!TechniqueEffects.apply(player, data, technique)) return Result.INVALID;
        } else {
            spawn(level, player, technique, DamageCalculator.kiOutgoing(data, technique.damageMult()));
        }

        if (!player.getAbilities().instabuild) data.setKi(data.getKi() - cost);
        data.setCooldown(technique.id(), now + technique.cooldownTicks());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                technique.explosionPower() > 0 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.6f, 1.4f + level.random.nextFloat() * 0.3f);
        return Result.FIRED;
    }

    /**
     * Spawns a technique's projectiles or beam for any caster, with no cost or cooldown checks.
     * Players go through {@link #use}; NPCs (Phase 4) and {@code /dbz cast} call this directly.
     */
    public static void spawn(ServerLevel level, LivingEntity caster, Technique technique, double damage) {
        if (technique.style() == Technique.Style.SELF) return; // self effects need a player caster (TechniqueEffects)
        Vec3 look = caster.getLookAngle();
        if (technique.style() == Technique.Style.BEAM) {
            level.addFreshEntity(KiBeamEntity.create(level, caster, technique, damage));
            return;
        }
        LivingEntity homingTarget = technique.homing() ? findTarget(caster, look) : null;
        for (int i = 0; i < technique.count(); i++) {
            Vec3 dir = technique.spreadDegrees() > 0 ? spread(look, technique.spreadDegrees(), caster) : look;
            KiBlastEntity blast = KiBlastEntity.create(level, caster, technique, damage);
            Vec3 start = caster.getEyePosition().add(look.scale(0.6)).subtract(0, technique.size() / 2.0, 0);
            blast.moveTo(start.x, start.y, start.z, caster.getYRot(), caster.getXRot());
            blast.setDeltaMovement(dir.normalize().scale(technique.speed()));
            blast.setHomingTarget(homingTarget);
            level.addFreshEntity(blast);
        }
    }

    private static Vec3 spread(Vec3 dir, float degrees, LivingEntity caster) {
        float yaw = (caster.getRandom().nextFloat() - 0.5f) * 2 * degrees * Mth.DEG_TO_RAD;
        float pitch = (caster.getRandom().nextFloat() - 0.5f) * 2 * degrees * Mth.DEG_TO_RAD;
        return dir.yRot(yaw).xRot(pitch);
    }

    private static LivingEntity findTarget(LivingEntity caster, Vec3 look) {
        Vec3 eye = caster.getEyePosition();
        AABB box = caster.getBoundingBox().inflate(HOMING_RANGE);
        return caster.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && !e.isSpectator()
                        && e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > HOMING_CONE_COS
                        && caster.hasLineOfSight(e))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(caster)))
                .orElse(null);
    }
}
