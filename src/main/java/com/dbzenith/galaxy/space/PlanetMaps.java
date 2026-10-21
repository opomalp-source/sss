package com.dbzenith.galaxy.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.galaxy.planet.PlanetRuntime;
import com.dbzenith.galaxy.planet.PlanetTerrain;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PlanetMapPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.Deflater;

/**
 * Planet maps from the real ground (the space rework, phase 4). The server draws each landable planet's orbital
 * texture from the same functions that generate its terrain: procedural worlds from {@link PlanetTerrain} (heights,
 * seas, top blocks, forests), Earth and Namek from their own biome and climate noise (coasts, oceans, deserts, snow,
 * mountains), the small flat worlds from their biome. Texel (i, j) shows the surface point the sphere renderer puts
 * there ({@link #surface}), so what you see from orbit is where you land. Maps are computed off the server thread,
 * cached, and sent on request; the client never receives a seed.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class PlanetMaps {
    private PlanetMaps() {}

    public static final int WIDTH = 512, HEIGHT = 256;
    /** Earth's map is centred on each player's departure point; anchors are rounded to this many blocks (under a texel). */
    public static final int ANCHOR_STEP = 64;

    /** A regional map: {@code SPAN} blocks square of surface around a point, {@code REGION} texels a side (32 blocks a texel), on a {@code REGION_STEP} grid. */
    public static final int SPAN = 8192, REGION = 256, REGION_STEP = 2048;

    private record Key(String ref, int ax, int az, int span) {}

    private static final Map<MinecraftServer, Map<Key, CompletableFuture<int[]>>> CACHE = new WeakHashMap<>();
    private static final Map<UUID, long[]> REQUESTS = new HashMap<>();
    /** Two low-priority threads draw maps (their rows in parallel), apart from world generation's own workers. */
    private static final ForkJoinPool WORKER = new ForkJoinPool(2, pool -> {
        var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("DBZ-planet-maps-" + t.getPoolIndex());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }, null, false);

    /** What a texel shows: the colour of the ground at a surface point. */
    public interface Ground {
        int color(double x, double z, double footprint);
    }

    /**
     * The surface point at texel (i, j) of a {@code width × height} map, exactly as {@code SpaceSky.sphere} lays the
     * texture on its mesh (mesh direction → body frame {@code (-v.z, -v.x, v.y)} → {@link SpaceFrames#surface}).
     */
    public static double[] surface(SpaceFrames.Planet unturned, int i, int j, int width, int height) {
        return surface(unturned, i + .5, j + .5, width, height);
    }

    /** The surface point at a fractional texel position ({@code i + .5, j + .5} is the texel's centre). */
    public static double[] surface(SpaceFrames.Planet unturned, double i, double j, int width, int height) {
        double lon = i * Math.PI * 2 / width, lat = -Math.PI / 2 + j * Math.PI / height;
        Vec3 v = new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
        return SpaceFrames.surface(unturned, new Vec3(-v.z, -v.x, v.y));
    }

    /** Draws a map: 0xRRGGBB per texel, row by row. */
    public static int[] render(SpaceFrames.Planet unturned, Ground ground, int width, int height) {
        int[] out = new int[width * height];
        java.util.stream.IntStream.range(0, height).parallel().forEach(j -> { for (int i = 0; i < width; i++) out[j * width + i] = texel(unturned, ground, i, j, width, height); });   // rows in parallel on the maps pool
        poles(out, width, height);
        return out;
    }

    /**
     * Evens out the rows near the map's poles along their length: the sphere's mesh pinches each of those rows into a
     * small circle, where differences along the row would draw as rays from the pole.
     */
    public static void poles(int[] map, int width, int height) {
        int[] row = new int[width];
        for (int j = 0; j < height; j++) {
            double across = Math.cos(-Math.PI / 2 + (j + .5) * Math.PI / height);
            int reach = (int) Math.min(width / 2, Math.round(.6 / Math.max(1e-3, across)) - 1);
            if (reach < 1) continue;
            System.arraycopy(map, j * width, row, 0, width);
            for (int i = 0; i < width; i++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int k = -reach; k <= reach; k++) {
                    int c = row[Math.floorMod(i + k, width)];
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                    n++;
                }
                map[j * width + i] = ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
    }

    /** A regional map: {@link #REGION}² texels over {@code [x0, x0 + SPAN) × [z0, z0 + SPAN)} of the surface, row by row (z). */
    public static int[] region(Ground ground, double x0, double z0) {
        int[] out = new int[REGION * REGION];
        double cell = (double) SPAN / REGION;
        java.util.stream.IntStream.range(0, REGION).parallel().forEach(j -> { for (int i = 0; i < REGION; i++) {
            int r = 0, g = 0, b = 0;
            for (int k = 0; k < 4; k++) {
                int c = ground.color(x0 + (i + .25 + (k & 1) * .5) * cell, z0 + (j + .25 + (k >> 1) * .5) * cell, cell / 2);
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
            out[j * REGION + i] = ((r / 4) << 16) | ((g / 4) << 8) | (b / 4);
        }});
        return out;
    }

    /**
     * One texel: the mean over its area. Near the map's poles a texel is long and thin (its row is short), so it takes
     * more samples along its length; one sample per texel would alias and draw streaks there.
     */
    public static int texel(SpaceFrames.Planet unturned, Ground ground, int i, int j, int width, int height) {
        boolean disc = unturned.mapping() == SpaceFrames.Mapping.DISC;   // a disc texel narrows towards the map poles; an equirectangular one keeps its width in x
        double across = Math.max(.02, Math.cos(-Math.PI / 2 + (j + .5) * Math.PI / height)), footprint = 2 * unturned.size() / width * (disc ? across : 1) / 2;   // blocks per sample, along the row
        int along = disc ? (int) Math.min(8, Math.max(2, Math.ceil(2 / across))) : 3, r = 0, g = 0, b = 0;   // (the polar rows are evened out afterwards anyway)
        for (int k = 0; k < along * 2; k++) {
            double[] at = surface(unturned, i + .25 + (k & 1) * .5, j + ((k >> 1) + .5) / along, width, height);
            int c = ground.color(at[0], at[1], footprint);
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
        }
        int n = along * 2;
        return ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    /** The ground of a landable planet, or null when it has no map (gas giants, asteroid fields, destroyed worlds). */
    public static Ground ground(MinecraftServer server, GalaxyCatalog.Ref ref) {
        var data = GalaxyData.get(server);
        if (data.destroyed(ref)) return null;
        var body = GalaxyCatalog.body(server.overworld().getSeed(), data.salt(), ref);
        if (!body.kind().landable() || body.kind() == GalaxyCatalog.Kind.ASTEROIDS) return null;
        var legacy = PlanetRuntime.legacy(ref);
        if (legacy != null) {
            ServerLevel level = server.getLevel(legacy);
            return level == null ? null : vanilla(level);
        }
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        var biome = biomes.getHolder(ResourceKey.create(Registries.BIOME, new ResourceLocation(DBZenith.MOD_ID, "planet_" + body.kind().name().toLowerCase(Locale.ROOT))));
        return procedural(new PlanetTerrain(body.seed(), body.kind()), biome.map(Holder::value).orElse(null));
    }

    // ---- procedural worlds: the terrain function itself ----

    public static Ground procedural(PlanetTerrain t, Biome biome) {
        int grass = biome == null ? 0x5CBA8B : biome.getSpecialEffects().getGrassColorOverride().orElse(0x5CBA8B);
        int foliage = biome == null ? grass : biome.getSpecialEffects().getFoliageColorOverride().orElse(grass);
        int water = biome == null ? 0x3F76E4 : biome.getWaterColor();
        boolean lava = t.kind == GalaxyCatalog.Kind.VOLCANIC, trees = t.kind == GalaxyCatalog.Kind.JUNGLE || t.kind == GalaxyCatalog.Kind.TEMPERATE || t.kind == GalaxyCatalog.Kind.TRAINING;
        int sea = t.sea();
        return (x, z, footprint) -> {
            double s = Math.max(2, Math.min(48, footprint / 2));
            int h = average(t, x, z, s), color;
            if (h < sea) {
                double depth = sea - h;
                if (lava) color = mix(0xFFB04A, 0xC0360F, depth / 8);
                else color = mix(mix(water, block(t.kind == GalaxyCatalog.Kind.TOXIC_SWAMP ? Blocks.CLAY : Blocks.SAND, grass), .45 * Math.max(0, 1 - depth / 5)), 0x0D2448, Math.min(.8, depth / 34));
                return color;
            }
            color = block(t.top().getBlock(), grass);
            if (trees) color = mix(color, scale(foliage, .55), t.kind == GalaxyCatalog.Kind.JUNGLE ? .38 : .16);
            if (t.kind == GalaxyCatalog.Kind.CRYSTAL) color = mix(color, 0x9A6EDB, .1);
            if (t.kind == GalaxyCatalog.Kind.ICE && h > 120) color = mix(color, 0xFFFFFF, .5);
            if (h == sea && sea > -64) color = block(lava ? Blocks.MAGMA_BLOCK : Blocks.SAND, grass);
            double slope = (t.height((int) Math.floor(x - s), (int) Math.floor(z - s)) - t.height((int) Math.floor(x + s), (int) Math.floor(z + s))) / (2 * s);
            return scale(color, Math.max(.7, Math.min(1.25, 1 + slope * .9 + (h - 80) * .0015)));   // relief lit from the north-west, heights a touch brighter
        };
    }

    private static int average(PlanetTerrain t, double x, double z, double s) {
        int a = t.height((int) Math.floor(x - s * .5), (int) Math.floor(z - s * .5)), b = t.height((int) Math.floor(x + s * .5), (int) Math.floor(z - s * .5));
        int c = t.height((int) Math.floor(x - s * .5), (int) Math.floor(z + s * .5)), d = t.height((int) Math.floor(x + s * .5), (int) Math.floor(z + s * .5));
        int[] v = {a, b, c, d};
        Arrays.sort(v);
        return (v[1] + v[2]) / 2;   // the middle two: a lone spike or pit doesn't decide a texel
    }

    /** A block's colour as it looks from far above (its texture's average; grass and leaves tinted). */
    static int block(net.minecraft.world.level.block.Block b, int grass) {
        if (b == Blocks.GRASS_BLOCK) return scale(grass, .78);
        if (b == Blocks.MOSS_BLOCK) return 0x5A7533;
        if (b == Blocks.RED_SAND) return 0xBE6621;
        if (b == Blocks.SAND) return 0xDBCFA0;
        if (b == Blocks.SNOW_BLOCK || b == Blocks.SNOW) return 0xEEF6F8;
        if (b == Blocks.BASALT) return 0x4F4F55;
        if (b == Blocks.ANDESITE) return 0x888A8A;
        if (b == Blocks.CALCITE) return 0xDCDDD8;
        if (b == Blocks.MUD) return 0x3D3A3E;
        if (b == Blocks.CLAY) return 0xA0A6B3;
        if (b == Blocks.MAGMA_BLOCK) return 0x8E3F1C;
        if (b == Blocks.WHITE_STAINED_GLASS) return 0xF4F4F4;
        return 0x7D7D7D;
    }

    // ---- Earth, Namek and the flat worlds: their own biome and climate noise ----

    public static Ground vanilla(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        var sampler = level.getChunkSource().randomState().sampler();
        var source = generator.getBiomeSource();
        if (generator instanceof FlatLevelSource) {
            int flat = land(source.getNoiseBiome(0, 16, 0, sampler), 0);
            return (x, z, footprint) -> flat;
        }
        boolean fixed = source.possibleBiomes().size() == 1;   // Namek: one biome, oceans only from the climate
        var multi = source instanceof net.minecraft.world.level.biome.MultiNoiseBiomeSource m ? m : null;
        return (x, z, footprint) -> {
            int qx = (int) Math.floor(x) >> 2, qz = (int) Math.floor(z) >> 2;
            var climate = sampler.sample(qx, 16, qz);
            double continent = Climate.unquantizeCoord(climate.continentalness());
            double surface = 128 * (Climate.unquantizeCoord(climate.depth()) + .5);   // vanilla's depth is offset + 1 - y/128 (here at y 64): zero at the ground
            int qy = (int) Math.floor(Math.max(-60, Math.min(300, surface + 4))) >> 2;
            Holder<Biome> holder = multi != null ? multi.getNoiseBiome(at(climate, qy)) : source.getNoiseBiome(qx, qy, qz, sampler);   // the biome at the ground, not under it (mountains would read as caves); one climate sample serves both
            if (isCave(holder)) holder = multi != null ? multi.getNoiseBiome(at(climate, qy + 6)) : source.getNoiseBiome(qx, qy + 6, qz, sampler);
            Biome biome = holder.value();
            int water = biome.getWaterColor();
            boolean ocean = fixed ? surface < 61 : holder.is(BiomeTags.IS_OCEAN);
            if (ocean) {
                double depth = Math.max(0, Math.min(1, (61 - surface) / 45));
                int color = mix(mix(water, 0x2B4F9C, .45), 0x0A1E3E, depth * .75);
                if (biome.getBaseTemperature() < .1) color = mix(color, 0xC9DCEF, .55);   // frozen seas
                return color;
            }
            boolean river = holder.is(BiomeTags.IS_RIVER);   // a river is a few blocks wide: from orbit it is its banks, a little bluer
            if (river) for (int[] o : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                var bank = source.getNoiseBiome(qx + o[0], qy, qz + o[1], sampler);
                if (!bank.is(BiomeTags.IS_RIVER) && !bank.is(BiomeTags.IS_OCEAN)) { holder = bank; biome = bank.value(); break; }
            }
            if (holder.is(BiomeTags.IS_RIVER)) return biome.getBaseTemperature() < .1 ? 0xB8CDE6 : mix(water, 0x2B4F9C, .35);
            if (fixed && surface < 65 || holder.is(BiomeTags.IS_BEACH)) return biome.getBaseTemperature() < .1 ? 0xE8EEF2 : 0xD8CC9C;
            double height = Math.max(0, Math.min(1.3, (surface - 63) / 150));
            int color = land(holder, height);
            if (fixed && height > .55) color = mix(color, 0x8C8C8C, Math.min(.6, (height - .55) * 1.5));
            if (river) color = mix(color, water, .2);
            return scale(color, Math.max(.82, Math.min(1.2, .9 + height * .3)));   // higher ground a touch brighter
        };
    }

    /** The climate at quart height {@code qy} from one sampled at y 64: only its depth changes with height (vanilla's depth falls 1/128 a block). */
    private static Climate.TargetPoint at(Climate.TargetPoint c, int qy) {
        return new Climate.TargetPoint(c.temperature(), c.humidity(), c.continentalness(), c.erosion(), Climate.quantizeCoord((float) (Climate.unquantizeCoord(c.depth()) - (qy * 4 - 64) / 128d)), c.weirdness());
    }

    private static boolean isCave(Holder<Biome> b) {
        return b.unwrapKey().map(k -> k.location().getPath().endsWith("_caves") || k.location().getPath().equals("deep_dark")).orElse(false);
    }

    /** Land colour of a biome seen from above: its grass (or the vanilla grass colour for its climate), sand, stone or snow. */
    static int land(Holder<Biome> holder, double height) {
        var biome = holder.value();
        var effects = biome.getSpecialEffects();
        float temperature = biome.getBaseTemperature(), downfall = biome.getModifiedClimateSettings().downfall();
        int grass = effects.getGrassColorOverride().orElseGet(() -> grassColor(temperature, downfall));
        if (effects.getGrassColorModifier() == BiomeSpecialEffects.GrassColorModifier.SWAMP) grass = 0x6A7039;
        int foliage = effects.getFoliageColorOverride().orElse(grass);
        if (holder.is(BiomeTags.IS_BADLANDS)) return 0xA9552E;
        if (holder.is(Tags.Biomes.IS_DESERT)) return 0xDBCFA0;
        if (holder.is(Tags.Biomes.IS_MUSHROOM)) return 0x76687A;
        if (holder.unwrapKey().map(k -> k.location().getPath().equals("stony_peaks")).orElse(false)) return 0x8E8E8E;
        if (temperature < .15f) return height > .6 ? 0xF4F8FA : mix(0xEEF4F7, scale(foliage, .55), .18);   // snow
        if (effects.getGrassColorModifier() == BiomeSpecialEffects.GrassColorModifier.DARK_FOREST) return scale(foliage, .42);
        int base = scale(grass, .74);
        if (downfall >= .8f) base = mix(base, scale(foliage, .5), .4);                                   // wet: forests and jungles read darker
        else if (downfall >= .6f) base = mix(base, scale(foliage, .55), .25);
        if (height > .75) base = mix(base, 0x8C8C8C, Math.min(.7, (height - .75) * 2));                // high ground goes to stone
        return base;
    }

    /** Vanilla's grass colour map, approximated from its well-known biome colours (no client texture on the server). */
    static int grassColor(float temperature, float downfall) {
        double t = Math.max(0, Math.min(1, temperature)), d = Math.max(0, Math.min(1, downfall)) * t;
        double[][] points = {{1, 1, 0x47CD33}, {1, 0, 0xBFB755}, {.8, .32, 0x91BD59}, {.7, .56, 0x79C05A}, {.25, .2, 0x86B783}, {0, 0, 0x80B497}, {.5, .25, 0x8EB971}};
        double r = 0, g = 0, b = 0, w = 0;
        for (double[] p : points) {
            double k = 1 / (Math.pow(p[0] - t, 2) + Math.pow(p[1] - d, 2) + 1e-4);
            int c = (int) p[2];
            r += ((c >> 16) & 255) * k;
            g += ((c >> 8) & 255) * k;
            b += (c & 255) * k;
            w += k;
        }
        return ((int) (r / w) << 16) | ((int) (g / w) << 8) | (int) (b / w);
    }

    // ---- requests, cache and transport ----

    /** The map of a planet (Earth: around this anchor), computed once off the server thread and kept while it is in use. */
    public static CompletableFuture<int[]> map(MinecraftServer server, GalaxyCatalog.Ref ref, int ax, int az) {
        return cached(server, new Key(ref.key(), ax, az, 0), ref, ground -> {
            var frame = frame(server, ref, ax, az);
            return () -> render(frame, ground, WIDTH, HEIGHT);
        });
    }

    /** The regional map of a planet whose corner is ({@code x0}, {@code z0}) (surface blocks; Earth's is the same for everyone). */
    public static CompletableFuture<int[]> region(MinecraftServer server, GalaxyCatalog.Ref ref, int x0, int z0) {
        return cached(server, new Key(ref.key(), x0, z0, SPAN), ref, ground -> () -> region(ground, x0, z0));
    }

    private static CompletableFuture<int[]> cached(MinecraftServer server, Key key, GalaxyCatalog.Ref ref, java.util.function.Function<Ground, java.util.function.Supplier<int[]>> work) {
        synchronized (CACHE) {
            var cache = CACHE.computeIfAbsent(server, s -> new LinkedHashMap<>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key, CompletableFuture<int[]>> e) { return size() > 24; }
            });
            var found = cache.get(key);
            if (found != null && !found.isCompletedExceptionally()) return found;
            var ground = ground(server, ref);
            if (ground == null) return CompletableFuture.completedFuture(null);
            var job = work.apply(ground);
            var future = CompletableFuture.supplyAsync(() -> {
                long began = System.nanoTime();
                int[] map = job.get();
                DBZenith.LOGGER.info("[planet-maps] {} {} drawn in {} ms", key.ref(), key.span() == 0 ? "map" : "region " + key.ax() + "," + key.az(), (System.nanoTime() - began) / 1_000_000);
                return map;
            }, WORKER);
            cache.put(key, future);
            return future;
        }
    }

    /** The unturned frame a map is drawn in (Earth's disc around the anchor, every other world its whole square). */
    public static SpaceFrames.Planet frame(MinecraftServer server, GalaxyCatalog.Ref ref, double ax, double az) {
        var body = GalaxyCatalog.body(server.overworld().getSeed(), GalaxyData.get(server).salt(), ref);
        return SpaceFrames.planet(body, ref, ax, az, SpaceFrames.Spin.IDENTITY);
    }

    /** Earth's anchor for a player, rounded to {@link #ANCHOR_STEP}; 0, 0 for every other planet. */
    public static int[] anchor(ServerPlayer p, GalaxyCatalog.Ref ref, GalaxyCatalog.Body body) {
        if (!SpaceFrames.earth(body, ref)) return new int[]{0, 0};
        var f = SpaceRuntime.frame(p, ref, body);
        return new int[]{(int) Math.round(f.cx() / ANCHOR_STEP) * ANCHOR_STEP, (int) Math.round(f.cz() / ANCHOR_STEP) * ANCHOR_STEP};
    }

    /**
     * A client asks for a planet's map ({@code span} 0) or a regional map ({@code span} = {@link #SPAN}, its corner on the
     * {@link #REGION_STEP} grid): only planets of the system it is in, at most a dozen every ten seconds.
     */
    public static void request(ServerPlayer p, String system, int index, int x0, int z0, int span) {
        long now = p.server.getTickCount();
        synchronized (REQUESTS) {
            long[] window = REQUESTS.computeIfAbsent(p.getUUID(), k -> new long[]{now, 0});
            if (now - window[0] > 200) { window[0] = now; window[1] = 0; }
            if (++window[1] > 12) return;
        }
        GalaxyCatalog.Id id;
        try { id = GalaxyCatalog.Id.parse(system); } catch (IllegalArgumentException e) { return; }
        if (!id.equals(GalaxyData.current(p))) return;
        var spec = GalaxyCatalog.system(p.server.overworld().getSeed(), GalaxyData.get(p.server).salt(), id);
        if (index < 0 || index >= spec.planets().size()) return;
        var ref = new GalaxyCatalog.Ref(id, index);
        boolean regional = span == SPAN;
        if (span != 0 && !regional) return;
        if (regional && (Math.floorMod(x0, REGION_STEP) != 0 || Math.floorMod(z0, REGION_STEP) != 0 || Math.abs(x0) > 30_000_000 || Math.abs(z0) > 30_000_000)) return;
        int[] at = regional ? new int[]{x0, z0} : anchor(p, ref, spec.planets().get(index));
        var uuid = p.getUUID();
        var server = p.server;
        (regional ? region(server, ref, at[0], at[1]) : map(server, ref, at[0], at[1])).thenAccept(colors -> {
            if (colors == null) return;
            byte[] packed = pack(colors);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                if (player != null) ModNetwork.sendTo(player, new PlanetMapPacket(system, index, at[0], at[1], span, regional ? REGION : WIDTH, regional ? REGION : HEIGHT, packed));
            });
        });
    }

    /** RGB bytes, deflated (a 512 × 256 map is about 100–250 KB). */
    public static byte[] pack(int[] colors) {
        byte[] raw = new byte[colors.length * 3];
        for (int i = 0; i < colors.length; i++) {
            raw[i * 3] = (byte) (colors[i] >> 16);
            raw[i * 3 + 1] = (byte) (colors[i] >> 8);
            raw[i * 3 + 2] = (byte) colors[i];
        }
        var deflater = new Deflater(6);
        deflater.setInput(raw);
        deflater.finish();
        var out = new ByteArrayOutputStream(raw.length / 3);
        byte[] buffer = new byte[16384];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }

    /** The texels of a map packet (the inverse of {@link #pack}). */
    public static int[] unpack(byte[] packed, int texels) throws java.util.zip.DataFormatException {
        var inflater = new java.util.zip.Inflater();
        inflater.setInput(packed);
        byte[] raw = new byte[texels * 3];
        int n = 0;
        try {
            while (n < raw.length && !inflater.finished()) {
                int got = inflater.inflate(raw, n, raw.length - n);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                n += got;
            }
        } finally {
            inflater.end();
        }
        if (n != raw.length) throw new java.util.zip.DataFormatException("short planet map");
        int[] out = new int[texels];
        for (int i = 0; i < texels; i++) out[i] = ((raw[i * 3] & 255) << 16) | ((raw[i * 3 + 1] & 255) << 8) | (raw[i * 3 + 2] & 255);
        return out;
    }

    /**
     * Vanilla's clouds seen from above: the share of cloud cells over {@code [u0,u1) × [v0,v1)} (cell units) of the
     * endlessly tiling {@code n × n} cloud texture, from its summed-area table {@code t} ({@code (n+1)²} entries).
     * A surface point (x, z) is in cell {@code ((x + 0.03 × ticks) / 12, z / 12 + 0.33)}, as vanilla draws them.
     */
    public static double cover(int[] t, int n, double u0, double v0, double u1, double v1) {
        int ax = (int) Math.floor(u0), az = (int) Math.floor(v0), bx = Math.max(ax + 1, (int) Math.floor(u1)), bz = Math.max(az + 1, (int) Math.floor(v1));
        double cells = prefix(t, n, bx, bz) - prefix(t, n, ax, bz) - prefix(t, n, bx, az) + prefix(t, n, ax, az);
        return cells / ((double) (bx - ax) * (bz - az));
    }

    private static double prefix(int[] t, int n, int x, int z) {   // cloud cells in [0,x) × [0,z) of the tiling
        int tx = Math.floorDiv(x, n), tz = Math.floorDiv(z, n), rx = x - tx * n, rz = z - tz * n;
        double whole = t[n * (n + 1) + n];
        return whole * tx * tz + (double) t[n * (n + 1) + rx] * tz + (double) t[rz * (n + 1) + n] * tx + t[rz * (n + 1) + rx];
    }

    /** The summed-area table of a cloud mask ({@code cloud[z * n + x]}). */
    public static int[] cloudTable(boolean[] cloud, int n) {
        int[] t = new int[(n + 1) * (n + 1)];
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++)
            t[(z + 1) * (n + 1) + x + 1] = (cloud[z * n + x] ? 1 : 0) + t[z * (n + 1) + x + 1] + t[(z + 1) * (n + 1) + x] - t[z * (n + 1) + x];
        return t;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        synchronized (REQUESTS) { REQUESTS.remove(e.getEntity().getUUID()); }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
        synchronized (CACHE) { CACHE.remove(e.getServer()); }
    }


    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        return ((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) << 16) | ((int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) << 8) | (int) ((a & 255) * (1 - t) + (b & 255) * t);
    }

    static int scale(int c, double k) {
        return (Math.min(255, (int) (((c >> 16) & 255) * k)) << 16) | (Math.min(255, (int) (((c >> 8) & 255) * k)) << 8) | Math.min(255, (int) ((c & 255) * k));
    }
}
