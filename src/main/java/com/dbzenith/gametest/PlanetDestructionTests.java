package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.planet.*;
import com.dbzenith.galaxy.space.*;
import com.dbzenith.dragonball.Wish;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(DBZenith.MOD_ID) @PrefixGameTestTemplate(false)
public final class PlanetDestructionTests {
    @GameTest(template="empty") public static void durableCountdownAndFissureJournalRoundTrip(GameTestHelper h){
        var ref=new GalaxyCatalog.Ref(IconicSystems.VEGETA,0);var data=new DoomData();var event=new DoomData.Event(ref,UUID.randomUUID(),1200);
        event.elapsed=851;event.age=7;event.scars.put(new BlockPos(15,80,-17),Blocks.RED_SANDSTONE.defaultBlockState());event.remnantChunks.add(new ChunkPos(4,-9).toLong());data.events.put(ref,event);data.working.add(ref);
        var loaded=DoomData.load(data.save(new CompoundTag()));var copy=loaded.events.get(ref);
        h.assertTrue(copy.elapsed==851&&copy.owner.equals(event.owner)&&copy.scars.equals(event.scars)&&copy.remnantChunks.equals(event.remnantChunks)&&loaded.working.contains(ref),"countdown, ownership, materialization and exact original block states survive restart");
        event.phase=DoomData.DESTROYED;event.age=1201;loaded=DoomData.load(data.save(new CompoundTag()));h.assertTrue(loaded.working.isEmpty()&&loaded.events.containsKey(ref),"historical destroyed planets do not enter per-tick iteration");
        var future=new CompoundTag();future.putInt("version",93);future.putString("future","retained");h.assertTrue(DoomData.load(future).save(new CompoundTag()).equals(future),"unknown schemas remain untouched");h.succeed();
    }
    @GameTest(template="empty") public static void destructionMetadataRestoresWithoutChangingIdentity(GameTestHelper h){
        var d=new GalaxyData();var ref=new GalaxyCatalog.Ref(new GalaxyCatalog.Id(301,2,5,3),0);var id=UUID.randomUUID();d.discover(ref,id,"First",123,"Planet Vegeta");d.rename(ref,id,"New Vegeta");d.base(ref,true);d.destruction(ref,true,false,"sealed-v1");
        var copy=GalaxyData.load(d.save(new CompoundTag()));h.assertTrue(copy.destroyed(ref)&&copy.discovery(ref).base(),"destruction and base flags persist together");copy.destruction(ref,true,true,"sealed-v1");
        h.assertTrue(!copy.destroyed(ref)&&copy.discovery(ref).owner().equals(id)&&copy.name(ref,"").equals("New Vegeta"),"restoration retains discoverer and custom name while making the planet intact");
        h.assertTrue(!PlanetDestruction.protection(h.getLevel().getServer(),new GalaxyCatalog.Ref(GalaxyCatalog.EARTH,0)).isEmpty(),"Earth always remains a safe return world");h.succeed();
    }
    @GameTest(template="empty") public static void fragmentsAreDeterministicBoundedAndMineable(GameTestHelper h){
        var ref=new GalaxyCatalog.Ref(IconicSystems.VEGETA,0);var b=GalaxyCatalog.body(42,GalaxyCatalog.SALT,ref);var a=Remnants.fragments(ref,b);
        h.assertTrue(a.equals(Remnants.fragments(ref,b))&&a.size()==24,"deterministic sparse fragment layout");
        for(var f:a){var p=BlockPos.containing(f.center());h.assertTrue(f.radius()>=8&&f.radius()<=14&&!Remnants.material(f,p.getX(),p.getY(),p.getZ()).isAir(),"real block material at the fragment core");h.assertTrue(Remnants.material(f,p.getX()+40,p.getY(),p.getZ()).isAir(),"bounded cluster radius");}
        h.succeed();
    }
    @GameTest(template="empty",batch="zz_planet_doom",timeoutTicks=900)
    public static void actualDestructionEvacuatesSealsAndWishRestoresBuilds(GameTestHelper h){new Lifecycle(h).start();}
    private static final class Lifecycle {
        final GameTestHelper h;final net.minecraft.server.level.ServerPlayer p;final GalaxyCatalog.Ref ref;
        final int cap=PlanetConfig.MAX_LOADED.get(),orbits=PlanetConfig.MAX_ORBITS.get(),seconds=PlanetConfig.DOOM_SECONDS.get();
        final long power=PlanetConfig.DOOM_POWER.get();final BlockPos original=new BlockPos(8,180,8),chest=original.above();int tries;
        Lifecycle(GameTestHelper h){this.h=h;p=TestPlayers.create(h);p.setGameMode(GameType.CREATIVE);p.setNoGravity(true);
            var data=GalaxyData.get(p.server);GalaxyCatalog.Ref found=null;for(int x=701;x<1000&&found==null;x++)for(var b:GalaxyCatalog.system(p.server.overworld().getSeed(),data.salt(),new GalaxyCatalog.Id(x,5,-8,4)).planets())if(b.kind().landable()&&data.discovery(new GalaxyCatalog.Ref(new GalaxyCatalog.Id(x,5,-8,4),b.index()))==null){found=new GalaxyCatalog.Ref(new GalaxyCatalog.Id(x,5,-8,4),b.index());break;}ref=found;
        }
        void close(){PlanetConfig.MAX_LOADED.set(cap);PlanetConfig.MAX_ORBITS.set(orbits);PlanetConfig.DOOM_SECONDS.set(seconds);PlanetConfig.DOOM_POWER.set(power);TestPlayers.remove(h,p);var d=DoomData.get(p.server);d.events.remove(ref);d.working.remove(ref);d.setDirty();GalaxyData.get(p.server).destruction(ref,false,false,"");GalaxyData.get(p.server).base(ref,false);var l=PlanetDestruction.level(p.server,ref);if(l!=null)PlanetRuntime.unload(l);}
        void check(Runnable r){try{r.run();}catch(Throwable ex){close();throw ex;}}
        void later(Runnable r){Delays.after(h,5,()->check(r));}
        void start(){check(()->{
            PlanetConfig.MAX_LOADED.set(16);PlanetConfig.MAX_ORBITS.set(16);PlanetConfig.DOOM_SECONDS.set(30);PlanetConfig.DOOM_POWER.set(Long.MAX_VALUE);
            GalaxyData.get(p.server).base(ref,false);GalaxyData.get(p.server).destruction(ref,false,false,"");DoomData.get(p.server).events.remove(ref);
            var l=PlanetRuntime.open(p.server,ref);l.getChunkAt(original);p.teleportTo(l,8.5,182,8.5,0,0);l.setBlock(original,Blocks.STONE.defaultBlockState(),18);l.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
            ((net.minecraft.world.level.block.entity.ChestBlockEntity)l.getBlockEntity(chest)).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
            PlanetDestruction.attack(l,p.position(),3,p);h.assertTrue(!DoomData.get(p.server).events.containsKey(ref),"insufficient battle power cannot start planetary destruction");
            PlanetDestruction.markBase(p,ref);h.assertTrue(!PlanetDestruction.begin(p.server,ref,p.getUUID(),"Test"),"bases reject even operator-triggered destruction");GalaxyData.get(p.server).base(ref,false);
            h.assertTrue(PlanetDestruction.begin(p.server,ref,p.getUUID(),"Test"),"actual server event starts");h.assertTrue(!PlanetDestruction.begin(p.server,ref,p.getUUID(),"Test"),"duplicate events refused");
            var event=DoomData.get(p.server).events.get(ref);event.scars.put(original,l.getBlockState(original));l.setBlock(original,Blocks.MAGMA_BLOCK.defaultBlockState(),18);event.elapsed=event.duration-3;later(this::evacuated);
        });}
        void evacuated(){
            h.assertTrue(GalaxyData.get(p.server).destroyed(ref),"detonation persists destroyed state");boolean blocked=false;try{PlanetRuntime.open(p.server,ref);}catch(IllegalArgumentException ex){blocked=true;}h.assertTrue(blocked&&!PlanetRuntime.travel(p,ref),"all surface landings are sealed");
            if(!SpaceRuntime.isSpace(p.level())||SpaceRuntime.pending(p)){h.assertTrue(++tries<100,"real evacuation completes");later(this::evacuated);return;}
            var body=GalaxyCatalog.body(p.server.overworld().getSeed(),GalaxyData.get(p.server).salt(),ref);var f=Remnants.fragments(ref,body).get(0);var pos=BlockPos.containing(f.center());var l=p.serverLevel();l.getChunkAt(pos);
            h.assertTrue(!l.getBlockState(pos).isAir(),"visited orbital fragment is real mineable terrain");l.setBlock(pos,Blocks.AIR.defaultBlockState(),18);Remnants.refresh(p.server,ref.system());
            later(()->{h.assertTrue(l.getBlockState(pos).isAir(),"mined debris does not respawn on refresh");GalaxyData.get(p.server).waypoint(p.getUUID(),ref);h.assertTrue(Wish.RESTORE_PLANET.canGrant(p),"dragon wish resolves the destroyed waypoint");Wish.RESTORE_PLANET.grant(p);tries=0;later(this::restored);});
        }
        void restored(){
            if(GalaxyData.get(p.server).destroyed(ref)){h.assertTrue(++tries<100,"bounded restoration finishes");later(this::restored);return;}
            var l=PlanetRuntime.open(p.server,ref);l.getChunkAt(original);h.assertTrue(l.getBlockState(original).is(Blocks.STONE),"exact pre-fissure material restored");
            var be=(net.minecraft.world.level.block.entity.ChestBlockEntity)l.getBlockEntity(chest);h.assertTrue(be!=null&&be.getItem(0).getCount()==7,"retained surface preserves player inventory");
            h.assertTrue(!Wish.RESTORE_PLANET.canGrant(p),"no destroyed target means no wish can be consumed");
            var dragon=new com.dbzenith.dragonball.DragonSpiritEntity(com.dbzenith.registry.ModEntities.DRAGON_SPIRIT.get(),p.level());dragon.setSummoner(p.getUUID());
            h.assertTrue(!dragon.grant(p,Wish.RESTORE_PLANET)&&dragon.grant(p,Wish.POWER),"a refused planet wish leaves the same dragon free to grant a valid wish");var survey=GalaxyService.survey(p,GalaxyService.SYSTEM,ref.system(),ref.planet(),"",0).getCompound("system").getList("bodies",10).getCompound(ref.planet());h.assertTrue(!survey.getBoolean("destroyed")&&survey.getBoolean("landable"),"atlas restores normal landing state");close();h.succeed();
        }
    }
}
