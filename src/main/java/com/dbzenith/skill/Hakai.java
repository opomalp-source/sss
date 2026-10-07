package com.dbzenith.skill;

import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.joml.Vector3f;

/**
 * Hakai, the destruction of a God of Destruction (CX-17b). Beerus teaches it. Open your hand at what you look at
 * (twelve blocks): whatever is clearly weaker than you is erased, gone in violet light. A player is erased when their
 * battle power is under half of yours; a creature when it is not a boss and its health is under three times your
 * body, or when anything (a boss too) is already down to a quarter. Whatever resists takes a crushing blow instead.
 */
public final class Hakai {
    private static final Vector3f VIOLET = new Vector3f(0.62f, 0.25f, 0.95f), BLACK = new Vector3f(0.08f, 0.02f, 0.12f);

    private Hakai() {}

    /** Whether a Hakai from {@code caster} erases {@code target} outright. */
    public static boolean erases(PlayerData caster, LivingEntity target) {
        if (target instanceof Player p) {
            PlayerData td = ModCapabilities.get(p).orElse(null);
            return td == null || StatCalculator.battlePower(td) * 2 < StatCalculator.battlePower(caster);
        }
        if (target.getHealth() <= target.getMaxHealth() * 0.25f) return true;
        boolean boss = target instanceof com.dbzenith.npc.BossFighter || target.getType().is(Tags.EntityTypes.BOSSES);
        return !boss && com.dbzenith.npc.KiFighter.effectiveMaxHealth(target) < caster.getDerived().maxBody() * 3;
    }

    /** Casts it at {@code target} (null: nothing in reach, nothing is spent). */
    static boolean strike(ServerPlayer player, PlayerData data, Technique t, LivingEntity target) {
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.dbzenith.hakai_no_target"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.8)).subtract(0, 0.3, 0);
        level.sendParticles(new DustColorTransitionOptions(VIOLET, BLACK, 1.4f), hand.x, hand.y, hand.z, 14, 0.12, 0.12, 0.12, 0.01);
        Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
        double w = target.getBbWidth() / 2 + 0.2, h = target.getBbHeight() / 2 + 0.2;
        if (erases(data, target)) {
            level.sendParticles(new DustColorTransitionOptions(VIOLET, BLACK, 2.0f), at.x, at.y, at.z, 90, w, h, w, 0.04);
            level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 40, w, h, w, 0.2);
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + h * 0.5, at.z, 18, w * 0.8, h * 0.6, w * 0.8, 0.03);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.6f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 0.5f);
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.hakai(level, player), Float.MAX_VALUE);
            if (target.isAlive()) target.kill();                                    // nothing outlives being erased
            player.displayClientMessage(Component.translatable("message.dbzenith.hakai_erased", target.getDisplayName()), true);
        } else {
            double damage = DamageCalculator.kiOutgoing(data, t.damageMult());
            level.sendParticles(new DustColorTransitionOptions(VIOLET, BLACK, 1.6f), at.x, at.y, at.z, 40, w, h, w, 0.03);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.9f);
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
            player.displayClientMessage(Component.translatable("message.dbzenith.hakai_resisted", target.getDisplayName()), true);
        }
        return true;
    }
}
