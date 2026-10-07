package com.dbzenith.duel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Duel records and ratings (CX-19 phase 9), saved with the world: wins, losses, draws and an Elo rating (from 1000;
 * 32 points at stake between equals), for {@code /duel stats} and {@code /duel top}.
 */
public final class DuelLadder extends SavedData {
    private static final String NAME = "dbzenith_duels";
    public static final int START = 1000, K = 32;

    public static final class Record {
        public String name;
        public int wins, losses, draws, rating = START;

        Record(String name) {
            this.name = name;
        }
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static DuelLadder get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DuelLadder::load, DuelLadder::new, NAME);
    }

    public Record record(UUID id, String name) {
        Record r = records.computeIfAbsent(id, k -> new Record(name));
        if (name != null) r.name = name;
        return r;
    }

    public Record peek(UUID id) {
        return records.get(id);
    }

    /** The result of a duel ({@code winner} 0 or 1, -1 a draw): records and ratings. Returns each side's rating change. */
    public int[] result(UUID a, String an, UUID b, String bn, int winner) {
        Record ra = record(a, an), rb = record(b, bn);
        double expectA = 1 / (1 + Math.pow(10, (rb.rating - ra.rating) / 400.0));
        double scoreA = winner == 0 ? 1 : winner == 1 ? 0 : 0.5;
        int delta = (int) Math.round(K * (scoreA - expectA));
        ra.rating += delta;
        rb.rating -= delta;
        if (winner == 0) {
            ra.wins++;
            rb.losses++;
        } else if (winner == 1) {
            rb.wins++;
            ra.losses++;
        } else {
            ra.draws++;
            rb.draws++;
        }
        setDirty();
        return new int[]{delta, -delta};
    }

    /** The best by rating. */
    public List<Record> top(int n) {
        List<Record> all = new ArrayList<>(records.values());
        all.sort(Comparator.comparingInt((Record r) -> -r.rating).thenComparing(r -> r.name));
        return all.subList(0, Math.min(n, all.size()));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        records.forEach((id, r) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", r.name);
            t.putInt("wins", r.wins);
            t.putInt("losses", r.losses);
            t.putInt("draws", r.draws);
            t.putInt("rating", r.rating);
            list.add(t);
        });
        tag.put("records", list);
        return tag;
    }

    static DuelLadder load(CompoundTag tag) {
        DuelLadder l = new DuelLadder();
        for (Tag e : tag.getList("records", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Record r = new Record(t.getString("name"));
            r.wins = t.getInt("wins");
            r.losses = t.getInt("losses");
            r.draws = t.getInt("draws");
            r.rating = t.contains("rating") ? t.getInt("rating") : START;
            l.records.put(t.getUUID("id"), r);
        }
        return l;
    }
}
