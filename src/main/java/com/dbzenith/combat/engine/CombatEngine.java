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
    public static boolean outcomeGuarded;
    /** The blow under way: its impact flags (critical, counter, Z-hit) and the move's own hitstop (-1: by the impact). */
    public static int pendingFlags, pendingHitstop = -1;

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
        if (!com.dbzenith.combat.PvpRules.combatOn(e)) return false;          // PvP off: no combat moves (CX-20)
        Fighter f = of(e);
        long now = e.level().getGameTime();
        if (now == f.lastPressTick && f.move != null && f.moveTick == 0) return false;   // one new move per tick
        f.lastPressTick = now;
        if (!mayAct(e)) return false;
        Fighter.State st = f.state(now);
        if (st == Fighter.State.STUNNED || st == Fighter.State.LAUNCHED || st == Fighter.State.KNOCKDOWN || st == Fighter.State.DEAD) return false;
        Move counter = Evasion.counter(f, now);                                 // right after a vanish or a perfect guard
        if (counter != null) {
            start(f, counter, now);
            return true;
        }
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
        if (e instanceof ServerPlayer sp) ModNetwork.sendToTrackingAndSelf(sp, new com.dbzenith.network.MoveAnimPacket(sp.getId(), m.anim, m.id));
        else {                                                                 // NPCs play the move's clip too (phase 5)
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> e),
                    new com.dbzenith.network.MoveAnimPacket(e.getId(), m.anim, m.id));
            e.swing(InteractionHand.MAIN_HAND, true);
        }
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), com.dbzenith.registry.ModSounds.WHOOSH.get(), SoundSource.PLAYERS,
                m.button == Move.Button.HEAVY ? 0.7f : 0.45f, m.button == Move.Button.HEAVY ? 0.8f : 1.2f + e.getRandom().nextFloat() * 0.2f);
    }

    /** Players throw blows through the engine, not vanilla's punch: a bare-handed vanilla attack does nothing. */
    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().isEmpty() && Moves.loaded() && com.dbzenith.combat.PvpRules.combatOn(event.getEntity())) event.setCanceled(true);   // PvP off: a plain punch (CX-20)
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
        if (f.freezeUntil > 0) {                                                // hitstop
            if (now < f.freezeUntil) {
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
                e.fallDistance = 0;
                syncState(f, now);
                return;
            }
            f.freezeUntil = 0;
            if (f.heldVelocity != null) push(e, f.heldVelocity);
            f.heldVelocity = null;
        }
        if (f.superDashTarget != null) Evasion.tickSuperDash(f, now);
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
            if (e.level() instanceof ServerLevel sl) {                             // the crater (CX-20)
                Vec3 dir = f.flightDir.lengthSqr() < 1e-4 ? e.getLookAngle().multiply(-1, 0, -1).normalize() : f.flightDir;
                Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0).add(dir.scale(e.getBbWidth() / 2 + 0.45));
                com.dbzenith.combat.Destruction.slam(sl, at, dir.scale(-1), impactForce(f), e, f.flightBy);
            }
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
            stun(f, e, 20, now);
        }
        if (now < f.flightUntil && e.onGround() && f.flightVy < -0.55 && f.flightBy != null && !f.spiked) {   // a hard landing (CX-20)
            f.flightUntil = 0;
            if (e.level() instanceof ServerLevel sl) {
                double hit = f.flightForce * Mth.clamp(-f.flightVy / 1.2, 0.35, 1.0);
                com.dbzenith.combat.Destruction.slam(sl, e.position(), new Vec3(0, 1, 0), hit, e, f.flightBy);
            }
        }
        if (f.spiked && e.onGround()) {                                         // driven into the ground
            f.spiked = false;
            if (e.level() instanceof ServerLevel sl && f.flightBy != null) {    // the crater (CX-20)
                double hit = Math.max(f.flightForce, 0.8) * Mth.clamp(-f.flightVy / 1.0, 0.5, 1.2);
                com.dbzenith.combat.Destruction.slam(sl, e.position(), new Vec3(0, 1, 0), hit, e, f.flightBy);
            }
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
        if (now < f.flightUntil || f.spiked) {                                  // in flight: the speed it would strike with (CX-20)
            Vec3 dv = e.getDeltaMovement();
            if (now >= f.freezeUntil) {
                f.flightSpeed = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
                f.flightVy = dv.y;
            }
        }
        boolean downed = CombatMoves.isDowned(e, now);
        if (f.wasDowned && !downed) {                                           // up again: a moment's grace
            f.wakeUntil = now + c.wakeUpGraceTicks.get();
            f.downedHits = 0;
            endCombo(f);
            f.comboHits = 0;
        }
        f.wasDowned = downed;
        if (f.comboHits > 0 && now - f.lastHitAt > c.comboResetTicks.get() && !f.juggled && !downed) {
            endCombo(f);
            f.comboHits = 0;
            f.juggleHits = 0;
            f.comboFrom = -1;
        }
        syncState(f, now);
    }

    /**
     * Tells the clients tracking a fighter when its stance changes (phase 5): stunned, launched, knocked down, guarding,
     * or none of those. Attacking, dodging and charging show through their own animations, so they count as idle here.
     */
    static void syncState(Fighter f, long now) {
        Fighter.State s = f.state(now);
        if (s != Fighter.State.STUNNED && s != Fighter.State.LAUNCHED && s != Fighter.State.KNOCKDOWN && s != Fighter.State.GUARDING) s = Fighter.State.IDLE;
        if (s == f.sentState) return;
        f.sentState = s;
        var packet = new com.dbzenith.network.FighterStatePacket(f.entity.getId(), s.ordinal());
        if (f.entity instanceof ServerPlayer sp) ModNetwork.sendToTrackingAndSelf(sp, packet);
        else ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> f.entity), packet);
    }

    // ------------------------------------------------------------------ hits

    /** Every foe in the move's hitbox, nearest first, up to its target count, each once per move. */
    static void detect(Fighter f, Move m, long now) {
        LivingEntity a = f.entity;
        Vec3 eye = a.getEyePosition(), look = a.getLookAngle();
        int rewind = LagComp.rewindTicks(a);                                   // where a player attacker saw them (phase 7)
        double reach = m.range + m.radius + 1.5 + (rewind > 0 ? 3 : 0);        // they may have moved off since
        AABB box = a.getBoundingBox().inflate(reach);
        SCRATCH.clear();
        for (LivingEntity v : a.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (v == a || !v.isAlive() || v.isSpectator() || v instanceof ArmorStand || f.struck.contains(v.getId())) continue;
            if (a.isPassengerOfSameVehicle(v) || v.isAlliedTo(a)) continue;
            if (!Hitbox.contains(m, eye, look, a.getYRot(), v.getBoundingBox())) {
                AABB then = rewind > 0 ? LagComp.boxAt(v, rewind) : null;
                if (then == null || !Hitbox.contains(m, eye, look, a.getYRot(), then)) continue;
            }
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
        for (LivingEntity e : new LivingEntity[]{a, v}) {                      // a callout for the players in it (phase 8)
            if (e instanceof ServerPlayer sp) com.dbzenith.network.CalloutPacket.send(sp, "message.dbzenith.clash", 0xFFFFFF);
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
        if (Evasion.tryVanish(v, a, now)) return;                               // they dashed just in time
        boolean downed = CombatMoves.isDowned(v, now);
        if (downed && g.downedHits >= c.downedHitLimit.get()) return;          // no beating a floored foe forever
        if (g.comboFrom != a.getId() || now - g.lastHitAt > c.comboResetTicks.get()) {
            endCombo(g);
            g.comboHits = 0;
            g.juggleHits = 0;
        }
        int n = g.comboHits;
        double scale = Math.max(c.comboMinDamage.get(), 1.0 - c.comboDamageDecay.get() * n);
        double stunScale = Math.max(c.hitstunMin.get(), 1.0 - c.hitstunDecay.get() * n);
        double raw = base(a) * m.damage * scale;
        if (a instanceof Player fp) raw *= m.formMultiplier(ModCapabilities.get(fp).map(d -> d.getFormId()).orElse(""));   // a form's own bonus (phase 10)
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
        // a critical (phase 5): from the back, or catching the foe in the wind-up of their own move
        boolean behind = fromBehind(v, a);
        boolean punish = g.move != null && g.moveTick < g.move.startup && g.moveTick >= g.move.armor;
        boolean crit = !downed && (behind || punish);
        if (crit) raw *= behind ? c.critBehindBonus.get() : c.critPunishBonus.get();
        int flags = (crit ? ImpactPacket.CRIT : 0) | (zhit ? ImpactPacket.ZHIT : 0) | (m.id.equals("counter_strike") ? ImpactPacket.COUNTER : 0);

        pendingImpact = zhit ? ImpactPacket.HEAVY : impactOf(m.impact);
        pendingUnblockable = m.unblockable;
        pendingFlags = flags;
        pendingHitstop = m.hitstop;
        outcomeImpact = -1;
        outcomeDealt = 0;
        v.invulnerableTime = 0;
        boolean struck = v.hurt(ModDamageTypes.strike(a.level(), a), (float) raw);
        int outcome = outcomeImpact;
        pendingImpact = -1;
        pendingUnblockable = false;
        pendingFlags = 0;
        pendingHitstop = -1;
        if (outcome == -2) return;                                              // dodged (an afterimage, Ultra Instinct)
        if (!struck && outcome < 0) return;                                     // refused (PvP rules, invulnerable)
        f.landed = true;
        int hitstop = hitstopTicks(m.hitstop, outcome < 0 ? impactOf(m.impact) : outcome, flags);
        if (outcome == ImpactPacket.GUARD || outcome == ImpactPacket.PARRY) {  // blocked: a little pushback, no combo
            Vec3 push = away(a, v).scale(0.25);
            v.setDeltaMovement(v.getDeltaMovement().add(push));
            v.hurtMarked = true;
            freeze(f, g, hitstop, now, null, null);
            return;
        }

        // ---- it landed
        g.comboHits++;
        g.comboFrom = a.getId();
        g.lastHitAt = now;
        g.lastBlow = raw;
        g.comboDamage += outcomeDealt > 0 ? outcomeDealt : raw;                // for the combat log (phase 9)
        if (downed) g.downedHits++;
        if (g.move != null && g.moveTick >= g.move.armor) g.move = null;      // knocked out of their own move
        if (a instanceof Player p) ModCapabilities.get(p).ifPresent(d -> d.registerHit(now, c.comboWindowTicks.get(), c.comboMaxHits.get()));
        SpecialMeter.gain(a, m.button == Move.Button.HEAVY ? c.specialPerHeavy.get() : c.specialPerHit.get());
        SpecialMeter.gain(v, c.specialPerHitTaken.get());
        int stun = (int) Math.round(m.hitstun * stunScale * (zhit ? 1.6 : 1.0)) + hitstop;     // the freeze doesn't eat the stun
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
        // ---- long combos and hard blows send the victim flying; where they hit, they leave a crater (CX-20)
        double force = force(v, outcomeDealt > 0 ? outcomeDealt : raw, m.button == Move.Button.HEAVY, zhit || (flags & ImpactPacket.CRIT) != 0, g.comboHits);

        boolean blown = blowsAway(m.launch, g.comboHits, force);
        if (blown) {
            double speed = Math.max(m.launch == Move.Launch.AWAY ? m.knockback * m.launchPower : 0, 0.35 + c.blowAwaySpeed.get() * force);
            vel = dir.scale(speed).add(0, Math.max(vel.y, 0.18 + 0.05 * force), 0);
            g.flightUntil = now + 10 + (int) (8 * force);
            g.flightBy = a;
            g.juggled = false;
            launchedAway(g, force, speed, dir);
        } else if (m.launch == Move.Launch.DOWN || m.launch == Move.Launch.SPIKE) {
            g.flightForce = Math.max(1.0, force);
        }
        Vec3 carry = !a.onGround() && m.launch == Move.Launch.NONE && !blown ? vel.scale(0.9) : null;   // an air combo carries the attacker along
        if (hitstop > 0) {                                                      // both hang in the blow, then it lands
            freeze(f, g, hitstop, now, vel, carry);
            return;
        }
        v.setDeltaMovement(vel);
        v.hurtMarked = true;
        v.hasImpulse = true;
        if (carry != null) {
            a.setDeltaMovement(carry);
            a.hurtMarked = true;
        }
    }

    /** A blow from the back: the attacker within the 90° behind the victim (a critical). */
    static boolean fromBehind(LivingEntity victim, LivingEntity attacker) {
        Vec3 to = attacker.position().subtract(victim.position()).multiply(1, 0, 1);
        Vec3 look = victim.getLookAngle().multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4 || look.lengthSqr() < 1e-4) return false;
        return to.normalize().dot(look.normalize()) < -0.7071;
    }

    /** Hitstop ticks for a blow: the move's own, or by the kind of impact, times the config's scale (0 = off). */
    public static int hitstopTicks(int moveHitstop, int impact, int flags) {
        double scale;
        try {
            scale = DBZConfig.SERVER.hitstopScale.get();
        } catch (IllegalStateException e) {
            scale = 1;
        }
        int base = moveHitstop >= 0 ? moveHitstop + ImpactPacket.hitstopFor(ImpactPacket.PUNCH, flags) - 2 : ImpactPacket.hitstopFor(impact, flags);
        return (int) Math.round(Math.max(0, base) * scale);
    }

    /**
     * Freezes attacker and victim for {@code ticks} (hitstop): their moves wait, they hang where they are, and the
     * knockback ({@code victimVel}, and the attacker's {@code attackerVel}) is given when it ends.
     */
    static void freeze(Fighter f, Fighter g, int ticks, long now, Vec3 victimVel, Vec3 attackerVel) {
        if (ticks <= 0) {
            if (victimVel != null) push(g.entity, victimVel);
            if (attackerVel != null) push(f.entity, attackerVel);
            return;
        }
        f.freezeUntil = Math.max(f.freezeUntil, now + ticks);
        g.freezeUntil = Math.max(g.freezeUntil, now + ticks);
        if (victimVel != null) g.heldVelocity = victimVel;
        if (attackerVel != null) f.heldVelocity = attackerVel;
        if (g.flightUntil > now) g.flightUntil += ticks;                       // the wall-slam window starts when it flies
        for (LivingEntity e : new LivingEntity[]{f.entity, g.entity}) {
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
        }
    }

    static void push(LivingEntity e, Vec3 vel) {
        e.setDeltaMovement(vel);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    /**
     * A ki blast from {@link KiCombat} struck {@code v} (after the damage, whose outcome CombatEvents left in the
     * outcome fields): meter, hitstun and a push, unless it was dodged or guarded. Counts toward the combo.
     */
    public static void kiHit(LivingEntity a, LivingEntity v, boolean struck, int hitstun, double knockback, boolean heavy) {
        if (a == null || v == null || !v.isAlive() || outcomeImpact == -2 || outcomeGuarded || (!struck && outcomeDealt <= 0)) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        long now = v.level().getGameTime();
        Fighter g = of(v);
        if (now < g.wakeUntil || CombatMoves.isDowned(v, now)) return;
        if (g.comboFrom != a.getId() || now - g.lastHitAt > c.comboResetTicks.get()) {
            endCombo(g);
            g.comboHits = 0;
            g.juggleHits = 0;
        }
        double stunScale = Math.max(c.hitstunMin.get(), 1.0 - c.hitstunDecay.get() * g.comboHits);
        g.comboHits++;
        g.comboFrom = a.getId();
        g.lastHitAt = now;
        g.comboDamage += outcomeDealt;
        SpecialMeter.gain(a, heavy ? c.specialPerHeavy.get() : c.specialPerHit.get());
        SpecialMeter.gain(v, c.specialPerHitTaken.get());
        if (g.move != null && g.moveTick >= g.move.armor) g.move = null;       // knocked out of their own move
        stun(g, v, (int) Math.round(hitstun * stunScale), now);
        if (knockback > 0) {
            Vec3 push = away(a, v).scale(knockback).add(0, heavy ? 0.2 : 0.02, 0);
            v.setDeltaMovement(v.onGround() || heavy ? push : v.getDeltaMovement().scale(0.4).add(push.x, Math.max(push.y, 0.08), push.z));
            v.hurtMarked = true;
            if (heavy) {                                                        // a charged blast throws them (wall slams)
                g.flightUntil = now + 12;
                g.flightBy = a;
                launchedAway(g, Mth.clamp(0.6 + knockback * 1.2, 0.6, 2.5), knockback, away(a, v));   // its crater (CX-20)
            }
        }
    }


    /** A combo on {@code g} is over: a long one goes in the combat log (phase 9). */
    static void endCombo(Fighter g) {
        if (g.comboHits >= com.dbzenith.combat.CombatLog.BIG_COMBO && g.comboFrom >= 0
                && g.entity.level().getEntity(g.comboFrom) instanceof LivingEntity a) {
            com.dbzenith.combat.CombatLog.combo(a, g.entity, g.comboHits, g.comboDamage);
        }
        g.comboDamage = 0;
    }
    static void stun(Fighter g, LivingEntity v, int ticks, long now) {
        if (ticks <= 0) return;
        g.stunUntil = Math.max(g.stunUntil, now + ticks);
        MobEffectInstance cur = v.getEffect(ModEffects.STUN.get());
        if (cur == null || cur.getDuration() < ticks) v.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0, false, false, true));
    }

    /**
     * How hard a blow lands (CX-20), about 0..3: mostly the share of the victim's health it took (its square root, so
     * small blows still count), more for a heavy, a critical or a Z-hit, and a little for the combo behind it.
     */
    public static double force(LivingEntity v, double dealt, boolean heavy, boolean crit, int comboHits) {
        double share;
        if (v instanceof Player p) {
            double max = ModCapabilities.get(p).map(d -> d.getDerived().maxBody()).orElse((double) v.getMaxHealth());
            share = dealt / Math.max(1, max);
        } else {
            share = com.dbzenith.combat.DamageCalculator.toVanilla(dealt) / Math.max(1f, v.getMaxHealth());
        }
        double f = (heavy ? 0.7 : 0.25) + 3.2 * Math.sqrt(Mth.clamp(share, 0, 1)) + 0.06 * Math.min(comboHits, 10) + (crit ? 0.3 : 0);
        return Mth.clamp(f, 0, 3);
    }

    /** Whether a landed blow sends the victim flying (CX-20): knock-away moves, every Nth hit of a combo, a hard enough blow. */
    public static boolean blowsAway(Move.Launch launch, int comboHits, double force) {
        if (launch == Move.Launch.AWAY) return true;
        if (launch != Move.Launch.NONE) return false;
        int every = DBZConfig.SERVER.blowAwayComboHits.get();
        return (every > 0 && comboHits > 0 && comboHits % every == 0) || force >= DBZConfig.SERVER.blowAwayForce.get();
    }

    /** Sent flying: remembered for the crater where it lands. */
    static void launchedAway(Fighter g, double force, double speed, Vec3 dir) {
        g.flightForce = force;
        g.launchSpeed = Math.max(0.1, speed);
        g.flightDir = dir;
        g.flightSpeed = speed;
        g.flightVy = 0;
    }

    /** The force a flying fighter strikes with: the launch's, by how much of its speed is left. */
    static double impactForce(Fighter g) {
        return g.flightForce * Mth.clamp(g.flightSpeed / Math.max(0.1, g.launchSpeed), 0.35, 1.0);
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
