package com.dbzenith.dragonball;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.living.Resident;
import com.dbzenith.living.Settlement;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.stats.Race;
import com.dbzenith.world.landmark.Landmark;
import com.dbzenith.world.landmark.LandmarkSites;
import com.dbzenith.world.landmark.NamekVillage;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Namek's Dragon Balls (v0.167.0). As in canon, each village elder keeps one of the seven: the seven Namekian villages
 * nearest Namek's centre (worked out from the world seed, as world generation sites them, {@link LandmarkSites}) each
 * keep one, on a pedestal beside the elder's seat ({@link NamekVillage#ballSpot}). Scattering, and every return from
 * stone after Porunga's wishes, puts a ball back on its keeper's pedestal: that chunk is loaded (generated if need be,
 * when the village draws itself there) and the ball set down; a village drawn before the pedestals existed gets one on
 * its floor, and anything a player put there is never replaced. Without seven villages within reach, or with a pedestal
 * blocked, a ball lies somewhere on Namek and no one keeps it.
 * <p>
 * Elders speak of their ball and hand it to those they trust (conversation conditions {@code dragon_ball},
 * {@code knows_words}; actions {@code give_dragon_ball}, {@code teach_words}). Porunga grants three wishes, and answers
 * only the Namekian words: a Namekian summoner knows them, others learn them from an elder, or bring a Namekian along
 * (any Namekian resident nearby speaks them for you).
 */
public final class NamekBalls {
    /** Porunga grants three wishes. */
    public static final int WISHES = 3;
    /** Player flag: taught the words that call Porunga. */
    public static final String WORDS_FLAG = "namekian_words";
    /** How many cells (rings of village cells) out from Namek's centre keepers are looked for. */
    static final int RINGS = 4;
    /** How near a Namekian must stand to speak the words for the summoner. */
    static final int WORDS_REACH = 16;
    static final String[] STARS = {"One", "Two", "Three", "Four", "Five", "Six", "Seven"};

    private static final Map<Long, CompletableFuture<List<NamekVillage>>> KEEPERS = new ConcurrentHashMap<>();

    private NamekBalls() {}

    public static ServerLevel namek(MinecraftServer server) {
        return server.getLevel(Landmark.Keys.NAMEK);
    }

    // ------------------------------------------------------------------ where they are kept

    /**
     * The villages that keep the seven, star 1 first (fewer when there aren't seven within {@link #RINGS} cells): the
     * nearest to Namek's centre, in an order from the world seed. The same every time for a world. The search reads
     * the terrain noise of dozens of village cells (seconds), so it runs once per world and server run off the server
     * thread: null until it is done.
     */
    public static List<NamekVillage> keepers(ServerLevel level) {
        return search(level).getNow(null);
    }

    /** The keepers, waiting for the search if need be (tests and commands). */
    public static List<NamekVillage> keepersNow(ServerLevel level) {
        return search(level).join();
    }

    private static CompletableFuture<List<NamekVillage>> search(ServerLevel level) {
        return KEEPERS.computeIfAbsent(level.getSeed(), s -> CompletableFuture.supplyAsync(() -> find(level), Util.backgroundExecutor()));
    }

    private static List<NamekVillage> find(ServerLevel level) {
        try {
            return findKeepers(level);
        } catch (RuntimeException e) {
            DBZenith.LOGGER.warn("Namek's Dragon Balls: no keepers ({})", e.toString());
            return List.of();
        }
    }

    private static List<NamekVillage> findKeepers(ServerLevel level) {
        long t0 = System.nanoTime(), seed = level.getSeed();
        LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
        List<NamekVillage> found = new ArrayList<>();
        for (int r = 0; r <= RINGS && found.size() < DragonBalls.COUNT; r++)                  // ring by ring round the centre's cells
            for (int cx = -r - 1; cx <= r; cx++)
                for (int cz = -r - 1; cz <= r; cz++) {
                    if (Math.max(ring(cx), ring(cz)) != r) continue;
                    if (LandmarkSites.plan(Landmark.NAMEK_VILLAGE, seed, cx, cz, t) instanceof NamekVillage v) found.add(v);
                }
        found.sort(Comparator.comparingDouble(v -> Math.hypot(v.x, v.z)));
        List<NamekVillage> seven = new ArrayList<>(found.subList(0, Math.min(DragonBalls.COUNT, found.size())));
        Collections.shuffle(seven, new Random(seed ^ 0x7a3b9ab1L));
        DBZenith.LOGGER.info("Namek's Dragon Balls: {} village elder(s) keep them ({} ms)", seven.size(), (System.nanoTime() - t0) / 1_000_000);
        return List.copyOf(seven);
    }

