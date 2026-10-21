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
        Vec3 settle=drift;for(int i=0;i<200;i++)settle=SpaceMath.step(settle,0,0,0,false,false,0,0,true);h.assertTrue(settle.equals(Vec3.ZERO),"powered flight eases to a stop when you let go");
        Vec3 sink=Vec3.ZERO;for(int i=0;i<200;i++)sink=SpaceMath.step(sink,0,0,0,false,true,0,0,true);h.assertTrue(sink.y<-1.7&&sink.y>=-SpaceMath.CRUISE-1e-6,"sneak sinks at cruise speed");
        Vec3 v=Vec3.ZERO;for(int i=0;i<400;i++)v=SpaceMath.step(v,1,1,1,true,false,0,-30,true);
        h.assertTrue(v.length()<=SpaceMath.SPRINT+1e-6&&v.length()>SpaceMath.SPRINT-.01&&v.y>0,"flight follows aim and reaches full speed, no further");
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
        BlockPos source=h.absolutePos(new BlockPos(3,3,3));p.teleportTo(p.serverLevel(),source.getX()+.5,512,source.getZ()+.5,30,-15);p.setDeltaMovement(.2,1,.1);
        SpaceRuntime.velocityHint(p,p.getDeltaMovement());h.assertTrue(SpaceRuntime.ascend(p),"server queues actual orbital ascent");
        pollAscent(h,p,source,0);
    }
    private static void pollAscent(GameTestHelper h,ServerPlayer p,BlockPos source,int tries){Delays.after(h,5,()->{
        if(!SpaceRuntime.isSpace(p.level())){h.assertTrue(tries<100,"orbital preload completes");SpaceRuntime.velocityHint(p,new Vec3(.2,1,.1));pollAscent(h,p,source,tries+1);return;}   // as the client sends it every ten ticks
        h.assertTrue(p.serverLevel().getChunkSource().getGenerator() instanceof OrbitGenerator,"actual runtime orbital world");
        h.assertTrue(PlanetEnvironment.gravity(p.level())==0,"space has real zero gravity");
        h.assertTrue(GalaxyData.current(p).equals(GalaxyCatalog.EARTH),"map remains in the correct star system");
        var packet=GalaxyService.survey(p,GalaxyService.SYSTEM,GalaxyCatalog.EARTH,0,"",0);FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());SpaceScenePacket.encode(new SpaceScenePacket(packet),buf);
        h.assertTrue(buf.readableBytes()<65536,"orbital metadata stays under 64 KiB");SpaceScenePacket.decode(buf);buf.release();
        var up=SpaceTelemetry.last(p.server,p.getUUID(),SpaceTelemetry.Kind.ASCENT);   // continuity of the actual crossing (space rework phase 1)
        h.assertTrue(up!=null&&up.positionError()<.01&&up.headingError()<.5&&up.pitchError()<.5,"ascent keeps position and view: "+(up==null?"no record":up.line()));
        h.assertTrue(up.directionError()<5&&Math.abs(up.speedError())<.1,"ascent keeps velocity through the turning ground frame: "+up.line());
        var body=SpaceRuntime.system(p.serverLevel()).planets().get(0);var ref=new GalaxyCatalog.Ref(GalaxyCatalog.EARTH,0);var frame=SpaceRuntime.frame(p,ref,body);
        float[] seen=SpaceFrames.look(SpaceFrames.basis(frame,source.getX()+.5,source.getZ()+.5),30,-15,true);   // phase 3: the planet turns, so the view is the departure view in its frame
        h.assertTrue(SpaceFrames.angle(SpaceFrames.forward(p.getYRot(),p.getXRot()),SpaceFrames.forward(seen[0],seen[1]))<.5,"the view carries across in the ground frame: "+p.getYRot()+"/"+p.getXRot());
        Vec3 n=SpaceFrames.normal(frame,source.getX()+.5,source.getZ()+.5),at=frame.center().add(n.scale(frame.orbitalRadius()+frame.entryAltitude()-1));
        var basis=SpaceFrames.basis(frame,source.getX()+.5,source.getZ()+.5);Vec3 inbound=n.scale(-1).add(basis.east().scale(.4));
        p.getPersistentData().remove(SpaceRuntime.SETTLE);   // as if the two-second settle after the ascent had passed
        p.teleportTo(p.serverLevel(),at.x,at.y,at.z,30,20);p.setDeltaMovement(inbound);SpaceRuntime.velocityHint(p,inbound);
        SpaceRuntime.player(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(PlanetRuntime.pending(p),"entry proximity queues surface preload");
        pollEntry(h,p,source,0,SpaceFrames.look(basis,30,20,false));   // the orbital view carried into the ground frame
    });}
    private static void pollEntry(GameTestHelper h,ServerPlayer p,BlockPos source,int tries,float[] view){Delays.after(h,5,()->{
        if(SpaceRuntime.isSpace(p.level())||PlanetRuntime.pending(p)){h.assertTrue(tries<100,"surface preload/reentry completes");pollEntry(h,p,source,tries+1,view);return;}
        h.assertTrue(p.level().dimension().equals(Level.OVERWORLD),"re-entry returns to original Earth dimension");
        h.assertTrue(Math.hypot(p.getX()-source.getX()-.5,p.getZ()-source.getZ()-.5)<16,"the round trip lands within 16 blocks of the departure (the planet turned under this client-less player during the preload): "+p.getX()+","+p.getZ());
        h.assertTrue(p.getY()>300&&p.getDeltaMovement().y<0,"re-entry is above the surface with downward momentum");
        Vec3 want=new Vec3(.4,-1,0),got=p.getDeltaMovement();                      // the inbound velocity, in the ground frame (it used to keep 15% of its sideways speed, along world axes)
        h.assertTrue(SpaceFrames.angle(got,want)<5&&Math.abs(got.length()/want.length()-1)<.1,"re-entry keeps the velocity's direction and speed: "+got);
        h.assertTrue(SpaceFrames.angle(SpaceFrames.forward(p.getYRot(),p.getXRot()),SpaceFrames.forward(view[0],view[1]))<2,"re-entry keeps the view (pitch used to snap to level): "+p.getYRot()+"/"+p.getXRot()+" for "+view[0]+"/"+view[1]);
        var down=SpaceTelemetry.last(p.server,p.getUUID(),SpaceTelemetry.Kind.ENTRY);
        h.assertTrue(down!=null&&down.headingError()<.5&&down.directionError()<5,"entry telemetry: "+(down==null?"no record":down.line()));
        TestPlayers.remove(h,p);h.succeed();
    });}
}
