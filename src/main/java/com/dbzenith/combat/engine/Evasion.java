package com.dbzenith.combat.engine;

import com.dbzenith.combat.CombatMoves;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.CombatState;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Defence and movement on the Dash key (CX-19 phase 3), in order of what the moment calls for:
 * <ol>
 *   <li>floored: a tech roll out;</li>
 *   <li>caught in a combo (stunned, or juggled): a <b>Burst</b>, a shockwave that ends the combo (ki and a charge);</li>
 *   <li>launched but free: an air recovery;</li>
 *   <li>guarding: a side step (with A/D) or a spot dodge;</li>
 *   <li>a foe you just launched: a chase, vanishing into its flight path;</li>
 *   <li>pushing forward at a foe in front: a <b>super dash</b> to them, arriving with a Z-hit ready;</li>
 *   <li>otherwise an ordinary dash, which opens a short <b>vanish</b> window: a blow landing in it misses, and you
 *       appear behind the attacker (ki, cooldown).</li>
 * </ol>
 * A vanish or a perfect guard opens the <b>counter</b>: the next press throws {@code counter_strike} at that foe.
 */
public final class Evasion {
    private Evasion() {}

    /** The Dash key. Returns true if it did something here (false: go on with an ordinary dash). */
    public static boolean dashKey(ServerPlayer p, PlayerData d, float forward, float strafe) {
        long now = p.level().getGameTime();
        Fighter f = CombatEngine.of(p);
        DBZConfig.Server c = DBZConfig.SERVER;
        CombatState s = d.combat();
        if (CombatMoves.isDowned(p, now) || s.downed(now)) return techRoll(p, d, forward, strafe, now);
        boolean caught = ModEffects.isStunned(p) || (f.juggled && !p.onGround() && f.comboHits > 0);
        if (caught) return burst(p, d, f, now);
        if (!p.onGround() && f.juggled) return airRecover(p, d, f, now);
        if (d.isGuarding()) return guardDodge(p, d, strafe, now);
        if (now < s.chaseReadyUntil && s.chaseCount < CombatMoves.MAX_CHASES && chase(p, d, now)) return true;
        if (forward > 0.5f && superDash(p, d, f, now)) return true;
        f.vanishUntil = now + c.vanishWindowTicks.get();                       // an ordinary dash: a blow now can be vanished
        return false;
    }

    // ------------------------------------------------------------------ vanish and counter

