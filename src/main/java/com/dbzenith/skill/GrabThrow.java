package com.dbzenith.skill;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.BossFighter;
import com.dbzenith.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grab and Throw. First use grabs the creature or player you are looking at (it is stunned and held in front of
 * you); the second use, or running out of hold time, throws it where you look. It takes impact damage when it hits
 * the ground or a wall. Bosses cannot be grabbed.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GrabThrow {
    private record Hold(ServerPlayer holder, LivingEntity target, long until) {}

    private record Flight(LivingEntity target, ServerPlayer thrower, double damage, long started) {}

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private GrabThrow() {}

    static boolean use(ServerPlayer player, PlayerData data, double range) {
        Hold held = HOLDS.remove(player.getUUID());
        if (held != null) {
            throwTarget(held, data);
            return true;
        }
        LivingEntity target = lookedAt(player, range);
        if (target != null && !com.dbzenith.combat.PvpRules.mayAffect(player, target)) return false;   // PvP mode (CX-19)
        if (target == null || !grabbable(target)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.nothing_to_grab"), true);
            return false;
        }
        int hold = DBZConfig.SERVER.grabHoldTicks.get();
        HOLDS.put(player.getUUID(), new Hold(player, target, player.level().getGameTime() + hold));
        target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), hold + 10, 0));
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.7f);
        return true;
    }

    public static boolean isHolding(ServerPlayer player) {
        return HOLDS.containsKey(player.getUUID());
    }

    public static boolean grabbable(LivingEntity e) {
        return !(e instanceof BossFighter) && !e.getType().is(Tags.EntityTypes.BOSSES)
                && e.getBbWidth() <= 1.6f && e.getBbHeight() <= 3.0f && !e.isSpectator();
    }

    private static void throwTarget(Hold hold, PlayerData data) {
        LivingEntity target = hold.target;
        ServerPlayer player = hold.holder;
        if (!target.isAlive()) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        target.removeEffect(ModEffects.STUN.get());
        target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 15, 0)); // tumbling
        Vec3 v = player.getLookAngle().scale(c.throwSpeed.get()).add(0, 0.35, 0);
        target.setDeltaMovement(v);
        target.hurtMarked = true;
        target.fallDistance = 0;
        double damage = DamageCalculator.meleeOutgoing(data, 0, 1) * c.throwDamageMultiplier.get();
        FLIGHTS.add(new Flight(target, player, damage, player.level().getGameTime()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.6f);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Iterator<Hold> it = HOLDS.values().iterator(); it.hasNext(); ) {
            Hold h = it.next();
            ServerPlayer p = h.holder;
            LivingEntity t = h.target;
            if (p.isRemoved() || !p.isAlive() || !t.isAlive() || t.level() != p.level() || t.distanceToSqr(p) > 64) {
                t.removeEffect(ModEffects.STUN.get());
                it.remove();
                continue;
            }
            if (p.level().getGameTime() >= h.until) {
                it.remove();
                com.dbzenith.data.ModCapabilities.get(p).ifPresent(d -> throwTarget(h, d));
                continue;
            }
            Vec3 pos = p.getEyePosition().add(p.getLookAngle().scale(1.2 + t.getBbWidth())).subtract(0, t.getBbHeight() / 2.0, 0);
            if (t instanceof ServerPlayer sp) sp.teleportTo(pos.x, pos.y, pos.z);
            else t.setPos(pos.x, pos.y, pos.z);
            t.setDeltaMovement(Vec3.ZERO);
            t.fallDistance = 0;
        }
        for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            LivingEntity t = f.target;
            long age = t.level().getGameTime() - f.started;
            if (!t.isAlive() || age > 60) {
                it.remove();
                continue;
            }
            if (age >= 3 && (t.horizontalCollision || t.onGround() || t.verticalCollision)) {
                it.remove();
                t.invulnerableTime = 0;
                t.hurt(ModDamageTypes.thrown(t.level(), f.thrower), (float) f.damage);
                if (t.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.5, t.getZ(), 2, 0.3, 0.2, 0.3, 0);
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.4f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        FLIGHTS.clear();
    }

    private static LivingEntity lookedAt(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> e != player && e.isAlive() && e.getBoundingBox().inflate(0.4).clip(eye, end).isPresent())
                .stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }
}
