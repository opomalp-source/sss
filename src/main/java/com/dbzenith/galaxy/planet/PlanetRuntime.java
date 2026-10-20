package com.dbzenith.galaxy.planet;

import commoble.infiniverse.api.*;
import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.registry.ModItems;
import com.dbzenith.world.*;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.util.*;
import java.util.concurrent.*;

/** Sole owner of procedural world lifecycle. Old/static dimensions never enter the unload path. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class PlanetRuntime {
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final TicketType<UUID> LANDING=TicketType.create("dbzenith_planet_landing",Comparator.<UUID>naturalOrder(),600);
    private static final String RETURN="dbzenithPlanetCheckpoint";
    private static final class State {
        final Map<ResourceKey<Level>,Long> used=new HashMap<>();
        final Map<ResourceKey<Level>,GalaxyCatalog.Body> bodies=new HashMap<>();
        final Map<UUID,Pending> pending=new LinkedHashMap<>();
        final Set<ResourceKey<Level>> unloading=new HashSet<>();
    }
    private record Pending(GalaxyCatalog.Ref ref,ResourceKey<Level> origin,double ox,double oy,double oz,long start,
                           ResourceKey<Level> target,ChunkPos chunk,CompletableFuture<Boolean> ready,CompoundTag checkpoint) {}
    private static State state(MinecraftServer s){return STATES.computeIfAbsent(s,k->new State());}
    public static ResourceKey<Level> key(GalaxyCatalog.Ref ref){
        var id=ref.system();return ResourceKey.create(Registries.DIMENSION,new ResourceLocation(DBZenith.MOD_ID,"planet/v1/"+id.x()+"_"+id.y()+"_"+id.z()+"_"+id.slot()+"/"+ref.planet()));
    }
    public static boolean procedural(Level l){return l instanceof ServerLevel sl && sl.getChunkSource().getGenerator() instanceof PlanetGenerator;}
    public static GalaxyCatalog.Body body(ServerLevel l){
        if(!(l.getChunkSource().getGenerator() instanceof PlanetGenerator g))return null;
        return state(l.getServer()).bodies.computeIfAbsent(l.dimension(),k->GalaxyCatalog.body(l.getServer().overworld().getSeed(),GalaxyData.get(l.getServer()).salt(),g.ref));
    }
    public static ResourceKey<Level> legacy(GalaxyCatalog.Ref ref){
        if(ref.planet()!=0)return null;
        if(ref.system().equals(GalaxyCatalog.EARTH))return Level.OVERWORLD;
        if(ref.system().equals(GalaxyCatalog.NAMEK))return Planet.NAMEK.dimension();
        if(ref.system().equals(GalaxyCatalog.NORTH))return Planet.NORTHERN_PLANET.dimension();
        if(ref.system().equals(GalaxyCatalog.BEERUS))return Planet.BEERUS_PLANET.dimension();return null;
    }
    /** Bounded operator preview search; normal navigation uses the atlas. */
    public static GalaxyCatalog.Ref sample(MinecraftServer s,GalaxyCatalog.Kind kind){
        var data=GalaxyData.get(s);
        for(int x=200;x<240;x++)for(var b:GalaxyCatalog.system(s.overworld().getSeed(),data.salt(),new GalaxyCatalog.Id(x,7,-4,1)).planets())
            if(b.kind()==kind)return new GalaxyCatalog.Ref(new GalaxyCatalog.Id(x,7,-4,1),b.index());
        throw new IllegalArgumentException("No sample in this survey window");
    }
    public static int loaded(MinecraftServer s){int n=0;for(var l:s.getAllLevels())if(procedural(l))n++;return n;}
    private static boolean pinned(ServerLevel l){
        if(!l.players().isEmpty()||!l.getForcedChunks().isEmpty()||restoration(l))return true;
        return state(l.getServer()).pending.values().stream().anyMatch(p->p.target.equals(l.dimension()));
    }
    private static GalaxyCatalog.Ref fromKey(ResourceKey<Level> dimension){
        var id=dimension.location();if(!id.getNamespace().equals(DBZenith.MOD_ID)||!id.getPath().startsWith("planet/v1/"))return null;
        try{return GalaxyCatalog.Ref.parse(id.getPath().substring(10).replace('_',':'));}catch(IllegalArgumentException ex){return null;}
    }
    /** Keep vanilla bed metadata when the library removes an idle level, including across offline sessions. */
    @SubscribeEvent public static void preserveBed(PlayerSetSpawnEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getNewSpawn()==null&&fromKey(p.getRespawnDimension())!=null
                &&(state(p.server).unloading.contains(p.getRespawnDimension())||p.server.getLevel(p.getRespawnDimension())==null))e.setCanceled(true);
    }
    @SubscribeEvent public static void cloneBed(PlayerEvent.Clone e){
        if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p&&fromKey(old.getRespawnDimension())!=null&&old.getRespawnPosition()!=null)
            p.setRespawnPosition(old.getRespawnDimension(),old.getRespawnPosition(),old.getRespawnAngle(),old.isRespawnForced(),false);
    }
    /** Used by vanilla respawn and the existing Other World return; the bed's world may be idle/unloaded. */
    public static boolean restoreBed(ServerPlayer p){
        var ref=fromKey(p.getRespawnDimension());BlockPos bed=p.getRespawnPosition();if(ref==null||bed==null)return false;
        CompoundTag tag=new CompoundTag();tag.putString("ref",ref.key());tag.putDouble("x",bed.getX()+.5);tag.putDouble("y",bed.getY());tag.putDouble("z",bed.getZ()+.5);
        tag.putFloat("yaw",p.getRespawnAngle());tag.putBoolean("bed",true);tag.putBoolean("forced",p.isRespawnForced());
        return queue(p,ref,tag,true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void respawn(PlayerEvent.PlayerRespawnEvent e){
        if(!e.isEndConquered()&&e.getEntity() instanceof ServerPlayer p&&!Otherworld.isOtherworld(p.level())&&!ModCapabilities.getOrThrow(p).isDead())restoreBed(p);
    }
    public static boolean unload(ServerLevel l){
        if(!procedural(l)||pinned(l))return false;
        InfiniverseAPI.get().markDimensionForUnregistration(l.getServer(),l.dimension());return true;
    }
    /** Creation does not request any chunk; landing generation is asynchronous and separately ticketed. */
    private static boolean restoration(ServerLevel l){var ref=fromKey(l.dimension());return ref!=null&&PlanetDestruction.restoring(l.getServer(),ref);}
    public static ServerLevel openForRestoration(MinecraftServer server,GalaxyCatalog.Ref ref){
        if(!PlanetDestruction.restoring(server,ref))throw new IllegalArgumentException("No restoration is active.");return open(server,ref,true);
    }
    public static ServerLevel open(MinecraftServer server,GalaxyCatalog.Ref ref){return open(server,ref,false);}
    private static ServerLevel open(MinecraftServer server,GalaxyCatalog.Ref ref,boolean recovery){
        var data=GalaxyData.get(server);var descriptor=GalaxyCatalog.body(server.overworld().getSeed(),data.salt(),ref);
        if(!descriptor.kind().landable())throw new IllegalArgumentException("Gas giants have no solid landing surface. Upper-cloud travel arrives with space flight.");
        var d=data.discovery(ref);if(!recovery&&(data.destroyed(ref)||PlanetDestruction.restoring(server,ref)))throw new IllegalArgumentException("That planet has been destroyed.");
        var old=legacy(ref);if(old!=null)return server.getLevel(old);
        var key=key(ref);var level=server.getLevel(key);if(level!=null)return level;
        if(loaded(server)>=PlanetConfig.MAX_LOADED.get())throw new IllegalArgumentException("All planet slots are occupied. Leave a planet and allow it to unload first.");
        var biomes=server.registryAccess().registryOrThrow(Registries.BIOME);
        var biome=biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME,new ResourceLocation(DBZenith.MOD_ID,"planet_"+descriptor.kind().name().toLowerCase(Locale.ROOT))));
        var types=server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        var type=types.getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE,new ResourceLocation(DBZenith.MOD_ID,"planet")));
        level=InfiniverseAPI.get().getOrCreateLevel(server,key,()->new LevelStem(type,new PlanetGenerator(new FixedBiomeSource(biome),descriptor.seed(),ref.key(),descriptor.kind().ordinal(),descriptor.radius(),1)));
        configure(level);state(server).used.put(key,(long)server.getTickCount());return level;
    }
    private static void configure(ServerLevel l){
        if(!(l.getChunkSource().getGenerator() instanceof PlanetGenerator g))return;
        isolateBorder(l);
        l.getWorldBorder().setCenter(0,0);l.getWorldBorder().setSize(g.radius*2d);l.getWorldBorder().setWarningBlocks(24);
        l.getWorldBorder().setDamageSafeZone(1);l.getWorldBorder().setDamagePerBlock(.2);
    }
    public static void isolateBorder(ServerLevel l){
        try {
            var field=ObfuscationReflectionHelper.findField(WorldBorder.class,"f_61905_");
            @SuppressWarnings("unchecked") var listeners=(List<BorderChangeListener>)field.get(l.getServer().overworld().getWorldBorder());
            var target=ObfuscationReflectionHelper.findField(BorderChangeListener.DelegateBorderChangeListener.class,"f_61864_");
            for(var listener:new ArrayList<>(listeners))if(listener instanceof BorderChangeListener.DelegateBorderChangeListener delegate && target.get(delegate)==l.getWorldBorder())
                l.getServer().overworld().getWorldBorder().removeListener(listener);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot isolate procedural planet border",ex);}
    }
    public static boolean hasPod(ServerPlayer p){return p.isCreative() || p.getVehicle() instanceof SpacePodEntity || p.getMainHandItem().is(ModItems.SPACE_POD.get())||p.getOffhandItem().is(ModItems.SPACE_POD.get());}
    public static boolean travel(ServerPlayer p,GalaxyCatalog.Ref ref){return queue(p,ref,null,false);}
    public static boolean transmit(ServerPlayer p,GalaxyCatalog.Ref ref){
        var d=ModCapabilities.getOrThrow(p);if(!com.dbzenith.galaxy.space.Interstellar.canTransmit(p)||!d.hasFlag("planet_landing:"+ref.key()))return false;
        var anchor=GalaxyData.get(p.server).arrivals(p.getUUID()).get(ref);var tag=new CompoundTag();tag.putBoolean("transmission",true);tag.putString("ref",ref.key());tag.putFloat("yaw",anchor==null?p.getYRot():anchor.yaw());
        tag.putDouble("x",anchor==null?8:anchor.x());tag.putDouble("y",anchor==null?72:anchor.y());tag.putDouble("z",anchor==null?8:anchor.z());tag.putBoolean("safeLanding",true);
        return queue(p,ref,tag,true);
    }
    public static boolean pending(ServerPlayer p){return state(p.server).pending.containsKey(p.getUUID());}
    /** Queues a re-entry to a surface point (SpaceFrames: the point under the crossing), arriving with this velocity and view. */
    public static boolean reenter(ServerPlayer p,GalaxyCatalog.Ref ref,net.minecraft.world.phys.Vec3 at,net.minecraft.world.phys.Vec3 velocity,float yaw,float pitch){
        if(!com.dbzenith.galaxy.space.Interstellar.accessible(p,ref))return false;
        var tag=new CompoundTag();tag.putString("ref",ref.key());tag.putDouble("x",at.x);tag.putDouble("y",at.y);tag.putDouble("z",at.z);tag.putFloat("yaw",yaw);tag.putFloat("pitch",pitch);
        tag.putBoolean("entry",true);tag.putDouble("vx",velocity.x);tag.putDouble("vy",velocity.y);tag.putDouble("vz",velocity.z);return queue(p,ref,tag,true);
    }
    public static boolean home(ServerPlayer p){
        if(!hasPod(p)){p.displayClientMessage(Component.literal("Hold a Space Pod to plot a course."),true);return false;}
        if(p.level().dimension().equals(Level.OVERWORLD)){p.getPersistentData().remove(RETURN);return true;}
        if(p.getVehicle() instanceof SpacePodEntity pod)return pod.launch(p,Planet.EARTH);
        boolean ok=Planet.travel(p,Planet.EARTH);if(ok){p.getPersistentData().remove(RETURN);PlanetEnvironment.enter(p);}return ok;
    }
    private static boolean queue(ServerPlayer p,GalaxyCatalog.Ref ref,CompoundTag checkpoint,boolean reconnect){
        var s=state(p.server);var data=ModCapabilities.getOrThrow(p);
        if(s.pending.containsKey(p.getUUID()))return false;
        if(!reconnect && (!hasPod(p)||TimeChamber.isIn(p)||data.isDead()||!p.isAlive()||p.isSpectator())){p.displayClientMessage(Component.literal("Hold a Space Pod in the living world to land."),true);return false;}
        if(!reconnect&&data.isOnCooldown("space_travel",p.level().getGameTime())&&!p.isCreative()){p.displayClientMessage(Component.literal("Your Space Pod is recharging."),true);return false;}
        if(!reconnect&&!p.isCreative()&&legacy(ref)==null&&!GalaxyData.current(p).equals(ref.system())){p.displayClientMessage(Component.literal("Reach this star system by hyperspeed before landing."),true);return false;}
        try {
            if(GalaxyData.get(p.server).destroyed(ref)||PlanetDestruction.restoring(p.server,ref))throw new IllegalArgumentException("This planet is sealed until restoration completes.");
            var legacy=legacy(ref);
            if(reconnect&&legacy!=null&&(checkpoint==null||!checkpoint.getBoolean("entry")&&!checkpoint.getBoolean("transmission"))){p.getPersistentData().remove(RETURN);return false;}
            if(legacy!=null&&!reconnect){
                Planet planet=Arrays.stream(Planet.values()).filter(t->t.dimension().equals(legacy)).findFirst().orElse(Planet.EARTH);
                return p.getVehicle() instanceof SpacePodEntity pod?pod.launch(p,planet):Planet.travel(p,planet);
            }
            if(s.pending.size()>=16)throw new IllegalArgumentException("Navigation is busy. Try again shortly.");
            ServerLevel level=open(p.server,ref);var target=level.dimension();
            if(target.equals(p.level().dimension())&&!reconnect)throw new IllegalArgumentException("You are already on that planet.");
            if(checkpoint!=null){
                double cx=checkpoint.getDouble("x"),cy=checkpoint.getDouble("y"),cz=checkpoint.getDouble("z");
                if(!Double.isFinite(cx)||!Double.isFinite(cy)||!Double.isFinite(cz)||Math.abs(cx)>level.getWorldBorder().getSize()/2-8||Math.abs(cz)>level.getWorldBorder().getSize()/2-8||cy< -63||cy>768)checkpoint=null;
            }
            double x=checkpoint==null?8:checkpoint.getDouble("x"),z=checkpoint==null?8:checkpoint.getDouble("z");
            ChunkPos chunk=new ChunkPos(BlockPos.containing(x,0,z));
            level.getChunkSource().addRegionTicket(LANDING,chunk,1,p.getUUID());
            // Calling getChunkFuture on the server thread would managedBlock; schedule its nonblocking path off-thread.
            int around=checkpoint!=null&&(checkpoint.getBoolean("bed")||checkpoint.getBoolean("transmission"))?1:0;
            List<CompletableFuture<Boolean>> chunks=new ArrayList<>();
            for(int dx=-around;dx<=around;dx++)for(int dz=-around;dz<=around;dz++){
                int cx=chunk.x+dx,cz=chunk.z+dz;
                chunks.add(CompletableFuture.supplyAsync(()->level.getChunkSource().getChunkFuture(cx,cz,ChunkStatus.FULL,true),Util.backgroundExecutor())
                        .thenCompose(f->f).thenApply(e->e.left().isPresent()));
            }
            CompletableFuture<Boolean> ready=CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenApply(v->chunks.stream().allMatch(CompletableFuture::join));
            s.pending.put(p.getUUID(),new Pending(ref,p.level().dimension(),p.getX(),p.getY(),p.getZ(),p.server.getTickCount(),target,chunk,ready,checkpoint));
            p.displayClientMessage(Component.literal(checkpoint!=null&&checkpoint.getBoolean("transmission")?"Sensing a familiar world. Stay still...":reconnect?"Restoring your planet location...":"Preparing landing terrain..."),true);return true;
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);return false;}
    }
    private static net.minecraft.world.phys.Vec3 transmissionLanding(ServerLevel l,ChunkPos chunk,ServerPlayer p){
        for(int x=chunk.getMinBlockX()+3;x<=chunk.getMinBlockX()+12;x++)for(int z=chunk.getMinBlockZ()+3;z<=chunk.getMinBlockZ()+12;z++){
            int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            for(int step=1;step<=4;step++){
                var floor=new BlockPos(x,y-step,z);var state=l.getBlockState(floor);if(!state.getFluidState().isEmpty()||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.POWDER_SNOW))break;
                var shape=state.getCollisionShape(l,floor);if(shape.isEmpty())continue;
                var at=new net.minecraft.world.phys.Vec3(x+.5,floor.getY()+shape.max(Direction.Axis.Y),z+.5);var box=p.getBoundingBox().move(at.subtract(p.position()));
                if(l.getWorldBorder().isWithinBounds(floor)&&l.noCollision(p,box))return at;break;
            }
        }return null;
    }
    private static BlockPos landing(ServerLevel l,ChunkPos chunk){
        int cx=chunk.getMinBlockX()+8,cz=chunk.getMinBlockZ()+8;
        for(int r=0;r<7;r++)for(int x=cx-r;x<=cx+r;x++)for(int z=cz-r;z<=cz+r;z++){
            int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);BlockPos floor=new BlockPos(x,y-1,z);var b=l.getBlockState(floor);
            if(b.isSolidRender(l,floor)&&b.getFluidState().isEmpty()&&!b.is(Blocks.MAGMA_BLOCK)&&l.getBlockState(floor.above()).isAir()&&l.getBlockState(floor.above(2)).isAir())return floor.above();
        }
        int y=Math.max(72,l.getHeight(Heightmap.Types.WORLD_SURFACE,cx,cz)+1);
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){
            l.setBlock(new BlockPos(x,y-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            for(int dy=0;dy<3;dy++)l.setBlock(new BlockPos(x,y+dy,z),Blocks.AIR.defaultBlockState(),3);
        }
        return new BlockPos(cx,y,cz);
    }
    private static void checkpoint(ServerPlayer p){
        if(!(p.serverLevel().getChunkSource().getGenerator() instanceof PlanetGenerator g))return;
        CompoundTag tag=new CompoundTag();tag.putString("ref",g.ref.key());tag.putDouble("x",p.getX());tag.putDouble("y",p.getY());tag.putDouble("z",p.getZ());tag.putFloat("yaw",p.getYRot());
        p.getPersistentData().put(RETURN,tag);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){
        if(procedural(p.serverLevel())){PlanetEnvironment.enter(p);return;}
        var tag=p.getPersistentData().getCompound(RETURN);if(!tag.isEmpty())try{queue(p,GalaxyCatalog.Ref.parse(tag.getString("ref")),tag,true);}catch(IllegalArgumentException ignored){p.getPersistentData().remove(RETURN);}
    }}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)checkpoint(p);}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){
        if(procedural(p.serverLevel()))checkpoint(p);else p.getPersistentData().remove(RETURN);PlanetEnvironment.enter(p);
    }}
    @SubscribeEvent public static void start(ServerStartedEvent e){for(var l:e.getServer().getAllLevels())if(procedural(l)){configure(l);state(e.getServer()).used.put(l.dimension(),(long)e.getServer().getTickCount());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){STATES.remove(e.getServer());}
    @SubscribeEvent(priority=EventPriority.HIGH) public static void beforeUnload(UnregisterDimensionEvent e){if(procedural(e.getLevel())){
        state(e.getLevel().getServer()).unloading.add(e.getLevel().dimension());e.getLevel().save(null,true,false);
    }}
    @SubscribeEvent public static void close(LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel l&&procedural(l)&&l.getServer().getLevel(l.dimension())==null){
            try{l.close();}catch(java.io.IOException ex){throw new IllegalStateException("Failed to close procedural planet storage",ex);}
            state(l.getServer()).used.remove(l.dimension());state(l.getServer()).bodies.remove(l.dimension());
            state(l.getServer()).unloading.remove(l.dimension());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        State s=state(server);long now=server.getTickCount();
        for(var it=s.pending.entrySet().iterator();it.hasNext();){var entry=it.next();Pending q=entry.getValue();ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());ServerLevel l=server.getLevel(q.target);
            boolean cancel=GalaxyData.get(server).destroyed(q.ref)||PlanetDestruction.restoring(server,q.ref)||p==null||l==null||now-q.start>600||!p.isAlive()||!p.level().dimension().equals(q.origin)||(q.checkpoint==null&&p.distanceToSqr(q.ox,q.oy,q.oz)>64);
            if(!cancel&&!q.ready.isDone())continue;
            boolean transmission=q.checkpoint!=null&&q.checkpoint.getBoolean("transmission");
            if(transmission&&(p==null||!com.dbzenith.galaxy.space.Interstellar.canTransmit(p)||p.distanceToSqr(q.ox,q.oy,q.oz)>4))cancel=true;
            if(!cancel){try{
                if(q.ready.join()){
                    boolean bed=q.checkpoint!=null&&q.checkpoint.getBoolean("bed");
                    BlockPos at=q.checkpoint!=null?BlockPos.containing(q.checkpoint.getDouble("x"),q.checkpoint.getDouble("y"),q.checkpoint.getDouble("z")):landing(l,q.chunk);double x=at.getX()+.5,y=at.getY(),z=at.getZ()+.5;
                    if(q.checkpoint!=null){x=q.checkpoint.getDouble("x");y=q.checkpoint.getDouble("y");z=q.checkpoint.getDouble("z");if(!l.getWorldBorder().isWithinBounds(BlockPos.containing(x,y,z))||y<-63||y>768){at=landing(l,q.chunk);x=at.getX()+.5;y=at.getY();z=at.getZ()+.5;}}
                    if(bed){
                        var safe=net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(l,at,q.checkpoint.getFloat("yaw"),q.checkpoint.getBoolean("forced"),true);
                        if(safe.isEmpty()){
                            p.setRespawnPosition(Level.OVERWORLD,null,0,false,false);
                            p.displayClientMessage(Component.literal("Your planet bed is missing or obstructed. You remain at your safe return point."),false);
                            l.getChunkSource().removeRegionTicket(LANDING,q.chunk,1,entry.getKey());it.remove();continue;
                        }
                        x=safe.get().x;y=safe.get().y;z=safe.get().z;
                    }
                    if(transmission){
                        var safe=transmissionLanding(l,q.chunk,p);
                        if(safe==null){p.displayClientMessage(Component.literal("Instant Transmission found no clear landing space. No ki spent."),true);l.getChunkSource().removeRegionTicket(LANDING,q.chunk,1,entry.getKey());it.remove();continue;}
                        // Revalidate the learned skill after async generation; charge only a completed cast.
                        if(!com.dbzenith.race.RacialSkillEffects.transmitAction(p,()->true)){
                            l.getChunkSource().removeRegionTicket(LANDING,q.chunk,1,entry.getKey());it.remove();continue;
                        }
                        x=safe.x;y=safe.y;z=safe.z;com.dbzenith.galaxy.space.Interstellar.blink(p);
                    }
                    boolean reentry=q.checkpoint!=null&&q.checkpoint.getBoolean("entry");
                    float yaw=q.checkpoint==null?p.getYRot():q.checkpoint.getFloat("yaw"),pitch=reentry?q.checkpoint.getFloat("pitch"):0;
                    net.minecraft.world.phys.Vec3 velocity=reentry?new net.minecraft.world.phys.Vec3(q.checkpoint.getDouble("vx"),q.checkpoint.getDouble("vy"),q.checkpoint.getDouble("vz")):net.minecraft.world.phys.Vec3.ZERO,raw=velocity;
                    var from=com.dbzenith.galaxy.space.SpaceTelemetry.Sample.of(p,com.dbzenith.galaxy.space.SpaceRuntime.momentum(p));
                    var expected=new net.minecraft.world.phys.Vec3(x,y,z);
                    if(reentry){var c=com.dbzenith.galaxy.space.SpaceRuntime.entryCommit(p,q.ref);   // the view and motion the player has now; the landing point stays where its terrain was prepared
                        if(c!=null){velocity=c.velocity();raw=c.rawVelocity();yaw=c.yaw();pitch=c.pitch();expected=c.position();}}
                    SpacePodEntity vessel=p.getVehicle() instanceof SpacePodEntity pod?pod:null;
                    p.stopRiding();
                    p.teleportTo(l,x,y,z,yaw,pitch);
                    if(vessel!=null){vessel.discard();SpacePodEntity.arrive(p,new net.minecraft.world.phys.Vec3(x,y,z));}p.fallDistance=0;p.setDeltaMovement(0,0,0);
                    if(reentry){
                        p.setDeltaMovement(velocity);
                        com.dbzenith.galaxy.space.SpaceTelemetry.record(p,com.dbzenith.galaxy.space.SpaceTelemetry.Kind.ENTRY,from,com.dbzenith.galaxy.space.SpaceTelemetry.Sample.of(p,velocity),
                                (int)(now-q.start),expected,raw,new float[]{yaw,pitch});
                        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));p.getPersistentData().putLong("dbzenithReentryUntil",now+140);
                        com.dbzenith.galaxy.space.SpaceRuntime.scene(p,true,velocity);
                    }
                    if(q.checkpoint==null)ModCapabilities.getOrThrow(p).setCooldown("space_travel",l.getGameTime()+com.dbzenith.config.DBZConfig.SERVER.spacePodRechargeTicks.get());
                    var d=GalaxyData.get(server);var body=body(l);if(body==null)body=GalaxyCatalog.body(server.overworld().getSeed(),d.salt(),q.ref);
                    com.dbzenith.galaxy.space.SpaceRuntime.scene(p,false,p.getDeltaMovement());
                    s.used.put(l.dimension(),now);checkpoint(p);PlanetEnvironment.enter(p);
                    if(transmission)com.dbzenith.galaxy.space.Interstellar.blink(p);
                    p.displayClientMessage(Component.literal("Landed on "+d.name(q.ref,body.name())),false);
                }else p.displayClientMessage(Component.literal("Landing terrain was unavailable. You remain at your departure point."),false);
            }catch(CompletionException ex){DBZenith.LOGGER.error("Planet landing generation failed",ex);p.displayClientMessage(Component.literal("Landing failed; your departure location is safe."),false);}}
            else if(p!=null)p.displayClientMessage(Component.literal("Landing cancelled: departure changed or terrain timed out."),true);
            if(l!=null)l.getChunkSource().removeRegionTicket(LANDING,q.chunk,1,entry.getKey());it.remove();
        }
        if(now%20==0)for(var l:server.getAllLevels())if(procedural(l)){
            if(pinned(l))s.used.put(l.dimension(),now);
            else if(now-s.used.getOrDefault(l.dimension(),now)>PlanetConfig.IDLE_TICKS.get()){unload(l);break;}
            for(var p:l.players())if(now%100==0)checkpoint(p);
        }
    }
}
