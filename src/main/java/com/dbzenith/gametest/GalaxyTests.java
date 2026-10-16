package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GalaxyTests {
    @GameTest(template="empty") public static void galaxyVersionOneFingerprint(GameTestHelper h) {
        var id=new GalaxyCatalog.Id(-2,5,9,3);var s=GalaxyCatalog.system(42,GalaxyCatalog.SALT,id);
        h.assertTrue(s.toString().hashCode()==-498425224,"v1 output must stay stable across releases");
        GalaxyCatalog.system(42,GalaxyCatalog.SALT,new GalaxyCatalog.Id(1024,-1024,1024,7));
        h.assertTrue(s.equals(GalaxyCatalog.system(42,GalaxyCatalog.SALT,id)),"query order cannot affect a system");
        h.assertTrue(!s.equals(GalaxyCatalog.system(43,GalaxyCatalog.SALT,id)),"world seed changes the galaxy");
        h.assertTrue(!s.equals(GalaxyCatalog.system(42,GalaxyCatalog.SALT+1,id)),"saved salt changes the galaxy");
        h.succeed();
    }
    @GameTest(template="empty") public static void galaxyCoordinatesAndPlanetBounds(GameTestHelper h) {
        for(String bad:List.of("-2147483648:0:0:0","0:0:0:8","1025:0:0:0","0:0:0","0:0:0:0:0","0:NaN:0:0")) {
            boolean rejected=false;try{GalaxyCatalog.Id.parse(bad);}catch(IllegalArgumentException ex){rejected=true;}
            h.assertTrue(rejected,"reject malformed/out-of-range address "+bad);
        }
        var id=GalaxyCatalog.Id.parse("-1024:1024:-1024:7");h.assertTrue(id.equals(GalaxyCatalog.Id.parse(id.toString())),"signed boundary round trip");
        var ref=new GalaxyCatalog.Ref(id,4);h.assertTrue(ref.equals(GalaxyCatalog.Ref.parse(ref.key())),"planet address round trip");
        Set<GalaxyCatalog.Kind> kinds=EnumSet.noneOf(GalaxyCatalog.Kind.class);Set<GalaxyCatalog.StarType> stars=EnumSet.noneOf(GalaxyCatalog.StarType.class);
        Set<Long> seeds=new HashSet<>();boolean binary=false,trinary=false;
        for(int i=0;i<192;i++) {
            var s=GalaxyCatalog.system(42,GalaxyCatalog.SALT,new GalaxyCatalog.Id(i-96,-i,17,i%8));
            h.assertTrue(seeds.add(GalaxyCatalog.seed(42,GalaxyCatalog.SALT,s.id())),"distinct coordinates have distinct sample seeds");
            h.assertTrue(s.planets().size()>=3&&s.planets().size()<=9,"bounded planet generation");
            binary|=s.stars().size()==2;trinary|=s.stars().size()==3;
            for(var star:s.stars()) {stars.add(star.type());h.assertTrue(star.radius()>0&&star.luminosity()>0,"physical star descriptors");}
            for(var b:s.planets()) {
                kinds.add(b.kind());h.assertTrue(b.radius()>=256&&b.radius()<=16384,"finite radius");
                h.assertTrue(b.gravity()>0&&b.gravity()<=20,"bounded gravity");h.assertTrue(b.moons().size()<=5,"bounded moon list");
                h.assertTrue(b.dayTicks()>0&&b.orbit()>0,"valid day/orbit");
                h.assertTrue(b.kind()!=GalaxyCatalog.Kind.GAS_GIANT||!b.kind().landable(),"gas giants are not landable");
            }
        }
        h.assertTrue(kinds.size()==GalaxyCatalog.Kind.values().length&&stars.size()==GalaxyCatalog.StarType.values().length&&binary&&trinary,"all archetypes and multiple-star systems occur");h.succeed();
    }
    @GameTest(template="empty") public static void galaxySparseOwnershipAndPersistence(GameTestHelper h) {
        GalaxyData d=new GalaxyData();var ref=new GalaxyCatalog.Ref(new GalaxyCatalog.Id(3,-4,5,2),0);UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        h.assertTrue(d.discoveries().isEmpty(),"unvisited catalogue is not stored");
        h.assertTrue(d.discover(ref,first,"First",123,"Generated name"),"first landing records discovery");
        h.assertTrue(!d.discover(ref,second,"Second",456,"Other name"),"second landing cannot steal discovery");
        h.assertTrue(!d.rename(ref,second,"Stolen"),"only first discoverer may name it");
        h.assertTrue(!d.rename(ref,first,"bad\nname")&&!d.rename(ref,first,"x"),"names reject controls and invalid length");
        h.assertTrue(d.rename(ref,first,"Quiet Horizon"),"first discoverer names planet");d.waypoint(second,ref);
        CompoundTag raw=d.save(new CompoundTag());var entries=raw.getList("discoveries",Tag.TAG_COMPOUND);var entry=entries.getCompound(0);
        entry.putBoolean("destroyed",true);entry.putBoolean("restored",true);entry.putBoolean("base",true);entry.putString("snapshot","snapshot-001");
        var loaded=GalaxyData.load(raw);var e=loaded.discovery(ref);
        h.assertTrue(loaded.salt()==GalaxyCatalog.SALT&&loaded.discoveries().size()==1,"sparse metadata/salt reload");
        h.assertTrue(e.owner().equals(first)&&e.discoverer().equals("First")&&e.tick()==123&&e.name().equals("Quiet Horizon"),"ownership and name reload");
        h.assertTrue(e.destroyed()&&e.restored()&&e.base()&&e.snapshot().equals("snapshot-001"),"future restoration/base metadata survives");
        h.assertTrue(ref.equals(loaded.waypoint(second)),"waypoints survive restart");loaded.waypoint(second,null);h.assertTrue(loaded.waypoint(second)==null,"waypoint clears");
        CompoundTag future=raw.copy();future.putInt("version",99);boolean rejected=false;try{GalaxyData.load(future).requireSupported();}catch(IllegalStateException ex){rejected=true;}
        h.assertTrue(rejected,"never silently regenerate an unsupported galaxy version");
        h.assertTrue(GalaxyData.load(future).save(new CompoundTag()).equals(future),"unknown schema preserved byte-for-byte as NBT data");h.succeed();
    }
    @GameTest(template="empty",batch="galaxy") public static void galaxySurveysAreBoundedAndDoNotDiscover(GameTestHelper h) {
        var p=TestPlayers.create(h);
        try {
            var d=GalaxyData.get(p.server);int count=d.discoveries().size();int dimensions=0;for(var ignored:p.server.getAllLevels())dimensions++;
            for(int view=0;view<4;view++) {
                var data=GalaxyService.survey(p,view,GalaxyCatalog.EARTH,-1,"",0);
                h.assertTrue(data.getList("entries",Tag.TAG_COMPOUND).size()<=(view==0?200:view==1?8:32),"bounded reply lists");
                String text=data.toString();h.assertTrue(!text.contains("seed:")&&!text.contains("salt:"),"world and terrain seeds are private");
                var b=new FriendlyByteBuf(Unpooled.buffer());
                try{GalaxyPackets.Data.encode(new GalaxyPackets.Data(12,false,data),b);h.assertTrue(b.readableBytes()<65536,"each survey below 64 KiB");var decoded=GalaxyPackets.Data.decode(b);h.assertTrue(decoded.sequence()==12&&decoded.data().equals(data),"reply wire round trip");}finally{b.release();}
            }
            int after=0;for(var ignored:p.server.getAllLevels())after++;
            h.assertTrue(dimensions==after&&d.discoveries().size()==count,"survey creates neither planet dimensions nor discoveries");
            InputGuard.reset(p);var before=d.waypoint(p.getUUID());
            GalaxyService.request(p,new GalaxyPackets.Request(1,4,"999999:0:0:0",0,"",0));
            h.assertTrue(Objects.equals(before,d.waypoint(p.getUUID())),"invalid request cannot change waypoint");
            GalaxyService.request(p,new GalaxyPackets.Request(2,4,GalaxyCatalog.EARTH.toString(),0,"",0));
            h.assertTrue(new GalaxyCatalog.Ref(GalaxyCatalog.EARTH,0).equals(d.waypoint(p.getUUID())),"server validates and stores own waypoint");
            d.waypoint(p.getUUID(),null);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try{var r=new GalaxyPackets.Request(19,2,"-3:4:5:6",2,"quiet",32);GalaxyPackets.Request.encode(r,b);h.assertTrue(r.equals(GalaxyPackets.Request.decode(b)),"request wire round trip");}finally{b.release();}
        }finally{InputGuard.reset(p);TestPlayers.remove(h,p);}
        h.succeed();
    }
}
