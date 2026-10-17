package com.dbzenith.galaxy.space;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.network.*;
import com.dbzenith.race.*;
import com.dbzenith.registry.*;
import com.dbzenith.world.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Trips stay in the departure instance until the existing asynchronous orbital transfer is ready. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class Interstellar {
    private static final Map<MinecraftServer,Map<UUID,Trip>> TRIPS=new WeakHashMap<>();
    private static final class Trip {
        final GalaxyCatalog.Ref ref;final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> origin;
        final Vec3 anchor;final long start,damage;final int duration;final UUID pod;final double cost;final String name;boolean queued;
        Trip(ServerPlayer p,GalaxyCatalog.Ref ref,int duration,String name){this.ref=ref;this.duration=duration;this.name=name;origin=p.level().dimension();anchor=p.position();start=p.server.getTickCount();damage=ModCapabilities.getOrThrow(p).getLastDamagedTick();pod=p.getVehicle() instanceof SpacePodEntity?p.getVehicle().getUUID():null;cost=pod==null?ModCapabilities.getOrThrow(p).getDerived().maxKi()*.2:0;}
    }
    private static Map<UUID,Trip> trips(MinecraftServer s){return TRIPS.computeIfAbsent(s,k->new HashMap<>());}
    public static boolean active(ServerPlayer p){return trips(p.server).containsKey(p.getUUID());}
    public static int duration(GalaxyCatalog.Id a,GalaxyCatalog.Id b){double d=Math.sqrt((double)(a.x()-b.x())*(a.x()-b.x())+(double)(a.y()-b.y())*(a.y()-b.y())+(double)(a.z()-b.z())*(a.z()-b.z()));return Math.max(200,Math.min(400,200+(int)(Math.sqrt(d)*32)));}
    private static boolean living(ServerPlayer p){var ref=SpaceRuntime.surfaceRef(p.serverLevel());if(ref!=null&&(GalaxyData.get(p.server).destroyed(ref)||com.dbzenith.galaxy.planet.PlanetDestruction.restoring(p.server,ref)))return false;return p.isAlive()&&!p.isSpectator()&&!p.isSleeping()&&!com.dbzenith.combat.KnockOut.isDown(p)&&!ModCapabilities.getOrThrow(p).isDead()&&!TimeChamber.isIn(p)&&(SpaceRuntime.isSpace(p.level())||SpaceRuntime.surfaceRef(p.serverLevel())!=null);}
    public static boolean accessible(ServerPlayer p,GalaxyCatalog.Ref ref){return !ref.system().equals(GalaxyCatalog.BEERUS)||p.isCreative()||ModCapabilities.getOrThrow(p).hasFlag("god_ki");}
    public static boolean begin(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!PlanetConfig.HYPERSPEED.get()||!living(p)||!SpaceRuntime.isSpace(p.level())||active(p)||SpaceRuntime.pending(p))return refuse(p,"Launch into orbit before plotting a hyperspeed course.");
        var d=ModCapabilities.getOrThrow(p);boolean pod=p.getVehicle() instanceof SpacePodEntity;
        if(!accessible(p,ref))return refuse(p,"Finding Beerus's system requires god ki.");
        if(!p.isCreative()&&(d.isOnCooldown("space_travel",p.level().getGameTime())||!pod&&(ModEffects.isKiSealed(p)||!d.isFlying()||d.getKi()<d.getDerived().maxKi()*.98)))return refuse(p,"Hyperspeed needs a piloted Space Pod or ki flight at full ki, with navigation recharged.");
        var data=GalaxyData.get(p.server);GalaxyCatalog.Body body;
        try{body=GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref);}catch(IllegalArgumentException ex){return false;}
        // Destroyed systems remain reachable: their orbital remnants can be explored.
        if(trips(p.server).size()>=16)return refuse(p,"Navigation is busy. Try again shortly.");
        var trip=new Trip(p,ref,duration(GalaxyData.current(p),ref.system()),data.name(ref,body.name()));trips(p.server).put(p.getUUID(),trip);p.setDeltaMovement(Vec3.ZERO);if(p.getVehicle()!=null)p.getVehicle().setDeltaMovement(Vec3.ZERO);
        ModNetwork.sendTo(p,new JourneyPacket(true,0,trip.duration,trip.name));return true;
    }
    private static boolean refuse(ServerPlayer p,String message){p.displayClientMessage(Component.literal(message),true);return false;}
    public static boolean cancel(ServerPlayer p){var t=trips(p.server).remove(p.getUUID());if(t==null)return false;if(t.queued)SpaceRuntime.cancel(p);ModNetwork.sendTo(p,new JourneyPacket(false,0,0,""));return true;}
    public static void arrived(ServerPlayer p){
        var t=trips(p.server).get(p.getUUID());if(t==null||!t.queued||!t.ref.system().equals(SpaceRuntime.id(p.level().dimension())))return;
        trips(p.server).remove(p.getUUID());SpaceRuntime.approach(p,t.ref.planet());var d=ModCapabilities.getOrThrow(p);if(!p.isCreative())d.setKi(Math.max(0,d.getKi()-t.cost));d.setCooldown("space_travel",p.level().getGameTime()+200);ModNetwork.sendTo(p,new JourneyPacket(false,0,0,t.name));
    }
    public static boolean canTransmit(ServerPlayer p){
        if(!living(p)||p.isPassenger()||ModEffects.isKiSealed(p))return false;var d=ModCapabilities.getOrThrow(p);var s=RacialSkills.byId("instant_transmission");
        return s!=null&&RacialSkills.has(d,s.id())&&p.level().getGameTime()>=d.getRacialCooldown(s.id())&&(p.isCreative()||d.getKi()>=d.getDerived().maxKi()*s.kiCostPercent()/100);
    }
    public static void blink(ServerPlayer p){p.serverLevel().sendParticles(ParticleTypes.END_ROD,p.getX(),p.getY()+1,p.getZ(),24,.3,.6,.3,.05);p.serverLevel().playSound(null,p.blockPosition(),ModSounds.TELEPORT.get(),SoundSource.PLAYERS,1,1.4f);}
    public static boolean transmit(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!accessible(p,ref))return false;
        if(active(p)||SpaceRuntime.pending(p)||!canTransmit(p))return refuse(p,"Instant Transmission requires the learned skill, enough ki and a ready cooldown.");
        if(!ModCapabilities.getOrThrow(p).hasFlag("planet_landing:"+ref.key()))return refuse(p,"Land on this planet before using Instant Transmission to return.");
        return PlanetRuntime.transmit(p,ref);
    }
    public static boolean transmitToPlayer(ServerPlayer p,ServerPlayer target){
        if(!canTransmit(p)||active(p)||SpaceRuntime.pending(p)||!living(target)||target==p||!ModCapabilities.getOrThrow(p).hasFlag("transmission_contact:"+target.getStringUUID()))return false;
        var td=ModCapabilities.getOrThrow(target);if(td.hasFlag("god_ki")&&!ModCapabilities.getOrThrow(p).hasFlag("god_ki"))return false;
        if(!accessible(p,new GalaxyCatalog.Ref(GalaxyData.current(target),0)))return false;
        var l=target.serverLevel();Vec3 at=null;
        for(int i=0;i<8;i++){Vec3 candidate=target.position().add(Math.cos(i*Math.PI/4)*2,0,Math.sin(i*Math.PI/4)*2);var box=p.getBoundingBox().move(candidate.subtract(p.position()));if(l.hasChunkAt(net.minecraft.core.BlockPos.containing(candidate))&&l.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(candidate))&&l.noCollision(p,box)){at=candidate;break;}}
        if(at==null)return false;Vec3 destination=at;
        return RacialSkillEffects.transmitAction(p,()->{blink(p);p.stopRiding();p.teleportTo(l,destination.x,destination.y,destination.z,p.getYRot(),p.getXRot());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;PlanetEnvironment.enter(p);SpaceRuntime.scene(p,true,Vec3.ZERO);blink(p);return true;});
    }
    @SubscribeEvent public static void contact(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof ServerPlayer target)ModCapabilities.getOrThrow(p).setFlag("transmission_contact:"+target.getStringUUID(),true);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){TRIPS.remove(e.getServer());}
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!active(p))return;
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;if(p.getVehicle()!=null)p.getVehicle().setDeltaMovement(Vec3.ZERO);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(var it=trips(server).entrySet().iterator();it.hasNext();){var item=it.next();var t=item.getValue();var p=server.getPlayerList().getPlayer(item.getKey());long elapsed=server.getTickCount()-t.start;
            boolean cancel=p==null||!living(p)||!p.level().dimension().equals(t.origin)||elapsed>t.duration+200||ModCapabilities.getOrThrow(p).getLastDamagedTick()!=t.damage||!t.queued&&p.position().distanceToSqr(t.anchor)>64;
            if(!cancel){boolean pod=t.pod!=null;cancel=pod?(p.getVehicle()==null||!t.pod.equals(p.getVehicle().getUUID())):!p.isCreative()&&(!ModCapabilities.getOrThrow(p).isFlying()||ModEffects.isKiSealed(p)||ModCapabilities.getOrThrow(p).getKi()<t.cost);}
            if(cancel){if(p!=null){if(t.queued)SpaceRuntime.cancel(p);ModNetwork.sendTo(p,new JourneyPacket(false,0,0,""));p.displayClientMessage(Component.literal("Hyperspeed interrupted. No jump cost charged."),true);}it.remove();continue;}
            if(elapsed>=t.duration&&!t.queued){if(!SpaceRuntime.jump(p,t.ref)){ModNetwork.sendTo(p,new JourneyPacket(false,0,0,""));it.remove();continue;}t.queued=true;}
            if(elapsed%20==0)ModNetwork.sendTo(p,new JourneyPacket(true,(int)Math.min(elapsed,t.duration),t.duration,t.name));
        }
    }
}
