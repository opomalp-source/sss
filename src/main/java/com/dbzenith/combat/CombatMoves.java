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
    public static final int MAX_CHASES = 3;
    static final int ZHIT_WINDOW = 10, ZHIT_STUN = 20, CHASE_WINDOW = 30, COUNTER_WINDOW = 8,
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

    /**
     * A blow from the combat engine sent a foe flying (CX-19): the Dash key may chase it (until phase 3 replaces the
     * dash moves too).
     */
    public static void launched(Player attacker, PlayerData ad, LivingEntity victim, long now) {
        CombatState a = ad.combat();
        if (isDowned(victim, now)) return;
        if (a.launchTargetId != victim.getId() || now - a.launchTick > 60) a.chaseCount = 0;
        a.launchTargetId = victim.getId();
        a.launchTick = now;
        if (a.chaseCount < MAX_CHASES) a.chaseReadyUntil = now + CHASE_WINDOW;
        ad.markDirty();
        CombatState v = state(victim);
        if (v != null) v.launchedAt = now;
    }

    /** Floor a fighter for {@code ticks}: stunned on the ground, half damage, no knockback, until they get up. */
    public static void knockDown(LivingEntity e, long now, int ticks) {
        DOWNED.put(e, now + ticks);
        e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, 0));
        CombatState s = state(e);
        if (s != null) {
            s.downedUntil = now + ticks;
            s.downedFlag = true;
            if (e instanceof Player p) ModCapabilities.get(p).ifPresent(PlayerData::markDirty);
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

    public static void getUp(LivingEntity e) {
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

    // ------------------------------------------------------------------ ticking and events

    /** Per player tick (KiTicker): breaker charges come back one at a time. */
    public static void tick(ServerPlayer player, PlayerData d, long now) {
        CombatState s = d.combat();
        if (s.breakerCharges < 2 && now >= s.breakerRechargeAt) {
            s.breakerCharges++;
            s.breakerRechargeAt = s.breakerCharges < 2 ? now + DBZConfig.SERVER.burstRechargeTicks.get() : Long.MIN_VALUE / 2;
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
