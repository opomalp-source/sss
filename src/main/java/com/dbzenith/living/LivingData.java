package com.dbzenith.living;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The living world's own saved data ({@code data/dbzenith_living.dat}, overworld storage): settlements and their points
 * of interest, rumors, and each player's fame. Who the residents are (identity, personality, memories, bonds) lives in
 * the Nemesis system's data, not here. Bounded: 256 rumors (the oldest forgotten). Schema-versioned.
 */
public final class LivingData extends SavedData {
    public static final String NAME = "dbzenith_living";
    static final int SCHEMA = 1, MAX_RUMORS = 256;

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Map<UUID, Rumor> rumors = new LinkedHashMap<>();
    private final Map<UUID, Errand> errands = new LinkedHashMap<>();
    private final Map<UUID, Integer> fame = new HashMap<>();
    /** The highest fame tier each player has reached (a new one is news once). */
    private final Map<UUID, Integer> fameTiers = new HashMap<>();
    /** Ginger Town has been built for this world (new worlds get it once). */
    public boolean townPlaced;
    /** World-generated settlements already registered (each once in a world's life). */
    private final java.util.Set<UUID> visited = new java.util.HashSet<>();

    public static LivingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LivingData::load, LivingData::new, NAME);
    }

    public Collection<Settlement> settlements() {
        return settlements.values();
    }

    public Settlement settlement(UUID id) {
        return id == null ? null : settlements.get(id);
    }

    public void add(Settlement s) {
        settlements.put(s.id, s);
        setDirty();
    }

    /** Marks a generated settlement as registered; false if it already was. */
    public boolean firstVisit(UUID id) {
        boolean f = visited.add(id);
        if (f) setDirty();
        return f;
    }

    /** Lets a generated settlement be registered again (dev: {@code /dbz structures resettle}). */
    public void forgetVisit(UUID id) {
        if (visited.remove(id)) setDirty();
    }

    /** Forgets a settlement (its residents keep their identities; with no town they wander). */
    public void remove(UUID id) {
        settlements.remove(id);
        setDirty();
    }

    /** The settlement covering a point, or null. */
    public Settlement at(String dim, BlockPos p) {
        for (Settlement s : settlements.values()) if (s.contains(dim, p)) return s;
        return null;
    }

    /** The nearest settlement in a world within {@code range} blocks, or null. */
    public Settlement nearest(String dim, BlockPos p, double range) {
        Settlement best = null;
        double bd = range * range;
        for (Settlement s : settlements.values()) {
            if (!s.dimension.equals(dim)) continue;
            double d = s.center.distSqr(p);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    public Rumor rumor(UUID id) {
        return id == null ? null : rumors.get(id);
    }

    public Collection<Rumor> rumors() {
        return rumors.values();
    }

    public void add(Rumor r) {
        rumors.put(r.id(), r);
        while (rumors.size() > MAX_RUMORS) rumors.remove(rumors.keySet().iterator().next());
        setDirty();
    }

    public java.util.Collection<Errand> errands() {
        return errands.values();
    }

    public Errand errand(UUID id) {
        return id == null ? null : errands.get(id);
    }

    public void add(Errand e) {
        errands.put(e.id, e);
        setDirty();
    }

    public void removeErrand(UUID id) {
        errands.remove(id);
        setDirty();
    }

    public int fame(UUID player) {
        return fame.getOrDefault(player, 0);
    }

    public void addFame(UUID player, int amount) {
        fame.merge(player, amount, Integer::sum);
        setDirty();
    }

    public int fameTier(UUID player) {
        return fameTiers.getOrDefault(player, 0);
    }

    public void setFameTier(UUID player, int tier) {
        fameTiers.put(player, tier);
        setDirty();
    }

    /** The last day news from elsewhere reached the towns. */
    public long lastNewsDay = Long.MIN_VALUE;

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA);
        tag.putBoolean("townPlaced", townPlaced);
        ListTag s = new ListTag();
        for (Settlement x : settlements.values()) s.add(x.save());
        tag.put("settlements", s);
        ListTag r = new ListTag();
        for (Rumor x : rumors.values()) r.add(x.save());
        tag.put("rumors", r);
        ListTag er = new ListTag();
        for (Errand x : errands.values()) er.add(x.save());
        tag.put("errands", er);
        CompoundTag f = new CompoundTag();
        fame.forEach((k, v) -> f.putInt(k.toString(), v));
        tag.put("fame", f);
        CompoundTag ft = new CompoundTag();
        fameTiers.forEach((k, v) -> ft.putInt(k.toString(), v));
        tag.put("fameTiers", ft);
        tag.putLong("newsDay", lastNewsDay);
        ListTag vis = new ListTag();
        for (UUID v : visited) vis.add(net.minecraft.nbt.NbtUtils.createUUID(v));
        tag.put("visited", vis);
        return tag;
    }

    public static LivingData load(CompoundTag tag) {
        LivingData d = new LivingData();
        d.townPlaced = tag.getBoolean("townPlaced");
        for (Tag t : tag.getList("settlements", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("id")) {
                Settlement s = Settlement.load(c);
                d.settlements.put(s.id, s);
            }
        }
        for (Tag t : tag.getList("rumors", Tag.TAG_COMPOUND)) {
            Rumor r = Rumor.load((CompoundTag) t);
            if (r != null) d.rumors.put(r.id(), r);
        }
        for (Tag t : tag.getList("errands", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("id")) {
                Errand e = Errand.load(c);
                d.errands.put(e.id, e);
            }
        }
        CompoundTag f = tag.getCompound("fame");
        for (String k : f.getAllKeys()) {
            try {
                d.fame.put(UUID.fromString(k), f.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag ft = tag.getCompound("fameTiers");
        for (String k : ft.getAllKeys()) {
            try {
                d.fameTiers.put(UUID.fromString(k), ft.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.lastNewsDay = tag.contains("newsDay") ? tag.getLong("newsDay") : Long.MIN_VALUE;
        for (Tag v : tag.getList("visited", Tag.TAG_INT_ARRAY)) d.visited.add(net.minecraft.nbt.NbtUtils.loadUUID(v));
        return d;
    }

    /** Tests: every settlement's id. */
    public List<UUID> settlementIds() {
        return new ArrayList<>(settlements.keySet());
    }
}
