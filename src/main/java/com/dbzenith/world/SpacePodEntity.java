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
    public static final int ORBIT_LAUNCH=3;
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
    public boolean launchOrbit(ServerPlayer pilot){
        if(state()!=PARKED||pilot.getVehicle()!=this||!com.dbzenith.galaxy.planet.PlanetConfig.SPACE.get()||com.dbzenith.galaxy.space.SpaceRuntime.surfaceRef(pilot.serverLevel())==null||com.dbzenith.data.ModCapabilities.getOrThrow(pilot).isDead())return false;
        setState(ORBIT_LAUNCH);return true;
    }
    public void orbitRefused(){if(state()==ORBIT_LAUNCH)setState(LANDING);}

    /** A pod that comes down from the sky at {@code at} with {@code pilot} inside. */
    public static SpacePodEntity arrive(ServerPlayer pilot, Vec3 at) {
        SpacePodEntity pod = create(pilot.level(), at.add(0, 40, 0));
        pod.setState(LANDING);
        pilot.level().addFreshEntity(pod);
        pilot.startRiding(pod, true);
        return pod;
    }
    /**
     * The space rework, phase 3: the pod that was left behind, carried to the pilot's new world: its saved data (name,
     * identity, settings) loaded into the new one, at the pilot's place and moving with them. {@code landing}: coming down
     * into an atmosphere. Minecraft can't move a ridden vehicle between dimensions, so this is the same pod rebuilt.
     */
    public static SpacePodEntity carry(SpacePodEntity old,ServerPlayer pilot,Vec3 at,Vec3 velocity,boolean landing){
        var riders=new java.util.ArrayList<ServerPlayer>();for(var e:old.getPassengers())if(e instanceof ServerPlayer q&&q!=pilot)riders.add(q);   // a passenger comes too (phase 5)
        for(var q:riders)q.stopRiding();
        var saved=old.saveWithoutId(new CompoundTag());old.discard();
        var pod=create(pilot.level(),at);pod.load(saved);pod.setPos(at.x,at.y,at.z);pod.setDeltaMovement(velocity);
        pod.setState(landing?LANDING:PARKED);pilot.level().addFreshEntity(pod);pilot.startRiding(pod,true);
        for(var q:riders){q.teleportTo(pilot.serverLevel(),at.x,at.y,at.z,q.getYRot(),q.getXRot());q.startRiding(pod,true);q.fallDistance=0;
            com.dbzenith.galaxy.planet.PlanetEnvironment.enter(q);com.dbzenith.galaxy.space.SpaceRuntime.scene(q,true,velocity);}
        return pod;
    }
    public static SpacePodEntity orbit(ServerPlayer pilot,Vec3 at,Vec3 velocity){
        var pod=create(pilot.level(),at);pod.setDeltaMovement(velocity);pilot.level().addFreshEntity(pod);pilot.startRiding(pod,true);return pod;
    }

    @Override
    public void tick() {
        super.tick();
        timer++;
        Vec3 v = getDeltaMovement();
        if(com.dbzenith.galaxy.space.SpaceRuntime.isSpace(level())){
            if(getFirstPassenger() instanceof Player pilot){
                setYRot(pilot.getYRot());setXRot(pilot.getXRot());
                setDeltaMovement(com.dbzenith.galaxy.space.SpaceMath.step(v,pilot.zza,pilot.xxa,0,pilot.isSprinting(),pilot.isSecondaryUseActive(),pilot.getYRot(),pilot.getXRot(),true));
            }
            move(MoverType.SELF,getDeltaMovement());return;
        }
        switch (state()) {
            case ORBIT_LAUNCH -> {setDeltaMovement(0,Math.min(2.2,v.y+.055),0);if(level().isClientSide)exhaust(3);}
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
            if (getPassengers().size() < 2) player.startRiding(this);   // the first in pilots; a second rides along
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
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        Vec3 back = getPassengers().indexOf(passenger) == 0 ? Vec3.ZERO : Vec3.directionFromRotation(0, getYRot()).scale(-.55);   // the passenger sits just behind the pilot
        move.accept(passenger, getX() + back.x, getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset() + (back == Vec3.ZERO ? 0 : .15), getZ() + back.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2 && passenger instanceof Player;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(STATE, tag.getInt("state") == LAUNCHING || tag.getInt("state")==ORBIT_LAUNCH ? PARKED : tag.getInt("state")); // an interrupted launch just stays
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
