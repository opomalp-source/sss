package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Sparse, restart-safe countdown and bounded fissure journal. Region files are retained as the sealed snapshot. */
public final class DoomData extends SavedData {
    public static final int ACTIVE=1,DESTROYED=2,RESTORING=3,RESTORED=4;
    public static final class Event {
        public final GalaxyCatalog.Ref ref;public final UUID owner;
        public int phase=ACTIVE,elapsed,duration,age;public boolean cancelled;
        public final Set<Long> remnantChunks=new HashSet<>();
        public final Map<BlockPos,BlockState> scars=new LinkedHashMap<>();
        public Event(GalaxyCatalog.Ref ref,UUID owner,int duration){this.ref=ref;this.owner=owner;this.duration=duration;}
        public double progress(){return Math.min(1,elapsed/(double)Math.max(1,duration));}
    }
    public final Map<GalaxyCatalog.Ref,Event> events=new LinkedHashMap<>();
    public final Set<GalaxyCatalog.Ref> working=new LinkedHashSet<>();
    private CompoundTag unsupported;
    public static DoomData get(MinecraftServer s){var d=s.overworld().getDataStorage().computeIfAbsent(DoomData::load,DoomData::new,"dbzenith_planet_destruction");if(d.unsupported!=null)throw new IllegalStateException("Unsupported planet destruction journal");return d;}
    @Override public CompoundTag save(CompoundTag t){
        if(unsupported!=null)return unsupported.copy();
        t.putInt("version",1);var list=new ListTag();
        for(var e:events.values()){
            var n=new CompoundTag();n.putString("ref",e.ref.key());n.putUUID("owner",e.owner);n.putInt("phase",e.phase);n.putInt("elapsed",e.elapsed);n.putInt("duration",e.duration);n.putInt("age",e.age);n.putBoolean("cancelled",e.cancelled);n.putLongArray("remnantChunks",e.remnantChunks.stream().mapToLong(Long::longValue).toArray());
            var scars=new ListTag();e.scars.forEach((p,b)->{var a=new CompoundTag();a.putLong("pos",p.asLong());a.put("state",NbtUtils.writeBlockState(b));scars.add(a);});n.put("scars",scars);list.add(n);
        }t.put("events",list);return t;
    }
    public static DoomData load(CompoundTag t){
        var data=new DoomData();
        if(t.getInt("version")!=1){data.unsupported=t.copy();return data;}
        for(var raw:t.getList("events",Tag.TAG_COMPOUND))try{
            var n=(CompoundTag)raw;var e=new Event(GalaxyCatalog.Ref.parse(n.getString("ref")),n.getUUID("owner"),Math.max(600,Math.min(1800,n.getInt("duration"))));
            e.phase=Math.max(ACTIVE,Math.min(RESTORED,n.getInt("phase")));e.elapsed=Math.max(0,Math.min(e.duration,n.getInt("elapsed")));e.age=Math.max(0,Math.min(1201,n.getInt("age")));e.cancelled=n.getBoolean("cancelled");
            for(var r:n.getList("scars",Tag.TAG_COMPOUND)){if(e.scars.size()>=1024)break;var a=(CompoundTag)r;e.scars.put(BlockPos.of(a.getLong("pos")),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),a.getCompound("state")));}
            for(long chunk:n.getLongArray("remnantChunks")){if(e.remnantChunks.size()>=1024)break;e.remnantChunks.add(chunk);}
            data.events.put(e.ref,e);
            if(e.phase!=DESTROYED||e.age<1201)data.working.add(e.ref);
        }catch(IllegalArgumentException ex){com.dbzenith.DBZenith.LOGGER.warn("Skipping malformed planet destruction entry",ex);}
        return data;
    }
}
