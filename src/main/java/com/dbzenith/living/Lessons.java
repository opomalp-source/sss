package com.dbzenith.living;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.TechniqueLibrary;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lessons with a martial arts master (docs/LIVING_WORLD.md). A master teaches the existing techniques it knows (its
 * school's curriculum and anything its character learned), one at a time, never one you know or can't use (your race,
 * your level), never the same one twice. A lesson has three steps, kept on your bond with the master
 * ({@code lesson:<technique>:<step>}) so it survives saving:
 * <ol>
 *   <li><b>demonstration:</b> the master shows the technique (fired harmlessly into the sky) and explains it;</li>
 *   <li><b>ki control:</b> you hold your ki charged near the master for five seconds in all;</li>
 *   <li><b>the test:</b> a spar with the master; when it ends, you have it ({@link TechniqueLibrary#learnFree}).</li>
 * </ol>
 * A master only teaches someone it trusts; it gains a student (and is glad of it), you gain the technique.
 */
public final class Lessons {
    /** The school's curriculum (existing techniques), in the order a master teaches them. */
    public static final List<Technique> CURRICULUM = List.of(Techniques.KI_BLAST, Techniques.KI_SENSE, Techniques.RAPID_VOLLEY,
            Techniques.WAVE_BEAM, Techniques.SOLAR_FLARE, Techniques.EXPLOSIVE_WAVE);
    static final double TRUST = 15;
    static final int PRACTICE = 100;
    /** Ticks of ki control done in the practice step, per player and master. */
    private static final Map<String, Integer> PRACTICE_DONE = new HashMap<>();

    public enum Step { NONE, DEMO, PRACTICE, TEST }

    private Lessons() {}

    /** What this master can teach this player now, in order. */
    public static List<Technique> teachable(NamedFighter master, Bond b, PlayerData d) {
        List<Technique> out = new ArrayList<>();
        if (Occupation.of(master) != Occupation.MASTER || d == null) return out;
        List<Technique> known = new ArrayList<>(CURRICULUM);
        for (String id : master.learned) {
            Technique t = Techniques.byId(id);
            if (t != null && !known.contains(t)) known.add(t);
        }
        int level = StatCalculator.level(d);
        for (Technique t : known) {
            if (d.knows(t.id()) || !t.races().contains(d.getRace()) || level < t.unlockLevel() || b.flags.contains("taught:" + t.id())) continue;
            out.add(t);
        }
        return out;
    }

    /** The lesson under way with this master, as {technique id, step}, or null. */
    public static String[] current(Bond b) {
        for (String f : b.flags) if (f.startsWith("lesson:")) {
            String[] p = f.split(":");
            if (p.length >= 3) return new String[]{p[1], p[2], p.length > 3 ? p[3] : ""};
        }
        return null;
    }

    public static Step step(Bond b) {
        String[] cur = current(b);
        if (cur == null) return Step.NONE;
        return switch (cur[1]) {
            case "demo" -> Step.DEMO;
            case "practice" -> Step.PRACTICE;
            case "test" -> Step.TEST;
            default -> Step.NONE;
        };
    }

    /** Why it won't teach now ("none" if it will): not_master, stranger, wanted, nothing. */
    public static String refusal(NamedFighter master, Bond b, ServerPlayer p, Settlement town) {
        if (Occupation.of(master) != Occupation.MASTER) return "not_master";
        if (town != null && town.wanted.contains(p.getUUID())) return "wanted";
        if (b.affinity < TRUST) return "stranger";
        if (current(b) == null && teachable(master, b, ModCapabilities.get(p).orElse(null)).isEmpty()) return "nothing";
        return "none";
    }

    /** The name of the technique a lesson is about (the next one, or the one under way). */
    public static String lessonName(NamedFighter master, Bond b, ServerPlayer p) {
        String[] cur = current(b);
        Technique t = cur != null ? Techniques.byId(cur[0]) : null;
        if (t == null) {
            List<Technique> next = teachable(master, b, ModCapabilities.get(p).orElse(null));
            t = next.isEmpty() ? null : next.get(0);
        }
        return t == null ? "nothing" : name(t);
    }

    static String name(Technique t) {
        return net.minecraft.locale.Language.getInstance().getOrDefault(t.translationKey(), Rumors.pretty(t.id()));
    }

    static void set(Bond b, String tech, String step, String extra) {
        b.flags.removeIf(f -> f.startsWith("lesson:"));
        if (tech != null) b.flags.add("lesson:" + tech + ":" + step + (extra.isEmpty() ? "" : ":" + extra));
    }

    /** Begins the next lesson: the demonstration. */
    public static boolean start(Resident master, NamedFighter c, Bond b, ServerPlayer p) {
        if (!refusal(c, b, p, LivingData.get(p.server).settlement(c.settlement)).equals("none") || current(b) != null) return false;
        List<Technique> next = teachable(c, b, ModCapabilities.get(p).orElse(null));
        if (next.isEmpty()) return false;
        Technique t = next.get(0);
        demonstrate(master, t);
        set(b, t.id(), "practice", "");
        PRACTICE_DONE.remove(key(p.getUUID(), c.id));
        p.sendSystemMessage(Component.translatable("living.dbzenith.lesson.demo", c.name, name(t)).withStyle(ChatFormatting.AQUA));
        Nemesis.data().setDirty();
        return true;
    }

    /** The master shows the technique: fired into the sky, harmlessly. */
    static void demonstrate(Resident master, Technique t) {
        if (!(master.level() instanceof ServerLevel sl)) return;
        master.setXRot(-70f);
        master.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (t.style() != Technique.Style.SELF) TechniqueHandler.spawn(sl, master, t, 0.0);
        else master.setAiFlag(com.dbzenith.npc.KiFighter.AI_CHARGING, true);
    }

    /** The test: a spar with the master (its end completes the lesson). */
    public static boolean test(Resident master, NamedFighter c, Bond b, ServerPlayer p) {
        String[] cur = current(b);
        if (cur == null || !cur[1].equals("test") || master.sparWith != null) return false;
        set(b, cur[0], "test", String.valueOf(b.spars));
        master.talkingTo = null;
        master.sparWith = p.getUUID();
        master.setTarget(p);
        Nemesis.data().setDirty();
        return true;
    }

    /** Gives up the lesson under way. */
    public static void quit(Bond b) {
        set(b, null, "", "");
    }

    static String key(UUID player, UUID master) {
        return player + "/" + master;
    }

    /** Every half second: ki control counted for those practising near their master; a test spar that ended completes its lesson. */
    static void tick(MinecraftServer server, long now) {
        NemesisData d = Nemesis.data();
        if (d == null) return;
        for (NamedFighter c : d.all()) {
            if (Occupation.of(c) != Occupation.MASTER || c.status == NamedFighter.Status.DEAD) continue;
            for (var e : c.players.entrySet()) {
                Bond b = e.getValue();
                String[] cur = current(b);
                if (cur == null) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) continue;
                Technique t = Techniques.byId(cur[0]);
                if (t == null) {
                    quit(b);
                    continue;
                }
                if (cur[1].equals("practice")) practice(c, b, p, t);
                else if (cur[1].equals("test") && !cur[2].isEmpty()) {
                    int before;
                    try {
                        before = Integer.parseInt(cur[2]);
                    } catch (NumberFormatException ex) {
                        before = Integer.MAX_VALUE;
                    }
                    if (b.spars > before) complete(c, b, p, t, now);
                }
            }
        }
    }

    static void practice(NamedFighter c, Bond b, ServerPlayer p, Technique t) {
        PlayerData pd = ModCapabilities.get(p).orElse(null);
        if (pd == null || !pd.isCharging()) return;
        if (!(c.body != null && p.serverLevel().getEntity(c.body) instanceof Resident m) || m.distanceTo(p) > 10) return;
        String k = key(p.getUUID(), c.id);
        int done = PRACTICE_DONE.merge(k, 10, Integer::sum);
        if (done < PRACTICE) return;
        PRACTICE_DONE.remove(k);
        set(b, t.id(), "test", "");
        p.sendSystemMessage(Component.translatable("living.dbzenith.lesson.practiced", c.name, name(t)).withStyle(ChatFormatting.AQUA));
        Nemesis.data().setDirty();
    }

    /** The lesson is learned: the technique, the master's pride, a memory each. */
    static void complete(NamedFighter c, Bond b, ServerPlayer p, Technique t, long now) {
        set(b, null, "", "");
        b.flags.add("taught:" + t.id());
        b.flags.add("student");
        PlayerData pd = ModCapabilities.get(p).orElse(null);
        boolean learned = pd != null && TechniqueLibrary.learnFree(pd, t);
        b.shift(8, 0);
        c.remember(new Memory(Memory.Kind.TALKED, now, p.getUUID(), "taught " + t.id()));
        if (c.goal == NamedFighter.Goal.FIND_STUDENT) Mood.set(c, Mood.HAPPY, now, 6000);
        p.sendSystemMessage(Component.translatable(learned ? "living.dbzenith.lesson.learned" : "living.dbzenith.lesson.passed", c.name, name(t))
                .withStyle(ChatFormatting.GOLD));
        Nemesis.data().setDirty();
    }

    /** Tests: practice counted so far. */
    public static int practiced(UUID player, UUID master) {
        return PRACTICE_DONE.getOrDefault(key(player, master), 0);
    }
}
