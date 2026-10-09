package com.dbzenith.world.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the landmarks stand (CX-33). Purely from the world seed and the terrain noise, so any chunk, on any thread,
 * at any time, works out the same answer without anything being saved: the world is cut into {@code spacing}-sized
 * cells per landmark, each cell tries a few candidate spots in turn, and a spot holds the landmark if the biome fits, the ground
 * suits its plan, and no earlier landmark is too close. Plans are cached.
 */
public final class LandmarkSites {
    private LandmarkSites() {}

    /** A candidate spot: the landmark, its cell, its centre and its own seed. */
    public record Site(Landmark type, int cellX, int cellZ, int x, int z, long seed) {}

    /** Reads the terrain the way world generation will make it (before features), from anywhere. */
    public record Terrain(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights, int seaLevel) {
        /** The ground's top (the first solid block's y) at a column. */
        public int height(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - 1;
        }

        /** Whether the column is under water. */
        public boolean wet(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random) - 1 > height(x, z);
        }

        public Holder<Biome> biome(int x, int y, int z) {
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
        }

        public static Terrain of(ServerLevel level) {
            return new Terrain(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level, level.getSeaLevel());
        }
    }

    /** How many spots each cell tries, in turn, before it goes without (the first that suits holds the landmark). */
    static final int ATTEMPTS = 4;

    /** The cell's first candidate spot. */
    public static Site site(Landmark type, long worldSeed, int cellX, int cellZ) {
        return site(type, worldSeed, cellX, cellZ, 0);
    }

    /** A candidate spot of a cell: anywhere in its middle part, so neighbouring landmarks keep apart. */
    static Site site(Landmark type, long worldSeed, int cellX, int cellZ, int attempt) {
        long s = mix(worldSeed ^ type.salt, cellX, cellZ);
        if (attempt > 0) s = mix(s, attempt, 0x5eedL);
        int margin = Math.min(type.spacing / 2 - 1, type.radius + 64);
        int span = Math.max(1, type.spacing - 2 * margin);
        int x = cellX * type.spacing + margin + (int) Math.floorMod(s, span);
        int z = cellZ * type.spacing + margin + (int) Math.floorMod(s >>> 20, span);
        return new Site(type, cellX, cellZ, x, z, mix(s, x, z));
    }

    private static final ConcurrentHashMap<String, Optional<LandmarkPlan>> PLANS = new ConcurrentHashMap<>();

    /** The landmark of a cell (at the first of its spots that suits), or null when the cell goes without. */
    public static LandmarkPlan plan(Landmark type, long worldSeed, int cellX, int cellZ, Terrain terrain) {
        String key = worldSeed + ":" + type.ordinal() + ":" + cellX + ":" + cellZ;
        Optional<LandmarkPlan> cached = PLANS.get(key);
        if (cached != null) return cached.orElse(null);
        if (PLANS.size() > 1024) PLANS.clear();
        LandmarkPlan made = null;
        for (int a = 0; a < ATTEMPTS && made == null; a++) made = make(site(type, worldSeed, cellX, cellZ, a), worldSeed, terrain);
        PLANS.putIfAbsent(key, Optional.ofNullable(made));
        return made;
    }

    private static LandmarkPlan make(Site site, long worldSeed, Terrain t) {
        if (!biomeFits(site, t) || crowded(site, worldSeed, t)) return null;
        return shape(site, t);
    }

    private static boolean biomeFits(Site site, Terrain t) {
        int y = Math.max(t.height(site.x(), site.z()), t.seaLevel());
        return t.biome(site.x(), y, site.z()).is(k -> site.type().biomes.contains(k));
    }

    /** Whether an earlier landmark (higher in the list) stands within reach. */
    private static boolean crowded(Site site, long worldSeed, Terrain t) {
        Landmark type = site.type();
        for (Landmark other : Landmark.ALL) {
            if (other.ordinal() >= type.ordinal()) break;
            int reach = other.radius + type.radius + 32;
            int c0x = Math.floorDiv(site.x() - reach, other.spacing), c1x = Math.floorDiv(site.x() + reach, other.spacing);
            int c0z = Math.floorDiv(site.z() - reach, other.spacing), c1z = Math.floorDiv(site.z() + reach, other.spacing);
            for (int cx = c0x; cx <= c1x; cx++)
                for (int cz = c0z; cz <= c1z; cz++) {
                    LandmarkPlan o = plan(other, worldSeed, cx, cz, t);
                    if (o != null && Math.abs(o.x - site.x()) <= reach && Math.abs(o.z - site.z()) <= reach) return true;
                }
        }
        return false;
    }

    /** For tuning: of the cells within {@code cells} cells, how many hold a landmark, and why the rest don't (first spot's reason). */
    public static int[] survey(Landmark type, long worldSeed, Terrain t, BlockPos from, int cells) {
        int fx = Math.floorDiv(from.getX(), type.spacing), fz = Math.floorDiv(from.getZ(), type.spacing);
        int[] n = new int[4];
        for (int cx = fx - cells; cx <= fx + cells; cx++)
            for (int cz = fz - cells; cz <= fz + cells; cz++) {
                Site s = site(type, worldSeed, cx, cz);
                if (plan(type, worldSeed, cx, cz, t) != null) n[3]++;
                else if (!biomeFits(s, t)) n[0]++;
                else if (crowded(s, worldSeed, t)) n[1]++;
                else n[2]++;
            }
        return n;
    }

    private static LandmarkPlan shape(Site site, Terrain t) {
        Landmark type = site.type();
        return switch (type) {
            case TOURNAMENT -> TournamentArena.plan(site, t);
            case LOOKOUT -> KamiLookout.plan(site, t);
            case CELL_GAMES -> CellGamesArena.plan(site, t);
            case FRYPAN -> FrypanMountains.plan(site, t);
            case WEST_CITY -> WestCity.plan(site, t);
        };
    }

    /** Every landmark plan whose reach covers the chunk. */
    static void forChunk(long worldSeed, Terrain t, int chunkX, int chunkZ, java.util.function.Consumer<LandmarkPlan> out) {
        int bx = (chunkX << 4) + 8, bz = (chunkZ << 4) + 8;
        for (Landmark type : Landmark.ALL) {
            int reach = type.radius + 8;
            int c0x = Math.floorDiv(bx - reach, type.spacing), c1x = Math.floorDiv(bx + reach, type.spacing);
            int c0z = Math.floorDiv(bz - reach, type.spacing), c1z = Math.floorDiv(bz + reach, type.spacing);
            for (int cx = c0x; cx <= c1x; cx++)
                for (int cz = c0z; cz <= c1z; cz++) {
                    LandmarkPlan p = plan(type, worldSeed, cx, cz, t);
                    if (p != null && Math.abs(p.x - bx) <= reach && Math.abs(p.z - bz) <= reach) out.accept(p);
                }
        }
    }

    /** The nearest landmark of a kind to a point, searching up to {@code cells} cells out, or null. */
    public static LandmarkPlan nearest(Landmark type, long worldSeed, Terrain t, BlockPos from, int cells) {
        int fx = Math.floorDiv(from.getX(), type.spacing), fz = Math.floorDiv(from.getZ(), type.spacing);
        LandmarkPlan best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= cells; r++) {
            for (int cx = fx - r; cx <= fx + r; cx++)
                for (int cz = fz - r; cz <= fz + r; cz++) {
                    if (Math.max(Math.abs(cx - fx), Math.abs(cz - fz)) != r) continue;
                    LandmarkPlan p = plan(type, worldSeed, cx, cz, t);
                    if (p == null) continue;
                    double d = from.distSqr(new BlockPos(p.x, from.getY(), p.z));
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            if (best != null && r >= 1) return best;                    // a ring further out cannot be nearer than this
        }
        return best;
    }

    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
