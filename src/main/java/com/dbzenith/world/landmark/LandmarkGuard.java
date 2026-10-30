package com.dbzenith.world.landmark;

import com.dbzenith.DBZenith;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps new landmarks off ground that existed before them (docs/WORLD_STRUCTURES.md). The first time a world runs with
 * a {@link Landmark#guarded} landmark, the overworld's region files then on disk are recorded for it
 * ({@code data/dbzenith_landmark_guard.dat}, written before any chunk generates); that landmark is never sited where
 * its reach touches one of those regions, so it can't be cut off by old terrain or build into a player's explored
 * world. A new world records nothing and gets every landmark. Read by world generation threads: the recorded sets are
 * replaced whole, never changed in place. The same file keeps each unique landmark's cell once it is found ({@link #saveSite}),
 * so it is searched for once in a world's life.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LandmarkGuard {
    static final String FILE = "dbzenith_landmark_guard.dat";
    private static volatile Map<String, Set<Long>> blocked = Map.of();

    private LandmarkGuard() {}

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        load(event.getServer());
    }

    /** Reads the record (recording the regions now on disk for any guarded landmark it doesn't know yet). */
    static void load(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        File file = root.resolve("data").resolve(FILE).toFile();
        Map<String, Set<Long>> out = new HashMap<>();
        CompoundTag tag = new CompoundTag();
        try {
            if (file.exists()) tag = NbtIo.readCompressed(file);
        } catch (IOException e) {
            DBZenith.LOGGER.error("Landmark guard unreadable; new landmarks stay off this world's existing regions", e);
        }
        boolean changed = false;
        Map<String, Set<Long>> onDisk = new HashMap<>();                               // by world: each keeps its own region files
        for (Landmark l : Landmark.ALL) {
            if (!l.guarded) continue;
            if (!tag.contains(l.id)) {
                Set<Long> regions = onDisk.computeIfAbsent(l.dimension.location().toString(), k -> regions(regionFolder(root, l)));
                tag.putLongArray(l.id, regions.stream().mapToLong(Long::longValue).toArray());
                changed = true;
            }
            Set<Long> s = new HashSet<>();
            for (long r : tag.getLongArray(l.id)) s.add(r);
            out.put(l.id, Set.copyOf(s));
        }
        if (changed) {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                NbtIo.writeCompressed(tag, file);
            } catch (IOException e) {
                DBZenith.LOGGER.error("Could not save the landmark guard", e);
            }
        }
        blocked = Map.copyOf(out);
        synchronized (LandmarkGuard.class) {
            current = file;
            record = tag;
        }
        LandmarkSites.clearCache();
    }

    /** The world's record file and contents (also where unique landmarks' sites are kept, once found). */
    private static File current;
    private static CompoundTag record = new CompoundTag();

    /** A unique landmark's cell as saved with this world ({cellX, cellZ}), or null if it hasn't been sited yet. */
    public static synchronized long[] savedSite(Landmark type) {
        CompoundTag sites = record.getCompound("sites");
        return sites.contains(type.id) ? sites.getLongArray(type.id) : null;
    }

    /** Keeps a unique landmark's cell with the world: it is searched for once, and stays put whatever later versions change. */
    public static synchronized void saveSite(Landmark type, long[] cell) {
        if (current == null) return;
        CompoundTag sites = record.getCompound("sites");
        sites.putLongArray(type.id, cell);
        record.put("sites", sites);
        try {
            Files.createDirectories(current.getParentFile().toPath());
            NbtIo.writeCompressed(record, current);
        } catch (IOException e) {
            DBZenith.LOGGER.error("Could not save the landmark guard", e);
        }
    }

    /** Where a landmark's world keeps its region files (Earth's in the world folder, the others under dimensions/). */
    static Path regionFolder(Path root, Landmark l) {
        if (l.dimension == net.minecraft.world.level.Level.OVERWORLD) return root.resolve("region");
        var key = l.dimension.location();
        return root.resolve("dimensions").resolve(key.getNamespace()).resolve(key.getPath()).resolve("region");
    }

    /** The region files in a folder ("r.X.Z.mca"), packed as region coordinates. */
    public static Set<Long> regions(Path dir) {
        Set<Long> out = new HashSet<>();
        File[] files = dir.toFile().listFiles();
        if (files == null) return out;
        for (File f : files) {
            String[] p = f.getName().split("\\.");
            if (p.length != 4 || !p[0].equals("r") || !p[3].equals("mca") || f.length() == 0) continue;
            try {
                out.add(pack(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static long pack(int rx, int rz) {
        return (long) rx << 32 | (rz & 0xFFFFFFFFL);
    }

    /** Whether a guarded landmark centred at x, z with this reach would touch a region that existed before it. */
    public static boolean blocked(Landmark type, int x, int z, int reach) {
        if (!type.guarded) return false;
        Set<Long> s = blocked.get(type.id);
        if (s == null || s.isEmpty()) return false;
        for (int rx = Math.floorDiv(x - reach, 512); rx <= Math.floorDiv(x + reach, 512); rx++)
            for (int rz = Math.floorDiv(z - reach, 512); rz <= Math.floorDiv(z + reach, 512); rz++)
                if (s.contains(pack(rx, rz))) return true;
        return false;
    }

    /** The regions recorded for a landmark (none for an unguarded one). */
    public static Set<Long> recorded(Landmark type) {
        return blocked.getOrDefault(type.id, Set.of());
    }

    /** Tests: pretend these regions existed before a landmark (or none). */
    public static void setForTest(Landmark type, Set<Long> regions) {
        Map<String, Set<Long>> m = new HashMap<>(blocked);
        m.put(type.id, Set.copyOf(regions));
        blocked = Map.copyOf(m);
        LandmarkSites.clearCache();
    }
}
