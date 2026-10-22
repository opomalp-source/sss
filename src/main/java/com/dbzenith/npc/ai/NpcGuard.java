package com.dbzenith.npc.ai;

import com.dbzenith.combat.engine.Evasion;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * An NPC's guard, by the players' rules ({@code GuardRules}, the same config): it covers blows from the front only;
 * raised just before a blow it parries (no damage, the attacker staggers, the counter opens); otherwise it blocks
 * (a little gets through) and the meter drains by how hard the blow was, half as much again for ki; an empty meter
 * breaks it (stunned, and no guard for a while). Sweeps ignore it. Called from {@code CombatEvents}.
 */
public final class NpcGuard {
    public enum Outcome { NONE, PARRY, BLOCK, BREAK }

    /** What a block lets through. */
    public static final double CHIP = 0.15;

    private NpcGuard() {}

    static boolean raise(NpcResources r, long now) {
        if (now < r.guardLockUntil || r.guard <= 0) return false;
        if (!r.guarding) r.guardStartTick = now;
        r.guarding = true;
        return true;
    }

    static void lower(NpcResources r) {
        r.guarding = false;
    }

    /**
     * A blow reached a guarding NPC. {@code share} is the share of its lasting health the blow would take;
     * {@code melee} fists and kicks (only those parry), {@code unblockable} a sweep.
     */
    public static Outcome onHit(KiFighter victim, NpcResources r, Entity attacker, double share, boolean melee, boolean ki, boolean unblockable, long now) {
        if (!r.guarding || attacker == null || unblockable || !Evasion.guardCovers(victim, attacker)) return Outcome.NONE;
        DBZConfig.Server c = DBZConfig.SERVER;
        r.lastGuardHit = now;
        if (melee && now - r.guardStartTick <= c.parryWindowTicks.get()) {
            r.guardStartTick = Long.MIN_VALUE / 2;                                // one parry per raise
            r.guard = Math.min(100, r.guard + 10);
            if (attacker instanceof LivingEntity foe) {
                foe.addEffect(new MobEffectInstance(ModEffects.STUN.get(), c.parryStunTicks.get(), 0));
                double dx = foe.getX() - victim.getX(), dz = foe.getZ() - victim.getZ();
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                foe.knockback(0.9, -dx / len, -dz / len);
            }
            Evasion.onPerfectGuard(victim, attacker, now);
            return Outcome.PARRY;
        }
        double cost = c.guardHitCost.get() + 100 * share * (1 - CHIP) / c.guardBreakBodyFraction.get();
        if (ki) cost *= 1.5;
        r.guard -= cost;
        if (r.guard > 0) return Outcome.BLOCK;
        r.guard = 0;
        r.guarding = false;
        r.guardLockUntil = now + c.guardBreakLockTicks.get();
        victim.addEffect(new MobEffectInstance(ModEffects.STUN.get(), c.guardBreakStunTicks.get(), 0));
        if (attacker instanceof ServerPlayer ap) com.dbzenith.network.CalloutPacket.send(ap, "message.dbzenith.guard_crushed", 0xAEE6FF);
        com.dbzenith.combat.CombatLog.near(victim, Component.translatable("log.dbzenith.guard_break", attacker.getDisplayName(), victim.getDisplayName()));
        return Outcome.BREAK;
    }
}
