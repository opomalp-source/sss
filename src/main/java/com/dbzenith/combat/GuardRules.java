package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Guarding with a meter. Raising the guard just before a blow lands parries it (no damage, the attacker staggers);
 * otherwise blocked hits empty the meter by how hard they were, and an empty meter breaks the guard: you are
 * stunned briefly and cannot guard again for a moment. The meter refills while you are not guarding.
 */
public final class GuardRules {
    public enum Outcome { NONE, PARRY, BLOCK, BREAK }

    private GuardRules() {}

    /** Guard key pressed. Refused while locked out after a break, or with nothing left in the meter. */
    public static boolean raise(PlayerData d, long now) {
        if (now < d.getGuardLockUntil() || d.getGuardMeter() <= 0) return false;
        if (!d.isGuarding()) d.setGuardStartTick(now);
        d.setGuarding(true);
        return true;
    }

    public static void lower(PlayerData d) {
        d.setGuarding(false);
    }

    /** Whether the guard went up recently enough to parry ({@code window} ticks). One parry per raise. */
    public static boolean inWindow(PlayerData d, long now, int window) {
        return d.isGuarding() && now - d.getGuardStartTick() <= window;
    }

    /**
     * A blow reached a guarding defender. {@code blocked} is the body damage the guard took off; {@code melee}
     * marks fists and kicks (only those can be parried; ki is deflected elsewhere). Applies meter, break and parry.
     */
    public static Outcome onHit(LivingEntity victim, PlayerData d, Entity attacker, double blocked, boolean melee, boolean ki, long now) {
        if (!d.isGuarding()) return Outcome.NONE;
        DBZConfig.Server c = DBZConfig.SERVER;
        d.setLastGuardHitTick(now);
        if (melee && inWindow(d, now, c.parryWindowTicks.get())) {
            d.setGuardStartTick(Long.MIN_VALUE / 2);          // used up
            d.setGuardMeter(d.getGuardMeter() + 10);
            if (attacker instanceof LivingEntity foe) {
                foe.addEffect(new MobEffectInstance(ModEffects.STUN.get(), c.parryStunTicks.get(), 0));
                double dx = foe.getX() - victim.getX(), dz = foe.getZ() - victim.getZ();
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                foe.knockback(0.9, -dx / len, -dz / len);
            }
            com.dbzenith.combat.engine.Evasion.onPerfectGuard(victim, attacker, now);   // a perfect guard: counter (CX-19)
            return Outcome.PARRY;
        }
        double maxBody = Math.max(1, d.getDerived().maxBody());
        double cost = c.guardHitCost.get() + 100 * blocked / (maxBody * c.guardBreakBodyFraction.get());
        if (ki) cost *= 1.5;                                 // ki is harder to hold back
        d.setGuardMeter(d.getGuardMeter() - cost);
        if (d.getGuardMeter() > 0) return Outcome.BLOCK;
        d.setGuarding(false);
        d.setGuardLockUntil(now + c.guardBreakLockTicks.get());
        victim.addEffect(new MobEffectInstance(ModEffects.STUN.get(), c.guardBreakStunTicks.get(), 0));
        if (victim instanceof net.minecraft.server.level.ServerPlayer sp) com.dbzenith.network.CalloutPacket.send(sp, "message.dbzenith.guard_broken", 0xFF6A5A);
        if (attacker instanceof net.minecraft.server.level.ServerPlayer ap) com.dbzenith.network.CalloutPacket.send(ap, "message.dbzenith.guard_crushed", 0xAEE6FF);
        return Outcome.BREAK;
    }

    /** Per tick: the meter refills a while after the last blocked hit, never while guarding. */
    public static void tick(PlayerData d, long now) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (d.isGuarding() || d.getGuardMeter() >= 100 || now - d.getLastGuardHitTick() < c.guardRegenDelayTicks.get()) return;
        d.setGuardMeter(d.getGuardMeter() + c.guardRegenPerSecond.get() / 20.0);
    }
}
