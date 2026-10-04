package com.dbzenith.world;

import com.dbzenith.registry.ModEntities;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A parked Space Pod. Right-click to climb in and pick a planet; the pod launches into the sky, and on arrival a pod
 * comes down at the landing site with the pilot inside (and stays there for the trip back). Punch it to pack it up.
 */
public class SpacePodEntity extends Entity {
    public static final int PARKED = 0, LAUNCHING = 1, LANDING = 2;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SpacePodEntity.class, EntityDataSerializers.INT);
    private static final int LAUNCH_TICKS = 50;

    private int timer;
    private Planet target = Planet.EARTH;

    public SpacePodEntity(EntityType<? extends SpacePodEntity> type, Level level) {
        super(type, level);
    }

    public static SpacePodEntity create(Level level, Vec3 at) {
        SpacePodEntity pod = new SpacePodEntity(ModEntities.SPACE_POD.get(), level);
        pod.moveTo(at.x, at.y, at.z, 0, 0);
        return pod;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, PARKED);
    }

    public int state() {
        return entityData.get(STATE);
    }

    private void setState(int s) {
        entityData.set(STATE, s);
        timer = 0;
    }

    /** Starts the flight if it may go (cooldown and so on are checked with the pilot). */
    public boolean launch(ServerPlayer pilot, Planet destination) {
        if (state() != PARKED || pilot.getVehicle() != this || !Planet.canTravel(pilot, destination)) return false;
        target = destination;
        setState(LAUNCHING);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2f, 0.5f);
        return true;
    }

    /** A pod that comes down from the sky at {@code at} with {@code pilot} inside. */
    public static SpacePodEntity arrive(ServerPlayer pilot, Vec3 at) {
        SpacePodEntity pod = create(pilot.level(), at.add(0, 40, 0));
        pod.setState(LANDING);
        pilot.level().addFreshEntity(pod);
        pilot.startRiding(pod, true);
        return pod;
    }

    @Override
    public void tick() {
        super.tick();
        timer++;
        Vec3 v = getDeltaMovement();
        switch (state()) {
            case LAUNCHING -> {
                setDeltaMovement(0, Math.min(1.6, v.y + 0.06), 0);
                if (level().isClientSide) exhaust(4);
                else if (timer >= LAUNCH_TICKS) depart();
            }
            case LANDING -> {
                setDeltaMovement(0, onGround() ? 0 : -0.9, 0);
                if (level().isClientSide) exhaust(2);
                if (onGround() || verticalCollision) {
                    setState(PARKED);
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, 1.2, 0.2, 1.2, 0);
                        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1f, 0.8f);
                    }
                }
            }
            default -> setDeltaMovement(0, onGround() ? 0 : Math.max(-0.8, v.y - 0.08), 0);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    /** End of the launch: the pod is gone from here and the pilot lands on the destination in a new one. */
    private void depart() {
        if (!(getFirstPassenger() instanceof ServerPlayer pilot)) {
            setState(PARKED);
            return;
        }
        pilot.stopRiding();
        discard();
        if (Planet.travel(pilot, target)) {
            arrive(pilot, pilot.position());
        } else {
            arrive(pilot, position().add(0, -40, 0)); // the flight was refused after all: come back down
        }
    }

    private void exhaust(int n) {
        for (int i = 0; i < n; i++) {
            double ox = (random.nextDouble() - 0.5) * 0.8, oz = (random.nextDouble() - 0.5) * 0.8;
            level().addParticle(ParticleTypes.FLAME, getX() + ox, getY() - 0.1, getZ() + oz, 0, -0.3, 0);
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + ox, getY() - 0.3, getZ() + oz, 0, -0.1, 0);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (state() != PARKED || player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            if (getPassengers().isEmpty()) player.startRiding(this);
        } else if (getPassengers().isEmpty() || getFirstPassenger() == player) {
            com.dbzenith.client.ClientHooks.openPlanetScreen(); // client-only class; this branch never runs on a server
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || state() != PARKED || !getPassengers().isEmpty()) return false;
        if (source.getEntity() instanceof Player p) {
            if (!p.getAbilities().instabuild) spawnAtLocation(new ItemStack(ModItems.SPACE_POD.get()));
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.25;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(STATE, tag.getInt("state") == LAUNCHING ? PARKED : tag.getInt("state")); // an interrupted launch just stays
        timer = tag.getInt("timer");
        try {
            target = Planet.valueOf(tag.getString("target"));
        } catch (IllegalArgumentException e) {
            target = Planet.EARTH;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("state", state());
        tag.putInt("timer", timer);
        tag.putString("target", target.name());
    }
}
