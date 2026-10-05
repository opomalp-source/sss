package com.dbzenith.skill;

import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.Comparator;
import java.util.List;

/** Non-projectile technique effects (style SELF) and special on-hit effects. Server side only. */
public final class TechniqueEffects {
    private TechniqueEffects() {}

    /** Applies a SELF technique. Returns false if it had no valid use (nothing is spent then). */
    public static boolean apply(ServerPlayer player, PlayerData data, Technique t) {
        ServerLevel level = player.serverLevel();
        double power = t.effectPower();
        switch (t.effect()) {
            case HEAL_SELF -> {
                data.setBody(data.getBody() + data.getDerived().maxBody() * power);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.8, 0.4, 0.1);
                return true;
            }
            case HEAL_ALLY -> {
                Player target = lookedAtPlayer(player, 6).orElse(player);
                PlayerData td = ModCapabilities.get(target).orElse(null);
                if (td == null) return false;
                td.setBody(td.getBody() + td.getDerived().maxBody() * power);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.8, 0.4, 0.1);
                return true;
            }
            case BLIND_AREA -> {
                Vec3 look = player.getLookAngle();
                for (LivingEntity e : around(player, power)) {
                    Vec3 to = e.position().subtract(player.position()).normalize();
                    if (to.dot(look) < 0.2) continue; // must be in front
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                    if (e instanceof Mob mob) mob.setTarget(null);
                }
                level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 3, 0.2, 0.2, 0.2, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 2.0f);
                return true;
            }
            case LIFE_DRAIN -> {                                    // a bite: the closest foe in front loses life, half comes back as body
                Vec3 look = player.getLookAngle();
                LivingEntity prey = around(player, power).stream()
                        .filter(e -> e.position().subtract(player.position()).normalize().dot(look) > 0.4)
                        .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
                if (prey == null) return false;
                double damage = DamageCalculator.kiOutgoing(data, t.damageMult());
                prey.invulnerableTime = 0;
                prey.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
                data.setBody(data.getBody() + damage * 0.5);
                level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.75f, 0.05f, 0.1f), 1.3f),
                        prey.getX(), prey.getY() + prey.getBbHeight() * 0.7, prey.getZ(), 24, 0.3, 0.4, 0.3, 0.05);
                level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1f, 0.6f);
                return true;
            }
            case EXPLOSIVE_WAVE -> {
                double damage = DamageCalculator.kiOutgoing(data, t.damageMult());
                for (LivingEntity e : around(player, power)) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
                    Vec3 push = e.position().subtract(player.position()).normalize().scale(1.2);
                    KiTraits.onHit(player, player, t.kiType(), t.flags(), e, damage, push, null);
                    e.push(push.x, 0.4, push.z);
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 0.8f);
                return true;
            }
            case TELEPORT -> {
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getLookAngle().scale(power));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 dest = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.8));
                dest = dest.subtract(0, player.getEyeHeight(), 0);
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
                player.teleportTo(dest.x, Math.max(dest.y, level.getMinBuildHeight()), dest.z);
                player.fallDistance = 0;
                level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f);
                return true;
            }
            case KI_SENSE -> {
                boolean divine = data.hasFlag("god_ki");
                List<LivingEntity> sensed = around(player, power).stream()
                        .filter(e -> divine || !(e instanceof Player p && ModCapabilities.get(p).map(pd -> pd.hasFlag("god_ki")).orElse(false)))
                        .toList(); // only god ki senses god ki
                StringBuilder sb = new StringBuilder();
                sensed.stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .sorted(Comparator.comparingLong(p -> -ModCapabilities.get(p).map(StatCalculator::battlePower).orElse(0L)))
                        .limit(3)
                        .forEach(p -> sb.append(p.getGameProfile().getName()).append(' ')
                                .append(String.format("%,d", ModCapabilities.get(p).map(StatCalculator::battlePower).orElse(0L))).append("   "));
                for (LivingEntity e : sensed) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
                player.displayClientMessage(Component.translatable("message.dbzenith.ki_sense", sensed.size(), sb.toString().trim()), false);
                return true;
            }
            case ENERGY_ABSORB -> {
                data.setAbsorbUntil(level.getGameTime() + (long) power);
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.5);
                return true;
            }
            case KI_TRANSFER -> {
                Player target = lookedAtPlayer(player, power).orElse(null);
                PlayerData td = target == null ? null : ModCapabilities.get(target).orElse(null);
                if (td == null) {
                    player.displayClientMessage(Component.translatable("message.dbzenith.no_transfer_target"), true);
                    return false;
                }
                double rate = Math.max(20.0, data.getDerived().kiTransfer() * com.dbzenith.config.DBZConfig.SERVER.kiTransferSeconds.get());
                double amount = Math.min(Math.min(rate, data.getKi()), td.getDerived().maxKi() - td.getKi());
                if (amount <= 0) return false;
                data.setKi(data.getKi() - amount);
                td.setKi(td.getKi() + amount);
                Vec3 from = player.getEyePosition();
                Vec3 step = target.getEyePosition().subtract(from).scale(1 / 8.0);
                for (int i = 1; i < 8; i++) {
                    Vec3 p = from.add(step.scale(i));
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y - 0.3, p.z, 2, 0.05, 0.05, 0.05, 0.01);
                }
                target.displayClientMessage(Component.translatable("message.dbzenith.ki_received", (int) amount, player.getDisplayName()), true);
                return true;
            }
            case STUN_AREA -> {
                Vec3 look = player.getLookAngle();
                for (LivingEntity e : around(player, 6)) {
                    Vec3 to = e.position().subtract(player.position()).normalize();
                    if (to.dot(look) < 0.3) continue;
                    e.addEffect(new MobEffectInstance(com.dbzenith.registry.ModEffects.STUN.get(), (int) power, 0));
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + look.x * 2, player.getEyeY(), player.getZ() + look.z * 2,
                        40, 1.5, 0.8, 1.5, 0.2);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.3f, 2.0f);
                return true; // a miss still spends the ki
            }
            case GRAB -> {
                return GrabThrow.use(player, data, power);
            }
            case FUSE -> {
                return com.dbzenith.race.Absorption.fuse(player, data, lookedAtLiving(player, power));
            }
            case ABSORB -> {
                return com.dbzenith.race.Absorption.absorb(player, data, lookedAtLiving(player, power));
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Candy beam hit: turns a weak non-boss, non-player creature into sweets. Returns true if it did
     * (the projectile then deals no damage).
     */
    public static boolean candy(Entity target, double maxHealth) {
        if (!(target instanceof Mob mob) || target instanceof Player) return false;
        if (mob.getType().is(Tags.EntityTypes.BOSSES) || mob.getType().is(EntityTypeTags.RAIDERS) || mob.getHealth() > maxHealth) return false;
        ServerLevel level = (ServerLevel) mob.level();
        int n = 1 + level.random.nextInt(3);
        level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(Items.COOKIE, n)));
        level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1, mob.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 1.2f);
        mob.discard();
        return true;
    }

    public static List<LivingEntity> around(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(player) <= radius * radius);
    }

    public static LivingEntity lookedAtLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> e != player && e.isAlive() && e.getBoundingBox().inflate(0.4).clip(eye, end).isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    private static java.util.Optional<Player> lookedAtPlayer(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(range),
                        p -> p != player && p.isAlive() && p.getBoundingBox().inflate(0.5).clip(eye, eye.add(look.scale(range))).isPresent())
                .stream().findFirst();
    }
}
