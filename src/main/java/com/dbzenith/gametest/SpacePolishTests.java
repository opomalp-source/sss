package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.space.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(DBZenith.MOD_ID) @PrefixGameTestTemplate(false)
public final class SpacePolishTests {
    @GameTest(template="empty") public static void orbitalMeshesAreSharedBoundedAndSeamless(GameTestHelper h){
        for(int quality=0;quality<3;quality++)for(double radius:new double[]{.1,1,4,25}){
            int detail=OrbitalLod.segments(radius,quality);var mesh=OrbitalLod.mesh(detail);
            h.assertTrue(mesh==OrbitalLod.mesh(detail)&&mesh.size()<=1568,"unit meshes are reused with a hard tessellation bound");
            for(var patch:mesh)for(var n:patch.corners())h.assertTrue(Double.isFinite(n.lengthSqr())&&Math.abs(n.length()-1)<1e-9,"unit normals remain finite through poles and seams");
            h.assertTrue(OrbitalLod.texture(radius,false,quality)<=512,"bounded texture resolution");
        }
        h.assertTrue(!OrbitalLod.visible(new Vec3(0,0,-100),10,new Vec3(0,0,1))&&OrbitalLod.visible(new Vec3(100,0,-.5),1,new Vec3(0,0,1)),"culling rejects fully hidden bodies while retaining silhouettes crossing the camera plane");
        boolean refused=false;try{OrbitalLod.mesh(10000);}catch(IllegalArgumentException ex){refused=true;}h.assertTrue(refused,"arbitrary detail cannot allocate an unbounded mesh");h.succeed();
    }
    @GameTest(template="empty") public static void discoverySearchHasBoundedWorkAndRestartSafeIndices(GameTestHelper h){
        var data=new GalaxyData();var owner=UUID.randomUUID();GalaxyCatalog.Ref last=null;
        for(int i=0;i<1800;i++){last=new GalaxyCatalog.Ref(new GalaxyCatalog.Id(i-900,33,800,3),0);data.discover(last,owner,"Explorer",i,i==1799?"Silent Needle":"Archive "+i);}
        h.assertTrue(data.discoveryCount()==1800&&data.knownSystem(last.system()),"derived discovery indices updated without touching terrain");
        int cursor=0,matches=0,pages=0;boolean more;
        do{var page=GalaxyService.searchPage(data,42,"Silent Needle",cursor);h.assertTrue(page.getInt("examined")<=512&&page.getList("entries",10).size()<=32,"both scanned records and returned matches are bounded");int next=page.getInt("nextCursor");h.assertTrue(next>cursor,"sparse pages always advance");cursor=next;matches+=page.getList("entries",10).size();more=page.getBoolean("more");pages++;}while(more&&pages<10);
        h.assertTrue(matches==1&&pages==4&&cursor==1800,"sparse searches continue through empty pages without missing late matches");
        var loaded=GalaxyData.load(data.save(new CompoundTag()));h.assertTrue(loaded.discoveryCount()==1800&&loaded.knownSystem(last.system()),"indices rebuild from the existing v1 save schema");
        h.assertTrue(!GalaxyService.searchPage(loaded,42,"",Integer.MAX_VALUE).getBoolean("more"),"large cursors cannot overflow or restart an unbounded scan");h.succeed();
    }
    @GameTest(template="empty") public static void remnantHeightQueriesMatchRealColumns(GameTestHelper h){
        var g=new OrbitGenerator(h.getLevel().getChunkSource().getGenerator().getBiomeSource(),42,new GalaxyCatalog.Id(10,4,7,2));g.remnants=List.of(new Remnants.Fragment(new Vec3(12,-500,12),12,57,0),new Remnants.Fragment(new Vec3(40,700,40),14,81,1));
        for(int x:new int[]{0,12,17,40,50,80})for(int z:new int[]{0,12,40,50}){
            int expected=-1024;for(int y=1023;y>=-1024;y--)if(!g.block(x,y,z).isAir()){expected=y+1;break;}
            int actual=g.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,h.getLevel(),null);h.assertTrue(actual==expected,"bounded height search agrees with actual negative/positive remnant columns");
        }h.succeed();
    }
    @GameTest(template="empty",batch="space_polish_momentum") public static void playerMotionHintsAreFiniteIndependentAndDimensionBound(GameTestHelper h){
        var a=TestPlayers.create(h);var b=TestPlayers.create(h);
        try{
            var orbit=SpaceRuntime.open(a.server,GalaxyCatalog.EARTH);a.teleportTo(orbit,0,700,0,0,0);b.teleportTo(orbit,10,700,0,0,0);
            h.assertTrue(!SpaceRuntime.velocityHint(a,new Vec3(Double.NaN,0,0))&&!SpaceRuntime.velocityHint(a,new Vec3(6,0,0)),"non-finite and oversized hints are refused");
            var av=new Vec3(1,.2,0);var bv=new Vec3(-1,0,.3);h.assertTrue(SpaceRuntime.velocityHint(a,av)&&SpaceRuntime.velocityHint(b,bv),"legal orbital hints accepted");
            h.assertTrue(SpaceRuntime.momentum(a).equals(av)&&SpaceRuntime.momentum(b).equals(bv),"players never share a velocity hint");
            a.teleportTo(a.server.overworld(),400,100,400,0,0);a.setDeltaMovement(.3,0,0);
            h.assertTrue(!SpaceRuntime.velocityHint(a,av)&&SpaceRuntime.momentum(a).equals(a.getDeltaMovement()),"orbital hints cannot leak into a different dimension or low-altitude ascent");
            h.assertTrue(SpaceRuntime.momentum(b).equals(bv),"another player's hint survives an unrelated transfer");
        }finally{TestPlayers.remove(h,a);TestPlayers.remove(h,b);}h.succeed();
    }
    @GameTest(template="empty",batch="space_polish_transit",timeoutTicks=700) public static void concurrentTransfersCancelOnlyThePlayerWhoLeft(GameTestHelper h){
        var a=TestPlayers.create(h);var b=TestPlayers.create(h);a.setGameMode(GameType.CREATIVE);b.setGameMode(GameType.CREATIVE);a.setNoGravity(true);b.setNoGravity(true);
        a.teleportTo(110,512,110);b.teleportTo(-110,512,-110);h.assertTrue(SpaceRuntime.ascend(a)&&SpaceRuntime.ascend(b),"independent concurrent preloads accepted");
        b.teleportTo(b.server.getLevel(com.dbzenith.world.Planet.NAMEK.dimension()),8,140,8,0,0);poll(h,a,b,0);
    }
    private static void poll(GameTestHelper h,ServerPlayer a,ServerPlayer b,int tries){Delays.after(h,5,()->{try{
        if(SpaceRuntime.pending(a)||SpaceRuntime.pending(b)){h.assertTrue(tries<100,"concurrent transfer preloads finish/cancel");poll(h,a,b,tries+1);return;}
        h.assertTrue(SpaceRuntime.isSpace(a.level())&&b.level().dimension().equals(com.dbzenith.world.Planet.NAMEK.dimension()),"one player's cancellation cannot redirect or cancel the other's journey");TestPlayers.remove(h,a);TestPlayers.remove(h,b);h.succeed();
    }catch(Throwable ex){TestPlayers.remove(h,a);TestPlayers.remove(h,b);throw ex;}});}
}
