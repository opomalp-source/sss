package com.dbzenith.nemesis;

import com.dbzenith.npc.ai.Personality;
import com.dbzenith.npc.ai.Temper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A named character: a persistent identity that outlives its Minecraft entity (which may unload, leave, be killed
 * and come back). Kept in {@link NemesisData}. Its body in the world, when it has one, carries its {@link #id} and the
 * entity's own UUID is recorded in {@link #body} so a stale copy that loads later knows to leave.
 */
public final class NamedFighter {
    public enum Status {
        /** In the world (its entity is somewhere, loaded or not). */
        PRESENT,
        /** Gone after a fight or an encounter, back at {@link NamedFighter#returnAt}. */
        AWAY,
        /** Survived a defeat; recovering until {@link NamedFighter#returnAt}. */
        RECOVERING,
        /** Withdrawn for good (old, beaten too often, at peace): remembered, never met again. */
        RETIRED,
        /** Dead and gone. */
        DEAD;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What it is after, which colours what it does next. */
    public enum Goal { TRAIN, REVENGE, RIVALRY, PROTECT, WANDER, REST,
        // the living world's ambitions (docs/LIVING_WORLD.md)
        STRONGEST, IMPRESS_MASTER, BUSINESS, EXPLORE, FIND_STUDENT, PROTECT_TOWN }

    public final UUID id;
    public String name = "", epithet = "";
    /** The entity type it comes back as (registry id). */
    public String type = "";
    public String race = "", variant = "";
    public Personality personality = Personality.BALANCED;
    public final Set<Trait> traits = EnumSet.noneOf(Trait.class);
    /** Its fighter level (the scale of {@code KiFighter.levelFor}), and experience toward the next. */
    public int level = 1;
    public double xp;
    public int battles, victories, defeats;
    /** Techniques it learned beyond its kind's own (existing technique ids). */
    public final List<String> learned = new ArrayList<>();
    /** Forms it has reached (existing form ids), in order. */
    public final Set<String> forms = new LinkedHashSet<>();
    public int reputation;
    public Status status = Status.PRESENT;
    public Goal goal = Goal.WANDER;
    /** Who the goal is about (a player or a character), or null. */
    public UUID goalTarget;
    public long created, lastSeen, returnAt, lastTrained, lastSimulated;
    /** The entity that is its body now (null when it has none). */
    public UUID body;
    public String dimension = "";
    public long pos;
    /** How quickly it grows (0.5..1.5), from its traits and temperament at birth. */
    public double growth = 1;
    /** A canon character (bosses, the tournament roster) keeps its own name; a promoted fighter gets one. */
    public boolean canon;
    public int survivedDeaths;

    // ---- the living world (docs/LIVING_WORLD.md): where it lives and what it does there
    /** Its occupation id ({@code living.Occupation}), "" for a fighter with none. */
    public String occupation = "";
    /** The settlement it lives in, and its home there (a block position as a long; 0: none). */
    public UUID settlement;
    public long home;
    /** Which of the town's looks it wears (residents). */
    public int look;
    /** Its mood now ({@code living.Mood} id) and until when (game time). */
    public String mood = "";
    public long moodUntil;
    /** Rumors it knows (ids in the living world's store), newest last, bounded. */
    public final java.util.Deque<UUID> knowledge = new java.util.ArrayDeque<>();
    /** The in-game day its growth is being counted for, and its level when that day began. */
    public long day = Long.MIN_VALUE;
    public int dayStartLevel;

    public final Map<UUID, Bond> players = new LinkedHashMap<>();
    public final Map<UUID, Bond> characters = new LinkedHashMap<>();
    public final Deque<Memory> history = new ArrayDeque<>();

    public NamedFighter(UUID id) {
        this.id = id;
    }

    public Rank rank() {
        return Rank.of(reputation);
    }

    /** A townsperson of the living world (it has an occupation): its life is run by {@code living}, not by encounters. */
    public boolean resident() {
        return !occupation.isEmpty();
    }

    /** Told of every memory a character makes (the living world turns the notable ones into rumors). */
    public static java.util.function.BiConsumer<NamedFighter, Memory> onMemory;

    public boolean active() {
        return status == Status.PRESENT || status == Status.AWAY || status == Status.RECOVERING;
    }

    /** Its temper: personality plus traits (what the combat AI fights with). */
    public Temper temper() {
        List<Temper.Shift> shifts = new ArrayList<>();
        for (Trait t : traits) shifts.add(t.shift);
        return Temper.of(personality, shifts);
    }

    public List<Temper.Shift> shifts() {
        List<Temper.Shift> shifts = new ArrayList<>();
        for (Trait t : traits) shifts.add(t.shift);
        return shifts;
    }

    public boolean has(Trait t) {
        return traits.contains(t);
    }

    /** Name with its epithet, as it is introduced. */
    public String fullName() {
        return epithet.isEmpty() ? name : name + " " + epithet;
    }

    public Bond player(UUID player, String playerName) {
        Bond b = players.computeIfAbsent(player, k -> new Bond());
        if (playerName != null && !playerName.isEmpty()) b.name = playerName;
        return b;
    }

    public Bond character(NamedFighter other) {
        Bond b = characters.computeIfAbsent(other.id, k -> new Bond());
        b.name = other.name;
        return b;
    }

    public void remember(Memory m) {
        history.addLast(m);
        while (history.size() > NemesisData.MAX_HISTORY) history.removeFirst();
        if (onMemory != null) onMemory.accept(this, m);
    }

    /** The latest memory of this kind about {@code who} (null: any), or null. */
    public Memory last(Memory.Kind kind, UUID who) {
        var it = history.descendingIterator();
        while (it.hasNext()) {
            Memory m = it.next();
            if (m.kind() == kind && (who == null || who.equals(m.who()))) return m;
        }
        return null;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("epithet", epithet);
        t.putString("type", type);
        t.putString("race", race);
        t.putString("variant", variant);
        t.putString("personality", personality.id());
        ListTag tr = new ListTag();
        for (Trait x : traits) tr.add(net.minecraft.nbt.StringTag.valueOf(x.id()));
        t.put("traits", tr);
        t.putInt("level", level);
        t.putDouble("xp", xp);
        t.putIntArray("record", new int[]{battles, victories, defeats, reputation, survivedDeaths});
        t.put("learned", Bond.strings(learned));
        t.put("forms", Bond.strings(forms));
        t.putString("status", status.id());
        t.putString("goal", goal.name().toLowerCase(Locale.ROOT));
        if (goalTarget != null) t.putUUID("goalTarget", goalTarget);
        t.putLongArray("times", new long[]{created, lastSeen, returnAt, lastTrained, lastSimulated});
        if (body != null) t.putUUID("body", body);
        t.putString("dim", dimension);
        t.putLong("pos", pos);
        t.putDouble("growth", growth);
        t.putBoolean("canon", canon);
        t.putLong("day", day);
        t.putInt("dayLevel", dayStartLevel);
        t.putString("occupation", occupation);
        if (settlement != null) t.putUUID("settlement", settlement);
        t.putLong("home", home);
        t.putInt("look", look);
        t.putString("mood", mood);
        t.putLong("moodUntil", moodUntil);
        ListTag kn = new ListTag();
        for (UUID k : knowledge) kn.add(net.minecraft.nbt.NbtUtils.createUUID(k));
        t.put("knowledge", kn);
        CompoundTag ps = new CompoundTag();
        players.forEach((k, v) -> ps.put(k.toString(), v.save()));
        t.put("players", ps);
        CompoundTag cs = new CompoundTag();
        characters.forEach((k, v) -> cs.put(k.toString(), v.save()));
        t.put("characters", cs);
        ListTag h = new ListTag();
        for (Memory m : history) h.add(m.save());
        t.put("history", h);
        return t;
    }

    static NamedFighter load(CompoundTag t) {
        NamedFighter c = new NamedFighter(t.getUUID("id"));
        c.name = t.getString("name");
        c.epithet = t.getString("epithet");
        c.type = t.getString("type");
        c.race = t.getString("race");
        c.variant = t.getString("variant");
        Personality p = Personality.byId(t.getString("personality"));
        c.personality = p == null ? Personality.BALANCED : p;
        for (Tag x : t.getList("traits", Tag.TAG_STRING)) {
            Trait tr = Trait.byId(x.getAsString());
            if (tr != null) c.traits.add(tr);
        }
        c.level = Math.max(1, t.getInt("level"));
        c.xp = t.getDouble("xp");
        int[] rec = java.util.Arrays.copyOf(t.getIntArray("record"), 5);
        c.battles = rec[0];
        c.victories = rec[1];
        c.defeats = rec[2];
        c.reputation = rec[3];
        c.survivedDeaths = rec[4];
        Bond.read(t.getList("learned", Tag.TAG_STRING), c.learned);
        Bond.read(t.getList("forms", Tag.TAG_STRING), c.forms);
        for (Status s : Status.values()) if (s.id().equals(t.getString("status"))) c.status = s;
        for (Goal g : Goal.values()) if (g.name().toLowerCase(Locale.ROOT).equals(t.getString("goal"))) c.goal = g;
        c.goalTarget = t.hasUUID("goalTarget") ? t.getUUID("goalTarget") : null;
        long[] times = java.util.Arrays.copyOf(t.getLongArray("times"), 5);
        c.created = times[0];
        c.lastSeen = times[1];
        c.returnAt = times[2];
        c.lastTrained = times[3];
        c.lastSimulated = times[4];
        c.body = t.hasUUID("body") ? t.getUUID("body") : null;
        c.dimension = t.getString("dim");
        c.pos = t.getLong("pos");
        c.growth = t.contains("growth") ? t.getDouble("growth") : 1;
        c.canon = t.getBoolean("canon");
        c.day = t.contains("day") ? t.getLong("day") : Long.MIN_VALUE;
        c.dayStartLevel = t.getInt("dayLevel");
        c.occupation = t.getString("occupation");
        c.settlement = t.hasUUID("settlement") ? t.getUUID("settlement") : null;
        c.home = t.getLong("home");
        c.look = t.getInt("look");
        c.mood = t.getString("mood");
        c.moodUntil = t.getLong("moodUntil");
        for (Tag k : t.getList("knowledge", Tag.TAG_INT_ARRAY)) c.knowledge.add(net.minecraft.nbt.NbtUtils.loadUUID(k));
        CompoundTag ps = t.getCompound("players");
        for (String k : ps.getAllKeys()) {
            try {
                c.players.put(UUID.fromString(k), Bond.load(ps.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag cs = t.getCompound("characters");
        for (String k : cs.getAllKeys()) {
            try {
                c.characters.put(UUID.fromString(k), Bond.load(cs.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Tag m : t.getList("history", Tag.TAG_COMPOUND)) {
            Memory mem = Memory.load((CompoundTag) m);
            if (mem != null) c.history.add(mem);
        }
        return c;
    }
}
