package com.dbzenith.gametest;
import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.galaxy.space.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(DBZenith.MOD_ID) @PrefixGameTestTemplate(false)
public final class PlanetExperienceTests {
    @GameTest(template="empty") public static void weatherCalendarAndSkyProjectionStayBounded(GameTestHelper h){
        var b=GalaxyCatalog.system(42,GalaxyCatalog.SALT,new GalaxyCatalog.Id(14,5,2,3)).planets().get(0);
        boolean active=false,quiet=false;for(long t=0;t<19200;t+=40){var w=PlanetConditions.sample(b,t);h.assertTrue(w.equals(PlanetConditions.sample(b,t+9600)),"weather calendar repeats independently of loaded chunks");h.assertTrue(w.intensity()>=0&&w.intensity()<=1,"bounded storm envelope");active|=w.intensity()>.9;quiet|=w.intensity()==0;}
        h.assertTrue(active,"calendar has an active weather interval");
        Vec3 ray=new Vec3(4,3,2);h.assertTrue(Math.abs(PlanetSkyMath.local(ray,new Vec3(0,1,0),.25).length()-ray.length())<1e-9,"surface frame preserves celestial distances");
        h.assertTrue(PlanetSkyMath.daylight(-1,3)>=.4&&PlanetSkyMath.daylight(-1,1)<.1,"three-star ambient floor, single-star night");
        h.assertTrue(PlanetSkyMath.eclipse(new Vec3(0,10,0),.1,new Vec3(0,1,0),.2)>.99&&PlanetSkyMath.eclipse(new Vec3(0,10,0),.1,new Vec3(1,0,0),.2)==0,"aligned moon eclipses sun, separated moon does not");h.succeed();
    }
    @GameTest(template="empty") public static void sitePersistencePreventsDuplicateInhabitants(GameTestHelper h){
        var v=new PlanetActivity.Visits();h.assertTrue(v.claim("1:2:3:4/0/10_20"),"first visit claims site");
        var copy=PlanetActivity.Visits.load(v.save(new CompoundTag()));h.assertTrue(!copy.claim("1:2:3:4/0/10_20"),"reload cannot spawn another persistent master");h.assertTrue(copy.claim("1:2:3:4/0/11_20"),"independent site remains available");h.succeed();
    }
    @GameTest(template="empty",batch="planet_experience",timeoutTicks=600) public static void actualLandmarkLandingAndShelterWork(GameTestHelper h){
        Delays.after(h,5,()->checkLandmark(h));
    }
    private static void checkLandmark(GameTestHelper h){
        var server=h.getLevel().getServer();GalaxyCatalog.Ref ref=null;
        for(int x=310;x<350&&ref==null;x++){
            var id=new GalaxyCatalog.Id(x,12,-8,2);var system=GalaxyCatalog.system(server.overworld().getSeed(),GalaxyData.get(server).salt(),id);
            for(var b:system.planets())if(b.kind()==GalaxyCatalog.Kind.DESERT&&GalaxyData.get(server).discovery(new GalaxyCatalog.Ref(id,b.index()))==null){ref=new GalaxyCatalog.Ref(id,b.index());break;}
        }
        h.assertTrue(ref!=null,"desert fixture exists");var l=PlanetRuntime.open(server,ref);net.minecraft.server.level.ServerPlayer p=null;
        try{var g=(PlanetGenerator)l.getChunkSource().getGenerator();PlanetLandmarks.Site site=null;
        for(int x=-4;x<=4&&site==null;x++)for(int z=-4;z<=4;z++){site=PlanetLandmarks.site(g,x,z);if(site!=null)break;}
        h.assertTrue(site!=null,"deterministic landmark exists inside finite border");l.getChunk(site.x()>>4,site.z()>>4);BlockPos marker=new BlockPos(site.x(),site.y(),site.z());
        h.assertTrue(l.getBlockState(marker).is(Blocks.LODESTONE),"real chunk decoration places landmark marker");
        p=TestPlayers.create(h);p.setGameMode(GameType.SURVIVAL);p.teleportTo(l,site.x()+.5,site.y()+20,site.z()+.5,0,0);p.setOnGround(false);
        h.assertTrue(!PlanetActivity.landing(p),"flying past does not claim discovery");p.setOnGround(true);h.assertTrue(!PlanetActivity.landing(p),"a client ground flag without physical support cannot claim a planet");
        int sx=site.x()+7,sz=site.z()+7;l.getChunk(sx>>4,sz>>4);p.teleportTo(l,sx+.5,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz),sz+.5,0,0);p.setOnGround(true);
        h.assertTrue(PlanetActivity.landing(p),"ground contact claims first discovery");h.assertTrue(!PlanetActivity.landing(p),"landing card does not repeat");
        h.assertTrue(GalaxyData.get(server).discovery(ref).owner().equals(p.getUUID()),"first landed player owns naming");
        var d=ModCapabilities.getOrThrow(p);p.getPersistentData().remove("dbzenithPlanetGrace");d.setRace(com.dbzenith.stats.Race.HUMAN);d.setKi(50);var storm=new PlanetConditions.Weather(PlanetConditions.Hazard.ION,1,100);
        PlanetActivity.applyHazard(p,storm);h.assertTrue(d.getKi()==48,"exposed ion storm drains ki on server");
        BlockPos roof=p.blockPosition().above(3);l.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());PlanetActivity.applyHazard(p,storm);h.assertTrue(d.getKi()==48&&PlanetActivity.sheltered(p),"real roof shields weather");
        l.removeBlock(roof,false);}finally{if(p!=null)TestPlayers.remove(h,p);PlanetRuntime.unload(l);}Delays.after(h,5,h::succeed);
    }
}
