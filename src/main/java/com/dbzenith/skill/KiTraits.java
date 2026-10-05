package com.dbzenith.skill;

import com.dbzenith.combat.BodyHealth;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Set;

/**
 * What a ki attack does on a hit beyond its damage: its ki type (burning, freezing, shock, corrosive, draining,
 * divine) and the hit modifiers (stun, knockback, guard break, chain). Shared by blasts, beams and novas.
 */
public final class KiTraits {
    private KiTraits() {}

    /** Damage multiplier a ki type carries (divine ki cuts deeper). */
    public static double damageFactor(Technique.KiType type) {
        return type == Technique.KiType.DIVINE ? 1.10 : type == Technique.KiType.PURE ? 1.05 : 1.0;
    }

    /**
     * After {@code damage} raw DBZ damage hit {@code target}. {@code once} holds the ids already given the once-per-attack
     * effects (a beam pulses many times; its stun and chain should not).
     */
    public static void onHit(Entity source, Entity owner, Technique.KiType type, int flags, Entity target, double damage, Vec3 motion, Set<Integer> once) {
        if (!(target instanceof LivingEntity living) || !(target.level() instanceof ServerLevel level)) return;
        boolean first = once == null || once.add(target.getId());
        switch (type) {
            case BURNING -> living.setSecondsOnFire(3);
            case FREEZING -> {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + 60, living.getTicksFrozen() + 60));
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            }
            case SHOCK -> {
                if (first && level.random.nextFloat() < 0.3f) living.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 8, 0));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
            }
            case CORROSIVE -> {
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                living.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0));
            }
            case DRAINING -> {
                if (owner instanceof Player p) ModCapabilities.get(p).ifPresent(d -> {
                    d.setBody(Math.min(d.getDerived().maxBody(), d.getBody() + damage * 0.15));
                    BodyHealth.mirror(p, d);
                });
                level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.1f, 0.2f), 1f), target.getX(), target.getY() + 1, target.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            }
            case DIVINE -> level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
            default -> { }
        }
        if (!first) return;
        if ((flags & Technique.STUN) != 0) living.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 10, 0));
        if ((flags & Technique.KNOCKBACK) != 0 && motion.lengthSqr() > 1e-6) {
            Vec3 push = motion.normalize().scale(1.6);
            living.push(push.x, 0.35, push.z);
            living.hurtMarked = true;
        }
        if ((flags & Technique.GUARD_BREAK) != 0 && target instanceof Player p) {
            ModCapabilities.get(p).ifPresent(d -> {
                if (d.isGuarding()) d.setGuardMeter(d.getGuardMeter() - 35);
            });
        }
        if ((flags & Technique.CHAIN) != 0) chain(source, owner, living, damage * 0.5, once);
    }

    /** Ki leaps from the struck to up to two more within six blocks. */
    static void chain(Entity source, Entity owner, LivingEntity from, double damage, Set<Integer> once) {
        ServerLevel level = (ServerLevel) from.level();
        int jumps = 0;
        for (LivingEntity next : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(6),
                e -> e != from && e != owner && e.isAlive() && !e.isSpectator() && (once == null || !once.contains(e.getId())))) {
            if (jumps++ >= 2) break;
            if (once != null) once.add(next.getId());
            next.invulnerableTime = 0;
            next.hurt(ModDamageTypes.kiBlast(level, source, owner), (float) damage);
            Vec3 a = from.getBoundingBox().getCenter(), b = next.getBoundingBox().getCenter();
            for (int i = 0; i <= 10; i++) {
                Vec3 p = a.lerp(b, i / 10.0).add((level.random.nextDouble() - 0.5) * 0.3, (level.random.nextDouble() - 0.5) * 0.3, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (jumps > 0) level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4f, 1.8f);
    }
}
