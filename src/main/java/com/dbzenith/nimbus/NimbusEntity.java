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
            clientMotion();
            Vec3 last=new Vec3(xo,yo,zo);
            if(position().distanceToSqr(last)>.0025) {
                if(!wake.isEmpty()&&position().distanceToSqr(wake.getLast().position())>64) wake.clear();
                wake.addLast(new Wake(rearPoint(),tickCount));
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
            wanted=wanted.scale(boost?1.0:.5);setYRot(pilot.getYRot());pilot.fallDistance=0;
            ModCapabilities.get(pilot).ifPresent(d-> { if(d.isFlying()) d.setFlying(false); });
        }
        Vec3 now=getDeltaMovement();                                               // momentum: speeds up over a moment, glides to a stop
        Vec3 velocity=now.lerp(wanted,wanted.lengthSqr()<1e-6?.06:wanted.dot(now)<0?.16:.11);
        if(velocity.lengthSqr()<.00001) velocity=Vec3.ZERO;
        setDeltaMovement(velocity);
        if(pilot==null&&velocity.horizontalDistanceSqr()>.006)                     // gliding on its own: nose first, tail behind
            setYRot(net.minecraft.util.Mth.rotLerp(.2f,getYRot(),(float)Math.toDegrees(Math.atan2(-velocity.x,velocity.z))));
        move(MoverType.SELF,velocity);
        if(horizontalCollision||verticalCollision) setDeltaMovement(getDeltaMovement().scale(.3));
        if(getY()>level().getMaxBuildHeight()-2) { setPos(getX(),level().getMaxBuildHeight()-2,getZ());setDeltaMovement(Vec3.ZERO); }
        fallDistance=0;
        if(pilot==null) { if(++emptyTicks>1200) discard(); } else emptyTicks=0;
    }
    // ---------------------------------------------------------------- client: how the cloud moves and what it sheds
    /** A puff peeling off the back of the cloud: where (world), drifting how, born when, how big. */
    public record Puff(Vec3 position, Vec3 drift, int born, int life, float size, boolean glint) {}
    public final java.util.List<Puff> puffs = new java.util.ArrayList<>();
    /** Nose-down tilt, bank and stretch (degrees, degrees, share), each with its spring's speed; the previous values for interpolation. */
    public float tilt, tiltO, tiltV, bank, bankO, bankV, stretch, stretchO, speed;
    private float lastYaw = Float.NaN, lastForward;

    private void clientMotion() {
        tiltO = tilt; bankO = bank; stretchO = stretch;
        Vec3 v = position().subtract(xo, yo, zo);
        speed = (float) v.length();
        float yaw = getYRot();
        float turn = Float.isNaN(lastYaw) ? 0 : net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        double yr = Math.toRadians(yaw);
        float fwd = (float) (v.x * -Math.sin(yr) + v.z * Math.cos(yr));
        float accel = fwd - lastForward;
        lastForward = fwd;
        float tiltTarget = net.minecraft.util.Mth.clamp(fwd * 16f + accel * 110f - (float) v.y * 22f, -14f, 24f);
        float bankTarget = net.minecraft.util.Mth.clamp(-turn * 1.7f, -28f, 28f);
        tiltV += (tiltTarget - tilt) * .14f - tiltV * .36f;                  // springs: a little past, then settling
        tilt += tiltV;
        bankV += (bankTarget - bank) * .12f - bankV * .34f;
        bank += bankV;
        stretch += (net.minecraft.util.Mth.clamp(speed * .6f, 0f, .55f) - stretch) * .15f;
        if (speed > .05f) {                                                  // shed puffs off the tail
            var rnd = random;
            Vec3 rear = rearPoint();
            int n = speed > .55f ? 2 : 1;
            for (int i = 0; i < n; i++) {
                Vec3 at = rear.add((rnd.nextFloat() - .5) * .5, (rnd.nextFloat() - .5) * .35, (rnd.nextFloat() - .5) * .5).add(v.scale(-i / (double) n));
                Vec3 drift = v.scale(-.04).add((rnd.nextFloat() - .5) * .025, .006 + rnd.nextFloat() * .012, (rnd.nextFloat() - .5) * .025);
                puffs.add(new Puff(at, drift, tickCount, 16 + rnd.nextInt(12), .3f + rnd.nextFloat() * .28f, false));
            }
            if (speed > .3f && rnd.nextInt(3) == 0) puffs.add(new Puff(rear.add((rnd.nextFloat() - .5) * .9, (rnd.nextFloat() - .3) * .6, (rnd.nextFloat() - .5) * .9),
                    new Vec3(0, .01, 0), tickCount, 18 + rnd.nextInt(10), .12f + rnd.nextFloat() * .1f, true));   // a glint
        }
        puffs.removeIf(p -> tickCount - p.born() > p.life());
        while (puffs.size() > 160) puffs.remove(0);
    }

    /** The tip of the cloud's tail, in the world. */
    public Vec3 rearPoint() {
        double yr = Math.toRadians(getYRot());
        double back = 1.35 + stretch * 1.2;
        return position().add(Math.sin(yr) * back, .42, -Math.cos(yr) * back);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(SITTING,tag.getBoolean("Sitting")); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putBoolean("Sitting",sitting()); }
}