    /** Rings of cells round Namek's centre, which lies between cells -1 and 0. */
    private static int ring(int c) {
        return c < 0 ? -c - 1 : c;
    }

    /** Puts a ball back with its keeper (or, failing that, anywhere on Namek). Null when Namek isn't there or no spot was found this time. */
    static BlockPos scatter(MinecraftServer server, int star) {
        ServerLevel level = namek(server);
        if (level == null) return null;
        List<NamekVillage> keepers = keepers(level);
        if (keepers == null) return null;                                                     // still looking: the next pass
        if (star <= keepers.size()) {
            if (!loaded(level, keepers.get(star - 1).ballSpot())) return null;                   // loading (or generating): the next pass
            BlockPos at = keep(level, keepers.get(star - 1), star);
            if (at != null) return at;
        }
        return DragonBalls.scatterAround(level, BlockPos.ZERO, DBZConfig.SERVER.blackStarScatterRadius.get(), BallSet.NAMEK, star);
    }

    /** Keeps a keeper's chunk loading for a while (generation runs off the server thread). */
    private static final TicketType<ChunkPos> LOADING = TicketType.create("dbzenith_namek_ball", Comparator.comparingLong(ChunkPos::toLong), 400);

    /** Whether the chunk at {@code pos} is loaded; if not, asks for it (it loads, or generates, in the background). */
    static boolean loaded(ServerLevel level, BlockPos pos) {
        ChunkPos c = new ChunkPos(pos);
        if (level.getChunkSource().getChunkNow(c.x, c.z) != null) return true;
        level.getChunkSource().addRegionTicket(LOADING, c, 1, c);
        return false;
    }

