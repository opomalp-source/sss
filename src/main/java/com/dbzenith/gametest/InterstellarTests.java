package com.dbzenith.gametest;
import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.galaxy.space.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder(DBZenith.MOD_ID) @PrefixGameTestTemplate(false)
public final class InterstellarTests {
    @GameTest(template="empty") public static void iconicAddressesAndPersonalArrivalsRoundTrip(GameTestHelper h){
        h.assertTrue(GalaxyCatalog.Id.parse("27:-6:14:8").equals(IconicSystems.VEGETA),"additive reserved Vegeta address");boolean rejected=false;try{new GalaxyCatalog.Id(1,2,3,8);}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"ordinary systems still have eight slots");
        var a=GalaxyCatalog.body(42,GalaxyCatalog.SALT,new GalaxyCatalog.Ref(IconicSystems.VEGETA,0));var b=GalaxyCatalog.body(42,GalaxyCatalog.SALT,new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0));
        h.assertTrue(a.name().equals("Planet Vegeta")&&a.gravity()==10&&a.kind()==GalaxyCatalog.Kind.DESERT&&b.name().equals("Planet Frieza No. 79"),"authored iconic identities and high gravity");
        var y=GalaxyCatalog.body(42,GalaxyCatalog.SALT,new GalaxyCatalog.Ref(IconicSystems.YARDRAT,0));var c=GalaxyCatalog.body(42,GalaxyCatalog.SALT,new GalaxyCatalog.Ref(IconicSystems.CEREAL,0));
        h.assertTrue(y.name().equals("Yardrat")&&c.name().equals("Cereal")&&IconicSystems.all().size()==8&&IconicSystems.all().stream().distinct().count()==8,"the series worlds are all in the catalogue, once each");
        h.assertTrue(IconicSystems.style(new GalaxyCatalog.Ref(IconicSystems.VEGETA,0))==0&&IconicSystems.style(new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0))==0&&IconicSystems.style(new GalaxyCatalog.Ref(IconicSystems.YARDRAT,1))==0&&IconicSystems.style(new GalaxyCatalog.Ref(IconicSystems.CEREAL,0))==PlanetTerrain.CEREAL,"only the new worlds get their own landscape, so visited ground never changes");
        for(var w:new GalaxyCatalog.Id[]{IconicSystems.VEGETA,IconicSystems.FRIEZA,IconicSystems.YARDRAT,IconicSystems.CEREAL})h.assertTrue(!IconicSystems.lore(new GalaxyCatalog.Ref(w,0)).isEmpty()&&IconicLandmarks.paving(w,0,0)!=null&&java.util.Objects.equals(IconicLandmarks.piece(w,3,2,-5),IconicLandmarks.piece(w,3,2,-5)),"each series world has its lore, plaza and place");
        for(int seed=0;seed<24;seed++){
            var g=new PlanetGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(h.getLevel().getBiome(h.absolutePos(BlockPos.ZERO))),seed,new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0).key(),GalaxyCatalog.Kind.TEMPERATE.ordinal(),4096,1);
            var site=PlanetLandmarks.site(g,0,0);h.assertTrue(site!=null&&site.kind()==9&&site.y()>g.terrain.sea()+1,"the Frieza Force base always stands above the ocean");
        }
        var data=new GalaxyData();var uuid=java.util.UUID.randomUUID();var ref=new GalaxyCatalog.Ref(IconicSystems.VEGETA,0);var anchor=new GalaxyData.Arrival(30,81,-25,90);data.arrival(uuid,ref,anchor);
        h.assertTrue(GalaxyData.load(data.save(new CompoundTag())).arrivals(uuid).get(ref).equals(anchor),"personal arrival anchors persist independently of players and clones");
        h.assertTrue(Interstellar.duration(GalaxyCatalog.EARTH,GalaxyCatalog.EARTH)==200&&Interstellar.duration(GalaxyCatalog.EARTH,new GalaxyCatalog.Id(1024,1024,1024,7))<=400,"bounded nominal hyperspeed leaves ten seconds for preloading");h.succeed();
    }
    @GameTest(template="empty",batch="interstellar_people",timeoutTicks=200) public static void crossWorldTransmissionRespectsContactGodKiCostAndCooldown(GameTestHelper h){
        var p=TestPlayers.create(h);var target=TestPlayers.create(h);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);target.setNoGravity(true);var d=ModCapabilities.getOrThrow(p);d.setSkillLevel("instant_transmission",1);d.setKi(d.getDerived().maxKi());
        var l=p.server.getLevel(com.dbzenith.world.Planet.NAMEK.dimension());l.getChunk(0,0);target.teleportTo(l,8,160,8,0,0);
        d.setFlag("transmission_contact:"+target.getStringUUID(),false); // creation in the same arena already fired StartTracking
        h.assertTrue(!Interstellar.transmitToPlayer(p,target),"strangers cannot be used as cross-world coordinates");
        Interstellar.contact(new net.minecraftforge.event.entity.player.PlayerEvent.StartTracking(p,target));ModCapabilities.getOrThrow(target).setFlag("god_ki",true);
        h.assertTrue(!Interstellar.transmitToPlayer(p,target),"ordinary ki cannot sense a known divine target");ModCapabilities.getOrThrow(target).setFlag("god_ki",false);
        double before=d.getKi();h.assertTrue(Interstellar.transmitToPlayer(p,target)&&p.level()==l,"actual teleport to known person in another dimension");
        h.assertTrue(d.getKi()<before&&d.getRacialCooldown("instant_transmission")>l.getGameTime(),"existing skill cost and cooldown applied");h.assertTrue(!Interstellar.transmitToPlayer(p,target),"cooldown prevents repeat use");
        TestPlayers.remove(h,p);TestPlayers.remove(h,target);h.succeed();
    }
    @GameTest(template="empty",batch="interstellar_trip",timeoutTicks=1400) public static void realHyperspeedAndVisitedPlanetTransmissionWork(GameTestHelper h){
        var p=TestPlayers.create(h);p.setGameMode(GameType.CREATIVE);p.setNoGravity(true);h.setBlock(1,1,1,Blocks.STONE);
        var at=h.absolutePos(new BlockPos(1,2,1));p.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);p.setOnGround(true);PlanetActivity.landing(p);
        h.assertTrue(ModCapabilities.getOrThrow(p).hasFlag("planet_landing:0:0:0:0/0"),"real Earth landing establishes personal visit");
        h.assertTrue(!Interstellar.begin(p,new GalaxyCatalog.Ref(IconicSystems.VEGETA,0)),"cannot jump from the ground");
        p.teleportTo(p.getX(),512,p.getZ());h.assertTrue(SpaceRuntime.ascend(p),"ascent queued");waitOrbit(h,p,0);
    }
    private static void waitOrbit(GameTestHelper h,ServerPlayer p,int tries){Delays.after(h,5,()->{
        if(!SpaceRuntime.isSpace(p.level())){h.assertTrue(tries<100,"ascent completes");waitOrbit(h,p,tries+1);return;}
        p.setGameMode(GameType.SURVIVAL);var d=ModCapabilities.getOrThrow(p);d.setRace(com.dbzenith.stats.Race.FROST_DEMON);d.setFlying(true);d.setKi(d.getDerived().maxKi());
        int levels=0;for(var ignored:p.server.getAllLevels())levels++;
        h.assertTrue(!Interstellar.begin(p,new GalaxyCatalog.Ref(GalaxyCatalog.BEERUS,0)),"existing god-ki gate protects Beerus's system");
        h.assertTrue(Interstellar.begin(p,new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0))&&Interstellar.cancel(p),"pilot can disengage before arrival");h.assertTrue(!Interstellar.active(p)&&d.getKi()==d.getDerived().maxKi(),"cancelled course spends no jump ki");
        h.assertTrue(Interstellar.begin(p,new GalaxyCatalog.Ref(IconicSystems.VEGETA,0)),"full-ki pilot starts real interstellar trip");h.assertTrue(!Interstellar.begin(p,new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0)),"one active course per pilot");
        int after=0;for(var ignored:p.server.getAllLevels())after++;h.assertTrue(levels==after,"charge creates no new destination instance, including repeat test saves");waitJump(h,p,0);
    });}
    private static void waitJump(GameTestHelper h,ServerPlayer p,int tries){Delays.after(h,10,()->{
        if(Interstellar.active(p)){h.assertTrue(tries<80,"trip and preloading bounded");waitJump(h,p,tries+1);return;}
        h.assertTrue(IconicSystems.VEGETA.equals(SpaceRuntime.id(p.level().dimension())),"real arrival in destination orbital instance");var d=ModCapabilities.getOrThrow(p);h.assertTrue(d.getKi()<d.getDerived().maxKi()*.9,"completed ki jump is charged");
        d.setFlying(false);d.setSkillLevel("instant_transmission",1);h.assertTrue(!Interstellar.transmit(p,new GalaxyCatalog.Ref(IconicSystems.FRIEZA,0)),"unvisited planet is rejected");
        h.assertTrue(Interstellar.transmit(p,new GalaxyCatalog.Ref(GalaxyCatalog.EARTH,0)),"visited Earth transmission asynchronously queues without a pod");waitHome(h,p,0);
    });}
    private static void waitHome(GameTestHelper h,ServerPlayer p,int tries){Delays.after(h,5,()->{
        if(!p.level().dimension().equals(Level.OVERWORLD)||PlanetRuntime.pending(p)){var d=ModCapabilities.getOrThrow(p);h.assertTrue(tries<100,"planet transmission finishes: "+p.level().dimension().location()+" pending="+PlanetRuntime.pending(p)+" ki="+d.getKi()+" ready="+Interstellar.canTransmit(p)+" cooldown="+d.getRacialCooldown("instant_transmission")+" pos="+p.position());waitHome(h,p,tries+1);return;}
        h.assertTrue(ModCapabilities.getOrThrow(p).getRacialCooldown("instant_transmission")>p.level().getGameTime(),"completed planetary cast uses existing cooldown");TestPlayers.remove(h,p);h.succeed();
    });}
}
