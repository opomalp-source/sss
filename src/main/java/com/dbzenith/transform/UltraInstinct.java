package com.dbzenith.transform;

import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.quest.QuestManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ultra Instinct (CX-17b), taught by Whis: the body dodges on its own. In the Sign a share of the blows and blasts aimed
 * at you slip past (a quarter, up to half with mastery); Mastered, most do (half, up to three quarters), and each dodge
 * answers with a counter-blow. A dodge is a short sidestep that leaves a vanishing streak; it costs a little stamina.
 */
public final class UltraInstinct {
    public static final String SIGN_FLAG = "ultra_instinct";
    public static final String MASTERED_FLAG = "ultra_instinct_mastered";
    /** The quest event each dodge counts towards (Whis's second lesson). */
    public static final String DODGED = "dbzenith:ultra_instinct_dodge";

    private UltraInstinct() {}

    public static boolean isMastered(PlayerData d) {
        return Forms.ULTRA_INSTINCT.id().equals(d.getFormId());
    }

    public static boolean isIn(PlayerData d) {
        return isMastered(d) || Forms.ULTRA_INSTINCT_SIGN.id().equals(d.getFormId());
    }

    /** Chance (0..1) that a blow slips past, or 0 outside Ultra Instinct. */
    public static double evadeChance(PlayerData d) {
        if (!isIn(d)) return 0;
        double m = Math.min(1.0, d.getMastery(d.getFormId()) / 100.0);
        return isMastered(d) ? 0.5 + 0.25 * m : 0.25 + 0.25 * m;
    }

    /**
     * Rolls a dodge for a blow from {@code foe}. On a dodge the player sidesteps (the blow lands on nothing), the quest
     * counts it, and the Mastered form strikes back. Returns whether it dodged.
     */
    public static boolean evade(ServerPlayer player, PlayerData d, Entity foe, double roll) {
        if (foe == null || foe == player || roll >= evadeChance(d)) return false;
        if (d.getStamina() < d.getDerived().maxStamina() * 0.02) return false;            // too winded for the body to move
        d.setStamina(d.getStamina() - d.getDerived().maxStamina() * 0.02);
        ServerLevel level = player.serverLevel();
        Vec3 away = player.position().subtract(foe.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = player.getLookAngle().multiply(-1, 0, -1);
        Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(player.getRandom().nextBoolean() ? 3.0 : -3.0);
        Vec3 to = player.position().add(side);
        if (level.noCollision(player, player.getBoundingBox().move(side))) player.teleportTo(to.x, player.getY(), to.z);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 8, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.6f);
        QuestManager.event(player, DODGED);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.ultra_instinct_dodge"), true);
        if (isMastered(d) && foe instanceof LivingEntity target && foe.isAlive() && foe.distanceToSqr(player) < 64) {
            double damage = DamageCalculator.meleeOutgoing(d, 1.0, 1) * 0.8;               // the counter-blow
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.thrown(level, player), (float) damage);
            ImpactPacket.melee(player, target, ImpactPacket.PUNCH).send(level);
        }
        return true;
    }
}
