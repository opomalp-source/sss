package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.galaxy.planet.PlanetTerrain;
import com.dbzenith.galaxy.space.PlanetMaps;
import com.dbzenith.galaxy.space.SpaceFrames;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The space rework, phase 4: planet maps drawn from the ground itself, laid on the sphere exactly where the renderer
 * puts each surface point, and vanilla's clouds seen from above.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetMapTests {
    private PlanetMapTests() {}

    private static final Vec3 CENTER = new Vec3(12000, 128, 0);

    /** The texel the sphere renderer shows a surface point in (the inverse of {@link PlanetMaps#surface}). */
    private static int[] texel(SpaceFrames.Planet f, double x, double z, int width, int height) {
        Vec3 nb = SpaceFrames.bodyNormal(f, x, z);
        double lon = Math.atan2(-nb.x, -nb.y), lat = Math.asin(Math.max(-1, Math.min(1, nb.z)));
        if (lon < 0) lon += Math.PI * 2;
        return new int[]{Math.min(width - 1, (int) (lon / (Math.PI * 2) * width)), Math.min(height - 1, (int) ((lat + Math.PI / 2) / Math.PI * height))};
    }

    @GameTest(template = "empty")
    public static void whatYouSeeFromOrbitIsWhereYouLand(GameTestHelper h) {
        int w = 512, ht = 256;
        for (var f : new SpaceFrames.Planet[]{SpaceFrames.planet(CENTER, 2048, false, 0, 0, SpaceFrames.Spin.IDENTITY), SpaceFrames.planet(CENTER, 6000, true, 1300, -700, SpaceFrames.Spin.IDENTITY)}) {
            double footprint = 2 * f.size() / w;
            for (double[] p : new double[][]{{0, 0}, {900, -400}, {-1500, 1200}, {1700, 300}, {f.cx() + 300, f.cz() - 9000}}) {
                double x = f.mapping() == SpaceFrames.Mapping.DISC ? p[0] + f.cx() : p[0], z = f.mapping() == SpaceFrames.Mapping.DISC ? p[1] + f.cz() : p[1];
                if (f.mapping() == SpaceFrames.Mapping.EQUIRECT && (Math.abs(x) > f.size() * .9 || Math.abs(z) > f.size() * .9)) continue;   // inside the world
                int[] t = texel(f, x, z, w, ht);
                double[] shown = PlanetMaps.surface(f, t[0], t[1], w, ht);
                h.assertTrue(Math.hypot(shown[0] - x, shown[1] - z) < footprint * 1.5, "texel " + t[0] + "," + t[1] + " shows " + (int) shown[0] + "," + (int) shown[1] + " for " + (int) x + "," + (int) z);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void aProceduralMapShowsItsOwnSeas(GameTestHelper h) {
        var biome = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).get(new ResourceLocation(DBZenith.MOD_ID, "planet_temperate"));
        var terrain = new PlanetTerrain(424242, GalaxyCatalog.Kind.TEMPERATE);
        var ground = PlanetMaps.procedural(terrain, biome);
        var f = SpaceFrames.planet(CENTER, 2048, false, 0, 0, SpaceFrames.Spin.IDENTITY);
        int w = 128, ht = 64, agree = 0, water = 0, total = 0;
        int[] map = PlanetMaps.render(f, ground, w, ht);
        for (int j = 4; j < ht - 4; j++) for (int i = 0; i < w; i++) {
            double[] at = PlanetMaps.surface(f, i, j, w, ht);
            boolean sea = terrain.height((int) Math.floor(at[0]), (int) Math.floor(at[1])) < terrain.sea();
            int c = map[j * w + i];
            boolean blue = (c & 255) > ((c >> 8) & 255) + 10;   // water reads bluer than green; this world's grass is the other way round
            if (sea == blue) agree++;
            if (sea) water++;
            total++;
        }
        h.assertTrue(water > total / 50 && water < total * 49 / 50, "this world has both sea and land: " + water + "/" + total);
        h.assertTrue(agree > total * .9, "seas on the map are seas on the ground: " + agree + "/" + total);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void noiseWorldMapsComeFromTheirOwnClimate(GameTestHelper h) {
        // the test server's overworld is flat (one colour); Namek is a real noise world (vanilla terrain, one biome)
        var server = h.getLevel().getServer();
        var namek = server.getLevel(com.dbzenith.world.Planet.NAMEK.dimension());
        h.assertTrue(namek != null, "Namek is loaded");
        var flat = PlanetMaps.render(PlanetMaps.frame(server, new GalaxyCatalog.Ref(GalaxyCatalog.EARTH, 0), 0, 0), PlanetMaps.vanilla(server.overworld()), 16, 8);
        if (server.overworld().getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource)
            h.assertTrue(java.util.Arrays.stream(flat).distinct().count() == 1, "a flat world is one colour from orbit");
        var f = PlanetMaps.frame(server, new GalaxyCatalog.Ref(GalaxyCatalog.NAMEK, 0), 0, 0);
        long began = System.nanoTime();
        int[] map = PlanetMaps.render(f, PlanetMaps.vanilla(namek), 64, 32);
        double ms = (System.nanoTime() - began) / 1e6;
        var ground = PlanetMaps.vanilla(namek);
        int sea = 0, land = 0;
        for (int j = 0; j < 32; j++) for (int i = 0; i < 64; i++) {
            double[] at = PlanetMaps.surface(f, i, j, 64, 32);
            var climate = namek.getChunkSource().randomState().sampler().sample((int) Math.floor(at[0]) >> 2, 16, (int) Math.floor(at[1]) >> 2);
            if (128 * (net.minecraft.world.level.biome.Climate.unquantizeCoord(climate.depth()) + .5) < 61) sea++; else land++;
            if (Math.cos(-Math.PI / 2 + (j + .5) * Math.PI / 32) > .45)   // (rows near the poles are evened out along their length)
                h.assertTrue(map[j * 64 + i] == PlanetMaps.texel(f, ground, i, j, 64, 32), "each texel is its own ground");
        }
        h.assertTrue(sea > 0 && land > 0, "Namek's map has its seas and its land: " + sea + " / " + land);
        h.assertTrue(ms < 4000, "a 64 x 32 noise-world map draws quickly (" + (int) ms + " ms; full maps run off-thread)");
        DBZenith.LOGGER.info("[planet-maps] Namek 64x32 in {} ms", (int) ms);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mapsTravelSmallAndWhole(GameTestHelper h) throws Exception {
        var terrain = new PlanetTerrain(77, GalaxyCatalog.Kind.TEMPERATE);
        int[] map = PlanetMaps.render(SpaceFrames.planet(CENTER, 4096, false, 0, 0, SpaceFrames.Spin.IDENTITY), PlanetMaps.procedural(terrain, null), PlanetMaps.WIDTH, PlanetMaps.HEIGHT);
        byte[] packed = PlanetMaps.pack(map);
        h.assertTrue(packed.length < 400_000, "a full map is under 400 KB on the wire: " + packed.length);
        h.assertTrue(java.util.Arrays.equals(PlanetMaps.unpack(packed, map.length), map), "the map arrives texel for texel");
        var server = h.getLevel().getServer();
        var data = GalaxyData.get(server);
        for (int i = 0; i < 40; i++) {
            var ref = new GalaxyCatalog.Ref(new GalaxyCatalog.Id(i % 5 - 2, 1, i / 5 - 4, i % 3), 0);
            var body = GalaxyCatalog.body(server.overworld().getSeed(), data.salt(), ref);
            if (body.kind() == GalaxyCatalog.Kind.GAS_GIANT) h.assertTrue(PlanetMaps.ground(server, ref) == null, "a gas giant has no ground to map");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cloudsFromAboveAreVanillasCloudCells(GameTestHelper h) {
        int n = 8;
        boolean[] mask = new boolean[n * n];
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) mask[z * n + x] = (x / 2 + z / 2) % 2 == 0;   // a 2 x 2 checkerboard
        int[] t = PlanetMaps.cloudTable(mask, n);
        h.assertTrue(Math.abs(PlanetMaps.cover(t, n, 0, 0, 8, 8) - .5) < 1e-9, "half the tile is cloud");
        h.assertTrue(PlanetMaps.cover(t, n, 0, 0, 1, 1) == 1 && PlanetMaps.cover(t, n, 2, 0, 3, 1) == 0, "single cells read as they are");
        h.assertTrue(PlanetMaps.cover(t, n, -8, -8, -7, -7) == 1 && PlanetMaps.cover(t, n, 42, 0, 43, 1) == 0, "the texture tiles endlessly, both ways");
        h.assertTrue(Math.abs(PlanetMaps.cover(t, n, -13, 5, 27, 45) - .5) < 1e-9, "large areas across many tiles average out");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theTerminatorIsWhereTheGroundsDayEnds(GameTestHelper h) {
        Vec3 star = new Vec3(0, 128, 0);
        for (var spin : new SpaceFrames.Spin[]{SpaceFrames.spin(17, .31), SpaceFrames.earthSpin(.62, star.subtract(CENTER))}) {
            var f = new SpaceFrames.Planet(CENTER, 400, SpaceFrames.Mapping.EQUIRECT, 4096, 0, 0, spin);
            Vec3 light = star.subtract(CENTER).normalize();
            for (int x = -4000; x <= 4000; x += 800) for (int z = -3600; z <= 3600; z += 900) {
                var ground = SpaceFrames.basis(f, x, z);
                double sphere = SpaceFrames.normal(f, x, z).dot(light), sky = SpaceFrames.toSurfaceVector(ground, light).y;
                h.assertTrue(Math.abs(sphere - sky) < 1e-6, "the lit side of the sphere is where the Sun is up on the ground");
            }
        }
        h.succeed();
    }
}
