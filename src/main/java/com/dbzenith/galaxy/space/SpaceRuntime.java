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
        final Map<UUID,Motion> motion=new HashMap<>();final Map<UUID,Track> tracks=new HashMap<>();
        final Map<ResourceKey<Level>,GalaxyCatalog.SystemSpec> systems=new HashMap<>();final Set<ResourceKey<Level>> retiring=new HashSet<>();
    }
    /** A player's last few positions, one per server tick, to measure their motion when no fresh client hint is at hand. */
    private static final class Track {final Vec3[] at=new Vec3[6];int n;ResourceKey<Level> dimension;
        void add(ServerPlayer p){if(!p.level().dimension().equals(dimension)){dimension=p.level().dimension();n=0;}at[n%at.length]=p.position();n++;}
        Vec3 velocity(){if(n<2)return null;int k=Math.min(n,at.length)-1;return at[(n-1)%at.length].subtract(at[(n-1-k)%at.length]).scale(1d/k);}
        Vec3 acceleration(){if(n<at.length)return Vec3.ZERO;Vec3 p0=at[n%6],p2=at[(n+2)%6],p3=at[(n+3)%6],p5=at[(n+5)%6];return p5.subtract(p3).subtract(p2.subtract(p0)).scale(1/6d);}   // (later velocity - earlier velocity) / 3 ticks, each over 2
    }
    private record Transit(ResourceKey<Level> origin,ResourceKey<Level> target,GalaxyCatalog.Ref ref,Vec3 position,Vec3 velocity,ChunkPos chunk,long tick,CompletableFuture<Boolean> ready,SpaceTelemetry.Kind kind,boolean await){}
    /** A crossing between a surface and orbit (SpaceFrames): where, how fast and which way, and the untouched velocity it came from (for telemetry). */
    public record Crossing(Vec3 position,Vec3 velocity,float yaw,float pitch,Vec3 rawVelocity){}
    /** The frame of a planet for this player: Earth maps a disc centred on their own departure point (or world spawn), every other planet its whole finite surface. */
    public static SpaceFrames.Planet frame(ServerPlayer p,GalaxyCatalog.Ref ref,GalaxyCatalog.Body body){return frame(p,ref,body,0);}
    /** The same, turned as it will be {@code ahead} ticks from now (a forecast crossing lands on the ground that will be under it then). */
    public static SpaceFrames.Planet frame(ServerPlayer p,GalaxyCatalog.Ref ref,GalaxyCatalog.Body body,double ahead){
        double cx=0,cz=0;
        if(SpaceFrames.earth(body,ref)){var a=p.getPersistentData().getCompound("dbzenithLaunchAnchor");
            if(a.getString("ref").equals(ref.key())){cx=a.getDouble("x");cz=a.getDouble("z");}else{var s=p.server.overworld().getSharedSpawnPos();cx=s.getX()+.5;cz=s.getZ()+.5;}}
        var o=p.server.overworld();double day=o.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)?ahead:0;
        var spin=SpaceFrames.spinAt(SpaceFrames.earth(body,ref),body.tilt(),body.dayTicks(),o.getGameTime()+ahead,o.getDayTime()+day,SpaceMath.center(body));   // turned as it is then (phase 3)
        return SpaceFrames.planet(body,ref,cx,cz,spin);
    }
    /** Leaving a surface: the orbital point over the player, their velocity and view carried through the local ground frame (never falling back through the boundary). */
    public static Crossing ascentState(ServerPlayer p,SpaceFrames.Planet f){
        Vec3 surface=new Vec3(p.getX(),Math.max(SpaceFrames.ASCENT_Y,p.getY()),p.getZ());var basis=SpaceFrames.basis(f,surface.x,surface.z);
        Vec3 raw=momentum(p),kept=new Vec3(raw.x,Math.max(.18,raw.y),raw.z);float[] look=SpaceFrames.look(basis,p.getYRot(),p.getXRot(),true);
        return new Crossing(SpaceFrames.toOrbit(f,surface),SpaceFrames.cap(SpaceFrames.toOrbitVector(basis,kept),5.5),look[0],look[1],SpaceFrames.toOrbitVector(basis,raw));
    }
    /** Ticks after any transfer during which no other crossing starts (phase 3: one crossing per crossing). */
    public static final String SETTLE="dbzenithSettleUntil";
    /** The clock for stamps saved with a player: the world's game time, which survives restarts (the server's tick count starts again at 0). */
    public static long clock(ServerPlayer p){return p.server.overworld().getGameTime();}
    /** Entering an atmosphere: the surface point under the player at the entry height, their velocity and view carried down (always descending, at least gently). */
    public static Crossing entryState(ServerPlayer p,SpaceFrames.Planet f){return entryState(p,f,p.position());}
    /** The same, for the crossing at an orbital point {@code at} (the predicted one, while preparing). */
    public static Crossing entryState(ServerPlayer p,SpaceFrames.Planet f,Vec3 at){
        Vec3 surface=SpaceFrames.toSurface(f,at);var basis=SpaceFrames.basis(f,surface.x,surface.z);
        Vec3 raw=SpaceFrames.toSurfaceVector(basis,momentum(p));float[] look=SpaceFrames.look(basis,p.getYRot(),p.getXRot(),false);
        return new Crossing(new Vec3(surface.x,Math.max(300,Math.min(SpaceFrames.ENTRY_Y,surface.y)),surface.z),SpaceFrames.cap(new Vec3(raw.x,Math.min(-.3,raw.y),raw.z),5.5),look[0],look[1],raw);
    }
    /**
     * A prepared re-entry's progress: 1 once the player is at or below the entry altitude (cross now), 0 while still above
     * it and coming, -1 when they left that system or turned well away (give the preparation up).
     */
    public static int entryProgress(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!isSpace(p.level())||!ref.system().equals(id(p.level().dimension())))return -1;
        var planets=system(p.serverLevel()).planets();if(ref.planet()<0||ref.planet()>=planets.size())return -1;
        var f=frame(p,ref,planets.get(ref.planet()));double alt=SpaceFrames.altitude(f,p.position());
        if(alt<=f.entryAltitude())return 1;
        Vec3 n=p.position().subtract(f.center()).normalize();double inbound=-momentum(p).dot(n);
        return alt>f.entryAltitude()+Math.max(16,inbound*160)+f.entryAltitude()&&inbound<.02?-1:0;
    }
    /**
     * Where a player heading into a planet's atmosphere will cross it, on the ground that will be under that point by then
     * (the planet turns meanwhile); null while they are not heading in within the preload lead (about six seconds).
     */
    public static Crossing entryForecast(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!isSpace(p.level())||!ref.system().equals(id(p.level().dimension())))return null;
        var planets=system(p.serverLevel()).planets();if(ref.planet()<0||ref.planet()>=planets.size())return null;
        var body=planets.get(ref.planet());var f=frame(p,ref,body);double alt=SpaceFrames.altitude(f,p.position());
        Vec3 n=p.position().subtract(f.center()).normalize(),m=momentum(p);double inbound=-m.dot(n);
        if(inbound<=.03)return null;
        var track=state(p.server).tracks.get(p.getUUID());double a=track==null||!p.level().dimension().equals(track.dimension)?0:SpaceFrames.clamp(-track.acceleration().dot(n),0,.2);
        double t=arrival(Math.max(0,alt-f.entryAltitude()),Math.max(.03,inbound),a);   // a dive under thrust speeds up: without that the forecast runs long and lands far off
        if(t>120&&alt-f.entryAltitude()>4)return null;                                  // the preload starts about six seconds before the crossing
        Vec3 drift=m.subtract(n.scale(m.dot(n))),ahead=p.position().add(drift.scale(t)).subtract(f.center());
        var then=frame(p,ref,body,t);
        return entryState(p,then,then.center().add(ahead.normalize().scale(then.orbitalRadius()+then.entryAltitude())));
    }
    /** Ticks to close {@code distance} at speed {@code v}, gaining {@code a} per tick up to the flight cap (3 blocks a tick unless already faster). */
    public static double arrival(double distance,double v,double a){
        double top=Math.max(v,3);if(a<1e-4||v>=top)return distance/v;
        double rise=(top-v)/a,covered=v*rise+.5*a*rise*rise;
        return distance<=covered?(Math.sqrt(v*v+2*a*distance)-v)/a:rise+(distance-covered)/top;
    }
    /** Holds a player at the edge of the atmosphere while its terrain is still being prepared (the client keeps them there). */
    public static void hold(ServerPlayer p,GalaxyCatalog.Ref ref,boolean on){
        var t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());t.putBoolean("hold",on);
        if(on&&isSpace(p.level())){var planets=system(p.serverLevel()).planets();if(ref.planet()>=0&&ref.planet()<planets.size()){var f=frame(p,ref,planets.get(ref.planet()));t.putInt("holdBody",ref.planet());t.putDouble("holdAltitude",f.entryAltitude());}}
        ModNetwork.sendTo(p,new SpaceScenePacket(t));
    }
    /** At a re-entry's commit: the crossing from the player's current orbital state, or null when they are no longer in that system. */
    public static Crossing entryCommit(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!isSpace(p.level())||!ref.system().equals(id(p.level().dimension())))return null;
        var planets=system(p.serverLevel()).planets();if(ref.planet()<0||ref.planet()>=planets.size())return null;
        return entryState(p,frame(p,ref,planets.get(ref.planet())));
    }
    private record Motion(Vec3 velocity,int tick,ResourceKey<Level> dimension){}
    public static boolean velocityHint(ServerPlayer p,Vec3 v){
        if(!p.isAlive()||!Double.isFinite(v.lengthSqr())||v.lengthSqr()>30.25001||!isSpace(p.level())&&(p.getY()<450||surfaceRef(p.serverLevel())==null))return false;
        state(p.server).motion.put(p.getUUID(),new Motion(v,p.server.getTickCount(),p.level().dimension()));return true;
    }
    /** The player's motion: their client's fresh hint, else what their last few positions measure (a server player's own delta movement is not their flight). */
    public static Vec3 momentum(ServerPlayer p){var s=state(p.server);var m=s.motion.get(p.getUUID());if(m!=null&&m.dimension.equals(p.level().dimension())&&p.server.getTickCount()-m.tick<30)return m.velocity;
        var t=s.tracks.get(p.getUUID());var v=t==null||!p.level().dimension().equals(t.dimension)?null:t.velocity();return v!=null&&v.lengthSqr()<=30.25?v:p.getDeltaMovement();}
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
        PlanetRuntime.isolateBorder(level);level.getWorldBorder().setCenter(0,0);level.getWorldBorder().setSize(240000);s.used.put(key,(long)server.getTickCount());Remnants.configure(level);return level;
    }
    public static boolean ascend(ServerPlayer p){return ascend(p,false);}
    /** {@code await}: prepared ahead by a flight that is still climbing; the transfer waits for the crossing itself (phase 3). Evacuations, pods and commands go at once. */
    public static boolean ascend(ServerPlayer p,boolean await){
        var ref=surfaceRef(p.serverLevel());if(ref==null||ModCapabilities.getOrThrow(p).isDead()||!p.isAlive())return false;
        try{
            var body=GalaxyCatalog.body(p.server.overworld().getSeed(),GalaxyData.get(p.server).salt(),ref);
            anchor(p,ref);var c=ascentState(p,frame(p,ref,body));      // the commit recomputes this from where the player then is
            return queue(p,open(p.server,ref.system()),ref,c.position(),c.velocity(),SpaceTelemetry.Kind.ASCENT,await);
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);return false;}
    }
    /** Operator preview of a body's daylight side, without creating its surface dimension. */
    public static boolean approach(ServerPlayer p,int index){
        if(!isSpace(p.level()))return false;var system=system(p.serverLevel());if(index<0||index>=system.planets().size())return false;
        var b=system.planets().get(index);Vec3 c=SpaceMath.center(b),at=c.add(new Vec3(0,128,0).subtract(c).normalize().scale(SpaceMath.radius(b.radius())+900));
        Vec3 look=c.subtract(at).normalize();float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(look.y));
        var from=SpaceTelemetry.Sample.of(p,momentum(p));
        p.teleportTo(p.serverLevel(),at.x,at.y,at.z,yaw,pitch);p.setDeltaMovement(Vec3.ZERO);velocityHint(p,Vec3.ZERO);scene(p,true,Vec3.ZERO);
        SpaceTelemetry.record(p,SpaceTelemetry.Kind.APPROACH,from,SpaceTelemetry.Sample.of(p,Vec3.ZERO),0,at,Vec3.ZERO,new float[]{yaw,pitch});return true;
    }
    /** Records where the player left a planet's surface (the departure point a return lands back on). */
    private static void anchor(ServerPlayer p,GalaxyCatalog.Ref ref){var anchor=new CompoundTag();anchor.putString("ref",ref.key());anchor.putDouble("x",p.getX());anchor.putDouble("z",p.getZ());p.getPersistentData().put("dbzenithLaunchAnchor",anchor);}
    private static boolean queue(ServerPlayer p,ServerLevel level,GalaxyCatalog.Ref ref,Vec3 at,Vec3 v,SpaceTelemetry.Kind kind){return queue(p,level,ref,at,v,kind,false);}
    private static boolean queue(ServerPlayer p,ServerLevel level,GalaxyCatalog.Ref ref,Vec3 at,Vec3 v,SpaceTelemetry.Kind kind,boolean await){
        State s=state(p.server);if(s.pending.containsKey(p.getUUID()))return false;
        if(s.pending.size()>=16)return false;ChunkPos chunk=new ChunkPos(BlockPos.containing(at));level.getChunkSource().addRegionTicket(TRANSIT,chunk,1,p.getUUID());
        var ready=CompletableFuture.supplyAsync(()->level.getChunkSource().getChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true),Util.backgroundExecutor()).thenCompose(f->f).thenApply(e->e.left().isPresent());
        s.pending.put(p.getUUID(),new Transit(p.level().dimension(),level.dimension(),ref,at,v,chunk,p.server.getTickCount(),ready,kind,await));prepare(p);return true;
    }
    /**
     * Hyperspeed into a system (the space rework, phase 5): you arrive on the line you came along, short of the target
     * planet and still moving that way (the client eases you to a stop), never in a star's glare or beside another body.
     */
    public static boolean jump(ServerPlayer p,GalaxyCatalog.Ref ref){
        if(!isSpace(p.level())||pending(p)||state(p.server).pending.size()>=16)return false;
        try{var a=arrival(p.server,GalaxyData.current(p),ref,p.position());return queue(p,open(p.server,ref.system()),ref,a.position(),a.velocity(),SpaceTelemetry.Kind.HYPERSPEED);}
        catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);return false;}
    }
    /** The course from one system to another, in light years (catalogue positions; galactic axes are the orbital axes). */
    public static Vec3 course(MinecraftServer server,GalaxyCatalog.Id from,GalaxyCatalog.Id to){
        long seed=server.overworld().getSeed(),salt=GalaxyData.get(server).salt();var a=GalaxyCatalog.system(seed,salt,from);var b=GalaxyCatalog.system(seed,salt,to);
        return new Vec3(b.x()-a.x(),b.y()-a.y(),b.z()-a.z());
    }
    /** Where a course arrives: {@code from} the system you left (or, within one system, from {@code here}). */
    public record Arrival(Vec3 position,Vec3 velocity){}
    public static Arrival arrival(MinecraftServer server,GalaxyCatalog.Id from,GalaxyCatalog.Ref ref,Vec3 here){
        var data=GalaxyData.get(server);var spec=GalaxyCatalog.system(server.overworld().getSeed(),data.salt(),ref.system());
        var b=spec.planets().get(Math.max(0,Math.min(spec.planets().size()-1,ref.planet())));Vec3 c=SpaceMath.center(b);double r=SpaceMath.radius(b.radius());
        Vec3 course=course(server,from,ref.system());
        if(course.lengthSqr()<1e-9)course=c.subtract(here);
        Vec3 dir=course.lengthSqr()<1e-9?c.subtract(new Vec3(0,128,0)).normalize():course.normalize(),at=c.subtract(dir.scale(r+900));
        if(!clear(spec,at,b.index())){at=c.add(new Vec3(0,128,0).subtract(c).normalize().scale(r+900));dir=c.subtract(at).normalize();}   // the old sunward approach
        return new Arrival(at,dir.scale(2));
    }
    /** Clear of every star's glare and every other body. */
    static boolean clear(GalaxyCatalog.SystemSpec spec,Vec3 at,int target){
        for(int i=0;i<spec.stars().size();i++)if(at.distanceTo(SpaceMath.star(i,spec.stars().size()))<1500)return false;
        for(var o:spec.planets())if(o.index()!=target&&at.distanceTo(SpaceMath.center(o))<SpaceMath.radius(o.radius())*4+300)return false;
        return true;
    }
    /** Riding a pod someone else pilots (they cross with the pilot, never on their own). */
    public static boolean passenger(ServerPlayer p){return p.getVehicle()!=null&&p.getVehicle().getFirstPassenger()!=p;}
    public static boolean pending(ServerPlayer p){return state(p.server).pending.containsKey(p.getUUID())||PlanetRuntime.pending(p);}
    public static void cancel(ServerPlayer p){var s=state(p.server);var q=s.pending.remove(p.getUUID());if(q!=null){var l=p.server.getLevel(q.target);if(l!=null)l.getChunkSource().removeRegionTicket(TRANSIT,q.chunk,1,p.getUUID());}}
    public static void prepare(ServerPlayer p){var t=new CompoundTag();t.putBoolean("preparing",true);t.putString("dimension",p.level().dimension().location().toString());ModNetwork.sendTo(p,new SpaceScenePacket(t));}
    public static void scene(ServerPlayer p,boolean transition,Vec3 velocity){
        GalaxyData.get(p.server).chart(p.getUUID(),GalaxyData.current(p));StarField.sync(p);   // being in a system charts it; its sky is the catalogue around it
        var ref=surfaceRef(p.serverLevel());var id=id(p.level().dimension());if(id==null&&ref!=null)id=ref.system();
        CompoundTag t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());
        if(id!=null){t.put("system",GalaxyService.survey(p,GalaxyService.SYSTEM,id,-1,"",0).getCompound("system"));t.putString("id",id.toString());if(ref!=null)t.putInt("surfacePlanet",ref.planet());
            if(id.equals(GalaxyCatalog.EARTH))for(var raw:t.getCompound("system").getList("bodies",net.minecraft.nbt.Tag.TAG_COMPOUND)){var b=(CompoundTag)raw;if(b.getInt("index")!=0)continue;   // Earth's map is centred on your departure (phase 4)
                var earth=new GalaxyCatalog.Ref(id,0);int[] a=PlanetMaps.anchor(p,earth,GalaxyCatalog.body(p.server.overworld().getSeed(),GalaxyData.get(p.server).salt(),earth));b.putInt("anchorX",a[0]);b.putInt("anchorZ",a[1]);}}
        t.putBoolean("transition",transition);t.putDouble("vx",velocity.x);t.putDouble("vy",velocity.y);t.putDouble("vz",velocity.z);
        ModNetwork.sendTo(p,new SpaceScenePacket(t));
    }
    private static void checkpoint(ServerPlayer p){if(!isSpace(p.level()))return;var t=new CompoundTag();t.putString("system",id(p.level().dimension()).toString());t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());p.getPersistentData().put(CHECKPOINT,t);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){
        var t=p.getPersistentData().getCompound(CHECKPOINT);
        if(!isSpace(p.level())&&!t.isEmpty())try{
            var id=GalaxyCatalog.Id.parse(t.getString("system"));Vec3 at=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));
            if(!Double.isFinite(at.lengthSqr())||Math.abs(at.x)>119000||Math.abs(at.z)>119000||at.y< -950||at.y>950)throw new IllegalArgumentException("Invalid orbital checkpoint");
            queue(p,open(p.server,id),new GalaxyCatalog.Ref(id,0),at,Vec3.ZERO,SpaceTelemetry.Kind.RESTORE);
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);}
        scene(p,false,Vec3.ZERO);
    }}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){checkpoint(p);state(p.server).motion.remove(p.getUUID());state(p.server).tracks.remove(p.getUUID());}}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){
        state(p.server).motion.remove(p.getUUID());
        if(isSpace(p.level())||surfaceRef(p.serverLevel())!=null)scene(p,false,Vec3.ZERO);   // any way in (a command, a portal, another mod): the client gets this place's sky and system (phase 5)
        if(isSpace(p.level()))checkpoint(p);else p.getPersistentData().remove(CHECKPOINT);
        if(!isSpace(p.level())&&p.getPersistentData().contains("dbzenithOrbitMayfly")){
            p.getAbilities().mayfly=p.isCreative()||p.isSpectator()||ModCapabilities.getOrThrow(p).isFlying()||p.getPersistentData().getBoolean("dbzenithOrbitMayfly");
            p.getPersistentData().remove("dbzenithOrbitMayfly");p.onUpdateAbilities();
        }scene(p,false,Vec3.ZERO);
    }}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){STATES.remove(e.getServer());}
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent e){for(var l:e.getServer().getAllLevels())if(isSpace(l)){
        PlanetRuntime.isolateBorder(l);l.getWorldBorder().setCenter(0,0);l.getWorldBorder().setSize(240000);state(e.getServer()).used.put(l.dimension(),(long)e.getServer().getTickCount());Remnants.configure(l);
    }}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void save(UnregisterDimensionEvent e){if(isSpace(e.getLevel())){state(e.getLevel().getServer()).retiring.add(e.getLevel().dimension());e.getLevel().save(null,true,false);}}
    @SubscribeEvent public static void close(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel l&&isSpace(l)&&l.getServer().getLevel(l.dimension())==null){
        try{l.close();}catch(java.io.IOException ex){throw new IllegalStateException("Failed to close orbit storage",ex);}var s=state(l.getServer());s.used.remove(l.dimension());s.systems.remove(l.dimension());s.retiring.remove(l.dimension());
    }}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!PlanetConfig.SPACE.get()&&!isSpace(p.level()))return;
        long now=p.server.getTickCount();
        if(isSpace(p.level())||p.getY()>400)state(p.server).tracks.computeIfAbsent(p.getUUID(),k->new Track()).add(p);   // measured motion near and in space
        if(isSpace(p.level())){
            if(!p.getPersistentData().contains("dbzenithOrbitMayfly"))p.getPersistentData().putBoolean("dbzenithOrbitMayfly",p.getAbilities().mayfly&&!ModCapabilities.getOrThrow(p).isFlying());
            if(!p.getAbilities().mayfly){p.getAbilities().mayfly=true;p.onUpdateAbilities();}p.fallDistance=0;
            if(p.getY()< -950||p.getY()>950){p.teleportTo(p.getX(),Math.max(-940,Math.min(940,p.getY())),p.getZ());p.setDeltaMovement(p.getDeltaMovement().multiply(1,-.3,1));}
            var system=system(p.serverLevel());var data=GalaxyData.get(p.server);
            for(var body:system.planets()){
                Vec3 center=SpaceMath.center(body);double radius=SpaceMath.radius(body.radius()),distance=p.position().distanceTo(center);
                if(distance>radius+600)continue;                                   // above every entry altitude (SpaceFrames)
                Vec3 n=p.position().subtract(center).normalize();if(n.lengthSqr()<.5)n=new Vec3(0,1,0);
                if(!body.kind().landable()){if(distance>radius+90)continue;
                    if(distance<radius+20){Vec3 at=center.add(n.scale(radius+22));p.teleportTo(at.x,at.y,at.z);p.setDeltaMovement(n.scale(.3));}
                    if(now%40==0&&!p.isCreative())p.hurt(p.damageSources().magic(),1);continue;
                }
                if(pending(p)||Interstellar.active(p)||Cruise.active(p)||passenger(p)||clock(p)<p.getPersistentData().getLong(SETTLE))continue;var ref=new GalaxyCatalog.Ref(system.id(),body.index());var discovery=data.discovery(ref);   // a passenger crosses with the pilot
                if(discovery!=null&&discovery.destroyed()&&!discovery.restored())continue;
                var c=entryForecast(p,ref);                                        // the preload starts about five seconds before the crossing, under where we will cross (phase 3)
                if(c!=null&&PlanetRuntime.reenter(p,ref,c.position(),c.velocity(),c.yaw(),c.pitch()))prepare(p);
            }
            for(int i=0;i<system.stars().size();i++)if(p.position().distanceTo(SpaceMath.star(i,system.stars().size()))<950&&now%20==0&&!p.isCreative()){p.hurt(p.damageSources().onFire(),6);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,50,0,false,false));break;}
            if(now%100==0)checkpoint(p);
        }else if(surfaceRef(p.serverLevel())!=null){
            // the ascent (phase 3): preload a few seconds ahead of the crossing at the climb rate, commit only at the crossing itself,
            // release the preload when the climb turns back
            var q=state(p.server).pending.get(p.getUUID());boolean pod=p.getVehicle() instanceof SpacePodEntity sp&&sp.state()==SpacePodEntity.ORBIT_LAUNCH,flying=p.getAbilities().flying||pod;
            double vy=momentum(p).y,lead=Math.max(32,Math.min(200,vy*80));
            if(q!=null){if(q.await&&(!flying||p.getY()<SpaceFrames.ASCENT_Y-lead-64))cancel(p);}
            else if(flying&&!pending(p)&&clock(p)>p.getPersistentData().getLong("dbzenithAscentRetry")&&clock(p)>=p.getPersistentData().getLong(SETTLE)
                    &&(p.getY()>=SpaceFrames.ASCENT_Y||vy>.05&&p.getY()>=SpaceFrames.ASCENT_Y-lead)){
                if(!ascend(p,p.getY()<SpaceFrames.ASCENT_Y)){p.getPersistentData().putLong("dbzenithAscentRetry",clock(p)+100);if(p.getVehicle() instanceof SpacePodEntity sp)sp.orbitRefused();}}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;var s=state(server);long now=server.getTickCount();
        for(var it=s.pending.entrySet().iterator();it.hasNext();){var entry=it.next();var q=entry.getValue();var p=server.getPlayerList().getPlayer(entry.getKey());var l=server.getLevel(q.target);
            boolean cancel=p==null||l==null||!p.isAlive()||!p.level().dimension().equals(q.origin)||now-q.tick>600;
            if(!cancel&&!q.ready.isDone())continue;
            if(!cancel&&q.await&&p.getY()<SpaceFrames.ASCENT_Y)continue;   // prepared early: the transfer itself waits for the crossing
            if(!cancel)try{if(q.ready.join()){
                Vec3 at=q.position,v=q.velocity,expected=q.velocity;float yaw=p.getYRot(),pitch=p.getXRot();var from=SpaceTelemetry.Sample.of(p,momentum(p));
                if(q.kind==SpaceTelemetry.Kind.ASCENT&&surfaceRef(p.serverLevel())!=null){   // from where the player is now, not where they asked to leave
                    var body=GalaxyCatalog.body(server.overworld().getSeed(),GalaxyData.get(server).salt(),q.ref);anchor(p,q.ref);var c=ascentState(p,frame(p,q.ref,body));
                    at=c.position();v=c.velocity();yaw=c.yaw();pitch=c.pitch();expected=c.rawVelocity();
                }
                SpacePodEntity old=p.getVehicle() instanceof SpacePodEntity pod?pod:null;p.stopRiding();p.teleportTo(l,at.x,at.y,at.z,yaw,pitch);
                if(old!=null)SpacePodEntity.carry(old,p,at,v,false);   // the same pod (name, settings, passengers) carried across
                p.setDeltaMovement(v);p.connection.send(new ClientboundSetEntityMotionPacket(p));p.fallDistance=0;
                SpaceTelemetry.record(p,q.kind,from,SpaceTelemetry.Sample.of(p,v),(int)(now-q.tick),at,expected,new float[]{yaw,pitch});
                p.getPersistentData().putLong(SETTLE,clock(p)+40);
                checkpoint(p);PlanetEnvironment.enter(p);scene(p,true,v);s.used.put(l.dimension(),now);Interstellar.arrived(p);
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
