package com.dbzenith.npc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A boss: boss bar, never despawns, teleports behind distant targets, and transforms at half health
 * (aura burst, x1.5 damage, +30% speed).
 */
public class BossFighter extends KiFighter {
    private final ServerBossEvent bossBar;
    private final int auraColor;
    private boolean enraged;
    private int teleportCooldown = 160;

    public BossFighter(EntityType<? extends BossFighter> type, Level world, Profile profile, BossEvent.BossBarColor color, int auraColor) {
        super(type, world, profile);
        this.bossBar = new ServerBossEvent(Component.translatable(profile.nameKey()), color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.auraColor = auraColor;
        setPersistenceRequired();
        xpReward = 200;
    }

    public boolean isEnraged() {
        return enraged;
    }

    public ServerBossEvent bossBar() {
        return bossBar;
    }

    /** Bosses match the strongest form the summoner can take, so transforming after the summons is no shortcut. */
    @Override
    protected long scaleTo(com.dbzenith.data.PlayerData d) {
        return com.dbzenith.stats.StatCalculator.peakPower(d);
    }

    @Override
    protected double damageMultiplier() {
        return enraged ? 1.5 : 1.0;
    }

    @Override
    public void setFighterLevel(int lvl) {
        super.setFighterLevel(lvl);
        bossBar.setName(getDisplayName());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (!enraged && getHealth() < getMaxHealth() * com.dbzenith.config.DBZConfig.SERVER.bossEnrageHealth.get()) enrage();
        if (teleportCooldown > 0) teleportCooldown--;
        LivingEntity t = getTarget();
        if (t != null && teleportCooldown == 0 && distanceToSqr(t) > 12 * 12) {
            teleportBehind(t);
            teleportCooldown = 160 + random.nextInt(80);
        }
        if (enraged && tickCount % 4 == 0 && level() instanceof ServerLevel sl) {
            Vector3f rgb = new Vector3f(((auraColor >> 16) & 0xFF) / 255f, ((auraColor >> 8) & 0xFF) / 255f, (auraColor & 0xFF) / 255f);
            sl.sendParticles(new DustParticleOptions(rgb, 1.6f), getX(), getY() + getBbHeight() / 2, getZ(), 4, 0.5, 0.9, 0.5, 0.02);
        }
    }

    /** Phase two. */
    public void enrage() {
        enraged = true;
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.3);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 2, 0.3, 0.3, 0.3, 0);
            sl.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1f, 1.4f);
            for (ServerPlayer p : bossBar.getPlayers()) {
                p.displayClientMessage(Component.translatable("message.dbzenith.boss_enraged", getDisplayName()), true);
            }
        }
    }

    private void teleportBehind(LivingEntity t) {
        Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        teleportTo(behind.x, t.getY(), behind.z);
        level().playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 1.2f);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("enraged");
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }
}
