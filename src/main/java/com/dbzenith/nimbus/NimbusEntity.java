package com.dbzenith.nimbus;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.race.Alignment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.ArrayDeque;
import java.util.Deque;

/** Server-authoritative flying cloud; no vanilla flight ability or ki is required. */
public class NimbusEntity extends Entity {
    private static final EntityDataAccessor<Boolean> SITTING=SynchedEntityData.defineId(NimbusEntity.class,EntityDataSerializers.BOOLEAN);
    private float forward,strafe;
    private boolean rise,boost;
    private int emptyTicks;
    private int inputTick=-100,toggleTick=-100,lerpSteps;
    private Vec3 lerpTarget=Vec3.ZERO;
    private float lerpYaw;
    public record Wake(Vec3 position,int born) {}
    public final Deque<Wake> wake=new ArrayDeque<>();
    public NimbusEntity(EntityType<? extends NimbusEntity> type,Level world) { super(type,world);setNoGravity(true); }
    public static boolean eligible(Player p) { return ModCapabilities.get(p).map(d->Alignment.of(d)==Alignment.Standing.GOOD).orElse(false); }
    public boolean sitting() { return entityData.get(SITTING); }
    public void togglePosture() { if(tickCount-toggleTick>=6) { entityData.set(SITTING,!sitting());toggleTick=tickCount; } }
    public void controls(Player player,float f,float s,boolean up,boolean fast,boolean toggle) {
        if(player!=getFirstPassenger()||!eligible(player)||!Float.isFinite(f)||!Float.isFinite(s)) return;
        forward=net.minecraft.util.Mth.clamp(f,-1,1);strafe=net.minecraft.util.Mth.clamp(s,-1,1);rise=up;boost=fast;inputTick=tickCount;
        if(toggle) togglePosture();
    }
    @Override protected void defineSynchedData() { entityData.define(SITTING,false); }
    @Override public boolean shouldRiderSit() { return sitting(); }
    @Override public double getPassengersRidingOffset() { return sitting()?.57:1.05; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty()&&e instanceof Player p&&(level().isClientSide||eligible(p)); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public InteractionResult interact(Player p,InteractionHand hand) {
        if(!level().isClientSide) {
            if(p.getVehicle()==this) togglePosture();
            else if(!eligible(p)) p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.good"),true);
            else if(getPassengers().isEmpty()&&p.startRiding(this,true)) {
                ModCapabilities.get(p).ifPresent(d->d.setFlying(false));
                p.displayClientMessage(Component.translatable("message.dbzenith.nimbus.controls"),true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport) {
        lerpTarget=new Vec3(x,y,z);lerpYaw=yaw;lerpSteps=3;
    }
    @Override public void tick() {
        super.tick();setNoGravity(true);
        if(level().isClientSide) {
            if(lerpSteps>0) { Vec3 p=position().lerp(lerpTarget,1.0/lerpSteps);setPos(p);setYRot(net.minecraft.util.Mth.rotLerp(1f/lerpSteps,getYRot(),lerpYaw));lerpSteps--; }
            Vec3 last=new Vec3(xo,yo,zo);
            if(position().distanceToSqr(last)>.0025) {
                if(!wake.isEmpty()&&position().distanceToSqr(wake.getLast().position())>64) wake.clear();
                wake.addLast(new Wake(position().add(0,.3,0),tickCount));
                while(wake.size()>42) wake.removeFirst();
            }
            while(!wake.isEmpty()&&tickCount-wake.getFirst().born()>36) wake.removeFirst();
            return;
        }
        Player pilot=getFirstPassenger() instanceof Player p?p:null;
        if(pilot!=null&&!eligible(pilot)) { pilot.stopRiding();pilot.displayClientMessage(Component.translatable("message.dbzenith.nimbus.good"),true);pilot=null; }
        Vec3 wanted=Vec3.ZERO;
        if(pilot!=null&&tickCount-inputTick<=10) {
            Vec3 look=pilot.getLookAngle();double yaw=Math.toRadians(pilot.getYRot());
            Vec3 side=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
            wanted=look.scale(forward).add(side.scale(strafe)).add(0,rise?.8:0,0);
            if(wanted.lengthSqr()>1) wanted=wanted.normalize();
            wanted=wanted.scale(boost?.85:.48);setYRot(pilot.getYRot());pilot.fallDistance=0;
            ModCapabilities.get(pilot).ifPresent(d-> { if(d.isFlying()) d.setFlying(false); });
        }
        Vec3 velocity=getDeltaMovement().lerp(wanted,.22);
        if(velocity.lengthSqr()<.00001) velocity=Vec3.ZERO;
        setDeltaMovement(velocity);
        move(MoverType.SELF,velocity);
        if(horizontalCollision||verticalCollision) setDeltaMovement(getDeltaMovement().scale(.3));
        if(getY()>level().getMaxBuildHeight()-2) { setPos(getX(),level().getMaxBuildHeight()-2,getZ());setDeltaMovement(Vec3.ZERO); }
        fallDistance=0;
        if(pilot==null) { if(++emptyTicks>1200) discard(); } else emptyTicks=0;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(SITTING,tag.getBoolean("Sitting")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putBoolean("Sitting",sitting()); }
}
