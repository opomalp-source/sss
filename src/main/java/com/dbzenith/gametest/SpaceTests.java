package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.network.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceTests {
    @GameTest(template="empty") public static void orbitGeometryAndMotionKeepTheirContracts(GameTestHelper h){
        for(int radius:new int[]{256,2048,16384,29999984})for(double x:new double[]{-.8,-.2,0,.4,.8})for(double z:new double[]{-.8,0,.6}){
            var normal=SpaceMath.normal(x*radius,z*radius,radius);var back=SpaceMath.surface(normal,radius);
            h.assertTrue(Math.abs(back[0]-x*radius)<1e-6&&Math.abs(back[1]-z*radius)<1e-6,"spherical location mapping round trips, including Earth scale");
        }
        Vec3 drift=new Vec3(1.2,-.4,.8);h.assertTrue(SpaceMath.step(drift,0,0,0,false,false,0,0,false).distanceTo(drift)<1e-9,"unpowered space preserves inertia");
        h.assertTrue(SpaceMath.step(drift,0,0,0,false,true,0,0,true).length()<drift.length(),"powered braking slows drift");
        Vec3 v=Vec3.ZERO;for(int i=0;i<400;i++)v=SpaceMath.step(v,1,1,1,true,false,0,-30,true);
        h.assertTrue(v.length()<=5.500001&&v.y>0,"thrust follows aim and bounded boost speed");
        h.assertTrue(SpaceMath.atmosphere(100)==0&&SpaceMath.atmosphere(512)==1,"continuous atmosphere endpoints");h.succeed();
    }
    @GameTest(template="empty") public static void orbitalAddressesAndSparseBeltAreDeterministic(GameTestHelper h){
        var id=new GalaxyCatalog.Id(-7,3,-2,6);h.assertTrue(id.equals(SpaceRuntime.id(SpaceRuntime.key(id))),"signed orbital address round trip");
        h.assertTrue(SpaceRuntime.id(Level.OVERWORLD)==null,"Earth never mistaken for orbital instance");
        var biome=h.getLevel().getChunkSource().getGenerator().getBiomeSource();var a=new OrbitGenerator(biome,42,id);var b=new OrbitGenerator(biome,42,id);
        int solid=0;for(int x=10000;x<11600;x+=8)for(int z=-96;z<=96;z+=8)for(int y=48;y<=224;y+=8){var block=a.block(x,y,z);h.assertTrue(block.equals(b.block(x,y,z)),"belt independent of generation order");if(!block.isAir())solid++;}
        h.assertTrue(solid>10&&a.block(0,128,0).isAir(),"real sparse asteroid clusters and empty interplanetary space");
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());var encoded=ChunkGenerator.CODEC.encodeStart(ops,a).getOrThrow(false,s->{});var decoded=ChunkGenerator.CODEC.parse(ops,encoded).getOrThrow(false,s->{});
        h.assertTrue(decoded instanceof OrbitGenerator g&&g.system.equals(id),"orbital generator persists through level.dat codec");h.succeed();
    }
    @GameTest(template="empty",batch="space_runtime",timeoutTicks=1000) public static void actualAscentAndReentryPreserveTheDepartureLocation(GameTestHelper h){
        var p=TestPlayers.create(h);p.setGameMode(GameType.CREATIVE);p.setNoGravity(true);p.getAbilities().flying=true;
        BlockPos source=h.absolutePos(new BlockPos(3,3,3));p.teleportTo(source.getX()+.5,512,source.getZ()+.5);p.setDeltaMovement(.2,1,.1);
        SpaceRuntime.velocityHint(p,p.getDeltaMovement());h.assertTrue(SpaceRuntime.ascend(p),"server queues actual orbital ascent");
        pollAscent(h,p,source,0);
    }
    private static void pollAscent(GameTestHelper h,ServerPlayer p,BlockPos source,int tries){Delays.after(h,5,()->{
        if(!SpaceRuntime.isSpace(p.level())){h.assertTrue(tries<100,"orbital preload completes");pollAscent(h,p,source,tries+1);return;}
        h.assertTrue(p.serverLevel().getChunkSource().getGenerator() instanceof OrbitGenerator,"actual runtime orbital world");
        h.assertTrue(PlanetEnvironment.gravity(p.level())==0,"space has real zero gravity");
        h.assertTrue(GalaxyData.current(p).equals(GalaxyCatalog.EARTH),"map remains in the correct star system");
        var packet=GalaxyService.survey(p,GalaxyService.SYSTEM,GalaxyCatalog.EARTH,0,"",0);FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());SpaceScenePacket.encode(new SpaceScenePacket(packet),buf);
        h.assertTrue(buf.readableBytes()<65536,"orbital metadata stays under 64 KiB");SpaceScenePacket.decode(buf);buf.release();
        var body=SpaceRuntime.system(p.serverLevel()).planets().get(0);Vec3 n=SpaceMath.normal(source.getX()+.5,source.getZ()+.5,body.radius()),at=SpaceMath.center(body).add(n.scale(SpaceMath.radius(body.radius())+60));
        p.teleportTo(at.x,at.y,at.z);p.setDeltaMovement(n.scale(-1));SpaceRuntime.velocityHint(p,n.scale(-1));
        SpaceRuntime.player(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(PlanetRuntime.pending(p),"entry proximity queues surface preload");
        pollEntry(h,p,source,0);
    });}
    private static void pollEntry(GameTestHelper h,ServerPlayer p,BlockPos source,int tries){Delays.after(h,5,()->{
        if(SpaceRuntime.isSpace(p.level())||PlanetRuntime.pending(p)){h.assertTrue(tries<100,"surface preload/reentry completes");pollEntry(h,p,source,tries+1);return;}
        h.assertTrue(p.level().dimension().equals(Level.OVERWORLD),"re-entry returns to original Earth dimension");
        h.assertTrue(Math.abs(p.getX()-source.getX()-.5)<.01&&Math.abs(p.getZ()-source.getZ()-.5)<.01,"matching surface location survives orbital round trip");
        h.assertTrue(p.getY()>300&&p.getDeltaMovement().y<0,"re-entry is above the surface with downward momentum");
        TestPlayers.remove(h,p);h.succeed();
    });}
}
