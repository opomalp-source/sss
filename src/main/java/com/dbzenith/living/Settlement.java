package com.dbzenith.living;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A place where people live: a name, its world and centre, how far it reaches, and its points of interest. Settlements
 * are registered (built by {@link TownBuilder}, or added around an existing place by command) and kept in
 * {@link LivingData}; residents belong to one.
 */
public final class Settlement {
    public final UUID id;
    public String name = "";
    public String dimension = "minecraft:overworld";
    public BlockPos center = BlockPos.ZERO;
    public int radius = 48;
    public final List<Poi> pois = new ArrayList<>();
    /** Under attack until this game time (residents react). */
    public long alarmUntil;
    /** The day it last took in a newcomer. */
    public long lastNewcomerDay = Long.MIN_VALUE;
    /** Players who attacked its people lately (guards go for them; civilians run). Not saved: it blows over. */
    public final java.util.Set<UUID> wanted = new java.util.HashSet<>();
    /** Points nobody could reach (dropped from use), by index. */
    public final java.util.Set<Integer> unreachable = new java.util.HashSet<>();

    public Settlement(UUID id) {
        this.id = id;
    }

    public List<Poi> of(Poi.Kind kind) {
        List<Poi> out = new ArrayList<>();
        for (int i = 0; i < pois.size(); i++) if (pois.get(i).kind() == kind && !unreachable.contains(i)) out.add(pois.get(i));
        return out;
    }

    public boolean contains(String dim, BlockPos p) {
        return dimension.equals(dim) && Math.abs(p.getX() - center.getX()) <= radius && Math.abs(p.getZ() - center.getZ()) <= radius
                && Math.abs(p.getY() - center.getY()) <= 48;
    }

    public boolean alarmed(long now) {
        return now < alarmUntil;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("dim", dimension);
        t.putLong("center", center.asLong());
        t.putInt("radius", radius);
        ListTag l = new ListTag();
        for (Poi p : pois) l.add(p.save());
        t.put("pois", l);
        t.putLong("alarm", alarmUntil);
        t.putLong("newcomer", lastNewcomerDay);
        t.putIntArray("unreachable", unreachable.stream().mapToInt(Integer::intValue).toArray());
        return t;
    }

    static Settlement load(CompoundTag t) {
        Settlement s = new Settlement(t.getUUID("id"));
        s.name = t.getString("name");
        s.dimension = t.getString("dim");
        s.center = BlockPos.of(t.getLong("center"));
        s.radius = t.getInt("radius");
        for (Tag p : t.getList("pois", Tag.TAG_COMPOUND)) {
            Poi poi = Poi.load((CompoundTag) p);
            if (poi != null) s.pois.add(poi);
        }
        for (int i : t.getIntArray("unreachable")) s.unreachable.add(i);
        s.alarmUntil = t.getLong("alarm");
        s.lastNewcomerDay = t.contains("newcomer") ? t.getLong("newcomer") : Long.MIN_VALUE;
        return s;
    }
}
