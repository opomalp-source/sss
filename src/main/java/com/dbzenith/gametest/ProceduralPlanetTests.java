package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.item.SpaceSuitItem;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.Race;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.concurrent.*;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProceduralPlanetTests {
    @GameTest(template="empty") public static void planetTerrainIsIndependentAndVaried(GameTestHelper h){
        Set<String> tops=new HashSet<>();
        for(var kind:GalaxyCatalog.Kind.values()){
            var a=new PlanetTerrain(123456789,kind);var b=new PlanetTerrain(123456789,kind);tops.add(a.top().toString());
            for(int x=-33;x<=33;x+=11)for(int z=-33;z<=33;z+=11){
                int height=a.height(x,z);h.assertTrue(height>=15&&height<=220,"bounded terrain height");
                for(int y=-64;y<=160;y+=17)h.assertTrue(a.block(x,y,z).equals(b.block(x,y,z)),"independent terrain instances match across chunk boundaries");
            }
            if(kind!=GalaxyCatalog.Kind.ASTEROIDS)h.assertTrue(a.block(0,-64,0).is(Blocks.BEDROCK),"solid lower boundary");
        }
        h.assertTrue(tops.size()>=8,"different surface palettes");
        var dry=new PlanetTerrain(9,GalaxyCatalog.Kind.BARREN);h.assertTrue(dry.sea()==-64,"airless barren worlds do not gain seas");
        var rocks=new PlanetTerrain(9,GalaxyCatalog.Kind.ASTEROIDS);h.assertTrue(!rocks.block(0,80,0).isAir()&&rocks.block(0,20,0).isAir(),"real floating home asteroid, no flat floor");
        h.succeed();
    }
    @GameTest(template="empty") public static void planetProtectionUsesExistingRaceAndBarrierSystems(GameTestHelper h){
        var p=TestPlayers.create(h);p.setGameMode(GameType.SURVIVAL);var d=ModCapabilities.getOrThrow(p);d.setRace(Race.HUMAN);p.getInventory().clearContent();
        h.assertTrue(PlanetEnvironment.protection(p,d).isEmpty(),"unprotected human needs air");
        d.setRace(Race.FROST_DEMON);h.assertTrue(!PlanetEnvironment.protection(p,d).isEmpty(),"existing breathless race survives");
        d.setRace(Race.HUMAN);d.setRacialState(0,Set.of("ki_barrier"),Set.of());h.assertTrue(PlanetEnvironment.protection(p,d).equals("Ki barrier"),"active ki barrier seals air");d.setRacialState(0,Set.of(),Set.of());
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
        p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));
        h.assertTrue(SpaceSuitItem.sealed(p)&&PlanetEnvironment.protection(p,d).equals("Sealed space suit"),"full suit protects");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.assertTrue(!SpaceSuitItem.sealed(p)&&PlanetEnvironment.protection(p,d).isEmpty(),"missing helmet opens suit");
        PlanetEnvironment.applyGravity(p,.2);h.assertTrue(Math.abs(p.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get())-.016)<1e-6,"low gravity really changes the synced movement attribute");
        PlanetEnvironment.applyGravity(p,10);h.assertTrue(Math.abs(p.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get())-.8)<1e-6,"high gravity really changes falling/jump physics");
        PlanetEnvironment.applyGravity(p,1);h.assertTrue(Math.abs(p.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get())-.08)<1e-6,"leaving planet removes modifier");
        h.assertTrue(PlanetEnvironment.drain(GalaxyCatalog.Atmosphere.NONE,0)==1&&PlanetEnvironment.drain(GalaxyCatalog.Atmosphere.TOXIC,0)==2,"distinct atmosphere drain");
        TestPlayers.remove(h,p);h.succeed();
    }
    @GameTest(template="empty") public static void planetGravityAffectsItemsAndFreeArrows(GameTestHelper h){
        BlockPos air=h.absolutePos(new BlockPos(1,5,1));
        var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),air.getX()+.5,air.getY()+.5,air.getZ()+.5,new ItemStack(Blocks.STONE));
        item.setDeltaMovement(0,.2,0);PlanetEnvironment.applyArc(item,5);h.assertTrue(Math.abs(item.getDeltaMovement().y-.04)<1e-6,"item acceleration scales with planet gravity");
        item.setNoGravity(true);item.setDeltaMovement(0,.2,0);PlanetEnvironment.applyArc(item,5);h.assertTrue(item.getDeltaMovement().y==.2,"no-gravity objects keep their own motion");
        var arrow=net.minecraft.world.entity.EntityType.ARROW.create(h.getLevel());arrow.setPos(air.getX()+.5,air.getY()+.5,air.getZ()+.5);arrow.setDeltaMovement(0,.25,0);
        PlanetEnvironment.applyArc(arrow,5);h.assertTrue(Math.abs(arrow.getDeltaMovement().y-.05)<1e-6,"flying arrow acceleration scales");
        h.setBlock(new BlockPos(1,1,1),Blocks.STONE);BlockPos solid=h.absolutePos(new BlockPos(1,1,1));arrow.setPos(solid.getX()+.5,solid.getY()+.5,solid.getZ()+.5);arrow.setDeltaMovement(0,.25,0);
        PlanetEnvironment.applyArc(arrow,5);h.assertTrue(arrow.getDeltaMovement().y==.25,"embedded arrows do not accumulate hidden velocity");h.succeed();
    }
    private static GalaxyCatalog.Ref find(net.minecraft.server.MinecraftServer server,GalaxyCatalog.Kind kind){
        var d=GalaxyData.get(server);
        for(int x=200;x<240;x++)for(var body:GalaxyCatalog.system(server.overworld().getSeed(),d.salt(),new GalaxyCatalog.Id(x,7,-4,1)).planets())
            if(body.kind()==kind)return new GalaxyCatalog.Ref(new GalaxyCatalog.Id(x,7,-4,1),body.index());
        throw new IllegalStateException("No test archetype found");
    }
    @GameTest(template="empty",batch="procedural_planets",timeoutTicks=1000) public static void runtimePlanetCreatesLandsUnloadsAndKeepsBuilds(GameTestHelper h){
        var server=h.getLevel().getServer();var ref=find(server,GalaxyCatalog.Kind.BARREN);var descriptor=GalaxyCatalog.body(server.overworld().getSeed(),GalaxyData.get(server).salt(),ref);
        double earth=server.overworld().getWorldBorder().getSize();ServerLevel l=PlanetRuntime.open(server,ref);
        h.assertTrue(l.getChunkSource().getGenerator() instanceof PlanetGenerator,"actual runtime world uses custom generator");
        h.assertTrue(l.getWorldBorder().getSize()==descriptor.radius()*2d&&server.overworld().getWorldBorder().getSize()==earth,"planet border does not resize Earth");
        server.overworld().getWorldBorder().setSize(earth-100);h.assertTrue(l.getWorldBorder().getSize()==descriptor.radius()*2d,"Earth border changes cannot overwrite planet border");server.overworld().getWorldBorder().setSize(earth);
        var ops=RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());var encoded=ChunkGenerator.CODEC.encodeStart(ops,l.getChunkSource().getGenerator()).getOrThrow(false,s->{});
        var decoded=ChunkGenerator.CODEC.parse(ops,encoded).getOrThrow(false,s->{});h.assertTrue(decoded instanceof PlanetGenerator g&&g.ref.equals(ref),"world generator survives level.dat codec round trip");
        var p=TestPlayers.create(h);p.setGameMode(GameType.CREATIVE);p.setNoGravity(true);BlockPos source=h.absolutePos(new BlockPos(3,3,3));p.teleportTo(source.getX()+.5,source.getY(),source.getZ()+.5);
        h.assertTrue(!PlanetRuntime.unload(p.serverLevel()),"legacy dimensions cannot be evicted");
        h.assertTrue(PlanetRuntime.travel(p,ref),"landing queues asynchronous preparation");
        pollLanding(h,p,l,ref,0);
    }
    private static void pollLanding(GameTestHelper h,ServerPlayer p,ServerLevel level,GalaxyCatalog.Ref ref,int tries){Delays.after(h,5,()->{
        if(p.level()!=level){h.assertTrue(tries<120,"asynchronous landing completed");pollLanding(h,p,level,ref,tries+1);return;}
        h.assertTrue(!PlanetRuntime.unload(level),"occupied planets cannot unload");
        p.setOnGround(true);PlanetActivity.landing(p); // embedded players do not simulate ordinary ground contact
        h.assertTrue(GalaxyData.get(p.server).discovery(ref)!=null,"grounded landing records discovery");
        BlockPos marker=new BlockPos(4,200,4);level.setBlock(marker,Blocks.OBSIDIAN.defaultBlockState(),3);
        for(int x=2;x<=6;x++)for(int z=1;z<=6;z++)if(x!=4||z!=4)level.setBlock(new BlockPos(x,200,z),Blocks.STONE.defaultBlockState(),3);
        var bed=Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,Direction.SOUTH);
        level.setBlock(marker.above().north(),bed,3);
        level.setBlock(marker.above(),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),3);
        p.setGameMode(GameType.SURVIVAL);ModCapabilities.getOrThrow(p).setRace(Race.HUMAN);p.getInventory().clearContent();
        p.getPersistentData().putLong("dbzenithPlanetGrace",0);p.getPersistentData().putInt("dbzenithPlanetAir",300);
        for(int i=0;i<100;i++)PlanetEnvironment.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(p.getPersistentData().getInt("dbzenithPlanetAir")==200,"air actually drains on an airless planet");
        p.teleportTo(h.getLevel(),h.absolutePos(BlockPos.ZERO).getX(),h.absolutePos(BlockPos.ZERO).getY()+5,h.absolutePos(BlockPos.ZERO).getZ(),0,0);
        p.setRespawnPosition(level.dimension(),marker.above(),0,false,false);
        h.assertTrue(PlanetRuntime.unload(level),"empty world with a saved respawn can unload");
        Delays.after(h,10,()->{
            h.assertTrue(h.getLevel().getServer().getLevel(PlanetRuntime.key(ref))==null,"runtime dimension really unregisters");
            h.assertTrue(p.getRespawnDimension().equals(PlanetRuntime.key(ref))&&marker.above().equals(p.getRespawnPosition()),"library unload preserves vanilla respawn metadata");
            ServerPlayer respawned=p.server.getPlayerList().respawn(p,false);
            h.assertTrue(respawned.getRespawnDimension().equals(PlanetRuntime.key(ref)),"vanilla death clone preserves unloaded planet bed");
            pollBed(h,respawned,ref,marker,0);
        });
    });}
    private static void pollBed(GameTestHelper h,ServerPlayer p,GalaxyCatalog.Ref ref,BlockPos marker,int tries){Delays.after(h,5,()->{
        if(!p.level().dimension().equals(PlanetRuntime.key(ref))){h.assertTrue(tries<100,"bed reload completes");pollBed(h,p,ref,marker,tries+1);return;}
        ServerLevel level=p.serverLevel();h.assertTrue(level.getBlockState(marker).is(Blocks.OBSIDIAN),"player build survives save, close, unregister and reopen");
        h.assertTrue(p.getY()>=marker.getY()+1&&p.distanceToSqr(marker.getX()+.5,marker.getY()+1,marker.getZ()+.5)<9,"return uses validated planet respawn location");
        p.setRespawnPosition(Level.OVERWORLD,null,0,false,false);p.teleportTo(h.getLevel(),h.absolutePos(BlockPos.ZERO).getX(),h.absolutePos(BlockPos.ZERO).getY()+5,h.absolutePos(BlockPos.ZERO).getZ(),0,0);
        TestPlayers.remove(h,p);PlanetRuntime.unload(level);h.succeed();
    });}
    @GameTest(template="empty",batch="procedural_caps",timeoutTicks=80) public static void runtimePlanetCapAndGasGiantAreEnforced(GameTestHelper h){
        var s=h.getLevel().getServer();int limit=PlanetConfig.MAX_LOADED.get();List<ServerLevel> created=new ArrayList<>();
        try{
            int attempt=0;while(created.size()<limit){var ref=new GalaxyCatalog.Ref(new GalaxyCatalog.Id(400+attempt++,3,9,2),0);var b=GalaxyCatalog.body(s.overworld().getSeed(),GalaxyData.get(s).salt(),ref);if(!b.kind().landable())continue;created.add(PlanetRuntime.open(s,ref));}
            boolean cap=false;try{PlanetRuntime.open(s,find(s,GalaxyCatalog.Kind.ICE));}catch(IllegalArgumentException ex){cap=true;}
            h.assertTrue(cap&&PlanetRuntime.loaded(s)<=limit,"hard active-world cap");
            boolean gas=false;try{PlanetRuntime.open(s,find(s,GalaxyCatalog.Kind.GAS_GIANT));}catch(IllegalArgumentException ex){gas=true;}
            h.assertTrue(gas,"gas giants never become a fake solid landing world");
        }finally{for(var l:created)PlanetRuntime.unload(l);}
        h.succeed();
    }
}
