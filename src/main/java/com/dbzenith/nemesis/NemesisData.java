package com.dbzenith.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every named character of the world, kept with it ({@code data/dbzenith_nemesis.dat} in the overworld's storage),
 * whatever happens to their entities. Bounded: at most {@code nemesis.maxCharacters} active characters (the least
 * important retire to make room) and {@link #MAX_RECORDS} records in all (the oldest retired or dead are forgotten).
 * Schema-versioned; unknown fields are ignored and missing ones default, so it reads older and newer saves.
 */
public final class NemesisData extends SavedData {
    public static final String NAME = "dbzenith_nemesis";
    static final int SCHEMA = 1, MAX_RECORDS = 512, MAX_HISTORY = 32;

    private final Map<UUID, NamedFighter> all = new LinkedHashMap<>();
    /** Where the world simulation is in its round, and the last day it held a tournament (per world, not saved). */
    int simCursor;
    long lastTournamentDay = Long.MIN_VALUE;
    /** Which characters know each player (rebuilt on load, kept as relations form). */
    private final Map<UUID, List<UUID>> byPlayer = new HashMap<>();

    public static NemesisData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NemesisData::load, NemesisData::new, NAME);
    }

    public NamedFighter get(UUID id) {
        return id == null ? null : all.get(id);
    }

    public Collection<NamedFighter> all() {
        return all.values();
    }

    public int activeCount() {
        int n = 0;
        for (NamedFighter c : all.values()) if (c.active() && !c.resident()) n++;              // townsfolk count against the town's own limit
        return n;
    }

    /** Adds a character (and makes room if it must). */
    public void add(NamedFighter c) {
        all.put(c.id, c);
        for (UUID p : c.players.keySet()) index(c, p);
        trim();
        setDirty();
    }

    /** Records that {@code c} now knows {@code player}. */
    public void index(NamedFighter c, UUID player) {
        List<UUID> l = byPlayer.computeIfAbsent(player, k -> new ArrayList<>());
        if (!l.contains(c.id)) l.add(c.id);
    }

    /** The characters that know a player. */
    public List<NamedFighter> knowing(UUID player) {
        List<NamedFighter> out = new ArrayList<>();
        for (UUID id : byPlayer.getOrDefault(player, List.of())) {
            NamedFighter c = all.get(id);
            if (c != null && c.players.containsKey(player)) out.add(c);
        }
        return out;
    }

    /** Keeps the numbers bounded: too many active characters retire the least known; too many records forget the oldest. */
    void trim() {
        int max = NemesisConfig.maxCharacters();
        if (activeCount() > max) {
            List<NamedFighter> active = new ArrayList<>();
            for (NamedFighter c : all.values()) if (c.active() && c.status != NamedFighter.Status.PRESENT && !c.resident()) active.add(c);
            active.sort(Comparator.comparingDouble(NemesisData::importance));
            for (NamedFighter c : active) {
                if (activeCount() <= max) break;
                c.status = NamedFighter.Status.RETIRED;
                c.remember(new Memory(Memory.Kind.RETIRED, c.lastSeen, null, "room"));
            }
        }
        if (all.size() > MAX_RECORDS) {
            List<NamedFighter> gone = new ArrayList<>();
            for (NamedFighter c : all.values()) if (!c.active()) gone.add(c);
            gone.sort(Comparator.comparingLong(c -> c.lastSeen));
            for (NamedFighter c : gone) {
                if (all.size() <= MAX_RECORDS) break;
                all.remove(c.id);
                byPlayer.values().forEach(l -> l.remove(c.id));
            }
        }
    }

    /** How much a character matters (kept longest): rivalries, reputation, fights. */
    static double importance(NamedFighter c) {
        double rivalry = 0;
        for (Bond b : c.players.values()) rivalry = Math.max(rivalry, b.rivalry + Math.abs(b.affinity) * 0.5);
        return rivalry + c.reputation * 0.2 + c.battles + (c.canon ? 1000 : 0);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA);
        ListTag list = new ListTag();
        for (NamedFighter c : all.values()) list.add(c.save());
        tag.put("characters", list);
        return tag;
    }

    public static NemesisData load(CompoundTag tag) {
        NemesisData d = new NemesisData();
        for (Tag raw : tag.getList("characters", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id")) continue;
            NamedFighter c = NamedFighter.load(t);
            d.all.put(c.id, c);
            for (UUID p : c.players.keySet()) d.index(c, p);
        }
        return d;
    }
}
