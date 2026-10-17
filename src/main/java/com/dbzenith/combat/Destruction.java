package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.ImpactPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Craters (CX-20): fighters blown into walls and the ground, and ki attacks, tear the land up, more the harder the
 * impact. One routine for both, by a {@code force} (about 0..3):
 * <ul>
 *   <li>under {@link #CRACK_FORCE}: nothing;</li>
 *   <li>up to {@link #DIG_FORCE}: the surface cracks (grass to coarse dirt, stone to cobblestone, bricks crack...);</li>
 *   <li>above: a bowl dug into the surface along its normal (wider than deep), its size growing with the force, cracked
 *       blocks round its rim, debris and dust.</li>
 * </ul>
 * Never inside safe zones (PvP zones, the spawn's safe radius, the tournament grounds), never blocks holding something
 * (chests, furnaces...), fluids, unbreakable blocks, or blocks harder than the force allows (up to {@code maxHardness}).
 * A player's crater asks the protection mods first (a block break event for each block). NPCs follow the
 * {@code mobGriefing} rule. Server config {@code [destruction]}.
 */
@Mod.EventBusSubscriber(modid = com.dbzenith.DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Destruction {
    public static final double CRACK_FORCE = 0.35, DIG_FORCE = 0.8;

    private static final Map<Block, Block> CRACKED = Map.ofEntries(
            Map.entry(Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT), Map.entry(Blocks.DIRT, Blocks.COARSE_DIRT), Map.entry(Blocks.PODZOL, Blocks.COARSE_DIRT),
            Map.entry(Blocks.MYCELIUM, Blocks.COARSE_DIRT), Map.entry(Blocks.DIRT_PATH, Blocks.COARSE_DIRT), Map.entry(Blocks.STONE, Blocks.COBBLESTONE),
            Map.entry(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS), Map.entry(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE),
            Map.entry(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS), Map.entry(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), Map.entry(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS),
            Map.entry(Blocks.SANDSTONE, Blocks.SAND), Map.entry(Blocks.RED_SANDSTONE, Blocks.RED_SAND), Map.entry(Blocks.SNOW_BLOCK, Blocks.SNOW),
            Map.entry(Blocks.ICE, Blocks.AIR), Map.entry(Blocks.GRAVEL, Blocks.GRAVEL));

    private Destruction() {}
    private static boolean internalPlace;
    public static boolean internalPlacement(){return internalPlace;}
    private static boolean sealed(ServerLevel l){var ref=com.dbzenith.galaxy.space.SpaceRuntime.surfaceRef(l);return ref!=null&&(com.dbzenith.galaxy.GalaxyData.get(l.getServer()).destroyed(ref)||com.dbzenith.galaxy.planet.PlanetDestruction.restoring(l.getServer(),ref));}

    /** The bowl's radius (blocks) for a force: about 1 at the digging threshold, 6 at full force. */
    public static double radius(double force) {
        if (force < DIG_FORCE) return 0;
        return Math.min(DBZConfig.SERVER.destructionMaxRadius.get(), 0.9 + (force - DIG_FORCE) * 2.3);
    }

    /** The hardest block a force can break (stone 1.5, deepslate 3, iron 5, obsidian 50). */
    public static double hardnessLimit(double force) {
        return Math.min(DBZConfig.SERVER.destructionMaxHardness.get(), 2 + force * 12);
    }

    /** A fighter blown away slamming into something. */
    public static int slam(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity victim, Entity by) {
        if (!DBZConfig.SERVER.destructionKnockback.get()) return 0;
        return crater(level, at, normal, force, by);
    }

    /** A ki attack striking something: the same routine, but far bigger, with a scorched floor, a raised rim and debris. */
    public static int ki(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        if (!DBZConfig.SERVER.destructionKi.get()) return 0;
        if(Double.isFinite(force)&&Double.isFinite(at.x+at.y+at.z+normal.x+normal.y+normal.z)&&level.hasChunkAt(BlockPos.containing(at))&&!isProtected(level,at))com.dbzenith.galaxy.planet.PlanetDestruction.attack(level,at,force,by);
        return crater(level, at, normal, force, by, true, true);
    }

    /** A beam held on something, carving into it as it goes: as big, but no lip or flung debris (it would pile up). */
    public static int kiCarve(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        if (!DBZConfig.SERVER.destructionKi.get()) return 0;
        if(Double.isFinite(force)&&Double.isFinite(at.x+at.y+at.z+normal.x+normal.y+normal.z)&&level.hasChunkAt(BlockPos.containing(at))&&!isProtected(level,at))com.dbzenith.galaxy.planet.PlanetDestruction.attack(level,at,force,by);
        return crater(level, at, normal, force, by, true, false);
    }

    /** A ki crater's radius for a force (the ki attack rework, phase 6: "make craters very huge"). */
    public static double kiRadius(double force) {
        if (force < DIG_FORCE) return 0;
        return Math.min(DBZConfig.SERVER.kiCraterMaxRadius.get(), (0.9 + (force - DIG_FORCE) * 2.3) * DBZConfig.SERVER.kiCraterScale.get());
    }


    /** Queued ki impacts return an accepted work estimate; small melee impacts return actual removals. */
    public static int crater(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by) {
        return crater(level, at, normal, force, by, false, false);
    }
    private static int crater(ServerLevel level, Vec3 at, Vec3 normal, double force, Entity by, boolean ki, boolean extras) {
        var c=DBZConfig.SERVER;
        if(!Double.isFinite(force)||!Double.isFinite(at.x+at.y+at.z+normal.x+normal.y+normal.z)
            ||sealed(level)||!c.destructionEnabled.get()||force<CRACK_FORCE||isProtected(level,at)
            ||!level.hasChunkAt(BlockPos.containing(at))||!(by instanceof Player)&&!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return 0;
        int cap=ki?c.kiCraterMaxBlocks.get():c.destructionMaxBlocks.get();if(cap==0)return 0;
        double r=ki?kiRadius(force):radius(force);
        if(JOBS.size()>=c.craterMaxJobs.get())return 0;
        var job=new Job(level,at,normal,force,by,ki,extras&&r>=3,cap);
        if(ki){
            if(JOBS.size()>=c.craterMaxJobs.get()||JOBS.stream().anyMatch(j->j.level==level&&j.owner==by&&j.centre.distanceToSqr(job.centre)<4))return 0;
            JOBS.addLast(job);
        }else{
            var budget=new Budget(100000,Math.max(1,cap*2),128);
            while(!job.done()&&budget.scans>0&&budget.writes>0){if(!job.step(budget))break;}
            // Remaining neighbour reconciliation/restoration uses the same bounded queue as ki work.
            if(!job.done())JOBS.addLast(job);else job.complete();
        }
        ImpactPacket.at(at,job.normal,ImpactPacket.CRATER,(float)Math.max(.6,r),0xA08A6A,by==null?-1:by.getId())
            .withFeel(c.craterDebris.get()?ImpactPacket.RUBBLE:0,0).send(level);
        return ki?Math.min(cap,Math.max(1,(int)(r*r*r*2))):job.removed;
    }

    private static final java.util.ArrayDeque<Job> JOBS=new java.util.ArrayDeque<>();
    private record Regrow(ServerLevel level,BlockPos pos,BlockState was,BlockState placed,long due){}
    private record Regrowth(ServerLevel level,java.util.NavigableMap<BlockPos,Regrow> blocks,long due){}
    private static final java.util.PriorityQueue<Regrowth> REGROW=new java.util.PriorityQueue<>(java.util.Comparator.comparingLong(Regrowth::due));
    private static int retained,pendingEdits;
    public record WorkStats(int inspected,int changed,int notifications,int jobs,int regrowth,int tracked){}
    private static WorkStats last=new WorkStats(0,0,0,0,0,0);
    public static WorkStats work(){return last;}
    private static final class Budget {
        int scans,writes,notify;
        Budget(int scans,int writes,int notify){this.scans=scans;this.writes=writes;this.notify=notify;}
    }
    /** Successive cube shells, each position once; no allocation or terrain scan when accepting an attack. */
    private static final class Shell {
        final BlockPos base;final int reach;int shell,face,i,j;
        Shell(BlockPos base,int reach){this.base=base;this.reach=reach;}
        BlockPos next(){
            if(shell>reach)return null;
            if(shell==0){shell=1;return base;}
            int a=face<2?2*shell+1:2*shell-1,b=face<4?2*shell+1:2*shell-1;
            int u=i-shell+(face<2?0:1),v=j-shell+(face<4?0:1);
            BlockPos out=switch(face){
                case 0->base.offset(-shell,u,v);case 1->base.offset(shell,u,v);
                case 2->base.offset(u,-shell,v);case 3->base.offset(u,shell,v);
                case 4->base.offset(u,v,-shell);default->base.offset(u,v,shell);};
            if(++j==b){j=0;if(++i==a){i=0;if(++face==6){face=0;shell++;}}}return out;
        }
    }
    private static final class Job {
        final ServerLevel level;final Entity owner;final ServerPlayer blame;final boolean ki,extras,regrow;
        final Vec3 centre,normal;final double r,depth,rim,force;final long seed;final int cap,reach;
        final Shell shell;final List<BlockPos> dug=new ArrayList<>(),changed=new ArrayList<>();
        final java.util.NavigableMap<BlockPos,Regrow> undo=new java.util.TreeMap<>(java.util.Comparator.comparingInt((BlockPos p)->p.getY()).thenComparingLong(p->p.asLong()));
        final int regrowTicks=DBZConfig.SERVER.craterRegrowSeconds.get()*20;
        int stage,index,removed,edits,effects,lx,lz,ly,pileLeft;boolean cancelled;BlockPos pileTop;BlockState pile;
        Job(ServerLevel l,Vec3 at,Vec3 n,double force,Entity by,boolean ki,boolean extras,int cap){
            level=l;owner=by;blame=by instanceof ServerPlayer p?p:null;this.force=force;this.ki=ki;this.extras=extras;this.cap=cap;
            normal=n.lengthSqr()<1e-6?new Vec3(0,1,0):n.normalize();r=ki?kiRadius(force):radius(force);depth=r*.7;
            rim=r>0?r*1.45+.6:1.3+force;centre=at.subtract(normal.scale(r*.3));reach=(int)Math.ceil(rim)+1;
            shell=new Shell(BlockPos.containing(centre),reach);seed=BlockPos.containing(centre).asLong()*31+l.getGameTime();
            regrow=DBZConfig.SERVER.craterRegrowSeconds.get()>0;lx=lz=-reach;ly=(int)centre.y+(int)Math.ceil(r);
        }
        boolean done(){return stage==4;}
        boolean valid(){
            var c=DBZConfig.SERVER;
            return !sealed(level)&&level.getServer().getLevel(level.dimension())==level&&c.destructionEnabled.get()&&(!ki||c.destructionKi.get())
                &&(blame==null?level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING):level.getServer().getPlayerList().getPlayer(blame.getUUID())==blame);
        }
        boolean available(BlockPos p){return level.isInWorldBounds(p)&&level.getWorldBorder().isWithinBounds(p)&&level.hasChunkAt(p)&&!isProtected(level,Vec3.atCenterOf(p));}
        boolean breakable(BlockPos p,BlockState s){
            float h=s.getDestroySpeed(level,p);
            return !s.isAir()&&s.getFluidState().isEmpty()&&!s.hasBlockEntity()&&h>=0&&h<=hardnessLimit(force);
        }
        boolean change(BlockPos p,BlockState from,BlockState to,Budget b,boolean place){
            int needed=1+(regrow&&!undo.containsKey(p)?1:0);
            if(b.writes<=0||edits>=cap*2||retained+pendingEdits+needed>DBZConfig.SERVER.craterMaxRegrowth.get()||!available(p))return false;
            if(blame!=null){
                if(place){
                    internalPlace=true;
                    try{if(MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,p),level.getBlockState(p.below()),blame)))return false;}finally{internalPlace=false;}
                }else if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,p,from,blame)))return false;
            }
            // A protection listener may have replaced the block or introduced a block entity.
            if(level.getBlockState(p)!=from||level.getBlockEntity(p)!=null)return false;
            // Send chunk-batched client changes, queue vanilla light checks, defer neighbour/shape work.
            if(!level.setBlock(p,to,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))return false;
            b.writes--;edits++;changed.add(p);pendingEdits++;
            if(regrow){var old=undo.put(p,new Regrow(level,p,undo.containsKey(p)?undo.get(p).was:from,to,0));if(old==null)retained++;}
            return true;
        }
        boolean step(Budget b){
            if(stage==3){
                if(index>=changed.size()){stage=4;return true;}
                if(b.notify<=0)return false;b.scans--;b.notify--;
                var p=changed.set(index++,null);pendingEdits--;
                boolean loaded=true;for(int dx=-16;dx<=16;dx+=16)for(int dz=-16;dz<=16;dz+=16)loaded&=level.hasChunkAt(p.offset(dx,0,dz));
                if(loaded&&available(p)){level.updateNeighborsAt(p,level.getBlockState(p).getBlock());level.getBlockState(p).updateNeighbourShapes(level,p,Block.UPDATE_CLIENTS);}
                return true;
            }
            b.scans--;
            if(stage==0){
                BlockPos p=shell.next();if(p==null){stage=extras?1:3;index=0;return true;}
                Vec3 v=Vec3.atCenterOf(p).subtract(centre);double along=v.dot(normal),lat2=Math.max(0,v.lengthSqr()-along*along);
                long sample=com.dbzenith.galaxy.GalaxyCatalog.mix(seed^p.asLong());
                double jitter=.82+.36*(sample>>>11)*0x1.0p-53;
                boolean dig=r>0&&lat2/(r*r)+along*along/(depth*depth)<=jitter;
                boolean crack=!dig&&lat2/(rim*rim)+along*along/Math.pow(Math.max(1,depth*1.4),2)<=jitter&&Math.floorMod(sample,100)<55;
                if(!dig&&!crack||!available(p))return true;
                var s=level.getBlockState(p);if(!breakable(p,s))return true;
                if(dig&&removed<cap||crack&&s.canBeReplaced()&&removed<cap){
                    if(change(p,s,Blocks.AIR.defaultBlockState(),b,false)){
                        removed++;if(extras)dug.add(p);
                        double drops=DBZConfig.SERVER.destructionDrops.get();
                        if(drops>0&&Math.floorMod(p.asLong()*40503L+seed,1000)<drops*1000)Block.dropResources(s,level,p,null,owner,ItemStack.EMPTY);
                        if(effects<24&&removed%16==0){level.levelEvent(2001,p,Block.getId(s));effects++;}
                    }
                }else if(crack){
                    var to=CRACKED.get(s.getBlock());
                    if(extras&&force>=1.5&&Math.floorMod(sample>>>24,100)<40&&(s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)))to=thermal(s,force,0);
                    if(ki&&to==Blocks.SAND)to=Blocks.SANDSTONE;
                    if(ki&&to==Blocks.RED_SAND)to=Blocks.RED_SANDSTONE;
                    if(ki&&to==Blocks.SNOW)to=Blocks.PACKED_ICE;
                    if(to!=null&&to!=s.getBlock())change(p,s,to.defaultBlockState(),b,false);
                }return true;
            }
            if(stage==1){
                if(index>=dug.size()){dug.clear();stage=normal.y>.6?2:3;index=0;return true;}
                var p=dug.get(index++).relative(net.minecraft.core.Direction.getNearest(normal.x,normal.y,normal.z).getOpposite());
                if(!available(p))return true;var s=level.getBlockState(p);
                var exposed=p.relative(net.minecraft.core.Direction.getNearest(normal.x,normal.y,normal.z));
                if(!breakable(p,s)||!level.hasChunkAt(exposed)||!level.getBlockState(exposed).isAir())return true;
                var to=thermal(s,force,(int)Math.floorMod(com.dbzenith.galaxy.GalaxyCatalog.mix(seed^p.asLong()),100));
                if(to!=null&&to!=s.getBlock())change(p,s,to.defaultBlockState(),b,false);return true;
            }
            // Raised lip: a single column read or placement per step, no unbounded finish pass.
            if(lx>reach){stage=3;index=0;return true;}
            if(pileLeft>0){
                var p=pileTop.above(pileLeft--);
                if(available(p)&&level.getBlockState(p).isAir())change(p,Blocks.AIR.defaultBlockState(),pile,b,true);
                if(pileLeft==0)nextColumn();return true;
            }
            double d=Math.sqrt(lx*lx+lz*lz)/Math.max(1,r);
            if(d<.95||d>1.45||ly<(int)centre.y-(int)Math.ceil(r)){nextColumn();return true;}
            var p=new BlockPos((int)Math.floor(centre.x)+lx,ly--,(int)Math.floor(centre.z)+lz);
            if(!available(p)){nextColumn();return true;}var s=level.getBlockState(p);
            if(!s.getFluidState().isEmpty()){nextColumn();return true;}
            if(s.isSolid()&&level.getBlockState(p.above()).isAir()){
                Block material=rubble(s);
                if(!breakable(p,s)||material==null){nextColumn();return true;}
                int count=(int)Math.round(Math.max(1,r*.13)*(1-Math.abs(d-1.12)/.33)*(.75+.5*Math.floorMod(com.dbzenith.galaxy.GalaxyCatalog.mix(seed^p.asLong()),100)/100d));
                if(count<=0){nextColumn();return true;}
                pileTop=p;pile=material.defaultBlockState();
                pileLeft=count;
            }return true;
        }
        void nextColumn(){if(++lz>reach){lz=-reach;lx++;}ly=(int)centre.y+(int)Math.ceil(r);pileLeft=0;}
        void complete(){
            long due=level.getServer().getTickCount()+regrowTicks;
            if(!undo.isEmpty())REGROW.add(new Regrowth(level,undo,due));
        }
    }
    private static Block rubble(BlockState s){
        if(s.is(Blocks.SAND)||s.is(Blocks.SANDSTONE))return Blocks.SANDSTONE;
        if(s.is(Blocks.RED_SAND)||s.is(Blocks.RED_SANDSTONE))return Blocks.RED_SANDSTONE;
        if(s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.PODZOL)||s.is(Blocks.MYCELIUM)||s.is(Blocks.MUD))return Blocks.COARSE_DIRT;
        if(s.is(Blocks.STONE)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.ANDESITE))return Blocks.COBBLESTONE;
        if(s.is(Blocks.BLACKSTONE)||s.is(Blocks.BASALT)||s.is(Blocks.TUFF))return s.getBlock();
        if(s.is(Blocks.SNOW)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.ICE))return Blocks.PACKED_ICE;
        var block=CRACKED.get(s.getBlock());return block==Blocks.SAND||block==Blocks.RED_SAND||block==Blocks.AIR?null:block;
    }
    /** Materials are selected from the struck substrate, not randomly substituted into player builds. */
    public static Block thermal(BlockState s,double force,int roll){
        if(s.is(Blocks.SAND)||s.is(Blocks.SANDSTONE))return force>=1.5?Blocks.GLASS:Blocks.SANDSTONE;
        if(s.is(Blocks.RED_SAND)||s.is(Blocks.RED_SANDSTONE))return force>=1.5?Blocks.ORANGE_STAINED_GLASS:Blocks.RED_SANDSTONE;
        if(s.is(Blocks.STONE)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.COBBLED_DEEPSLATE)||s.is(Blocks.TUFF)||s.is(Blocks.BASALT)||s.is(Blocks.BLACKSTONE))
            return force>=2.5&&roll<14?Blocks.MAGMA_BLOCK:roll<60?Blocks.BLACKSTONE:Blocks.BASALT;
        if(s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.PODZOL)||s.is(Blocks.MYCELIUM))return roll<65?Blocks.COARSE_DIRT:Blocks.TUFF;
        return null;
    }
    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        int scans=DBZConfig.SERVER.craterScansPerTick.get(),writes=DBZConfig.SERVER.craterBlocksPerTick.get();
        var b=new Budget(scans,writes,128);int rounds=JOBS.size(),quantum=Math.max(64,scans/Math.max(1,rounds));
        while(rounds-->0&&!JOBS.isEmpty()&&b.scans>0&&b.writes>0){
            var j=JOBS.removeFirst();
            if(j.level.getServer().getLevel(j.level.dimension())!=j.level){retained-=j.undo.size();pendingEdits-=j.stage==3?j.changed.size()-j.index:j.changed.size();continue;}
            if(!j.cancelled&&!j.valid()){j.cancelled=true;j.dug.clear();if(j.stage!=3){j.stage=3;j.index=0;}}
            int limit=Math.max(0,b.scans-quantum);
            while(!j.done()&&b.scans>limit&&b.writes>0)if(!j.step(b))break;
            if(j.done())j.complete();else JOBS.addLast(j);
        }
        int restore=300;long now=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount();
        while(!REGROW.isEmpty()&&REGROW.peek().due<=now&&b.scans>0&&b.writes>0&&restore-->0){
            var batch=REGROW.peek();var g=batch.blocks.pollFirstEntry().getValue();
            if(batch.blocks.isEmpty())REGROW.remove();
            retained--;b.scans--;var l=g.level;
            if(sealed(l)||l.getServer().getLevel(l.dimension())!=l||!l.hasChunkAt(g.pos)||isProtected(l,Vec3.atCenterOf(g.pos))||!l.getWorldBorder().isWithinBounds(g.pos))continue;
            if(l.getBlockState(g.pos)==g.placed&&l.getBlockEntity(g.pos)==null&&l.setBlock(g.pos,g.was,Block.UPDATE_CLIENTS)){b.writes--;}
        }
        last=new WorkStats(scans-b.scans,writes-b.writes,128-b.notify,JOBS.size(),retained,retained+pendingEdits);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel l){
            JOBS.removeIf(j->{if(j.level!=l)return false;retained-=j.undo.size();pendingEdits-=j.stage==3?j.changed.size()-j.index:j.changed.size();return true;});
            REGROW.removeIf(g->{if(g.level!=l)return false;retained-=g.blocks.size();return true;});
        }
    }
    @SubscribeEvent public static void onServerStopping(ServerStoppingEvent e){JOBS.clear();REGROW.clear();retained=pendingEdits=0;}

    /** Places nothing is torn up: PvP safe zones, the world spawn's safe radius, the tournament grounds, the other world. */
    public static boolean isProtected(ServerLevel level, Vec3 at) {
        DBZConfig.Server c = DBZConfig.SERVER;
        if (level.dimension() == com.dbzenith.world.Otherworld.OTHERWORLD) return true;
        int r = c.pvpSpawnSafeRadius.get();
        if (r > 0 && level.dimension() == Level.OVERWORLD) {
            BlockPos s = level.getSharedSpawnPos();
            double dx = at.x - s.getX() - 0.5, dz = at.z - s.getZ() - 0.5;
            if (dx * dx + dz * dz < (double) r * r) return true;
        }
        if (level.dimension() == Level.OVERWORLD) {
            BlockPos ring = com.dbzenith.tournament.TournamentGrounds.ring(level);
            int half = com.dbzenith.tournament.TournamentGrounds.PLAZA + 4;
            if (ring != null && Math.abs(at.x - ring.getX() - 0.5) <= half && Math.abs(at.z - ring.getZ() - 0.5) <= half && Math.abs(at.y - ring.getY()) < 32) return true;
        }
        return PvpZones.of(level.getServer()).at(level.dimension().location().toString(), at) != null;
    }
}