    /**
     * A blow from {@code attacker} is about to land on {@code victim}: if the victim dashed just before (and may vanish),
     * the blow misses and the victim appears behind the attacker, ready to counter. Returns true if it vanished.
     */
    static boolean tryVanish(LivingEntity victim, LivingEntity attacker, long now) {
        Fighter g = CombatEngine.peek(victim);
        if (g == null || now > g.vanishUntil || now < g.vanishReadyAt || ModEffects.isStunned(victim)) return false;
        DBZConfig.Server c = DBZConfig.SERVER;
        if (victim instanceof ServerPlayer p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            double cost = d == null ? 0 : d.getDerived().maxKi() * c.vanishKiPercent.get() / 100.0;
            if (d == null || (!p.getAbilities().instabuild && d.getKi() < cost)) return false;
            if (!p.getAbilities().instabuild) d.setKi(d.getKi() - cost);
        }
        g.vanishUntil = 0;
        g.vanishReadyAt = now + c.vanishCooldownTicks.get();
        Vec3 back = attacker.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize();
        Vec3 dest = attacker.position().subtract(back.scale(1.6));
        ServerLevel level = (ServerLevel) victim.level();
        level.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 1, victim.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        victim.teleportTo(dest.x, attacker.getY(), dest.z);
        victim.lookAt(EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        victim.fallDistance = 0;
        openCounter(g, attacker, now);
        SpecialMeter.gain(victim, c.specialPerVanish.get());
        com.dbzenith.duel.Duels.onDefense(victim, false);
        level.playSound(null, dest.x, dest.y, dest.z, ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.8f, 1.6f);
        tell(victim, "message.dbzenith.vanish", ChatFormatting.AQUA);
        return true;
    }


    /** Opens the vanish window for anyone (a training dummy reading a blow): a blow landing within {@code ticks} misses. */
    public static void openVanish(LivingEntity e, int ticks) {
        Fighter g = CombatEngine.of(e);
        long now = e.level().getGameTime();
        if (now >= g.vanishReadyAt) g.vanishUntil = now + ticks;
    }

    /** Whether {@code e} may throw a counter now (after a vanish or a perfect guard). */
    public static boolean counterReady(LivingEntity e, long now) {
        Fighter g = CombatEngine.peek(e);
        return g != null && now <= g.counterUntil;
    }
    /** The counter window: the next press of {@code g} throws the counter at {@code at}. */
    public static void openCounter(Fighter g, LivingEntity at, long now) {
        g.counterUntil = now + DBZConfig.SERVER.counterWindowTicks.get();
        g.counterTarget = at.getId();
    }

    /** A perfect guard (a parry) opens the counter too. Called by GuardRules. */
    public static void onPerfectGuard(LivingEntity defender, Entity attacker, long now) {
        if (attacker instanceof LivingEntity a) openCounter(CombatEngine.of(defender), a, now);
        SpecialMeter.gain(defender, DBZConfig.SERVER.specialPerPerfectGuard.get());
        com.dbzenith.duel.Duels.onDefense(defender, true);
        tell(defender, "message.dbzenith.perfect_guard", ChatFormatting.GOLD);
        tell(attacker, "message.dbzenith.parried", ChatFormatting.RED);
    }

    /** Within the counter window, a press is the counter: faces the foe and throws {@code counter_strike}. */
    static Move counter(Fighter f, long now) {
        if (now > f.counterUntil) return null;
        Move m = Moves.get("counter_strike");
        if (m == null) return null;
        f.counterUntil = 0;
        Entity foe = f.entity.level().getEntity(f.counterTarget);
        if (foe != null) f.entity.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
        tell(f.entity, "message.dbzenith.counter", ChatFormatting.GOLD);
        return m;
    }

    // ------------------------------------------------------------------ the Dash key's moves

    static boolean techRoll(ServerPlayer p, PlayerData d, float forward, float strafe, long now) {
        CombatMoves.getUp(p);
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(forward == 0 && strafe == 0 ? -1 : forward)
                .add(new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(strafe));
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(-1) : dir.normalize();
        p.setDeltaMovement(dir.scale(DBZConfig.SERVER.dashStrength.get() * 0.7).add(0, 0.15, 0));
        p.hurtMarked = true;
        d.setDashEvadeUntil(now + 8);
        anim(p, AnimEventPacket.RECOVER, 1);
        return true;
    }

    /** Burst: break a combo. Costs ki and a charge (two, coming back one at a time). */
    static boolean burst(ServerPlayer p, PlayerData d, Fighter f, long now) {
        CombatState s = d.combat();
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean free = p.getAbilities().instabuild;
        double cost = d.getDerived().maxKi() * c.burstKiPercent.get() / 100.0;
        if (s.breakerCharges <= 0 && !free) {
            p.displayClientMessage(Component.translatable("message.dbzenith.burst_empty"), true);
            return true;
        }
        if (!free && d.getKi() < cost) {
            p.displayClientMessage(Component.translatable("message.dbzenith.burst_no_ki"), true);
            return true;
        }
        if (!free) {
            d.setKi(d.getKi() - cost);
            s.breakerCharges--;
            if (s.breakerRechargeAt < now) s.breakerRechargeAt = now + c.burstRechargeTicks.get();
        }
        d.markDirty();
        p.removeEffect(ModEffects.STUN.get());
        CombatMoves.getUp(p);
        f.stunUntil = 0;
        f.juggled = false;
        f.spiked = false;
        f.comboHits = 0;
        f.juggleHits = 0;
        f.move = null;
        d.setDashEvadeUntil(now + 10);
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : com.dbzenith.skill.TechniqueEffects.around(p, 5)) {   // everyone off you
            if (!com.dbzenith.combat.PvpRules.mayAffect(p, e)) continue;
            Vec3 push = e.position().subtract(p.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : push.normalize();
            e.setDeltaMovement(push.scale(1.6).add(0, 0.45, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 10, 0));
            Fighter g = CombatEngine.peek(e);
            if (g != null) g.move = null;
        }
        p.setDeltaMovement(0, p.onGround() ? 0 : 0.1, 0);
        p.hurtMarked = true;
        p.fallDistance = 0;
        int aura = com.dbzenith.ki.Aura.color(d);
        ImpactPacket.at(p.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.3f, aura, p.getId()).send(level);
        anim(p, AnimEventPacket.BREAKER, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 0.9f, 1.3f);
        tell(p, "message.dbzenith.burst", ChatFormatting.YELLOW);
        com.dbzenith.combat.CombatLog.near(p, Component.translatable("log.dbzenith.burst", p.getDisplayName()));
        return true;
    }

    static boolean airRecover(ServerPlayer p, PlayerData d, Fighter f, long now) {
        boolean free = p.getAbilities().instabuild;
        if (!free && d.getStamina() < 15) return true;
        if (!free) d.setStamina(d.getStamina() - 15);
        f.juggled = false;
        f.juggleHits = 0;
        d.combat().launchedAt = Long.MIN_VALUE / 2;
        p.setDeltaMovement(0, 0.12, 0);
        p.hurtMarked = true;
        p.fallDistance = 0;
        d.setDashEvadeUntil(now + 8);
        anim(p, AnimEventPacket.RECOVER, 0);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DASH.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        return true;
    }

    static boolean guardDodge(ServerPlayer p, PlayerData d, float strafe, long now) {
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean free = p.getAbilities().instabuild;
        double cost = c.dashStaminaCost.get() * 0.5;
        if (!free && d.getStamina() < cost) return true;
        if (!free) d.setStamina(d.getStamina() - cost);
        if (Math.abs(strafe) > 0.3f) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(Math.signum(strafe) * c.dashStrength.get() * 0.55);
            p.setDeltaMovement(left.x, p.onGround() ? 0.1 : p.getDeltaMovement().y, left.z);
            p.hurtMarked = true;
            d.setDashEvadeUntil(now + 8);
            anim(p, AnimEventPacket.DODGE, strafe > 0 ? 1 : 2);
        } else {
            d.setDashEvadeUntil(now + 10);
            anim(p, AnimEventPacket.DODGE, 0);
        }
        return true;
    }