    /**
     * Sets a ball on a village's pedestal and records that village as its keeper. Loads (or generates) the chunk first
     * if it isn't loaded ({@link #scatter} waits for it to load in the background instead).
     * Null when the spot is taken: never over anything but air, and a missing pedestal only goes onto the elder house's floor.
     */
    public static BlockPos keep(ServerLevel level, NamekVillage village, int star) {
        BlockPos at = village.ballSpot();
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);                                      // the village draws itself as it generates
        BlockState pedestal = NamekVillage.pedestal();
        boolean there = level.getBlockState(at).is(DragonBalls.block(BallSet.NAMEK, star));      // (left there untracked: taken back as it is)
        if (!there && !level.getBlockState(at).isAir()) return null;
        if (!level.getBlockState(at.below()).is(pedestal.getBlock())) {
            if (!level.getBlockState(at.below()).isAir() || !level.getBlockState(at.below(2)).is(NamekVillage.floor().getBlock())) return null;
            level.setBlockAndUpdate(at.below(), pedestal);                                     // a village from before the pedestals
        }
        DragonBalls.place(level, at, BallSet.NAMEK, star);
        DragonBallData data = DragonBallData.get(level.getServer(), BallSet.NAMEK);
        data.entry(star).keeper = village.settlementId().toString();
        data.setDirty();
        return at;
    }

    /**
     * {@code /dbz namekballs}: each ball, where it is and who keeps it. {@code home}: every ball not carried by anyone is
     * sent back to its keeper now (stone ones wake), for testing.
     */
    public static int report(net.minecraft.commands.CommandSourceStack src, boolean home) {
        MinecraftServer server = src.getServer();
        ServerLevel level = namek(server);
        if (level == null) {
            src.sendFailure(Component.literal("No Namek on this server"));
            return 0;
        }
        List<NamekVillage> keepers = keepersNow(level);
        DragonBallData data = DragonBallData.get(server, BallSet.NAMEK);
        for (int star = 1; star <= DragonBalls.COUNT; star++) {
            DragonBallData.Entry e = data.entry(star);
            if (home && e.state != DragonBallData.State.HELD) {
                if (e.state == DragonBallData.State.PLACED) {
                    ServerLevel at = server.getLevel(e.dimension);
                    if (at != null && at.getBlockState(e.pos).is(DragonBalls.block(BallSet.NAMEK, star))) at.setBlockAndUpdate(e.pos, Blocks.AIR.defaultBlockState());
                }
                scatter(server, star);
            }
            NamekVillage v = star <= keepers.size() ? keepers.get(star - 1) : null;
            String line = String.format(java.util.Locale.ROOT, "%d-star: %s at %s %d %d %d; keeper %s", star, e.state.name().toLowerCase(java.util.Locale.ROOT),
                    e.dimension.location().getPath(), e.pos.getX(), e.pos.getY(), e.pos.getZ(),
                    v == null ? "none" : v.name() + " (pedestal " + v.ballSpot().toShortString() + (e.keeper.isEmpty() ? ", not there" : "") + ")");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return keepers.size();
    }

    // ------------------------------------------------------------------ what the elders know

    /** The star of the ball a settlement's elder keeps, or 0. */
    public static int starOf(MinecraftServer server, Settlement town) {
        if (town == null) return 0;
        DragonBallData data = DragonBallData.get(server, BallSet.NAMEK);
        String id = town.id.toString();
        for (int star = 1; star <= DragonBalls.COUNT; star++) if (id.equals(data.entry(star).keeper)) return star;
        return 0;
    }

    /**
     * A settlement's ball: {@code kept} (in the village), {@code taken} (carried off, or set down elsewhere), {@code stone}
     * (after Porunga's wishes, until it returns), {@code maker} (the Grand Elder's house: he made them), or {@code none}
     * (this elder keeps none).
     */
    public static String state(MinecraftServer server, Settlement town) {
        int star = starOf(server, town);
        if (star == 0) return town != null && com.dbzenith.world.landmark.GrandElderHouse.NAME.equals(town.name) ? "maker" : "none";
        DragonBallData.Entry e = DragonBallData.get(server, BallSet.NAMEK).entry(star);
        if (e.state == DragonBallData.State.INERT || e.state == DragonBallData.State.UNSET) return "stone";
        boolean home = e.state == DragonBallData.State.PLACED && e.dimension.location().toString().equals(town.dimension)
                && e.pos.distSqr(town.center) <= (double) town.radius * town.radius;
        return home ? "kept" : "taken";
    }

    /** "the Four-Star Ball" (for what elders say), or "no ball". */
    public static String ballName(MinecraftServer server, Settlement town) {
        int star = starOf(server, town);
        return star == 0 ? "no ball" : "the " + STARS[star - 1] + "-Star Ball";
    }

    /** The elder hands over the ball it keeps (only while it is in the village). Returns whether it did. */
    public static boolean give(ServerPlayer player, Settlement town) {
        if (!"kept".equals(state(player.server, town))) return false;
        int star = starOf(player.server, town);
        DragonBallData.Entry e = DragonBallData.get(player.server, BallSet.NAMEK).entry(star);
        ServerLevel level = player.server.getLevel(e.dimension);
        if (level == null || !level.getBlockState(e.pos).is(DragonBalls.block(BallSet.NAMEK, star))) return false;
        level.setBlockAndUpdate(e.pos, Blocks.AIR.defaultBlockState());                       // recorded as carried
        ItemStack ball = new ItemStack(DragonBalls.item(BallSet.NAMEK, star));
        if (!player.getInventory().add(ball)) player.drop(ball, false);
        return true;
    }

    // ------------------------------------------------------------------ the Namekian words

    /** Fondness (bond affinity), or renown, that earns an elder's trust; a Namekian has it from the start. */
    public static final double TRUST_AFFINITY = 10;
    public static final int TRUST_FAME = 100;

    /** Whether an elder entrusts its ball and the words to this player: a Namekian, a friend of the elder, or a famous hero. */
    public static boolean trusts(ServerPlayer player, double affinity, int fame) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        return affinity >= TRUST_AFFINITY || fame >= TRUST_FAME || d != null && d.getRace() == Race.NAMEKIAN;
    }

    public static boolean knowsWords(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        return d != null && (d.getRace() == Race.NAMEKIAN || d.hasFlag(WORDS_FLAG));
    }

    public static void teachWords(ServerPlayer player) {
        ModCapabilities.get(player).ifPresent(d -> d.setFlag(WORDS_FLAG, true));
    }

    /** Who speaks the Namekian words for this summoner here: themself if they know them, else a Namekian standing near; null if no one. */
    public static Component speaker(ServerLevel level, ServerPlayer player, BlockPos at) {
        if (knowsWords(player)) return player.getDisplayName();
        for (Resident r : level.getEntitiesOfClass(Resident.class, new AABB(at).inflate(WORDS_REACH), Resident::isAlive)) {
            NamedFighter c = r.character();
            if (c != null && "namekian".equals(c.race)) return r.getDisplayName();
        }
        return null;
    }
}
