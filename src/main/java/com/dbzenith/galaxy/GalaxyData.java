package com.dbzenith.galaxy;

import com.dbzenith.DBZenith;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Sparse changes only. Surveying an unvisited body never creates a record or a dimension. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class GalaxyData extends SavedData {
    public record Discovery(UUID owner, String discoverer, long tick, String name, boolean destroyed, boolean restored, boolean base, String snapshot, String catalogName) {}
    private final Map<GalaxyCatalog.Ref,Discovery> discoveries=new TreeMap<>(Comparator.comparing(GalaxyCatalog.Ref::key));
    private final Map<UUID,GalaxyCatalog.Ref> waypoints=new LinkedHashMap<>();
    private long salt=GalaxyCatalog.SALT;
    private CompoundTag unsupported;
    public static GalaxyData get(MinecraftServer server) {
        GalaxyData data=server.overworld().getDataStorage().computeIfAbsent(GalaxyData::load,GalaxyData::new,"dbzenith_galaxy");
        data.requireSupported(); return data;
    }
    public void requireSupported() {
        if(unsupported!=null)throw new IllegalStateException("Unsupported galaxy generator version; keep the galaxy save intact");
    }
    public long salt() { requireSupported(); return salt; }
    public Map<GalaxyCatalog.Ref,Discovery> discoveries() { return Collections.unmodifiableMap(discoveries); }
    public Discovery discovery(GalaxyCatalog.Ref ref) { return discoveries.get(ref); }
    public GalaxyCatalog.Ref waypoint(UUID player) { return waypoints.get(player); }
    public void waypoint(UUID player,GalaxyCatalog.Ref ref) {
        if(ref==null) waypoints.remove(player); else waypoints.put(player,ref);
        setDirty();
    }
    public boolean discover(GalaxyCatalog.Ref ref,UUID player,String name,long tick,String catalogName) {
        if(discoveries.containsKey(ref)) return false;
        discoveries.put(ref,new Discovery(player,name,tick,"",false,false,false,"",catalogName)); setDirty(); return true;
    }
    public boolean rename(GalaxyCatalog.Ref ref,UUID player,String name) {
        Discovery d=discovery(ref);
        if(d==null || !d.owner.equals(player) || !validName(name)) return false;
        if(ref.planet()==0 && Set.of(GalaxyCatalog.EARTH,GalaxyCatalog.NAMEK,GalaxyCatalog.NORTH,GalaxyCatalog.BEERUS).contains(ref.system())) return false;
        discoveries.put(ref,new Discovery(d.owner,d.discoverer,d.tick,name.strip(),d.destroyed,d.restored,d.base,d.snapshot,d.catalogName));
        setDirty(); return true;
    }
    public static boolean validName(String s) { return s!=null && s.strip().length()>=2 && s.strip().length()<=32 && s.strip().matches("[\\p{L}\\p{N} .'-]+"); }
    public String name(GalaxyCatalog.Ref ref,String generated) {
        var d=discovery(ref); return d==null || d.name.isEmpty()?generated:d.name;
    }
    public static GalaxyCatalog.Id current(ServerPlayer p) {
        var orbit=com.dbzenith.galaxy.space.SpaceRuntime.id(p.level().dimension());if(orbit!=null)return orbit;
        if(p.serverLevel().getChunkSource().getGenerator() instanceof com.dbzenith.galaxy.planet.PlanetGenerator g)return g.ref.system();
        return switch(p.level().dimension().location().toString()) {
            case "dbzenith:namek" -> GalaxyCatalog.NAMEK;
            case "dbzenith:northern_planet" -> GalaxyCatalog.NORTH;
            case "dbzenith:beerus_planet" -> GalaxyCatalog.BEERUS;
            default -> GalaxyCatalog.EARTH;
        };
    }
    private static void landed(ServerPlayer p) {
        String dim=p.level().dimension().location().toString();
        if(!Set.of("minecraft:overworld","dbzenith:namek","dbzenith:northern_planet","dbzenith:beerus_planet").contains(dim)) return;
        com.dbzenith.galaxy.planet.PlanetActivity.landing(p);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof ServerPlayer p) landed(p); }
    @SubscribeEvent public static void travel(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) landed(p); }
    @Override public CompoundTag save(CompoundTag tag) {
        if(unsupported!=null)return unsupported.copy();
        tag.putInt("version",GalaxyCatalog.VERSION); tag.putLong("salt",salt);
        ListTag list=new ListTag();
        discoveries.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().key())).forEach(e->{
            var d=e.getValue(); CompoundTag t=new CompoundTag(); t.putString("ref",e.getKey().key()); t.putUUID("owner",d.owner);
            t.putString("discoverer",d.discoverer); t.putLong("tick",d.tick); t.putString("name",d.name); t.putBoolean("destroyed",d.destroyed);
            t.putBoolean("restored",d.restored); t.putBoolean("base",d.base); t.putString("snapshot",d.snapshot); t.putString("catalogName",d.catalogName); list.add(t);
        });
        tag.put("discoveries",list); ListTag wp=new ListTag();
        waypoints.forEach((u,r)->{ CompoundTag t=new CompoundTag(); t.putUUID("player",u); t.putString("ref",r.key()); wp.add(t); });
        tag.put("waypoints",wp); return tag;
    }
    public static GalaxyData load(CompoundTag tag) {
        // SavedData swallows loader exceptions and may create a blank replacement. Preserve an unknown schema instead.
        if(tag.getInt("version")!=GalaxyCatalog.VERSION) { GalaxyData data=new GalaxyData();data.unsupported=tag.copy();return data; }
        GalaxyData data=new GalaxyData(); data.salt=tag.getLong("salt");
        for(Tag raw:tag.getList("discoveries",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)raw;
            try { data.discoveries.put(GalaxyCatalog.Ref.parse(t.getString("ref")),new Discovery(t.getUUID("owner"),t.getString("discoverer"),t.getLong("tick"),
                    t.getString("name"),t.getBoolean("destroyed"),t.getBoolean("restored"),t.getBoolean("base"),t.getString("snapshot"),t.getString("catalogName"))); }
            catch(IllegalArgumentException ex) { DBZenith.LOGGER.warn("Skipping malformed galaxy discovery: {}",t.getString("ref")); }
        }
        for(Tag raw:tag.getList("waypoints",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)raw;
            try { data.waypoints.put(t.getUUID("player"),GalaxyCatalog.Ref.parse(t.getString("ref"))); } catch(IllegalArgumentException ignored) {}
        }
        return data;
    }
}
