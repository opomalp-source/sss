package com.dbzenith.galaxy.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Deterministic public-identity debris. Only visited chunks materialize; mined fragments never respawn. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class Remnants {
    public record Fragment(Vec3 center,int radius,long seed,int body){}
    public static List<Fragment> fragments(GalaxyCatalog.Ref ref,GalaxyCatalog.Body body){
        return fragments(ref.key(),body.index(),SpaceMath.center(body),SpaceMath.radius(body.radius()));
    }
    public static List<Fragment> fragments(String identity,int index,Vec3 center,double radius){
        var result=new ArrayList<Fragment>();
        for(int i=0;i<24;i++){
            long h=GalaxyCatalog.mix(identity.hashCode()*31L+i*0x632be59bd9b4e019L);double a=i*2.399963229728653,dist=radius*(.65+Math.floorMod(h,100)/100d*.85);
            result.add(new Fragment(center.add(Math.cos(a)*dist,Math.sin(i*2.17)*radius*.32,Math.sin(a)*dist),8+(int)Math.floorMod(h>>>12,7),h,index));
        }return List.copyOf(result);
    }
    public static BlockState material(Fragment f,int x,int y,int z){
        var p=f.center;double d=((x-p.x)*(x-p.x)+(z-p.z)*(z-p.z)+(y-p.y)*(y-p.y)*1.35)/(f.radius*(double)f.radius);
        if(d>1)return Blocks.AIR.defaultBlockState();
        long h=GalaxyCatalog.mix(f.seed^x*31L^y*97L^z*131L);
        return ((h&127)==0?Blocks.IRON_ORE:(h&31)==0?Blocks.MAGMA_BLOCK:(h&3)==0?Blocks.BLACKSTONE:Blocks.ANDESITE).defaultBlockState();
    }
    private static final Map<MinecraftServer,ArrayDeque<Fill>> WORK=new WeakHashMap<>();
    private static final class Fill {
        final ServerLevel level;final LevelChunk chunk;final Fragment f;int cursor;boolean wrote;
        Fill(ServerLevel l,LevelChunk c,Fragment f){level=l;chunk=c;this.f=f;}
        boolean step(){
            wrote=false;int height=f.radius*2+3;if(cursor>=256*height)return true;
            int n=cursor++,x=chunk.getPos().getMinBlockX()+(n&15),z=chunk.getPos().getMinBlockZ()+((n>>4)&15),y=(int)Math.floor(f.center.y)-f.radius-1+(n>>8);
            var p=new BlockPos(x,y,z);var state=material(f,x,y,z);
            if(!state.isAir()&&level.isInWorldBounds(p)&&level.hasChunkAt(p)&&level.getBlockState(p).isAir()&&!com.dbzenith.combat.Destruction.isProtected(level,Vec3.atCenterOf(p)))wrote=level.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            return false;
        }
    }
    public static void configure(ServerLevel l){
        if(!(l.getChunkSource().getGenerator() instanceof OrbitGenerator g))return;
        var list=new ArrayList<Fragment>();var data=GalaxyData.get(l.getServer());var sys=SpaceRuntime.system(l);
        for(var b:sys.planets()){var ref=new GalaxyCatalog.Ref(sys.id(),b.index());if(data.destroyed(ref))list.addAll(fragments(ref,b));}
        g.remnants=List.copyOf(list);
    }
    public static void refresh(MinecraftServer s,GalaxyCatalog.Id id){
        var l=s.getLevel(SpaceRuntime.key(id));if(l==null)return;configure(l);
        var g=(OrbitGenerator)l.getChunkSource().getGenerator();var seen=new HashSet<ChunkPos>();
        for(var f:g.remnants)for(int x=((int)f.center.x-f.radius)>>4;x<=((int)f.center.x+f.radius)>>4;x++)for(int z=((int)f.center.z-f.radius)>>4;z<=((int)f.center.z+f.radius)>>4;z++){
            var cp=new ChunkPos(x,z);if(!seen.add(cp))continue;var c=l.getChunkSource().getChunkNow(x,z);if(c!=null)enqueue(l,c);
        }
    }
    private static void enqueue(ServerLevel l,LevelChunk c){
        if(!(l.getChunkSource().getGenerator() instanceof OrbitGenerator g))return;
        var queue=WORK.computeIfAbsent(l.getServer(),s->new ArrayDeque<>());
        var byBody=new HashMap<Integer,List<Fragment>>();
        for(var f:g.remnants)if(intersects(f,c.getPos()))byBody.computeIfAbsent(f.body,k->new ArrayList<>()).add(f);
        for(var entry:byBody.entrySet()){
            var data=DoomData.get(l.getServer());var event=data.events.get(new GalaxyCatalog.Ref(g.system,entry.getKey()));
            if(event==null||event.remnantChunks.contains(c.getPos().toLong())||event.remnantChunks.size()>=1024||queue.size()+entry.getValue().size()>256)continue;
            // Existing occupied chunks are left alone, including air inside old orbital builds.
            boolean empty=true;for(var section:c.getSections())if(!section.hasOnlyAir()){empty=false;break;}
            event.remnantChunks.add(c.getPos().toLong());data.setDirty();
            if(empty)for(var f:entry.getValue())queue.addLast(new Fill(l,c,f));
        }
    }
    public static boolean intersects(Fragment f,ChunkPos c){return f.center.x+f.radius>=c.getMinBlockX()&&f.center.x-f.radius<=c.getMaxBlockX()&&f.center.z+f.radius>=c.getMinBlockZ()&&f.center.z-f.radius<=c.getMaxBlockZ();}
    @SubscribeEvent public static void load(ChunkEvent.Load e){if(e.getLevel() instanceof ServerLevel l&&e.getChunk() instanceof LevelChunk c)enqueue(l,c);}
    public static void tick(MinecraftServer s){
        var q=WORK.get(s);if(q==null)return;int scan=2048,writes=128;
        while(!q.isEmpty()&&scan-->0&&writes>0){var f=q.peekFirst();
            var g=(OrbitGenerator)f.level.getChunkSource().getGenerator();
            if(!GalaxyData.get(s).destroyed(new GalaxyCatalog.Ref(g.system,f.f.body))||s.getLevel(f.level.dimension())!=f.level||!f.level.hasChunk(f.chunk.getPos().x,f.chunk.getPos().z)||f.step())q.removeFirst();
            if(f.wrote)writes--;
        }
    }
    @SubscribeEvent public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e){
        if(e.isCanceled()||com.dbzenith.combat.Destruction.internalPlacement()||!(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))return;
        var q=WORK.get(p.server);if(q!=null)q.removeIf(f->f.level==p.level()&&f.chunk.getPos().equals(new ChunkPos(e.getPos())));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){WORK.remove(e.getServer());}
}
