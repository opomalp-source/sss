package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.CombatMoves;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The combat engine (CX-19): melee from data for players and NPCs alike, on the server.
 * <ul>
 *   <li><b>Input:</b> a press (light or heavy, with the direction pushed) picks a move from {@link Moves}: the next of
 *       the chain if the last move ended within the chain window, else an opener. A press that comes too early is
 *       buffered and used as soon as it can be: when the move that landed reaches its cancel tick, or when it ends.</li>
 *   <li><b>A move:</b> winds up ({@code startup}, lunging toward the foe), checks its hitbox every {@code active} tick
 *       (each foe once), then recovers. Being hit cuts it short unless it has armour then.</li>
 *   <li><b>A hit:</b> damage scaled down along the combo, hitstun shortened along it, then the move's knockback or
 *       launch: away (a wall within the flight slams), up (a juggle: air hits keep the foe floating, up to a limit),
 *       a spike (the ground slams and floors them) or a knockdown. Floored foes take half and only a couple of hits;
 *       getting up leaves a moment untouchable. Damage goes through {@code CombatEvents} (defence, guard, parry,
 *       evasion, PvP rules) as a {@link ModDamageTypes#STRIKE}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEngine {
    private static final Map<LivingEntity, Fighter> FIGHTERS = new IdentityHashMap<>();
    private static final List<LivingEntity> SCRATCH = new ArrayList<>();
    private static final List<Fighter> TICKING = new ArrayList<>();

    /** Set while the engine's own blow is being dealt: the impact it shows, and whether it ignores guard. Read by CombatEvents. */
    public static int pendingImpact = -1;
    public static boolean pendingUnblockable;
    /** What CombatEvents made of the engine's blow: the impact shown (guard, parry, ...) and the damage taken. */
    public static int outcomeImpact = -1;
    public static double outcomeDealt;

    private CombatEngine() {}

    public static Fighter of(LivingEntity e) {
        return FIGHTERS.computeIfAbsent(e, Fighter::new);
    }

    public static Fighter peek(LivingEntity e) {
        return FIGHTERS.get(e);
    }

    // ------------------------------------------------------------------ input

    /** A press from a fighter. Returns whether a move started now (false: refused, or buffered for later). */
    public static boolean press(LivingEntity e, Moves.Input in) {
        Fighter f = of(e);
        long now = e.level().getGameTime();
        if (now == f.lastPressTick && f.move != null && f.moveTick == 0) return false;   // one new move per tick
        f.lastPressTick = now;
        if (!mayAct(e)) return false;
        Fighter.State st = f.state(now);
        if (st == Fighter.State.STUNNED || st == Fighter.State.LAUNCHED || st == Fighter.State.KNOCKDOWN || st == Fighter.State.DEAD) return false;
        if (f.move != null) {
            boolean cancellable = f.landed && f.moveTick >= f.move.cancel;
            Move next = cancellable ? Moves.select(in, f.move.id) : null;
            if (next != null && next.follows(f.move.id)) {
                start(f, next, now);
                return true;
            }
            f.buffered = in;                                                   // too early: keep it
            f.bufferedAt = now;
            return false;
        }
        Move next = Moves.select(in, now <= f.chainUntil ? f.lastMove : "start");
        if (next == null) return false;
        start(f, next, now);
        return true;
    }

    /** Things that keep a player from fighting at all (beam struggles, power-ups, meditation, spectating). */
    static boolean mayAct(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d == null || d.isTransforming() || d.isMeditating()) return false;
            if (com.dbzenith.skill.BeamStruggle.isStruggling(p)) return false;
        }
        return true;
    }

    static void start(Fighter f, Move m, long now) {
        LivingEntity e = f.entity;
        if (e instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d != null) {
                if (m.kiCost > 0 && d.getKi() < m.kiCost && !p.getAbilities().instabuild) return;
                if (!p.getAbilities().instabuild) {
                    d.setKi(d.getKi() - m.kiCost);
                    d.setStamina(Math.max(0, d.getStamina() - m.staminaCost));
                }
            }
        }
        f.move = m;
        f.moveTick = 0;
        f.landed = false;
        f.struck.clear();
        f.buffered = null;
        if (m.lunge > 0) {                                                     // step in toward the foe
            Vec3 look = e.getLookAngle();
            Vec3 push = e.onGround() ? new Vec3(look.x, 0, look.z).normalize().scale(m.lunge * 0.45) : look.scale(m.lunge * 0.45);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
        if (e instanceof ServerPlayer sp) ModNetwork.sendToTrackingAndSelf(sp, new com.dbzenith.network.MoveAnimPacket(sp.getId(), m.anim));
        else e.swing(InteractionHand.MAIN_HAND, true);
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), com.dbzenith.registry.ModSounds.WHOOSH.get(), SoundSource.PLAYERS,
                m.button == Move.Button.HEAVY ? 0.7f : 0.45f, m.button == Move.Button.HEAVY ? 0.8f : 1.2f + e.getRandom().nextFloat() * 0.2f);
    }

    /** Players throw blows through the engine, not vanilla's punch: a bare-handed vanilla attack does nothing. */
    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().isEmpty() && Moves.loaded()) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FIGHTERS.isEmpty()) return;
        FIGHTERS.values().removeIf(f -> f.entity.isRemoved());
        TICKING.clear();
        TICKING.addAll(FIGHTERS.values());                                    // a hit may add a fighter (the victim) as we go
        for (int i = 0; i < TICKING.size(); i++) {
            Fighter f = TICKING.get(i);
            tick(f, f.entity.level().getGameTime());
        }
    }

    static void tick(Fighter f, long now) {
        LivingEntity e = f.entity;
        DBZConfig.Server c = DBZConfig.SERVER;
        if (f.move != null) {
            Move m = f.move;
            if (now < f.stunUntil && f.moveTick >= m.armor) {                  // hit out of it
                f.move = null;
            } else {
                if (m.isActive(f.moveTick)) detect(f, m, now);
                f.moveTick++;
                if (f.buffered != null && f.landed && f.moveTick >= m.cancel && now - f.bufferedAt <= c.inputBufferTicks.get()) {
                    Moves.Input in = f.buffered;
                    f.buffered = null;
                    Move next = Moves.select(in, m.id);
                    if (next != null && next.follows(m.id)) start(f, next, now);
                }
                if (f.move == m && f.moveTick >= m.total()) {                   // done: the chain stays open a moment
                    f.lastMove = f.landed ? m.id : "start";
                    f.chainUntil = now + c.chainWindowTicks.get();
                    f.move = null;
                }
            }
        }
        if (f.move == null && f.buffered != null) {
            Moves.Input in = f.buffered;
            f.buffered = null;
            if (now - f.bufferedAt <= c.inputBufferTicks.get()) press(e, in);
        }

        // ---- as a victim: slams, landings, getting up, combos running out
        if (now < f.flightUntil && e.horizontalCollision && f.flightBy != null) {    // knocked into a wall
            f.flightUntil = 0;
            slam(f, f.flightBy, f.lastBlow * c.wallSlamBonus.get(), now);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            stun(f, e, 20, now);
        }
        if (f.spiked && e.onGround()) {                                         // driven into the ground
            f.spiked = false;
            if (f.flightBy != null) slam(f, f.flightBy, f.lastBlow * c.groundSlamBonus.get(), now);
            e.setDeltaMovement(new Vec3(0, 0.38, 0));
            e.hurtMarked = true;
            CombatMoves.knockDown(e, now, 32);
            f.juggled = false;
        }
        if (f.juggled && e.onGround() && now - f.lastHitAt > 3) {
            f.juggled = false;
            f.juggleHits = 0;
        }
        boolean downed = CombatMoves.isDowned(e, now);
        if (f.wasDowned && !downed) {                                           // up again: a moment's grace
            f.wakeUntil = now + c.wakeUpGraceTicks.get();
            f.downedHits = 0;
            f.comboHits = 0;
        }
        f.wasDowned = downed;
        if (f.comboHits > 0 && now - f.lastHitAt > c.comboResetTicks.get() && !f.juggled && !downed) {
            f.comboHits = 0;
            f.juggleHits = 0;
            f.comboFrom = -1;
        }
    }

    // ------------------------------------------------------------------ hits

    /** Every foe in the move's hitbox, nearest first, up to its target count, each once per move. */
    static void detect(Fighter f, Move m, long now) {
        LivingEntity a = f.entity;
        Vec3 eye = a.getEyePosition(), look = a.getLookAngle();
        double reach = m.range + m.radius + 1.5;
        AABB box = a.getBoundingBox().inflate(reach);
        SCRATCH.clear();
        for (LivingEntity v : a.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (v == a || !v.isAlive() || v.isSpectator() || v instanceof ArmorStand || f.struck.contains(v.getId())) continue;
            if (a.isPassengerOfSameVehicle(v) || v.isAlliedTo(a)) continue;
            if (!Hitbox.contains(m, eye, look, a.getYRot(), v.getBoundingBox())) continue;
            if (!a.hasLineOfSight(v)) continue;
            SCRATCH.add(v);
        }
        if (SCRATCH.isEmpty()) return;
        SCRATCH.sort(Comparator.comparingDouble(v -> v.distanceToSqr(a)));
        int n = Math.min(m.maxTargets, SCRATCH.size());
        for (int i = 0; i < n; i++) {
            LivingEntity v = SCRATCH.get(i);
            f.struck.add(v.getId());
            if (clash(f, m, v, now)) return;
            hit(f, m, v, now);
        }
    }

    /** Both throwing blows at each other in the same instant: they cancel, and the two are thrown apart. */
    static boolean clash(Fighter f, Move m, LivingEntity v, long now) {
        Fighter g = peek(v);
        if (g == null || g.move == null || !g.move.isActive(g.moveTick) || g.struck.contains(f.entity.getId())) return false;
        LivingEntity a = f.entity;
        if (!Hitbox.contains(g.move, v.getEyePosition(), v.getLookAngle(), v.getYRot(), a.getBoundingBox())) return false;
        f.move = null;
        g.move = null;
        Vec3 apart = v.position().subtract(a.position()).multiply(1, 0, 1);
        apart = apart.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : apart.normalize();
        a.setDeltaMovement(apart.scale(-0.8).add(0, 0.2, 0));
        v.setDeltaMovement(apart.scale(0.8).add(0, 0.2, 0));
        a.hurtMarked = v.hurtMarked = true;
        if (a.level() instanceof ServerLevel level) {
            Vec3 mid = a.position().add(v.position()).scale(0.5).add(0, 1.2, 0);
            ImpactPacket.at(mid, apart, ImpactPacket.PARRY, 1.3f, 0xFFFFFF, a.getId()).send(level);
            level.playSound(null, mid.x, mid.y, mid.z, com.dbzenith.registry.ModSounds.PARRY.get(), SoundSource.PLAYERS, 0.7f, 1.5f);
        }
        return true;
    }

    /** The attacker's own strength for a blow of power 1 (DBZ damage). */
    static double base(LivingEntity a) {
        if (a instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d != null) {
                d.recomputeIfStale();
                return DamageCalculator.meleeOutgoing(d, 1.0, 1);
            }
        }
        var attr = a.getAttribute(Attributes.ATTACK_DAMAGE);
        double dmg = attr == null ? 2.0 : attr.getValue();
        if (a instanceof com.dbzenith.npc.KiFighter k) dmg *= k.meleeMultiplier();
        return DamageCalculator.fromVanilla(dmg);
    }

    static void hit(Fighter f, Move m, LivingEntity v, long now) {
        LivingEntity a = f.entity;
        DBZConfig.Server c = DBZConfig.SERVER;
        Fighter g = of(v);
        if (now < g.wakeUntil) return;                                          // just got up
        boolean downed = CombatMoves.isDowned(v, now);
        if (downed && g.downedHits >= c.downedHitLimit.get()) return;          // no beating a floored foe forever
        if (g.comboFrom != a.getId() || now - g.lastHitAt > c.comboResetTicks.get()) {
            g.comboHits = 0;
            g.juggleHits = 0;
        }
        int n = g.comboHits;
        double scale = Math.max(c.comboMinDamage.get(), 1.0 - c.comboDamageDecay.get() * n);
        double stunScale = Math.max(c.hitstunMin.get(), 1.0 - c.hitstunDecay.get() * n);
        double raw = base(a) * m.damage * scale;
        boolean zhit = false;
        if (a instanceof Player p) {
            PlayerData ad = ModCapabilities.get(p).orElse(null);
            if (ad != null && now - ad.combat().lastDashTick <= 10) {           // off a dash: a Z-hit
                raw *= c.dashStrikeBonus.get();
                ad.combat().lastDashTick = Long.MIN_VALUE / 2;
                zhit = true;
            }
        }
        if (downed) raw *= c.downedDamage.get();

        pendingImpact = zhit ? ImpactPacket.HEAVY : impactOf(m.impact);
        pendingUnblockable = m.unblockable;
        outcomeImpact = -1;
        outcomeDealt = 0;
        v.invulnerableTime = 0;
        boolean struck = v.hurt(ModDamageTypes.strike(a.level(), a), (float) raw);
        int outcome = outcomeImpact;
        pendingImpact = -1;
        pendingUnblockable = false;
        if (!struck && outcome < 0) return;                                     // refused (PvP rules, invulnerable)
        f.landed = true;
        if (outcome == ImpactPacket.GUARD || outcome == ImpactPacket.PARRY) {  // blocked: a little pushback, no combo
            Vec3 push = away(a, v).scale(0.25);
            v.setDeltaMovement(v.getDeltaMovement().add(push));
            v.hurtMarked = true;
            return;
        }

        // ---- it landed
        g.comboHits++;
        g.comboFrom = a.getId();
        g.lastHitAt = now;
        g.lastBlow = raw;
        if (downed) g.downedHits++;
        if (g.move != null && g.moveTick >= g.move.armor) g.move = null;      // knocked out of their own move
        if (a instanceof Player p) ModCapabilities.get(p).ifPresent(d -> d.registerHit(now, c.comboWindowTicks.get(), c.comboMaxHits.get()));
        int stun = (int) Math.round(m.hitstun * stunScale * (zhit ? 1.6 : 1.0));
        if (!downed) stun(g, v, stun, now);
        if (downed) return;                                                     // the floored are not thrown about

        Vec3 dir = away(a, v);
        Vec3 vel;
        switch (m.launch) {
            case AWAY -> {
                vel = dir.scale(m.knockback * m.launchPower).add(0, 0.22 + m.lift, 0);
                g.flightUntil = now + 14;
                g.flightBy = a;
            }
            case UP -> {
                vel = dir.scale(0.12).add(0, 0.95 * m.launchPower, 0);
                g.juggled = true;
                g.juggleHits++;
                if (a instanceof Player p) ModCapabilities.get(p).ifPresent(d -> CombatMoves.launched(p, d, v, now));
            }
            case DOWN, SPIKE -> {
                vel = dir.scale(0.2).add(0, -1.5 * m.launchPower, 0);
                g.spiked = true;
                g.flightBy = a;
            }
            case KNOCKDOWN -> {
                vel = dir.scale(m.knockback).add(0, 0.18, 0);
                CombatMoves.knockDown(v, now, 30);
            }
            default -> {
                vel = dir.scale(m.knockback).add(0, m.lift, 0);
                if (!v.onGround()) {                                            // in the air: keep them up, a while
                    double keep = Math.max(0, 1.0 - (double) g.juggleHits / Math.max(1, c.juggleLimit.get()));
                    vel = new Vec3(vel.x, Math.max(vel.y, 0.3 * keep), vel.z);
                    g.juggled = true;
                    g.juggleHits++;
                }
            }
        }
        v.setDeltaMovement(vel);
        v.hurtMarked = true;
        v.hasImpulse = true;
        if (!a.onGround() && m.launch == Move.Launch.NONE) {                    // an air combo carries the attacker along
            a.setDeltaMovement(vel.scale(0.9));
            a.hurtMarked = true;
        }
    }

    static void stun(Fighter g, LivingEntity v, int ticks, long now) {
        if (ticks <= 0) return;
        g.stunUntil = Math.max(g.stunUntil, now + ticks);
        MobEffectInstance cur = v.getEffect(ModEffects.STUN.get());
        if (cur == null || cur.getDuration() < ticks) v.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0, false, false, true));
    }

    /** A slam into a wall or the ground: extra damage and a crater. */
    static void slam(Fighter g, LivingEntity by, double damage, long now) {
        LivingEntity v = g.entity;
        if (damage > 0 && v.isAlive()) {
            pendingImpact = ImpactPacket.SPIKE;
            v.invulnerableTime = 0;
            v.hurt(ModDamageTypes.strike(v.level(), by), (float) damage);
            pendingImpact = -1;
        }
        if (v.level() instanceof ServerLevel level) {
            ImpactPacket.at(v.position().add(0, 0.6, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.1f, 0xC0A070, by.getId()).send(level);
            level.playSound(null, v.getX(), v.getY(), v.getZ(), com.dbzenith.registry.ModSounds.PUNCH_HEAVY.get(), SoundSource.PLAYERS, 1f, 0.6f);
        }
    }

    /** Horizontal direction from the attacker to the victim (the attacker's facing if they overlap). */
    static Vec3 away(LivingEntity a, LivingEntity v) {
        Vec3 d = v.position().subtract(a.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = a.getLookAngle().multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    static int impactOf(String s) {
        return switch (s) {
            case "heavy" -> ImpactPacket.HEAVY;
            case "spike" -> ImpactPacket.SPIKE;
            default -> ImpactPacket.PUNCH;
        };
    }

    // ------------------------------------------------------------------ NPCs

    /**
     * An NPC's melee swing goes through the engine: the next light of its chain, and now and then a heavy to finish
     * (a launcher or a knock-away). Returns true if a move started.
     */
    public static boolean npcAttack(LivingEntity npc, LivingEntity target) {
        Fighter f = of(npc);
        long now = npc.level().getGameTime();
        boolean finisher = f.lastMove.startsWith("light_") && now <= f.chainUntil && npc.getRandom().nextInt(3) == 0;
        Move.Dir push = npc.getRandom().nextBoolean() ? Move.Dir.FORWARD : Move.Dir.NEUTRAL;
        boolean up = finisher && npc.getRandom().nextInt(3) == 0;
        npc.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        return press(npc, new Moves.Input(finisher ? Move.Button.HEAVY : Move.Button.LIGHT, push, up, false, npc.onGround()));
    }

    /** Dev and tests: what a fighter is doing. */
    public static String describe(LivingEntity e) {
        Fighter f = peek(e);
        if (f == null) return "idle";
        long now = e.level().getGameTime();
        return f.state(now).name().toLowerCase() + (f.move != null ? " " + f.move.id + " t" + f.moveTick : "") + (f.comboHits > 0 ? " combo " + f.comboHits : "");
    }

    static double lerp(double a, double b, double t) {
        return Mth.lerp(t, a, b);
    }
}
