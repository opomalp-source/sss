package com.dbzenith.galaxy.planet;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.network.*;
import com.dbzenith.data.ModCapabilities;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.*;

/** Durable event-level destruction. Surface storage is sealed, never deleted; bounded scars are separately journaled. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class PlanetDestruction {
    private record Loading(ServerLevel level,ChunkPos chunk,CompletableFuture<Boolean> ready){}
    private static final Map<MinecraftServer,Map<GalaxyCatalog.Ref,Loading>> LOADING=new WeakHashMap<>();
    private static final TicketType<String> RESTORE=TicketType.create("dbzenith_planet_restore",Comparator.<String>naturalOrder(),600);
    public static ServerLevel level(MinecraftServer s,GalaxyCatalog.Ref ref){var legacy=PlanetRuntime.legacy(ref);return s.getLevel(legacy==null?PlanetRuntime.key(ref):legacy);}
    public static boolean restoring(MinecraftServer s,GalaxyCatalog.Ref ref){var e=DoomData.get(s).events.get(ref);return e!=null&&e.phase==DoomData.RESTORING;}
    public static String protection(MinecraftServer s,GalaxyCatalog.Ref ref){
        if(ref.equals(new GalaxyCatalog.Ref(GalaxyCatalog.EARTH,0)))return "Earth is the protected server return world.";
        if(PlanetConfig.DOOM_PROTECTED.get().contains(ref.key()))return "This planet is protected in the server configuration.";
        var d=GalaxyData.get(s).discovery(ref);var l=level(s,ref);
        if(PlanetConfig.DOOM_BASES.get()&&d!=null&&d.base())return "A player base protects this planet.";
        if(PlanetConfig.DOOM_SPAWNS.get()&&l!=null&&!l.getForcedChunks().isEmpty())return "Forced or spawn chunks protect this planet.";
        if(PlanetConfig.DOOM_SPAWNS.get())for(var p:s.getPlayerList().getPlayers())if(p.getRespawnPosition()!=null&&p.getRespawnDimension().equals(PlanetRuntime.legacy(ref)==null?PlanetRuntime.key(ref):PlanetRuntime.legacy(ref)))return "A player spawn protects this planet.";
        return "";
    }
    public static boolean begin(MinecraftServer s,GalaxyCatalog.Ref ref,UUID owner,String name){
        var data=DoomData.get(s);
        if(!PlanetConfig.DOOM_ENABLED.get()||!PlanetConfig.SPACE.get()||!protection(s,ref).isEmpty())return false;
        var old=data.events.get(ref);if(old!=null&&old.phase!=DoomData.RESTORED)return false;
        if(data.working.stream().map(data.events::get).filter(e->e.phase==DoomData.ACTIVE||e.phase==DoomData.RESTORING).count()>=PlanetConfig.DOOM_MAX.get())return false;
        var galaxy=GalaxyData.get(s);var b=GalaxyCatalog.body(s.overworld().getSeed(),galaxy.salt(),ref);if(!b.kind().landable())return false;
        galaxy.discover(ref,owner,name,s.overworld().getGameTime(),b.name());
        data.events.put(ref,new DoomData.Event(ref,owner,PlanetConfig.DOOM_SECONDS.get()*20));data.working.add(ref);data.setDirty();
        notifySystem(s,ref,"Core instability detected on "+galaxy.name(ref,b.name())+". Evacuate!");syncSystem(s,ref.system());return true;
    }
    public static void attack(ServerLevel l,Vec3 at,double force,Entity by){
        if(!Double.isFinite(force)||!Double.isFinite(at.x+at.y+at.z)||!l.hasChunkAt(BlockPos.containing(at))||com.dbzenith.combat.Destruction.isProtected(l,at)||!(by instanceof ServerPlayer p)||force<2||!p.hasPermissions(PlanetConfig.DOOM_PERMISSION.get()))return;
        if(com.dbzenith.stats.StatCalculator.battlePower(ModCapabilities.getOrThrow(p))<PlanetConfig.DOOM_POWER.get())return;
        var ref=SpaceRuntime.surfaceRef(l);if(ref!=null&&com.dbzenith.config.DBZConfig.SERVER.destructionEnabled.get())begin(l.getServer(),ref,p.getUUID(),p.getGameProfile().getName());
    }
    public static boolean canRestore(MinecraftServer s,GalaxyCatalog.Ref ref){
        var data=DoomData.get(s);var event=data.events.get(ref);return event!=null&&event.phase==DoomData.DESTROYED&&data.working.stream().map(data.events::get).filter(e->e!=null&&(e.phase==DoomData.ACTIVE||e.phase==DoomData.RESTORING)).count()<PlanetConfig.DOOM_MAX.get();
    }
    public static boolean restore(MinecraftServer s,GalaxyCatalog.Ref ref){
        var data=DoomData.get(s);var e=data.events.get(ref);if(!canRestore(s,ref))return false;
        e.phase=DoomData.RESTORING;data.working.add(ref);data.setDirty();notifySystem(s,ref,"A dragon's wish is restoring "+GalaxyData.get(s).name(ref,"the planet")+".");syncSystem(s,ref.system());return true;
    }
    public static GalaxyCatalog.Ref wishTarget(ServerPlayer p){
        var d=DoomData.get(p.server);var w=GalaxyData.get(p.server).waypoint(p.getUUID());var e=d.events.get(w);if(e!=null&&canRestore(p.server,w))return w;
        return d.events.values().stream().filter(a->canRestore(p.server,a.ref)).map(a->a.ref).min(Comparator.comparing(GalaxyCatalog.Ref::key)).orElse(null);
    }
    public static double gravity(ServerLevel l){
        var ref=SpaceRuntime.surfaceRef(l);if(ref==null)return 1;var e=DoomData.get(l.getServer()).events.get(ref);
        return e==null||e.phase!=DoomData.ACTIVE?1:1+Math.sin(l.getServer().getTickCount()*.07)*e.progress()*.18;
    }
    public static void markBase(ServerPlayer p,GalaxyCatalog.Ref ref){
        var data=GalaxyData.get(p.server);var b=GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref);
        data.discover(ref,p.getUUID(),p.getGameProfile().getName(),p.server.overworld().getGameTime(),b.name());data.base(ref,true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent e){
        if(e.isCanceled()||com.dbzenith.combat.Destruction.internalPlacement()||!(e.getEntity() instanceof ServerPlayer p))return;
        var ref=SpaceRuntime.surfaceRef(p.serverLevel());if(ref!=null)markBase(p,ref);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void spawn(PlayerSetSpawnEvent e){
        if(e.isCanceled()||e.getNewSpawn()==null||!(e.getEntity() instanceof ServerPlayer p))return;
        var l=p.server.getLevel(e.getSpawnLevel());if(l!=null){var ref=SpaceRuntime.surfaceRef(l);if(ref!=null)markBase(p,ref);}
    }
    private static void notifySystem(MinecraftServer s,GalaxyCatalog.Ref ref,String text){
        for(var p:s.getPlayerList().getPlayers())if(GalaxyData.current(p).equals(ref.system()))p.sendSystemMessage(Component.literal(text));
    }
    public static void sync(ServerPlayer p){
        var id=GalaxyData.current(p);var data=GalaxyData.get(p.server);var list=new ArrayList<DoomPacket.Body>();
        for(var b:GalaxyCatalog.system(p.server.overworld().getSeed(),data.salt(),id).planets()){
            var ref=new GalaxyCatalog.Ref(id,b.index());var e=DoomData.get(p.server).events.get(ref);if(e!=null)
                list.add(new DoomPacket.Body(e.ref.planet(),e.phase,e.elapsed,e.duration,e.age,data.name(e.ref,b.name())));
        }
        ModNetwork.sendTo(p,new DoomPacket(p.level().dimension().location().toString(),id.toString(),List.copyOf(list)));
    }
    private static void syncSystem(MinecraftServer s,GalaxyCatalog.Id id){for(var p:s.getPlayerList().getPlayers())if(GalaxyData.current(p).equals(id)){sync(p);SpaceRuntime.scene(p,false,Vec3.ZERO);}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    private static void evacuate(ServerPlayer p){
        if(!PlanetConfig.DOOM_EJECT.get()&&!p.isCreative()&&!p.isSpectator())p.hurt(p.damageSources().fellOutOfWorld(),Float.MAX_VALUE);
        if(!p.isAlive()||ModCapabilities.getOrThrow(p).isDead()||SpaceRuntime.pending(p))return;
        if(SpaceRuntime.ascend(p))return;
        // An occupied orbital cap must never trap a survivor on a sealed surface.
        var earth=p.server.overworld();var at=earth.getSharedSpawnPos();p.stopRiding();p.teleportTo(earth,at.getX()+.5,earth.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ())+1,at.getZ()+.5,p.getYRot(),0);p.fallDistance=0;
        p.sendSystemMessage(Component.literal("Emergency evacuation returned you to Earth; orbital navigation was unavailable."));
    }
    private static boolean natural(net.minecraft.world.level.block.state.BlockState b){
        return b.is(Blocks.STONE)||b.is(Blocks.DEEPSLATE)||b.is(Blocks.GRASS_BLOCK)||b.is(Blocks.DIRT)||b.is(Blocks.SANDSTONE)||b.is(Blocks.RED_SANDSTONE)||b.is(Blocks.SAND)||b.is(Blocks.RED_SAND)||b.is(Blocks.BASALT)||b.is(Blocks.BLACKSTONE)||b.is(Blocks.ANDESITE)||b.is(Blocks.TUFF)||b.is(Blocks.MOSS_BLOCK);
    }
    private static void scars(ServerLevel l,DoomData.Event e,int budget){
        if(e.scars.size()>=1024||e.progress()<.2)return;var actor=l.getServer().getPlayerList().getPlayer(e.owner);
        for(var p:l.players()){
            if(budget<=0)break;long h=GalaxyCatalog.mix(p.getUUID().getLeastSignificantBits()^e.elapsed);double a=(h>>>11)*0x1.0p-53*Math.PI*2;
            int x=p.getBlockX()+(int)(Math.cos(a)*14),z=p.getBlockZ()+(int)(Math.sin(a)*14);
            for(int i=0;i<4&&budget>0&&e.scars.size()<1024;i++,budget--){
                var test=new BlockPos(x+i,64,z+i/2);if(!l.hasChunkAt(test))continue;var at=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,test).below();var b=l.getBlockState(at);
                if(!natural(b)||b.hasBlockEntity()||!b.getFluidState().isEmpty()||e.scars.containsKey(at)||com.dbzenith.combat.Destruction.isProtected(l,Vec3.atCenterOf(at))||!l.getWorldBorder().isWithinBounds(at))continue;
                if(actor==null||MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l,at,b,actor))||l.getBlockState(at)!=b)continue;
                if(l.setBlock(at,Blocks.MAGMA_BLOCK.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)){
                    e.scars.put(at,b);l.sendParticles(ParticleTypes.LAVA,at.getX()+.5,at.getY()+1,at.getZ()+.5,2,.2,.15,.2,.02);
                }
            }
        }
    }
    private static void restoreTick(MinecraftServer s,DoomData.Event e){
        if(e.scars.isEmpty()){
            e.phase=DoomData.RESTORED;e.age=0;GalaxyData.get(s).destruction(e.ref,!e.cancelled,!e.cancelled,"sealed-v1");
            Remnants.refresh(s,e.ref.system());syncSystem(s,e.ref.system());return;
        }
        ServerLevel l;
        try{l=PlanetRuntime.openForRestoration(s,e.ref);}catch(IllegalArgumentException ex){return;}
        var loading=LOADING.computeIfAbsent(s,k->new HashMap<>());var first=e.scars.keySet().iterator().next();var chunk=new ChunkPos(first);var pending=loading.get(e.ref);
        if(!l.hasChunkAt(first)){
            if(pending==null){l.getChunkSource().addRegionTicket(RESTORE,chunk,1,e.ref.key());
                var future=CompletableFuture.supplyAsync(()->l.getChunkSource().getChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true),Util.backgroundExecutor()).thenCompose(f->f).thenApply(r->r.left().isPresent());
                loading.put(e.ref,new Loading(l,chunk,future));return;}
            if(!pending.ready.isDone())return;
            try{pending.ready.join();}catch(CompletionException ex){DBZenith.LOGGER.error("Planet restoration preload failed",ex);}
            pending.level.getChunkSource().removeRegionTicket(RESTORE,pending.chunk,1,e.ref.key());loading.remove(e.ref);if(!l.hasChunkAt(first))return;
        }
        int count=0;for(var it=e.scars.entrySet().iterator();it.hasNext()&&count<64;){var a=it.next();if(!l.hasChunkAt(a.getKey()))continue;
            if(l.getBlockState(a.getKey()).is(Blocks.MAGMA_BLOCK)&&l.getBlockEntity(a.getKey())==null)l.setBlock(a.getKey(),a.getValue(),Block.UPDATE_CLIENTS);
            it.remove();count++;
        }
        if(pending!=null){pending.level.getChunkSource().removeRegionTicket(RESTORE,pending.chunk,1,e.ref.key());loading.remove(e.ref);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var s=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(s==null)return;var data=DoomData.get(s);boolean dirty=false;
        for(var ref:List.copyOf(data.working)){
            var e=data.events.get(ref);if(e==null){data.working.remove(ref);continue;}
            var l=level(s,e.ref);
            if(e.phase==DoomData.ACTIVE){
                if(!PlanetConfig.DOOM_ENABLED.get()||!protection(s,e.ref).isEmpty()){e.phase=DoomData.RESTORING;e.cancelled=true;notifySystem(s,e.ref,"Planet destruction cancelled: protection engaged.");}
                else if(++e.elapsed>=e.duration){e.phase=DoomData.DESTROYED;e.age=0;GalaxyData.get(s).destruction(e.ref,true,false,"sealed-v1");notifySystem(s,e.ref,"The planet has been destroyed.");Remnants.refresh(s,e.ref.system());syncSystem(s,e.ref.system());}
                else if(e.elapsed%20==0&&l!=null)scars(l,e,16);
                dirty=true;
            }
            if(e.phase==DoomData.DESTROYED){
                if(e.age<1201){e.age++;dirty=true;}
                if(l!=null){for(var p:List.copyOf(l.players()))evacuate(p);if(l.players().isEmpty()&&PlanetRuntime.procedural(l))PlanetRuntime.unload(l);}
                if(e.age>=1201)data.working.remove(ref);
            }else if(e.phase==DoomData.RESTORING){restoreTick(s,e);dirty=true;}
            else if(e.phase==DoomData.RESTORED&&++e.age>=100){data.events.remove(ref);data.working.remove(ref);dirty=true;}
        }
        if(dirty)data.setDirty();
        if(s.getTickCount()%20==0)for(var p:s.getPlayerList().getPlayers())sync(p);
        Remnants.tick(s);
    }
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!p.isAlive()||p.tickCount%20!=0)return;
        var ref=SpaceRuntime.surfaceRef(p.serverLevel());if(ref!=null&&GalaxyData.get(p.server).destroyed(ref))evacuate(p);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){LOADING.remove(e.getServer());}
}
