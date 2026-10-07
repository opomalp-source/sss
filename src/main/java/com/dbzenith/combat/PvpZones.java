package com.dbzenith.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Named no-PvP regions (CX-19), set by operators with {@code /dbz pvpzone}. Kept with the world (in the overworld's
 * data); each zone is a box in one dimension.
 */
public final class PvpZones extends SavedData {
    private static final String KEY = "dbzenith_pvp_zones";

    public record Zone(String name, String dimension, int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(String dim, Vec3 p) {
            return dimension.equals(dim) && p.x >= x0 && p.x < x1 + 1 && p.y >= y0 && p.y < y1 + 1 && p.z >= z0 && p.z < z1 + 1;
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    private PvpZones() {}

    public static PvpZones of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PvpZones::load, PvpZones::new, KEY);
    }

    static PvpZones load(CompoundTag tag) {
        PvpZones z = new PvpZones();
        for (Tag t : tag.getList("zones", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int[] b = c.getIntArray("box");
            if (b.length == 6) z.zones.add(new Zone(c.getString("name"), c.getString("dim"), b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return z;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones) {
            CompoundTag c = new CompoundTag();
            c.putString("name", z.name);
            c.putString("dim", z.dimension);
            c.putIntArray("box", new int[]{z.x0, z.y0, z.z0, z.x1, z.y1, z.z1});
            list.add(c);
        }
        tag.put("zones", list);
        return tag;
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    /** Adds or replaces a zone between two corners (inclusive). */
    public void add(String name, String dimension, BlockPos a, BlockPos b) {
        remove(name);
        zones.add(new Zone(name, dimension, Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
        setDirty();
    }

    public boolean remove(String name) {
        boolean gone = zones.removeIf(z -> z.name.equals(name));
        if (gone) setDirty();
        return gone;
    }

    /** The zone at a point, or null. */
    public Zone at(String dimension, Vec3 p) {
        for (Zone z : zones) if (z.contains(dimension, p)) return z;
        return null;
    }
}
