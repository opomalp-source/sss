package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.*;
import com.dbzenith.config.DBZConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(DBZenith.MOD_ID) @PrefixGameTestTemplate(false)
public final class CraterPipelineTests {
    @GameTest(template="empty") public static void thermalMaterialsFollowTheSubstrate(GameTestHelper h){
        h.assertTrue(Destruction.thermal(Blocks.SANDSTONE.defaultBlockState(),2,0)==Blocks.GLASS,"sand melts into glass");
        h.assertTrue(Destruction.thermal(Blocks.RED_SAND.defaultBlockState(),2,0)==Blocks.ORANGE_STAINED_GLASS,"red sand retains its hue");
        h.assertTrue(Destruction.thermal(Blocks.STONE.defaultBlockState(),3,0)==Blocks.MAGMA_BLOCK,"powerful impact leaves hot rock");
        h.assertTrue(Destruction.thermal(Blocks.CHEST.defaultBlockState(),8,0)==null&&Destruction.thermal(Blocks.DIAMOND_BLOCK.defaultBlockState(),8,0)==null,"build materials are not converted into generic earth");
        h.succeed();
    }
    @GameTest(template="empty") public static void malformedImpactsNeverStartWork(GameTestHelper h){
        var at=Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO));var up=new Vec3(0,1,0);
        h.assertTrue(Destruction.ki(h.getLevel(),at,up,Double.NaN,null)==0,"NaN force refused");
        h.assertTrue(Destruction.ki(h.getLevel(),new Vec3(Double.POSITIVE_INFINITY,0,0),up,3,null)==0,"infinite position refused");
        h.assertTrue(Destruction.ki(h.getLevel(),at,new Vec3(Double.NaN,0,0),3,null)==0,"invalid normal refused");h.succeed();
    }
    public static final class Denied {
        BlockPos pos;
        @SubscribeEvent public void protect(BlockEvent.BreakEvent e){if(e.getPos().equals(pos))e.setCanceled(true);}
    }
    @GameTest(template="empty") public static void legacyExplosionCannotBypassTheTerrainQueue(GameTestHelper h){
        var cfg=DBZConfig.SERVER;boolean before=cfg.kiBlastsBreakBlocks.get();var at=h.absolutePos(new BlockPos(0,100,0));
        try{
            cfg.kiBlastsBreakBlocks.set(true);h.getLevel().setBlock(at,Blocks.STONE.defaultBlockState(),18);
            com.dbzenith.skill.KiExplosion.blast(h.getLevel(),null,at.getX()+.5,at.getY()+.5,at.getZ()+.5,3);
            h.assertTrue(h.getLevel().getBlockState(at).is(Blocks.STONE),"vanilla blast cannot perform immediate unprotected block removal");
        }finally{cfg.kiBlastsBreakBlocks.set(before);h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),18);}
        h.succeed();
    }
    @GameTest(template="empty",batch="zz_crater_pipeline",timeoutTicks=500)
    public static void realJobsRespectBudgetsBoundariesRevalidationAndPlayerEdits(GameTestHelper h){
        var ctx=new Context(h);
        try{
            ctx.fill();var l=h.getLevel();var c=DBZConfig.SERVER;
            c.pvpSpawnSafeRadius.set(0);c.craterRegrowSeconds.set(0);c.craterBlocksPerTick.set(50);c.craterScansPerTick.set(512);c.craterMaxJobs.set(2);c.kiCraterMaxRadius.set(4d);c.kiCraterMaxBlocks.set(300);
            PvpZones.of(l.getServer()).add(ctx.zone,l.dimension().location().toString(),ctx.c.offset(1,-16,-12),ctx.c.offset(12,8,12));
            l.setBlock(ctx.chest,Blocks.CHEST.defaultBlockState(),18);
            h.assertTrue(Destruction.ki(l,Vec3.atBottomCenterOf(ctx.c),new Vec3(0,1,0),2,ctx.p)>0,"first crater accepted without scanning");
            l.setBlock(ctx.hardened,Blocks.BEDROCK.defaultBlockState(),18);
            var other=ctx.c.offset(32,0,0);l.getChunkAt(other);l.getChunkAt(ctx.c.offset(64,0,0));
            h.assertTrue(Destruction.ki(l,Vec3.atBottomCenterOf(other),new Vec3(0,1,0),2,ctx.p)>0,"second job accepted");
            h.assertTrue(Destruction.ki(l,Vec3.atBottomCenterOf(ctx.c.offset(64,0,0)),new Vec3(0,1,0),2,ctx.p)==0,"global job cap refuses excess work");
            h.assertTrue(l.getBlockState(ctx.denied.pos).is(Blocks.SANDSTONE),"acceptance does not edit terrain");
            waitJobs(ctx,0,false);
        }catch(Throwable ex){ctx.close();throw ex;}
    }
    private static void waitJobs(Context ctx,int tick,boolean regrow){
        Delays.after(ctx.h,1,()->{try{
            var h=ctx.h;var w=Destruction.work();
            h.assertTrue(w.inspected()<=512&&w.changed()<=50&&w.notifications()<=128&&w.tracked()<=DBZConfig.SERVER.craterMaxRegrowth.get(),"all queued stages share bounded work: "+w);
            if(w.jobs()>0){h.assertTrue(tick<220,"bounded fixture eventually finishes");waitJobs(ctx,tick+1,regrow);return;}
            var l=h.getLevel();
            if(!regrow){
                h.assertTrue(l.getBlockState(ctx.chest).is(Blocks.CHEST),"inventory survives");
                h.assertTrue(l.getBlockState(ctx.hardened).is(Blocks.BEDROCK),"hardness revalidated after acceptance");
                h.assertTrue(l.getBlockState(ctx.denied.pos).is(Blocks.SANDSTONE),"Forge protection cancellation survives");
                int glass=0;for(int x=0;x<9;x++)for(int y=0;y<8;y++)for(int z=0;z<9;z++){
                    var pos=ctx.o.offset(x,y,z);
                    if(pos.getX()>=ctx.c.getX()+1)h.assertTrue(l.getBlockState(pos).is(Blocks.SANDSTONE),"crater edge cannot enter safe zone");
                    if(l.getBlockState(pos).is(Blocks.GLASS))glass++;
                }
                h.assertTrue(glass>0,"real crater has a melted glass floor");
                h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new net.minecraft.world.phys.AABB(ctx.o,ctx.o.offset(9,12,9))).isEmpty(),"ki rim material cannot migrate as falling blocks");
                PvpZones.of(l.getServer()).remove(ctx.zone);MinecraftForge.EVENT_BUS.unregister(ctx.denied);
                ctx.fill();DBZConfig.SERVER.craterRegrowSeconds.set(1);
                h.assertTrue(Destruction.ki(l,Vec3.atBottomCenterOf(ctx.c),new Vec3(0,1,0),2,ctx.p)>0,"temporary crater queued");
                waitJobs(ctx,0,true);
            }else{
                h.assertTrue(l.getBlockState(ctx.c.below()).isAir(),"temporary crater really removed its core");
                l.setBlock(ctx.c.below(),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
                Delays.after(h,60,()->{try{
                    h.assertTrue(l.getBlockState(ctx.c.below()).is(Blocks.DIAMOND_BLOCK),"restoration preserves a later player build");
                    int restored=0;var changed=new java.util.ArrayList<String>();for(int x=0;x<9;x++)for(int y=0;y<8;y++)for(int z=0;z<9;z++){
                        var pos=ctx.o.offset(x,y,z);if(l.getBlockState(pos).is(Blocks.SANDSTONE))restored++;else if(!pos.equals(ctx.c.below()))changed.add(pos+"="+l.getBlockState(pos));
                    }
                    h.assertTrue(restored==647,"temporary removal and scorch restore original terrain: "+restored+" "+changed);
                    ctx.close();h.succeed();
                }catch(Throwable ex){ctx.close();throw ex;}});
            }
        }catch(Throwable ex){ctx.close();throw ex;}});
    }
    private static final class Context {
        final GameTestHelper h;final ServerPlayer p;final BlockPos o,c,chest,hardened;final Denied denied=new Denied();final String zone="crater-pipeline-"+java.util.UUID.randomUUID();
        final int spawn,regrow,writes,scans,jobs,cap;final double radius;boolean closed;
        Context(GameTestHelper h){
            this.h=h;var cfg=DBZConfig.SERVER;spawn=cfg.pvpSpawnSafeRadius.get();regrow=cfg.craterRegrowSeconds.get();writes=cfg.craterBlocksPerTick.get();scans=cfg.craterScansPerTick.get();jobs=cfg.craterMaxJobs.get();radius=cfg.kiCraterMaxRadius.get();cap=cfg.kiCraterMaxBlocks.get();
            p=TestPlayers.create(h);p.setGameMode(GameType.CREATIVE);
            o=h.absolutePos(new BlockPos(-4,70,-4));c=o.offset(4,8,4);chest=c.below().south();hardened=c.below(2).west(2);denied.pos=c.below().west();
            MinecraftForge.EVENT_BUS.register(denied);
        }
        void fill(){for(int x=-3;x<12;x++)for(int y=-2;y<12;y++)for(int z=-3;z<12;z++)h.getLevel().setBlock(o.offset(x,y,z),x>=0&&x<9&&y>=0&&y<8&&z>=0&&z<9?Blocks.SANDSTONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);}
        void close(){
            if(closed)return;closed=true;var cfg=DBZConfig.SERVER;cfg.pvpSpawnSafeRadius.set(spawn);cfg.craterRegrowSeconds.set(regrow);cfg.craterBlocksPerTick.set(writes);cfg.craterScansPerTick.set(scans);cfg.craterMaxJobs.set(jobs);cfg.kiCraterMaxRadius.set(radius);cfg.kiCraterMaxBlocks.set(cap);
            PvpZones.of(h.getLevel().getServer()).remove(zone);MinecraftForge.EVENT_BUS.unregister(denied);
            for(int x=-3;x<12;x++)for(int y=-2;y<12;y++)for(int z=-3;z<12;z++)h.getLevel().setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
            TestPlayers.remove(h,p);
        }
    }
}
