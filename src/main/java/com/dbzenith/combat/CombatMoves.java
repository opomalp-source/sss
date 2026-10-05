package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.CombatState;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.TechniqueEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Combat v3, on the keys fighters already have (attack, Heavy, Dash, Guard):
 * <ul>
 * <li>Z-hit: a blow within half a second of a dash: x1.5 and the longest stun.</li>
 * <li>Directional heavies (movement held on release): forward rushes the foe away, back is an uppercut, sideways a
 * stunning hook; airborne and looking down still spikes.</li>
 * <li>Sweep: Heavy released while guarding: an unblockable low kick in front that knocks foes down.</li>
 * <li>Chase: after launching a foe, Dash vanishes into its flight path (three times a combo); the chased foe can
 * counter by raising their guard just as the chaser arrives.</li>
 * <li>Revenge Counter: Dash while stunned: hyper armour and a Z-hit on whoever hit you, for a tenth of your stamina.</li>
 * <li>Breaker Wave: Shift+Dash while under attack: a burst that throws everyone off you (two charges, 30s each).</li>
 * <li>Snap recovery: Dash while launched rights you in the air; Dash while downed rolls you out.</li>
 * <li>Spot dodge and side step: Dash while guarding.</li>
 * <li>Clash: two fighters punching each other in the same instant: both blows cancel and they are thrown apart.</li>
 * <li>Downed: a sweep or a spike into the ground floors a fighter for 1.5s: half damage, no knockback, no launch.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatMoves {
    public static final int DIR_NEUTRAL = 0, DIR_FORWARD = 1, DIR_BACK = 2, DIR_LEFT = 3, DIR_RIGHT = 4;
    static final int ZHIT_WINDOW = 10, ZHIT_STUN = 20, CHASE_WINDOW = 30, MAX_CHASES = 3, COUNTER_WINDOW = 8,
            DOWNED_TICKS = 30, BREAKER_RECHARGE = 600, REVENGE_COOLDOWN = 80, CLASH_WINDOW = 3;
    static final double ZHIT_MULT = 1.5, CHASE_BONUS = 1.15, SWEEP_MULT = 1.2, DOWNED_TAKEN = 0.5;

    /** Mobs and players knocked down, until when (players also keep it in their combat state for the pose). */
    private static final Map<LivingEntity, Long> DOWNED = new HashMap<>();
    /** Spiked fighters: downed when they land. */
    private static final Map<LivingEntity, Long> SPIKED = new HashMap<>();
    /** Blows that guards cannot stop (the sweep), for the next hurt event only. */
    static LivingEntity unblockable;

    private CombatMoves() {}

    static CombatState state(LivingEntity e) {
        return e instanceof Player p ? ModCapabilities.get(p).map(PlayerData::combat).orElse(null) : null;
    }

    public static boolean isDowned(LivingEntity e, long now) {
        Long until = DOWNED.get(e);
        return until != null && now < until;
    }

    // ------------------------------------------------------------------ heavy release

    /** The movement held when a heavy is released picks its direction. */
    public static int direction(float forward, float strafe) {
        if (Math.abs(forward) < 0.3f && Math.abs(strafe) < 0.3f) return DIR_NEUTRAL;
        if (Math.abs(forward) >= Math.abs(strafe)) return forward > 0 ? DIR_FORWARD : DIR_BACK;
        return strafe > 0 ? DIR_LEFT : DIR_RIGHT;
    }

    /** Heavy key released: a sweep while guarding, otherwise arm a directional heavy. */
    public static void heavyReleased(ServerPlayer player, PlayerData d, float forward, float strafe) {
        if (d.isGuarding()) {
            d.releaseHeavyCharge();
            sweep(player, d);
            return;
        }
        int dir = direction(forward, strafe);
        if (HeavyStrike.release(player, d) > 0) {
            d.combat().heavyDir = dir;
            if (dir != DIR_NEUTRAL) {
                int kind = dir == DIR_BACK ? AnimEventPacket.UPPERCUT : dir == DIR_FORWARD ? AnimEventPacket.RUSH : AnimEventPacket.HOOK;
                ModNetwork.sendToTrackingAndSelf(player, new AnimEventPacket(player.getId(), kind, dir));
            }
        }
    }

    /** The sweep: an unblockable low kick that floors whatever stands in front. */
    public static int sweep(ServerPlayer player, PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!player.getAbilities().instabuild && d.getStamina() < c.heavyStaminaCost.get()) return 0;
        if (!player.getAbilities().instabuild) d.setStamina(d.getStamina() - c.heavyStaminaCost.get());
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        double damage = DamageCalculator.meleeOutgoing(d, 1.0, 1) * SWEEP_MULT;
        int hits = 0;
        for (LivingEntity e : TechniqueEffects.around(player, 3.0)) {
            Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1e-4 && to.normalize().dot(look) < 0.3) continue;
            if (Math.abs(e.getY() - player.getY()) > 1.5) continue;
            unblockable = e;
            e.invulnerableTime = 0;
            e.hurt(ModDamageTypes.thrown(level, player), (float) damage);
            unblockable = null;
            knockDown(e, now);
            hits++;
        }
        ModNetwork.sendToTrackingAndSelf(player, new AnimEventPacket(player.getId(), AnimEventPacket.SWEEP, 0));
        ImpactPacket.at(player.position().add(look.scale(1.5)).add(0, 0.3, 0), look, ImpactPacket.SPIKE, 0.9f, 0xE0D0B0, player.getId()).send(level);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.WHOOSH.get(), SoundSource.PLAYERS, 1f, 0.7f);
        return hits;
    }

    // ------------------------------------------------------------------ melee

    /**
     * Before a blow lands: its multiplier from the attacker's moves (Z-hit, chase) and the victim's state (downed).
     * Returns 0 for a clash (both blows cancel).
     */
    public static double beforeMelee(Player attacker, PlayerData ad, LivingEntity victim, long now) {
        CombatState a = ad.combat();
        CombatState v = state(victim);
        if (v != null && v.lastMeleeTargetId == attacker.getId() && now - v.lastMeleeTick <= CLASH_WINDOW) {   // both swung at once
            clash(attacker, victim, now);
            v.lastMeleeTick = Long.MIN_VALUE / 2;
            a.lastMeleeTick = Long.MIN_VALUE / 2;
            return 0;
        }
        a.lastMeleeTargetId = victim.getId();
        a.lastMeleeTick = now;
        double m = 1.0;
        if (now - a.lastDashTick <= ZHIT_WINDOW) m *= ZHIT_MULT;
        if (now - a.chaseTick <= 20) m *= CHASE_BONUS;
        if (isDowned(victim, now)) m *= DOWNED_TAKEN;
        if (v != null) v.lastAttackerId = attacker.getId();
        return m;
    }

    /** True if this blow was a Z-hit (checked after {@link #beforeMelee}). */
    public static boolean isZHit(PlayerData ad, long now) {
        return now - ad.combat().lastDashTick <= ZHIT_WINDOW;
    }

    /** After a blow lands: Z-hit stun, launches and chases. */
    public static void afterMelee(Player attacker, PlayerData ad, LivingEntity victim, boolean heavy, long now) {
        CombatState a = ad.combat();
        boolean armoured = state(victim) != null && now < state(victim).hyperArmorUntil;
        if (isZHit(ad, now)) {
            a.lastDashTick = Long.MIN_VALUE / 2;                      // one Z-hit per dash
            if (!armoured) victim.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ZHIT_STUN, 0));
            if (victim.level() instanceof ServerLevel level) {
                ImpactPacket.melee(attacker, victim, ImpactPacket.HEAVY).send(level);
                level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), com.dbzenith.registry.ModSounds.PUNCH_HEAVY.get(), SoundSource.PLAYERS, 1f, 0.5f);
            }
            if (attacker instanceof ServerPlayer sp) ModNetwork.sendToTrackingAndSelf(sp, new AnimEventPacket(sp.getId(), AnimEventPacket.ZHIT, 0));
        }
        if (heavy && !isDowned(victim, now)) {
            if (a.launchTargetId != victim.getId() || now - a.launchTick > 60) a.chaseCount = 0;
            a.launchTargetId = victim.getId();
            a.launchTick = now;
            if (a.chaseCount < MAX_CHASES) a.chaseReadyUntil = now + CHASE_WINDOW;
            ad.markDirty();
            CombatState v = state(victim);
            if (v != null) v.launchedAt = now;
        }
        if (a.heavyDir != DIR_NEUTRAL && heavy) a.heavyDir = DIR_NEUTRAL;
    }

    static void clash(Player a, LivingEntity b, long now) {
        Vec3 mid = a.position().add(b.position()).scale(0.5).add(0, 1.2, 0);
        Vec3 apart = b.position().subtract(a.position()).multiply(1, 0, 1);
        apart = apart.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : apart.normalize();
        AerialCombat.queue(a, apart.scale(-0.9).add(0, 0.25, 0));
        AerialCombat.queue(b, apart.scale(0.9).add(0, 0.25, 0));
        if (a.level() instanceof ServerLevel level) {
            ImpactPacket.at(mid, apart, ImpactPacket.PARRY, 1.3f, 0xFFFFFF, a.getId()).send(level);
            level.playSound(null, mid.x, mid.y, mid.z, com.dbzenith.registry.ModSounds.PARRY.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
            level.playSound(null, mid.x, mid.y, mid.z, com.dbzenith.registry.ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 0.4f, 1.8f);
        }
    }

    /** Floor a fighter: stunned on the ground, half damage, no knockback, until they get up. */
    public static void knockDown(LivingEntity e, long now) {
        DOWNED.put(e, now + DOWNED_TICKS);
        e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), DOWNED_TICKS, 0));
        CombatState s = state(e);
        if (s != null) {
            s.downedUntil = now + DOWNED_TICKS;
            s.downedFlag = true;
            if (e instanceof Player p) ModCapabilities.get(p).ifPresent(PlayerData::markDirty);
        }
    }

    static void getUp(LivingEntity e) {
        DOWNED.remove(e);
        e.removeEffect(ModEffects.STUN.get());
        CombatState s = state(e);
        if (s != null) {
            s.downedFlag = false;
            s.downedUntil = Long.MIN_VALUE / 2;
            if (e instanceof Player p) ModCapabilities.get(p).ifPresent(PlayerData::markDirty);
        }
    }

    /** A spike: the victim is floored when it hits the ground. */
    static void spiked(LivingEntity e, long now) {
        SPIKED.put(e, now + 60);
    }

    // ------------------------------------------------------------------ the dash key, in context

    /**
     * Runs before an ordinary dash. Returns true when the key did something else: ground slide, snap recovery, Breaker
     * Wave, Revenge Counter, spot dodge / side step, or a chase.
     */
    public static boolean dashKey(ServerPlayer player, PlayerData d, float forward, float strafe) {
        long now = player.level().getGameTime();
        CombatState s = d.combat();
        DBZConfig.Server c = DBZConfig.SERVER;
        boolean free = player.getAbilities().instabuild;
        boolean underAttack = now - d.getLastFoeHitTick() <= 20;
        if (isDowned(player, now) || s.downed(now)) {                       // ground slide: roll out
            getUp(player);
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(forward == 0 && strafe == 0 ? -1 : forward)
                    .add(new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(strafe));
            dir = dir.lengthSqr() < 1e-4 ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(-1) : dir.normalize();
            player.setDeltaMovement(dir.scale(c.dashStrength.get() * 0.7).add(0, 0.15, 0));
            player.hurtMarked = true;
            d.setDashEvadeUntil(now + 8);
            anim(player, AnimEventPacket.RECOVER, 1);
            return true;
        }
        if (player.isShiftKeyDown() && (underAttack || ModEffects.isStunned(player))) return breaker(player, d, now);
        if (ModEffects.isStunned(player)) return revenge(player, d, now);
        if (!player.onGround() && now - s.launchedAt <= 30) {                // snap recovery
            if (!free && d.getStamina() < 15) return true;
            if (!free) d.setStamina(d.getStamina() - 15);
            s.launchedAt = Long.MIN_VALUE / 2;
            player.setDeltaMovement(0, 0.12, 0);
            player.hurtMarked = true;
            player.fallDistance = 0;
            d.setDashEvadeUntil(now + 8);
            anim(player, AnimEventPacket.RECOVER, 0);
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.DASH.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            return true;
        }
        if (d.isGuarding()) {                                              // spot dodge or side step
            double cost = c.dashStaminaCost.get() * 0.5;
            if (!free && d.getStamina() < cost) return true;
            if (!free) d.setStamina(d.getStamina() - cost);
            if (Math.abs(strafe) > 0.3f) {
                float yaw = player.getYRot() * Mth.DEG_TO_RAD;
                Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(Math.signum(strafe) * c.dashStrength.get() * 0.55);
                player.setDeltaMovement(left.x, player.onGround() ? 0.1 : player.getDeltaMovement().y, left.z);
                player.hurtMarked = true;
                d.setDashEvadeUntil(now + 8);
                anim(player, AnimEventPacket.DODGE, strafe > 0 ? 1 : 2);
            } else {
                d.setDashEvadeUntil(now + 10);
                anim(player, AnimEventPacket.DODGE, 0);
            }
            return true;
        }
        if (now < s.chaseReadyUntil && s.chaseCount < MAX_CHASES) return chase(player, d, now);
        return false;
    }

    static boolean chase(ServerPlayer player, PlayerData d, long now) {
        CombatState s = d.combat();
        Entity e = player.level().getEntity(s.launchTargetId);
        if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > 40) return false;
        boolean free = player.getAbilities().instabuild;
        DBZConfig.Server c = DBZConfig.SERVER;
        if (!free && (d.getStamina() < c.dashStaminaCost.get() || d.getKi() < c.dashKiCost.get())) return false;
        if (!free) {
            d.setStamina(d.getStamina() - c.dashStaminaCost.get());
            d.setKi(d.getKi() - c.dashKiCost.get());
        }
        Vec3 flight = target.getDeltaMovement();
        Vec3 ahead = flight.lengthSqr() > 0.01 ? flight.normalize() : target.position().subtract(player.position()).normalize();
        Vec3 dest = target.position().add(ahead.scale(1.8));
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0;
        s.chaseCount++;
        s.chaseTick = now;
        s.chaseReadyUntil = Long.MIN_VALUE / 2;
        d.setDashEvadeUntil(now + 6);
        d.markDirty();
        CombatState v = state(target);
        if (v != null) {
            v.chasedTick = now;
            v.chaserId = player.getId();
        }
        anim(player, AnimEventPacket.DASH, 0);
        level.playSound(null, dest.x, dest.y, dest.z, com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.5f, 1.9f);
        return true;
    }

    /** Guard raised: just as a chaser arrives, that is a chase counter: you vanish behind them and they stagger. */
    public static void guardRaised(ServerPlayer player, PlayerData d) {
        long now = player.level().getGameTime();
        CombatState s = d.combat();
        if (now - s.chasedTick > COUNTER_WINDOW) return;
        Entity e = player.level().getEntity(s.chaserId);
        s.chasedTick = Long.MIN_VALUE / 2;
        if (!(e instanceof LivingEntity chaser) || !chaser.isAlive()) return;
        Vec3 behind = chaser.position().subtract(chaser.getLookAngle().normalize().scale(1.5));
        player.teleportTo(behind.x, behind.y, behind.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, chaser.getEyePosition());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        chaser.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 15, 0));
        CombatState cs = state(chaser);
        if (cs != null) cs.chaseReadyUntil = Long.MIN_VALUE / 2;
        ImpactPacket.at(chaser.position().add(0, 1, 0), player.getLookAngle(), ImpactPacket.PARRY, 1.1f, 0xC0E0FF, player.getId()).send(player.serverLevel());
        player.displayClientMessage(Component.translatable("message.dbzenith.chase_counter"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    static boolean revenge(ServerPlayer player, PlayerData d, long now) {
        boolean free = player.getAbilities().instabuild;
        double cost = d.getDerived().maxStamina() * 0.10;
        if (d.isOnCooldown("revenge", now) || !free && d.getStamina() < cost) return true;   // stunned: nothing else to do
        if (!free) d.setStamina(d.getStamina() - cost);
        d.setCooldown("revenge", now + REVENGE_COOLDOWN);
        CombatState s = d.combat();
        player.removeEffect(ModEffects.STUN.get());
        s.hyperArmorUntil = now + 12;
        Entity e = player.level().getEntity(s.lastAttackerId);
        if (e instanceof LivingEntity foe && foe.isAlive() && foe.distanceTo(player) < 10) {
            Vec3 front = foe.position().add(player.position().subtract(foe.position()).multiply(1, 0, 1).normalize().scale(1.2));
            player.teleportTo(front.x, foe.getY(), front.z);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
            s.lastDashTick = now;                                      // the counter lands as a Z-hit
            double damage = DamageCalculator.meleeOutgoing(d, 1.0, 1) * ZHIT_MULT;
            foe.invulnerableTime = 0;
            foe.hurt(ModDamageTypes.thrown(player.serverLevel(), player), (float) damage);
            foe.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ZHIT_STUN, 0));
            ImpactPacket.melee(player, foe, ImpactPacket.HEAVY).send(player.serverLevel());
            s.lastDashTick = Long.MIN_VALUE / 2;
        }
        anim(player, AnimEventPacket.ZHIT, 1);
        player.displayClientMessage(Component.translatable("message.dbzenith.revenge"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.PUNCH_HEAVY.get(), SoundSource.PLAYERS, 1f, 0.6f);
        return true;
    }

    static boolean breaker(ServerPlayer player, PlayerData d, long now) {
        CombatState s = d.combat();
        if (s.breakerCharges <= 0) {
            player.displayClientMessage(Component.translatable("message.dbzenith.breaker_empty"), true);
            return true;
        }
        s.breakerCharges--;
        if (s.breakerRechargeAt < now) s.breakerRechargeAt = now + BREAKER_RECHARGE;
        d.markDirty();
        player.removeEffect(ModEffects.STUN.get());
        getUp(player);
        d.setDashEvadeUntil(now + 10);
        ServerLevel level = player.serverLevel();
        double damage = DamageCalculator.kiOutgoing(d, 0.3);
        for (LivingEntity e : TechniqueEffects.around(player, 5)) {
            e.invulnerableTime = 0;
            e.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
            Vec3 push = e.position().subtract(player.position()).normalize().scale(1.7);
            AerialCombat.queue(e, new Vec3(push.x, 0.45, push.z));
        }
        int aura = com.dbzenith.ki.Aura.color(d);
        ImpactPacket.at(player.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.3f, aura, player.getId()).send(level);
        anim(player, AnimEventPacket.BREAKER, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 0.9f, 1.3f);
        return true;
    }

    static void anim(ServerPlayer player, int kind, int data) {
        ModNetwork.sendToTrackingAndSelf(player, new AnimEventPacket(player.getId(), kind, data));
    }

    // ------------------------------------------------------------------ ticking and events

    /** Per player tick (KiTicker): breaker charges come back one at a time. */
    public static void tick(ServerPlayer player, PlayerData d, long now) {
        CombatState s = d.combat();
        if (s.breakerCharges < 2 && now >= s.breakerRechargeAt) {
            s.breakerCharges++;
            s.breakerRechargeAt = s.breakerCharges < 2 ? now + BREAKER_RECHARGE : Long.MIN_VALUE / 2;
            d.markDirty();
        }
        if (s.downedUntil > Long.MIN_VALUE / 2 && now >= s.downedUntil) {
            s.downedUntil = Long.MIN_VALUE / 2;
            s.downedFlag = false;
            d.markDirty();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!SPIKED.isEmpty()) {
            for (Iterator<Map.Entry<LivingEntity, Long>> it = SPIKED.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                LivingEntity v = e.getKey();
                long now = v.level().getGameTime();
                if (!v.isAlive() || now > e.getValue()) it.remove();
                else if (v.onGround()) {                               // slammed into the ground: floored
                    it.remove();
                    knockDown(v, now);
                    if (v.level() instanceof ServerLevel level) {
                        ImpactPacket.at(v.position().add(0, 0.1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.0f, 0xC0A070, -1).send(level);
                    }
                }
            }
        }
        if (!DOWNED.isEmpty()) DOWNED.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getKey().level().getGameTime() >= e.getValue());
    }

    /** Hyper armour and the downed take no knockback. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity e = event.getEntity();
        long now = e.level().getGameTime();
        CombatState s = state(e);
        if (isDowned(e, now) || s != null && now < s.hyperArmorUntil) event.setCanceled(true);
    }

    static boolean hyperArmoured(LivingEntity e, long now) {
        CombatState s = state(e);
        return s != null && now < s.hyperArmorUntil;
    }
}
