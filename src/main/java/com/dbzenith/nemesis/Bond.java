package com.dbzenith.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a character has between itself and one other: a player, or another character. The two numbers behind the
 * {@link Relationship} (affinity, rivalry), the record of their fights, and for players what the character has
 * learned of them (their habits, the forms and techniques it has seen) and what the player has learned of it
 * (forms seen, last power level seen, last place and status known), so the history screen shows only what the player
 * discovered.
 */
public final class Bond {
    public String name = "";
    public Relationship state = Relationship.STRANGER;
    public double affinity, rivalry;
    /** Fights the character won, lost; draws; times each side spared the other; the player getting away; it retreating. */
    public int wins, losses, draws, sparedByThem, sparedThem, escapes, retreats, encounters, spars, alliesLost, helped;
    public long lastEncounter = Long.MIN_VALUE / 2, nextChallengeAt;
    /** Who won the last fight: 1 the character, -1 the other, 0 none yet. */
    public int lastResult;

    // ---- what the character learned of a player (combat habits: OpponentProfile's moving averages)
    public double[] habits;
    public final Map<String, Integer> techniquesSeen = new LinkedHashMap<>();
    public final Set<String> formsSeen = new LinkedHashSet<>();
    public String strongestFormSeen = "";
    public int playerLevelSeen;

    // ---- what the player learned of the character (shown in the history screen)
    public final Set<String> formsShown = new LinkedHashSet<>();
    public long powerShown;
    public String placeShown = "";
    public String statusShown = "";

    /** Conversation choices it remembers about a player (asked its name, accepted an errand...), for the living world. */
    public final Set<String> flags = new LinkedHashSet<>();
    /** Conversations had (the living world), and the rumors already told them (ids, newest last, bounded). */
    public int talks;
    public final Deque<String> told = new ArrayDeque<>();

    /** Dialogue lines recently said to this one (no repeats). */
    public final Deque<String> recentLines = new ArrayDeque<>();

    public void sawTechnique(String id) {
        if (id == null || id.isEmpty()) return;
        techniquesSeen.merge(id, 1, Integer::sum);
        if (techniquesSeen.size() > 12) techniquesSeen.remove(techniquesSeen.keySet().iterator().next());
    }

    /** The technique it has seen most. */
    public String favouriteTechnique() {
        String best = "";
        int n = 0;
        for (Map.Entry<String, Integer> e : techniquesSeen.entrySet()) if (e.getValue() > n) {
            n = e.getValue();
            best = e.getKey();
        }
        return best;
    }

    public void said(String lineId) {
        recentLines.addLast(lineId);
        while (recentLines.size() > 8) recentLines.removeFirst();
    }

    /** Moves the numbers and re-reads the state. Returns the old state if it changed, else null. */
    public Relationship shift(double dAffinity, double dRivalry) {
        affinity = Math.max(-100, Math.min(100, affinity + dAffinity));
        rivalry = Math.max(0, Math.min(100, rivalry + dRivalry));
        Relationship old = state;
        state = Relationship.next(state, affinity, rivalry, encounters, spars);
        return old != state ? old : null;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("state", state.id());
        t.putDouble("affinity", affinity);
        t.putDouble("rivalry", rivalry);
        t.putIntArray("record", new int[]{wins, losses, draws, sparedByThem, sparedThem, escapes, retreats, encounters, spars, alliesLost, helped, lastResult});
        t.putLong("last", lastEncounter);
        t.putLong("challenge", nextChallengeAt);
        if (habits != null) {
            ListTag h = new ListTag();
            for (double v : habits) h.add(net.minecraft.nbt.DoubleTag.valueOf(v));
            t.put("habits", h);
        }
        CompoundTag tech = new CompoundTag();
        techniquesSeen.forEach(tech::putInt);
        t.put("techniques", tech);
        t.put("formsSeen", strings(formsSeen));
        t.putString("strongestForm", strongestFormSeen);
        t.putInt("playerLevel", playerLevelSeen);
        t.put("formsShown", strings(formsShown));
        t.putLong("powerShown", powerShown);
        t.putString("placeShown", placeShown);
        t.putString("statusShown", statusShown);
        t.put("recent", strings(recentLines));
        t.put("flags", strings(flags));
        t.putInt("talks", talks);
        t.put("told", strings(told));
        return t;
    }

    static Bond load(CompoundTag t) {
        Bond b = new Bond();
        b.name = t.getString("name");
        Relationship r = Relationship.byId(t.getString("state"));
        b.state = r == null ? Relationship.STRANGER : r;
        b.affinity = t.getDouble("affinity");
        b.rivalry = t.getDouble("rivalry");
        int[] rec = t.getIntArray("record");
        int[] full = new int[12];
        System.arraycopy(rec, 0, full, 0, Math.min(rec.length, 12));
        b.wins = full[0];
        b.losses = full[1];
        b.draws = full[2];
        b.sparedByThem = full[3];
        b.sparedThem = full[4];
        b.escapes = full[5];
        b.retreats = full[6];
        b.encounters = full[7];
        b.spars = full[8];
        b.alliesLost = full[9];
        b.helped = full[10];
        b.lastResult = full[11];
        b.lastEncounter = t.contains("last") ? t.getLong("last") : Long.MIN_VALUE / 2;
        b.nextChallengeAt = t.getLong("challenge");
        if (t.contains("habits")) {
            ListTag h = t.getList("habits", Tag.TAG_DOUBLE);
            b.habits = new double[h.size()];
            for (int i = 0; i < h.size(); i++) b.habits[i] = h.getDouble(i);
        }
        CompoundTag tech = t.getCompound("techniques");
        for (String k : tech.getAllKeys()) b.techniquesSeen.put(k, tech.getInt(k));
        read(t.getList("formsSeen", Tag.TAG_STRING), b.formsSeen);
        b.strongestFormSeen = t.getString("strongestForm");
        b.playerLevelSeen = t.getInt("playerLevel");
        read(t.getList("formsShown", Tag.TAG_STRING), b.formsShown);
        b.powerShown = t.getLong("powerShown");
        b.placeShown = t.getString("placeShown");
        b.statusShown = t.getString("statusShown");
        for (Tag s : t.getList("recent", Tag.TAG_STRING)) b.recentLines.add(s.getAsString());
        read(t.getList("flags", Tag.TAG_STRING), b.flags);
        b.talks = t.getInt("talks");
        read(t.getList("told", Tag.TAG_STRING), b.told);
        return b;
    }

    static ListTag strings(Iterable<String> it) {
        ListTag l = new ListTag();
        for (String s : it) l.add(StringTag.valueOf(s));
        return l;
    }

    static void read(ListTag l, java.util.Collection<String> into) {
        for (Tag s : l) into.add(s.getAsString());
    }
}