    /** Chase: vanish into the flight path of a foe you just launched (three times a combo). */
    static boolean chase(ServerPlayer p, PlayerData d, long now) {
        CombatState s = d.combat();
        Entity e = p.level().getEntity(s.launchTargetId);
        if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > 40) return false;
        boolean free = p.getAbilities().instabuild;
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!free && (d.getStamina() < c.dashStaminaCost.get() || d.getKi() < c.dashKiCost.get())) return false;
        if (!free) {
            d.setStamina(d.getStamina() - c.dashStaminaCost.get());
            d.setKi(d.getKi() - c.dashKiCost.get());
        }
        Vec3 flight = target.getDeltaMovement();
        Vec3 ahead = flight.lengthSqr() > 0.01 ? flight.normalize() : target.position().subtract(p.position()).normalize();
        Vec3 dest = target.position().add(ahead.scale(1.8));
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.fallDistance = 0;
        s.chaseCount++;
        s.chaseTick = now;
        s.chaseReadyUntil = Long.MIN_VALUE / 2;
        s.lastDashTick = now;                                                    // the next blow is a Z-hit
        d.markDirty();
        if (target instanceof net.minecraft.world.entity.player.Player tp) ModCapabilities.get(tp).ifPresent(td -> {
            td.combat().chasedTick = now;
            td.combat().chaserId = p.getId();
        });
        anim(p, AnimEventPacket.DASH, 0);
        level.playSound(null, dest.x, dest.y, dest.z, ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.5f, 1.9f);
        return true;
    }

    /** Guard raised just as a chaser arrives: a chase counter, you vanish behind them and they stagger. */
    public static void guardRaised(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        CombatState s = d.combat();
        if (now - s.chasedTick > 8) return;
        Entity e = p.level().getEntity(s.chaserId);
        s.chasedTick = Long.MIN_VALUE / 2;
        if (!(e instanceof LivingEntity chaser) || !chaser.isAlive()) return;
        Vec3 behind = chaser.position().subtract(chaser.getLookAngle().normalize().scale(1.5));
        p.teleportTo(behind.x, behind.y, behind.z);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, chaser.getEyePosition());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        chaser.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 15, 0));
        if (chaser instanceof net.minecraft.world.entity.player.Player cp) ModCapabilities.get(cp).ifPresent(cd -> cd.combat().chaseReadyUntil = Long.MIN_VALUE / 2);
        openCounter(CombatEngine.of(p), chaser, now);
        ImpactPacket.at(chaser.position().add(0, 1, 0), p.getLookAngle(), ImpactPacket.PARRY, 1.1f, 0xC0E0FF, p.getId()).send(p.serverLevel());
        tell(p, "message.dbzenith.chase_counter", ChatFormatting.AQUA);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    // ------------------------------------------------------------------ super dash

    /** The foe a super dash would go for: the one nearest the line of sight, in range and in view. */
    static LivingEntity superDashTarget(ServerPlayer p) {
        double range = DBZConfig.SERVER.superDashRange.get();
        LivingEntity locked = Targeting.target(p);                              // locked on (phase 6): that foe, from any angle
        if (locked != null) {
            double d = locked.distanceTo(p);
            if (d >= 4 && d <= range && p.hasLineOfSight(locked)) return locked;
        }
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 4 || dist > range) continue;
            double cos = to.dot(look) / dist;
            if (cos < 0.94) continue;                                            // about 20 degrees
            if (!p.hasLineOfSight(e)) continue;
            double score = (1 - cos) * 200 + dist;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    static boolean superDash(ServerPlayer p, PlayerData d, Fighter f, long now) {
        LivingEntity target = superDashTarget(p);
        if (target == null) return false;
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean free = p.getAbilities().instabuild;
        double cost = d.getDerived().maxKi() * c.superDashKiPercent.get() / 100.0;
        if (!free && d.getKi() < cost) return false;
        if (!free) d.setKi(d.getKi() - cost);
        f.superDashTarget = target;
        f.superDashUntil = now + c.superDashMaxTicks.get();
        d.setDashEvadeUntil(now + 4);
        anim(p, AnimEventPacket.DASH, 0);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DASH.get(), SoundSource.PLAYERS, 1f, 0.8f);
        tickSuperDash(f, now);
        return true;
    }

    /** One tick of a super dash: fly at the foe; close in, and the next blow is a Z-hit. */
    static void tickSuperDash(Fighter f, long now) {
        LivingEntity e = f.entity, t = f.superDashTarget;
        if (t == null) return;
        if (now > f.superDashUntil || !t.isAlive() || ModEffects.isStunned(e) || t.level() != e.level()) {
            f.superDashTarget = null;
            return;
        }
        Vec3 to = t.position().subtract(e.position());
        double dist = to.length();
        if (dist < 2.6) {                                                       // arrived
            f.superDashTarget = null;
            e.setDeltaMovement(Vec3.ZERO);                                     // stop dead: no sliding through them
            e.hurtMarked = true;
            e.lookAt(EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
            if (e instanceof ServerPlayer p) ModCapabilities.get(p).ifPresent(d -> d.combat().lastDashTick = now);
            return;
        }
        double speed = DBZConfig.SERVER.superDashSpeed.get();
        if (e instanceof ServerPlayer p) speed *= 1.0 + ModCapabilities.get(p).map(d -> d.getDerived().moveSpeed()).orElse(0.0);
        Vec3 v = to.normalize().scale(Math.min(speed, (dist - 2.4) * 0.45));   // ease off as it closes in (clients carry momentum)
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        e.fallDistance = 0;
        e.lookAt(EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
        if (e instanceof ServerPlayer p && now % 2 == 0) ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.DASH, 0));
    }

    // ------------------------------------------------------------------ helpers

    static void anim(ServerPlayer p, int kind, int data) {
        ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), kind, data));
    }

    /** A big callout on that player's screen (phase 8): the key's text in the colour. */
    static void tell(Entity e, String key, ChatFormatting color) {
        if (e instanceof ServerPlayer p) com.dbzenith.network.CalloutPacket.send(p, key, color.getColor() == null ? 0xFFFFFF : color.getColor());
    }

    /** Whether {@code defender} faces {@code from} closely enough for its guard to cover the blow. */
    public static boolean guardCovers(LivingEntity defender, Entity from) {
        double arc = DBZConfig.SERVER.guardArcDegrees.get();
        if (from == null || arc >= 360) return true;
        Vec3 to = from.position().subtract(defender.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return true;
        Vec3 look = defender.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) return true;
        double cos = to.normalize().dot(look.normalize());
        return cos >= Math.cos(Math.toRadians(arc / 2));
    }
}
