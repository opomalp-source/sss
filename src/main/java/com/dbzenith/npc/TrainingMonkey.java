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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Kai's training monkey (CX-12): it bolts from anyone who comes near and is far quicker than a fighter not yet used
 * to ten times gravity. Lay a hand on it (touch it) and it counts as caught; then it slips away to be chased again.
 */
public class TrainingMonkey extends PathfinderMob {
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

    /** The quest event a catch counts towards. */
    public static final String CAUGHT = "dbzenith:catch_monkey";
    private int caughtCooldown;

    public TrainingMonkey(EntityType<? extends TrainingMonkey> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 9f, 1.4, 1.9));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (caughtCooldown > 0) {
            caughtCooldown--;
            return;
        }
        if (onGround() && random.nextInt(25) == 0) setDeltaMovement(getDeltaMovement().add(0, 0.42, 0));   // a skip and a hop
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.4))) {
            if (!(p instanceof ServerPlayer sp) || p.isSpectator()) continue;
            caught(sp);
            break;
        }
    }

    private void caught(ServerPlayer player) {
        caughtCooldown = 60;
        QuestManager.event(player, CAUGHT);
        player.displayClientMessage(Component.translatable("message.dbzenith.monkey_caught"), true);
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, blockPosition(), SoundEvents.FOX_SCREECH, SoundSource.NEUTRAL, 0.8f, 1.6f);
        Vec3 away = position().add((random.nextDouble() - 0.5) * 24, 2, (random.nextDouble() - 0.5) * 24);
        teleportTo(away.x, away.y, away.z);                                                    // off it scampers
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isCreativePlayer();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }
}
