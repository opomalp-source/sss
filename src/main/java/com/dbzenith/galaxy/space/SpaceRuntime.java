package com.dbzenith.galaxy.space;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.network.*;
import com.dbzenith.world.*;
import commoble.infiniverse.api.*;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.*;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class SpaceRuntime {
    private static final String PREFIX="orbit/v1/",CHECKPOINT="dbzenithOrbitCheckpoint";
    private static final TicketType<UUID> TRANSIT=TicketType.create("dbzenith_orbit_transit",Comparator.<UUID>naturalOrder(),600);
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final class State {
        final Map<UUID,Transit> pending=new HashMap<>();final Map<ResourceKey<Level>,Long> used=new HashMap<>();
        final Map<UUID,Motion> motion=new HashMap<>();
        final Map<ResourceKey<Level>,GalaxyCatalog.SystemSpec> systems=new HashMap<>();final Set<ResourceKey<Level>> retiring=new HashSet<>();
    }
    private record Transit(ResourceKey<Level> origin,ResourceKey<Level> target,GalaxyCatalog.Ref ref,Vec3 position,Vec3 velocity,ChunkPos chunk,long tick,CompletableFuture<Boolean> ready){}
    private record Motion(Vec3 velocity,int tick){}
    public static void velocityHint(ServerPlayer p,Vec3 v){
        if(!Double.isFinite(v.lengthSqr())||v.lengthSqr()>31||!isSpace(p.level())&&(p.getY()<450||surfaceRef(p.serverLevel())==null))return;
        state(p.server).motion.put(p.getUUID(),new Motion(v,p.server.getTickCount()));
    }
    private static Vec3 velocity(ServerPlayer p){var m=state(p.server).motion.get(p.getUUID());return m!=null&&p.server.getTickCount()-m.tick<30?m.velocity:p.getDeltaMovement();}
    private static State state(MinecraftServer s){return STATES.computeIfAbsent(s,k->new State());}
    public static boolean isSpace(Level l){return id(l.dimension())!=null;}
    public static GalaxyCatalog.Id id(ResourceKey<Level> key){
        var r=key.location();if(!r.getNamespace().equals(DBZenith.MOD_ID)||!r.getPath().startsWith(PREFIX))return null;
        try{return GalaxyCatalog.Id.parse(r.getPath().substring(PREFIX.length()).replace('_',':'));}catch(IllegalArgumentException ex){return null;}
    }
    public static ResourceKey<Level> key(GalaxyCatalog.Id id){return ResourceKey.create(Registries.DIMENSION,new ResourceLocation(DBZenith.MOD_ID,PREFIX+id.toString().replace(':','_')));}
    public static GalaxyCatalog.Ref surfaceRef(ServerLevel l){
        if(l.getChunkSource().getGenerator() instanceof PlanetGenerator g)return g.ref;
        Planet p=Planet.of(l);if(p==null)return null;
        return new GalaxyCatalog.Ref(switch(p){case EARTH->GalaxyCatalog.EARTH;case NAMEK->GalaxyCatalog.NAMEK;case NORTHERN_PLANET->GalaxyCatalog.NORTH;case BEERUS_PLANET->GalaxyCatalog.BEERUS;},0);
    }
    public static GalaxyCatalog.SystemSpec system(ServerLevel l){
        var id=id(l.dimension());if(id==null){var ref=surfaceRef(l);if(ref==null)return null;id=ref.system();}
        var at=id;return state(l.getServer()).systems.computeIfAbsent(key(at),k->GalaxyCatalog.system(l.getServer().overworld().getSeed(),GalaxyData.get(l.getServer()).salt(),at));
    }
    public static ServerLevel open(MinecraftServer server,GalaxyCatalog.Id id){
        State s=state(server);var key=key(id);if(s.retiring.contains(key))throw new IllegalArgumentException("Orbital space is finishing its save. Try again shortly.");
        var old=server.getLevel(key);if(old!=null)return old;
        long loaded=java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(),false).filter(SpaceRuntime::isSpace).count();
        if(loaded>=PlanetConfig.MAX_ORBITS.get())throw new IllegalArgumentException("All orbital space slots are active. Allow an empty system to unload.");
        var registry=server.registryAccess();var type=registry.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE,new ResourceLocation(DBZenith.MOD_ID,"space")));
        var biome=registry.registryOrThrow(Registries.BIOME).getHolderOrThrow(ResourceKey.create(Registries.BIOME,new ResourceLocation(DBZenith.MOD_ID,"orbital_void")));
        var level=InfiniverseAPI.get().getOrCreateLevel(server,key,()->new LevelStem(type,new OrbitGenerator(new FixedBiomeSource(biome),GalaxyCatalog.seed(server.overworld().getSeed(),GalaxyData.get(server).salt(),id),id)));
        PlanetRuntime.isolateBorder(level);level.getWorldBorder().setCenter(0,0);level.getWorldBorder().setSize(240000);s.used.put(key,(long)server.getTickCount());return level;
    }
    public static boolean ascend(ServerPlayer p){
        var ref=surfaceRef(p.serverLevel());if(ref==null||ModCapabilities.getOrThrow(p).isDead()||!p.isAlive())return false;
        try{
            var body=GalaxyCatalog.body(p.server.overworld().getSeed(),GalaxyData.get(p.server).salt(),ref);Vec3 normal=SpaceMath.normal(p.getX(),p.getZ(),body.radius());
            Vec3 at=SpaceMath.center(body).add(normal.scale(SpaceMath.radius(body.radius())+100));
            Vec3 oldVelocity=velocity(p);double longitude=Math.atan2(normal.x,normal.y);Vec3 east=new Vec3(Math.cos(longitude),-Math.sin(longitude),0),north=east.cross(normal).normalize();
            Vec3 v=normal.scale(Math.max(.18,Math.min(3,oldVelocity.y))).add(east.scale(oldVelocity.x)).add(north.scale(oldVelocity.z));if(v.lengthSqr()>30.25)v=v.normalize().scale(5.5);
            var anchor=new CompoundTag();anchor.putString("ref",ref.key());anchor.putDouble("x",p.getX());anchor.putDouble("z",p.getZ());p.getPersistentData().put("dbzenithLaunchAnchor",anchor);
            return queue(p,open(p.server,ref.system()),ref,at,v);
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);return false;}
    }
    /** Operator preview of a body's daylight side, without creating its surface dimension. */
    public static boolean approach(ServerPlayer p,int index){
        if(!isSpace(p.level()))return false;var system=system(p.serverLevel());if(index<0||index>=system.planets().size())return false;
        var b=system.planets().get(index);Vec3 c=SpaceMath.center(b),at=c.add(new Vec3(0,128,0).subtract(c).normalize().scale(SpaceMath.radius(b.radius())+900));
        Vec3 look=c.subtract(at).normalize();float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(look.y));
        p.teleportTo(p.serverLevel(),at.x,at.y,at.z,yaw,pitch);p.setDeltaMovement(Vec3.ZERO);velocityHint(p,Vec3.ZERO);scene(p,true,Vec3.ZERO);return true;
    }
    private static boolean queue(ServerPlayer p,ServerLevel level,GalaxyCatalog.Ref ref,Vec3 at,Vec3 v){
        State s=state(p.server);if(s.pending.containsKey(p.getUUID()))return false;
        if(s.pending.size()>=16)return false;ChunkPos chunk=new ChunkPos(BlockPos.containing(at));level.getChunkSource().addRegionTicket(TRANSIT,chunk,1,p.getUUID());
        var ready=CompletableFuture.supplyAsync(()->level.getChunkSource().getChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true),Util.backgroundExecutor()).thenCompose(f->f).thenApply(e->e.left().isPresent());
        s.pending.put(p.getUUID(),new Transit(p.level().dimension(),level.dimension(),ref,at,v,chunk,p.server.getTickCount(),ready));prepare(p);return true;
    }
    public static boolean pending(ServerPlayer p){return state(p.server).pending.containsKey(p.getUUID())||PlanetRuntime.pending(p);}
    public static void prepare(ServerPlayer p){var t=new CompoundTag();t.putBoolean("preparing",true);t.putString("dimension",p.level().dimension().location().toString());ModNetwork.sendTo(p,new SpaceScenePacket(t));}
    public static void scene(ServerPlayer p,boolean transition,Vec3 velocity){
        var ref=surfaceRef(p.serverLevel());var id=id(p.level().dimension());if(id==null&&ref!=null)id=ref.system();
        CompoundTag t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());
        if(id!=null){t.put("system",GalaxyService.survey(p,GalaxyService.SYSTEM,id,-1,"",0).getCompound("system"));t.putString("id",id.toString());if(ref!=null)t.putInt("surfacePlanet",ref.planet());}
        t.putBoolean("transition",transition);t.putDouble("vx",velocity.x);t.putDouble("vy",velocity.y);t.putDouble("vz",velocity.z);
        ModNetwork.sendTo(p,new SpaceScenePacket(t));
    }
    private static void checkpoint(ServerPlayer p){if(!isSpace(p.level()))return;var t=new CompoundTag();t.putString("system",id(p.level().dimension()).toString());t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());p.getPersistentData().put(CHECKPOINT,t);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){
        var t=p.getPersistentData().getCompound(CHECKPOINT);
        if(!isSpace(p.level())&&!t.isEmpty())try{
            var id=GalaxyCatalog.Id.parse(t.getString("system"));Vec3 at=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));
            if(!Double.isFinite(at.lengthSqr())||Math.abs(at.x)>119000||Math.abs(at.z)>119000||at.y< -950||at.y>950)throw new IllegalArgumentException("Invalid orbital checkpoint");
            queue(p,open(p.server,id),new GalaxyCatalog.Ref(id,0),at,Vec3.ZERO);
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);}
        scene(p,false,Vec3.ZERO);
    }}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){checkpoint(p);state(p.server).motion.remove(p.getUUID());}}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){
        if(isSpace(p.level()))checkpoint(p);else p.getPersistentData().remove(CHECKPOINT);
        if(!isSpace(p.level())&&p.getPersistentData().contains("dbzenithOrbitMayfly")){
            p.getAbilities().mayfly=p.isCreative()||p.isSpectator()||ModCapabilities.getOrThrow(p).isFlying()||p.getPersistentData().getBoolean("dbzenithOrbitMayfly");
            p.getPersistentData().remove("dbzenithOrbitMayfly");p.onUpdateAbilities();
        }scene(p,false,Vec3.ZERO);
    }}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){STATES.remove(e.getServer());}
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent e){for(var l:e.getServer().getAllLevels())if(isSpace(l)){
        PlanetRuntime.isolateBorder(l);l.getWorldBorder().setCenter(0,0);l.getWorldBorder().setSize(240000);state(e.getServer()).used.put(l.dimension(),(long)e.getServer().getTickCount());
    }}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void save(UnregisterDimensionEvent e){if(isSpace(e.getLevel())){state(e.getLevel().getServer()).retiring.add(e.getLevel().dimension());e.getLevel().save(null,true,false);}}
    @SubscribeEvent public static void close(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel l&&isSpace(l)&&l.getServer().getLevel(l.dimension())==null){
        try{l.close();}catch(java.io.IOException ex){throw new IllegalStateException("Failed to close orbit storage",ex);}var s=state(l.getServer());s.used.remove(l.dimension());s.systems.remove(l.dimension());s.retiring.remove(l.dimension());
    }}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!PlanetConfig.SPACE.get()&&!isSpace(p.level()))return;
        long now=p.server.getTickCount();
        if(isSpace(p.level())){
            if(!p.getPersistentData().contains("dbzenithOrbitMayfly"))p.getPersistentData().putBoolean("dbzenithOrbitMayfly",p.getAbilities().mayfly&&!ModCapabilities.getOrThrow(p).isFlying());
            if(!p.getAbilities().mayfly){p.getAbilities().mayfly=true;p.onUpdateAbilities();}p.fallDistance=0;
            if(p.getY()< -950||p.getY()>950){p.teleportTo(p.getX(),Math.max(-940,Math.min(940,p.getY())),p.getZ());p.setDeltaMovement(p.getDeltaMovement().multiply(1,-.3,1));}
            var system=system(p.serverLevel());var data=GalaxyData.get(p.server);
            for(var body:system.planets()){
                Vec3 center=SpaceMath.center(body);double radius=SpaceMath.radius(body.radius()),distance=p.position().distanceTo(center);
                if(distance>radius+90)continue;
                Vec3 n=p.position().subtract(center).normalize();if(n.lengthSqr()<.5)n=new Vec3(0,1,0);
                if(!body.kind().landable()){
                    if(distance<radius+20){Vec3 at=center.add(n.scale(radius+22));p.teleportTo(at.x,at.y,at.z);p.setDeltaMovement(n.scale(.3));}
                    if(now%40==0&&!p.isCreative())p.hurt(p.damageSources().magic(),1);continue;
                }
                if(pending(p))continue;var ref=new GalaxyCatalog.Ref(system.id(),body.index());var discovery=data.discovery(ref);
                if(discovery!=null&&discovery.destroyed()&&!discovery.restored())continue;
                double[] surface=SpaceMath.surface(n,body.radius());var anchor=p.getPersistentData().getCompound("dbzenithLaunchAnchor");
                if(anchor.getString("ref").equals(ref.key())&&SpaceMath.normal(anchor.getDouble("x"),anchor.getDouble("z"),body.radius()).dot(n)>.997){surface[0]=anchor.getDouble("x");surface[1]=anchor.getDouble("z");}
                if(distance<radius+65&&velocity(p).dot(n)<.03){
                    Vec3 motion=velocity(p);Vec3 entryVelocity=new Vec3(motion.x*.15,-Math.max(.8,Math.min(3,Math.abs(motion.dot(n)))),motion.z*.15);
                    if(PlanetRuntime.reenter(p,ref,surface[0],surface[1],entryVelocity))prepare(p);
                }
            }
            for(int i=0;i<system.stars().size();i++)if(p.position().distanceTo(SpaceMath.star(i,system.stars().size()))<950&&now%20==0&&!p.isCreative()){p.hurt(p.damageSources().onFire(),6);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,50,0,false,false));break;}
            if(now%100==0)checkpoint(p);
        }else if(p.getY()>=SpaceMath.ASCENT&&surfaceRef(p.serverLevel())!=null&&!pending(p)&&now>p.getPersistentData().getLong("dbzenithAscentRetry")){
            if(p.getAbilities().flying||p.getVehicle() instanceof SpacePodEntity pod&&pod.state()==SpacePodEntity.ORBIT_LAUNCH){if(!ascend(p)){p.getPersistentData().putLong("dbzenithAscentRetry",now+100);if(p.getVehicle() instanceof SpacePodEntity pod)pod.orbitRefused();}}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;var s=state(server);long now=server.getTickCount();
        for(var it=s.pending.entrySet().iterator();it.hasNext();){var entry=it.next();var q=entry.getValue();var p=server.getPlayerList().getPlayer(entry.getKey());var l=server.getLevel(q.target);
            boolean cancel=p==null||l==null||!p.isAlive()||!p.level().dimension().equals(q.origin)||now-q.tick>600;
            if(!cancel&&!q.ready.isDone())continue;
            if(!cancel)try{if(q.ready.join()){
                SpacePodEntity old=p.getVehicle() instanceof SpacePodEntity pod?pod:null;p.stopRiding();p.teleportTo(l,q.position.x,q.position.y,q.position.z,p.getYRot(),p.getXRot());
                if(old!=null){old.discard();SpacePodEntity.orbit(p,q.position,q.velocity);}p.setDeltaMovement(q.velocity);p.connection.send(new ClientboundSetEntityMotionPacket(p));p.fallDistance=0;
                checkpoint(p);PlanetEnvironment.enter(p);scene(p,true,q.velocity);s.used.put(l.dimension(),now);
            }}catch(CompletionException ex){DBZenith.LOGGER.error("Orbital preload failed",ex);p.displayClientMessage(Component.literal("Orbital transfer failed. Your departure world is safe."),false);}
            if(l!=null)l.getChunkSource().removeRegionTicket(TRANSIT,q.chunk,1,entry.getKey());it.remove();
        }
        if(now%20==0)for(var l:server.getAllLevels())if(isSpace(l)){
            boolean pinned=!l.players().isEmpty()||!l.getForcedChunks().isEmpty()||s.pending.values().stream().anyMatch(q->q.target.equals(l.dimension()));
            if(pinned)s.used.put(l.dimension(),now);
            else if(now-s.used.computeIfAbsent(l.dimension(),k->now)>PlanetConfig.IDLE_TICKS.get()&&!s.retiring.contains(l.dimension())){s.retiring.add(l.dimension());InfiniverseAPI.get().markDimensionForUnregistration(server,l.dimension());break;}
        }
    }
}
