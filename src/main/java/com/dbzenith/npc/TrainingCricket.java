package com.dbzenith.npc;

import com.dbzenith.quest.QuestManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Kai's cricket (CX-12): tiny, jumpy and never where you swing. Land one blow on it and the training counts; it
 * shrugs the hit off (it is a Kai's pet) and bounds away.
 */
public class TrainingCricket extends PathfinderMob {
    private final HomeKeeper home = new HomeKeeper();

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        home.save(tag);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home.load(tag);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        home.tick(this);
    }

    /** The quest event a hit counts towards. */
    public static final String STRUCK = "dbzenith:strike_cricket";
    private int hopCooldown;

    public TrainingCricket(EntityType<? extends TrainingCricket> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 5f, 1.3, 1.7));
        goalSelector.addGoal(4, new RandomStrollGoal(this, 1.0));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (hopCooldown > 0) hopCooldown--;
        Player near = level().getNearestPlayer(this, 4);
        if (onGround() && hopCooldown == 0 && (near != null || random.nextInt(30) == 0)) {         // a spring out of reach
            double a = random.nextDouble() * Math.PI * 2;
            if (near != null) a = Math.atan2(getZ() - near.getZ(), getX() - near.getX()) + (random.nextDouble() - 0.5);
            setDeltaMovement(Math.cos(a) * 0.55, 0.62, Math.sin(a) * 0.55);
            hopCooldown = 10 + random.nextInt(10);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player && !level().isClientSide) {
            QuestManager.event(player, STRUCK);
            player.displayClientMessage(Component.translatable("message.dbzenith.cricket_struck"), true);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), 10, 0.2, 0.2, 0.2, 0.2);
            level().playSound(null, blockPosition(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.NEUTRAL, 1f, 1.8f);
            setDeltaMovement((random.nextDouble() - 0.5) * 1.4, 0.8, (random.nextDouble() - 0.5) * 1.4);
            hopCooldown = 20;
        }
        return false;                                                                           // a Kai's pet does not get hurt
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }
}
