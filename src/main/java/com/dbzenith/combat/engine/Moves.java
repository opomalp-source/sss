package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every melee move (CX-19), from data packs: {@code data/<ns>/combat/moves/<id>.json}, reloaded with {@code /reload}.
 * {@link #select} picks the move for a button press from what the fighter is doing and what they did last.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Moves extends SimpleJsonResourceReloadListener {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static Map<String, Move> moves = new LinkedHashMap<>();
    /** The move files as loaded, for clients to predict move starts with (phase 7). */
    private static Map<String, String> sources = new LinkedHashMap<>();

    private Moves() {
        super(new Gson(), "combat/moves");
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Moves());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, Move> out = new LinkedHashMap<>();
        Map<String, String> src = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            String id = e.getKey().getNamespace().equals(DBZenith.MOD_ID) ? e.getKey().getPath() : e.getKey().toString();
            try {
                out.put(id, Move.parse(id, e.getValue().getAsJsonObject()));
                src.put(id, e.getValue().toString());
            } catch (RuntimeException ex) {
                LOG.error("Bad combat move {}: {}", e.getKey(), ex.toString());
            }
        }
        moves = out;
        sources = src;
        LOG.info("Combat engine: {} moves", out.size());
    }

    public static boolean loaded() {
        return !moves.isEmpty();
    }

    public static Move get(String id) {
        return moves.get(id);
    }

    public static List<Move> all() {
        return new ArrayList<>(moves.values());
    }

    /** What a press asks for: the button, which way the fighter pushes, and whether they look up or (in the air) down. */
    public record Input(Move.Button button, Move.Dir push, boolean lookUp, boolean lookDown, boolean ground) {}

    /**
     * The move for {@code input} after {@code previous} ("start" for none). A move naming the exact direction beats one
     * that takes any; then the higher priority. Falls back to the openers when nothing follows {@code previous}.
     */
    public static Move select(Input input, String previous) {
        return select(moves.values(), input, previous);
    }

    /** The same choice among any set of moves (the client predicts with its copy). */
    public static Move select(Iterable<Move> from, Input input, String previous) {
        Move m = best(from, input, previous);
        return m != null || "start".equals(previous) ? m : best(from, input, "start");
    }

    /** The move files (id, JSON), for {@code MovesSyncPacket}. */
    public static Map<String, String> sources() {
        return sources;
    }

    private static Move best(Iterable<Move> from, Input in, String previous) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move m : from) {
            if (m.button != in.button() || !m.follows(previous)) continue;
            if (m.where == Move.Where.GROUND && !in.ground() || m.where == Move.Where.AIR && in.ground()) continue;
            int score;
            switch (m.dir) {
                case ANY -> score = 0;
                case UP -> score = in.lookUp() ? 30 : -1;
                case DOWN -> score = in.lookDown() && !in.ground() ? 30 : -1;
                case NEUTRAL -> score = in.push() == Move.Dir.NEUTRAL ? 10 : -1;
                default -> score = in.push() == m.dir ? 20 : -1;
            }
            if (score < 0) continue;
            score = score * 100 + m.priority;
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }
}
