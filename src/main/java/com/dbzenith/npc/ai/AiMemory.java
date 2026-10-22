package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Long-term memory of important NPC fighters (the bosses), kept with the world ({@code data/dbzenith_ai_memory.dat}):
 * for each NPC, by its UUID, and each opponent it fought, by theirs: fights, wins, losses, retreats, a grudge, and what
 * each action paid against that opponent ({@link FightLog}). It is the foundation a Nemesis feature can build on (no
 * Nemesis system existed before; see docs/COMBAT_AI.md): records outlive the NPC (marked fallen) and are bounded
 * ({@code npc_ai.memoryMaxNpcs}, the longest unseen forgotten first; 16 opponents each).
 */
public final class AiMemory extends SavedData {
    public static final String NAME = "dbzenith_ai_memory";
    static final int SCHEMA = 1, MAX_OPPONENTS = 16;

    /** What one NPC remembers of one opponent. */
    public static final class Opponent {
        public String name = "";
        public int fights, wins, losses, fled;
        public float grudge;
        public long lastSeen;
        public final float[] payoff = new float[FightLog.N];
        public final int[] tries = new int[FightLog.N];

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putInt("fights", fights);
            t.putInt("wins", wins);
            t.putInt("losses", losses);
            t.putInt("fled", fled);
            t.putFloat("grudge", grudge);
            t.putLong("seen", lastSeen);
            CompoundTag acts = new CompoundTag();                               // by action name, so new actions never shift old ones
            for (Action a : Action.values()) {
                if (tries[a.ordinal()] <= 0) continue;
                CompoundTag one = new CompoundTag();
                one.putFloat("p", payoff[a.ordinal()]);
                one.putInt("n", tries[a.ordinal()]);
                acts.put(a.id(), one);
            }
            t.put("actions", acts);
            return t;
        }

        static Opponent load(CompoundTag t) {
            Opponent o = new Opponent();
            o.name = t.getString("name");
            o.fights = t.getInt("fights");
            o.wins = t.getInt("wins");
            o.losses = t.getInt("losses");
            o.fled = t.getInt("fled");
            o.grudge = t.getFloat("grudge");
            o.lastSeen = t.getLong("seen");
            CompoundTag acts = t.getCompound("actions");
            for (Action a : Action.values()) {
                if (!acts.contains(a.id())) continue;
                o.payoff[a.ordinal()] = acts.getCompound(a.id()).getFloat("p");
                o.tries[a.ordinal()] = acts.getCompound(a.id()).getInt("n");
            }
            return o;
        }
    }

    /** One remembered NPC. */
    public static final class Npc {
        public String type = "";
        public long lastSeen;
        public boolean fallen;
        public final Map<UUID, Opponent> opponents = new LinkedHashMap<>();

        public Opponent opponent(UUID id, String name, long now) {
            Opponent o = opponents.get(id);
            if (o == null) {
                if (opponents.size() >= MAX_OPPONENTS) {
                    opponents.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastSeen))
                            .map(Map.Entry::getKey).ifPresent(opponents::remove);
                }
                o = new Opponent();
                opponents.put(id, o);
            }
            if (name != null && !name.isEmpty()) o.name = name;
            o.lastSeen = now;
            return o;
        }
    }

    private final Map<UUID, Npc> npcs = new HashMap<>();

    public static AiMemory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AiMemory::load, AiMemory::new, NAME);
    }

    public Npc npc(UUID id, String type, long now) {
        Npc n = npcs.get(id);
        if (n == null) {
            int max;
            try {
                max = DBZConfig.SERVER.aiMemoryMaxNpcs.get();
            } catch (IllegalStateException e) {
                max = 256;
            }
            while (npcs.size() >= max) {
                npcs.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastSeen)).map(Map.Entry::getKey).ifPresent(npcs::remove);
            }
            n = new Npc();
            n.type = type;
            npcs.put(id, n);
        }
        n.lastSeen = now;
        setDirty();
        return n;
    }

    public Npc peek(UUID id) {
        return npcs.get(id);
    }

    public int size() {
        return npcs.size();
    }

    /** What a fight taught: blended into the remembered payoffs (recent fights count more). */
    void learn(Npc n, Opponent o, FightLog log) {
        for (int i = 0; i < FightLog.N; i++) {
            if (log.tries[i] <= 0) continue;
            o.payoff[i] = o.tries[i] <= 0 ? (float) log.payoff[i] : (float) (o.payoff[i] * 0.6 + log.payoff[i] * 0.4);
            o.tries[i] = (int) Math.min(1000, o.tries[i] + log.tries[i]);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA);
        ListTag list = new ListTag();
        List<Map.Entry<UUID, Npc>> sorted = new ArrayList<>(npcs.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        for (Map.Entry<UUID, Npc> e : sorted) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            Npc n = e.getValue();
            t.putString("type", n.type);
            t.putLong("seen", n.lastSeen);
            t.putBoolean("fallen", n.fallen);
            ListTag opps = new ListTag();
            for (Map.Entry<UUID, Opponent> o : n.opponents.entrySet()) {
                CompoundTag ot = o.getValue().save();
                ot.putUUID("id", o.getKey());
                opps.add(ot);
            }
            t.put("opponents", opps);
            list.add(t);
        }
        tag.put("npcs", list);
        return tag;
    }

    public static AiMemory load(CompoundTag tag) {
        AiMemory m = new AiMemory();
        for (Tag raw : tag.getList("npcs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id")) continue;
            Npc n = new Npc();
            n.type = t.getString("type");
            n.lastSeen = t.getLong("seen");
            n.fallen = t.getBoolean("fallen");
            for (Tag ro : t.getList("opponents", Tag.TAG_COMPOUND)) {
                CompoundTag ot = (CompoundTag) ro;
                if (ot.hasUUID("id")) n.opponents.put(ot.getUUID("id"), Opponent.load(ot));
            }
            m.npcs.put(t.getUUID("id"), n);
        }
        return m;
    }
}
