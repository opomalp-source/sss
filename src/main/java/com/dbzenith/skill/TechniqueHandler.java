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

    public enum Result { FIRED, COOLDOWN, NOT_ENOUGH_KI, NOT_EQUIPPED, INVALID, STUNNED, SEALED, NO_METER }

    private TechniqueHandler() {}

    public static Result use(ServerPlayer player, Technique technique) {
        return use(player, technique, false, 0);
    }

    /** {@code bypassDeck}: admin/testing path (/dbz technique) that skips the learned + deck check. */
    public static Result use(ServerPlayer player, Technique technique, boolean bypassDeck) {
        return use(player, technique, bypassDeck, 0);
    }

    /**
     * Whether the technique could be used now (FIRED if so), with no cost paid and nothing fired: before a charge starts
     * (CX-23). The Spirit Bomb's second press (the throw) counts as usable.
     */
    public static Result check(ServerPlayer player, Technique technique, boolean bypassDeck) {
        if (technique == null || !player.isAlive() || player.isSpectator()) return Result.INVALID;
        PlayerData data = ModCapabilities.get(player).orElse(null);
        if (data == null || !Forms.byId(data.getFormId()).allowsTechniques()) return Result.INVALID;
        if (com.dbzenith.registry.ModEffects.isStunned(player)) return Result.STUNNED;
        if (com.dbzenith.registry.ModEffects.isKiSealed(player)) return Result.SEALED;
        if (BeamStruggle.isStruggling(player)) return Result.INVALID;     // both hands are busy
        if (!com.dbzenith.duel.Duels.kiAllowed(player)) return Result.INVALID;   // a melee-only duel (CX-19 phase 9)
        if (!bypassDeck && technique.style() != Technique.Style.SELF && !com.dbzenith.combat.PvpRules.combatOn(player)) return Result.INVALID;   // PvP off: no attacks (CX-20)
        if (!bypassDeck && !(data.knows(technique.id()) && data.deckView().contains(technique.id()))) return Result.NOT_EQUIPPED;
        data.recomputeIfStale();
        long now = player.serverLevel().getGameTime();
        if (data.isOnCooldown(technique.id(), now)) return Result.COOLDOWN;
        double cost = DamageCalculator.kiCost(data, technique.kiCost()) * TechniqueMastery.costMultiplier(data, technique);
        if (!player.getAbilities().instabuild && data.getKi() < cost) return Result.NOT_ENOUGH_KI;
        double meter = bypassDeck ? 0 : com.dbzenith.combat.engine.KiCombat.tier(technique.id()).meter();
        if (meter > 0 && com.dbzenith.combat.engine.SpecialMeter.enabled() && !player.getAbilities().instabuild
                && data.getSpecial() + 1e-6 < meter) return Result.NO_METER;
        return Result.FIRED;
    }

    /** The ki a technique costs this player when it goes off (before any charging). */
    public static double baseCost(PlayerData data, Technique technique) {
        return DamageCalculator.kiCost(data, technique.kiCost()) * TechniqueMastery.costMultiplier(data, technique);
    }

    /** The damage multiplier of a charge (0..1 of the technique's longest): up to its {@code chargePower}. */
    public static double chargeDamage(Technique technique, double charge) {
        double f = Math.max(0, Math.min(1, charge));
        return 1 + (technique.chargePower() - 1) * Math.pow(f, 0.85);
    }

    /** How much bigger a charge makes it: beams up to 2.2x as wide, blasts twice the size, volleys a little. */
    public static float chargeScale(Technique technique, double charge) {
        double f = Math.max(0, Math.min(1, charge));
        double grow = technique.style() == Technique.Style.BEAM ? 1.2 : technique.count() > 1 || technique.has(Technique.RAIN) ? 0.4 : 1.0;
        return (float) (1 + grow * f);
    }

    /** Uses a technique charged to {@code charge} (0..1 of its longest charge; 0 for a tap): stronger and bigger (CX-23). */
    public static Result use(ServerPlayer player, Technique technique, boolean bypassDeck, double charge) {
        Result ok = check(player, technique, bypassDeck);
        if (technique != null && technique.effect() == Technique.Effect.SPIRIT_BOMB && ok != Result.INVALID && ok != Result.NOT_EQUIPPED && ok != Result.STUNNED && ok != Result.SEALED
                && KiBlastEntity.releaseSpiritBomb(player)) {                  // cast again: thrown
            com.dbzenith.network.ModNetwork.sendToTrackingAndSelf(player,
                    new com.dbzenith.network.AnimEventPacket(player.getId(), com.dbzenith.network.AnimEventPacket.THROW, 2));
            return Result.FIRED;
        }
        if (ok != Result.FIRED) return ok;
        com.dbzenith.combat.PvpRules.actor(player);                         // effects it causes on players are judged (CX-19)
        PlayerData data = ModCapabilities.get(player).orElse(null);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        double cost = baseCost(data, technique);
        com.dbzenith.combat.engine.KiCombat.Tier tier = com.dbzenith.combat.engine.KiCombat.tier(technique.id());   // supers and ultimates: the special meter (CX-19)
        double meter = bypassDeck ? 0 : tier.meter();                       // the admin path pays no meter

        if (technique.style() == Technique.Style.SELF) {
            if (!TechniqueEffects.apply(player, data, technique)) return Result.INVALID;
        } else {
            spawn(level, player, technique, chargeDamage(technique, charge) * DamageCalculator.kiOutgoing(data, technique.damageMult()) * TechniqueMastery.damageMultiplier(data, technique)
                    * com.dbzenith.race.RacialSkillEffects.risingChargeBonus(data), chargeScale(technique, charge));
        }

        if (!player.getAbilities().instabuild) data.setKi(data.getKi() - cost);
        data.setCooldown(technique.id(), now + TechniqueMastery.cooldownTicks(data, technique));
        TechniqueMastery.gain(data, technique);
        com.dbzenith.combat.engine.SpecialMeter.spend(player, data, meter);
        if (tier.cinematic()) {
            com.dbzenith.network.UltimatePacket cine = new com.dbzenith.network.UltimatePacket(player.getId(), technique.id(),
                    technique.color() == 0xFFFFFF ? com.dbzenith.ki.Aura.color(data) : technique.color());
            for (ServerPlayer near : level.players()) if (near.distanceToSqr(player) < 64 * 64) com.dbzenith.network.ModNetwork.sendTo(near, cine);
            com.dbzenith.combat.CombatLog.near(player, net.minecraft.network.chat.Component.translatable("log.dbzenith.ultimate", player.getDisplayName(), technique.name()));
        }
        com.dbzenith.network.ModNetwork.sendToTrackingAndSelf(player, com.dbzenith.network.AnimEventPacket.forTechnique(player, technique));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                technique.style() == Technique.Style.BEAM ? com.dbzenith.registry.ModSounds.BEAM_FIRE.get() : com.dbzenith.registry.ModSounds.KI_FIRE.get(),
                SoundSource.PLAYERS, 0.6f, 1.4f + level.random.nextFloat() * 0.3f);
        return Result.FIRED;
    }

    /**
     * Spawns a technique's projectiles or beam for any caster, with no cost or cooldown checks.
     * Players go through {@link #use}; NPCs (Phase 4) and {@code /dbz cast} call this directly.
     */
    public static void spawn(ServerLevel level, LivingEntity caster, Technique technique, double damage) {
        spawn(level, caster, technique, damage, 1f);
    }

    /** The same, {@code scale} times the size (a charged technique, CX-23). */
    public static void spawn(ServerLevel level, LivingEntity caster, Technique technique, double damage, float scale) {
        if (technique.style() == Technique.Style.SELF) return; // self effects need a player caster (TechniqueEffects)
        Vec3 look = caster.getLookAngle();
        if (technique.style() == Technique.Style.BEAM) {
            level.addFreshEntity(KiBeamEntity.create(level, caster, technique, damage, scale));
            return;
        }
        if (technique.has(Technique.PLACED) || technique.has(Technique.RAIN)) {
            Vec3 eye = caster.getEyePosition();
            double reach = technique.has(Technique.PLACED) ? 24 : 40;
            net.minecraft.world.phys.BlockHitResult aim = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(reach)),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
            Vec3 spot = aim.getType() == net.minecraft.world.phys.HitResult.Type.MISS && technique.has(Technique.PLACED)
                    ? eye.add(look.scale(10))                         // nothing in reach: a mine hangs ten blocks out
                    : aim.getLocation().subtract(look.scale(0.6));
            for (int i = 0; i < technique.count(); i++) {
                KiBlastEntity blast = KiBlastEntity.create(level, caster, technique, damage, scale);
                if (technique.has(Technique.PLACED)) {              // a mine where you look
                    blast.moveTo(spot.x, spot.y - technique.size() * scale / 2.0, spot.z, 0, 0);
                    blast.setDeltaMovement(Vec3.ZERO);
                } else {                                            // rain: falls on the spot from high above
                    double r = 3.0 * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2;
                    blast.moveTo(spot.x + Math.cos(a) * r, spot.y + 14 + level.random.nextDouble() * 6, spot.z + Math.sin(a) * r, 0, 90);
                    blast.setDeltaMovement(new Vec3((level.random.nextDouble() - 0.5) * 0.1, -technique.speed(), (level.random.nextDouble() - 0.5) * 0.1));
                }
                level.addFreshEntity(blast);
            }
            return;
        }
        LivingEntity homingTarget = technique.homing() ? findTarget(caster, look) : null;
        for (int i = 0; i < technique.count(); i++) {
            Vec3 dir = technique.spreadDegrees() > 0 ? spread(look, technique.spreadDegrees(), caster) : look;
            KiBlastEntity blast = KiBlastEntity.create(level, caster, technique, damage, scale);
            Vec3 start = caster.getEyePosition().add(look.scale(0.6)).subtract(0, technique.size() * scale / 2.0, 0);
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
