package com.dbzenith.world.landmark;

import com.dbzenith.living.Poi;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.world.arch.CivilizationBlocks;
import com.dbzenith.world.settle.SettlementPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A traditional village (canon-inspired, docs/WORLD_STRUCTURES.md): the small country places of Dragon Ball's Earth.
 * A levelled round site; a plaza with a roofed well in the middle, four dirt roads out and a ring road; eight plots
 * round the ring (houses, a shop with market stalls, an inn, now and then a little dojo), each door toward the
 * middle; fields between the roads further out, lanterns along them. Three looks by biome: round stucco huts under
 * tiled domes (temperate land), log cabins in the snow, flat-roofed adobe in the desert. A settlement of the living
 * world: farmers, a merchant, a cook, citizens, a guard, sometimes a master and students; its own name.
 */
public final class Village extends SettlementPlan {
    static final int FLAT = 42, BLEND = 14, RING = 21, FIELD_IN = 30, FIELD_OUT = 40;

    enum Style { EARTH, SNOW, DESERT }

    enum Kind { HOUSE, SHOP, INN, DOJO }

    record Plot(int px, int pz, Direction front, Kind kind, int variant) {}

    final int y0;
    final Style style;
    final String name;
    final List<Plot> plots = new ArrayList<>();

    static final String[] EARTH_NAMES = {"Barley Village", "Paprika Village", "Millet Village", "Fennel Village", "Radish Village", "Chestnut Village",
            "Sorrel Village", "Turnip Village", "Cumin Village", "Mochi Village", "Soba Village", "Parsnip Village", "Leek Village", "Lentil Village",
            "Mustard Village", "Oregano Village", "Plum Village", "Quince Village", "Rhubarb Village", "Shallot Village", "Tarragon Village",
            "Udon Village", "Walnut Village", "Yam Village", "Basil Village", "Celery Village", "Dill Village", "Endive Village", "Garlic Village",
            "Hazel Village", "Kombu Village", "Lotus Root Village", "Marjoram Village", "Nutmeg Village", "Okra Village", "Peppercorn Village"};
    static final String[] SNOW_NAMES = {"Juniper Village", "Rye Village", "Kale Village", "Pinenut Village", "Mulled Village", "Cranberry Village",
            "Lingonberry Village", "Caraway Village", "Buckwheat Village", "Cloudberry Village", "Sauerkraut Village", "Horseradish Village",
            "Spruce Tip Village", "Oatmeal Village", "Chicory Village", "Beetroot Village", "Bilberry Village", "Clove Village"};
    static final String[] DESERT_NAMES = {"Sesame Village", "Date Village", "Saffron Village", "Sumac Village", "Fig Village", "Couscous Village",
            "Tahini Village", "Cardamom Village", "Pistachio Village", "Za'atar Village", "Harissa Village", "Apricot Village", "Bulgur Village",
            "Carob Village", "Tamarind Village", "Anise Village", "Pomegranate Village", "Coriander Village"};

    private Village(LandmarkSites.Site site, int y0, Style style) {
        super(site);
        this.y0 = y0;
        this.style = style;
        String[] names = style == Style.SNOW ? SNOW_NAMES : style == Style.DESERT ? DESERT_NAMES : EARTH_NAMES;
        this.name = names[(int) Math.floorMod(seed >>> 7, names.length)];
        boolean dojo = Math.floorMod(seed >>> 13, 4) == 0;
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int px = x + (int) Math.round(Math.cos(a) * RING), pz = z + (int) Math.round(Math.sin(a) * RING);
            double tx = -Math.cos(a), tz = -Math.sin(a);                                        // toward the middle
            Direction front = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? Direction.EAST : Direction.WEST) : (tz > 0 ? Direction.SOUTH : Direction.NORTH);
            Kind kind = k == 0 ? Kind.SHOP : k == 4 ? Kind.INN : k == 2 && dojo ? Kind.DOJO : Kind.HOUSE;
            plots.add(new Plot(px, pz, front, kind, Canvas.hash(seed, k, 1, 0) & 7));
        }
        layOutPlaces();
    }

    /** A village at a chosen site, ground level and look ("earth", "snow", "desert"), without the terrain checks (tests). */
    public static Village at(LandmarkSites.Site site, int y0, String style) {
        return new Village(site, y0, Style.valueOf(style.toUpperCase(java.util.Locale.ROOT)));
    }

    /** A village needs dry, fairly level ground; its look follows the land. */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[25];
        int n = 0, wet = 0;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int px = s.x() + i * 20, pz = s.z() + j * 20, h = t.height(px, pz);
                if (t.wetOver(px, pz, h) && ++wet > 1) return null;
                hs[n++] = h;
            }
        Arrays.sort(hs);
        if (hs[21] - hs[3] > 8 || hs[12] <= t.seaLevel()) return null;
        var biome = t.biome(s.x(), hs[12], s.z());
        Style style = biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA) ? Style.SNOW : biome.is(Biomes.DESERT) ? Style.DESERT : Style.EARTH;
        return new Village(s, hs[12], style);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String style() {
        return "village_" + style.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public int reach() {
        return FLAT + BLEND;
    }

    @Override
    public BlockPos centre() {
        return new BlockPos(x + 8, y0 + 1, z);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + FLAT - 2);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.hypot(wx - x, wz - z) <= FLAT + BLEND ? y0 : Integer.MAX_VALUE;
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    WestCity.F frame(Plot p) {
        return new WestCity.F(p.px, p.pz, y0, p.front);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void build(Canvas c) {
        int R = FLAT + BLEND;
        if (!c.intersects(x - R, z - R, x + R, z + R)) return;
        ground(c);
        if (style != Style.SNOW) fields(c);
        paths(c);
        plaza(c);
        trees(c);
        for (Plot p : plots) {
            if (!c.intersects(p.px - 9, p.pz - 9, p.px + 9, p.pz + 9)) continue;
            switch (p.kind) {
                case HOUSE -> house(c, frame(p), p.variant);
                case SHOP -> shop(c, frame(p));
                case INN -> inn(c, frame(p));
                case DOJO -> dojo(c, frame(p));
            }
        }
    }

    void ground(Canvas c) {
        BlockState top = style == Style.DESERT ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState fill = style == Style.DESERT ? Blocks.SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                double d = Math.hypot(wx - x, wz - z);
                if (d > FLAT + BLEND) continue;
                int target = y0;
                if (d > FLAT) {
                    double f = (d - FLAT) / BLEND;
                    f = f * f * (3 - 2 * f);
                    target = (int) Math.round(y0 + (c.surface(wx, wz) - y0) * f);
                }
                c.ground(wx, wz, target, top, fill, d > FLAT ? 10 : 24);
                if (style == Style.SNOW) c.set(wx, target + 1, wz, Blocks.SNOW.defaultBlockState());          // snow over all of it, the eased edge too
            }
    }

    /** Whether a point is on a road: the four out from the plaza, or the ring. */
    boolean road(int dx, int dz) {
        double d = Math.hypot(dx, dz);
        if (d > FLAT) return false;
        if (d >= 6 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) return true;
        return d >= RING - 7.5 && d <= RING - 5.5;
    }

    void paths(Canvas c) {
        BlockState path = style == Style.DESERT ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                int dx = wx - x, dz = wz - z;
                if (!road(dx, dz)) continue;
                c.set(wx, y0, wz, path);
                c.set(wx, y0 + 1, wz, Canvas.AIR);
            }
        for (int r = 10; r <= FLAT - 2; r += 8)                                                // lanterns along the roads
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int lx = x + d[0] * r + d[1] * 2, lz = z + d[1] * r + d[0] * 2;
                if (Math.abs(r - (RING - 6.5)) < 3) continue;
                c.column(lx, lz, y0 + 1, y0 + 2, style == Style.SNOW ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
                c.set(lx, y0 + 3, lz, s("village_lantern"));
            }
    }

    /** The plaza and its well: stone flags, a ring of stone round the water, four posts and a little tiled roof. */
    void plaza(Canvas c) {
        if (!c.intersects(x - 8, z - 8, x + 8, z + 8)) return;
        BlockState flag = style == Style.DESERT ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        Shapes.disc(c, x, z, y0, 6.5, flag);
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) if (Shapes.inDisc(dx, dz, 6.5)) c.set(x + dx, y0 + 1, z + dz, Canvas.AIR);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                c.set(x + dx, y0 + 1, z + dz, dx == 0 && dz == 0 ? Canvas.AIR : Blocks.STONE_BRICK_WALL.defaultBlockState());
                c.column(x + dx, z + dz, y0 - 4, y0, dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
        for (int[] p : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) c.column(x + p[0], z + p[1], y0 + 2, y0 + 3, Blocks.OAK_FENCE.defaultBlockState());
        String roof = style == Style.SNOW ? "village_roof_blue" : style == Style.DESERT ? "village_roof_orange" : "village_roof_red";
        c.box(x - 1, y0 + 4, z - 1, x + 1, y0 + 4, z + 1, Canvas.slab(s(roof + "_slab"), false));
        for (int[] p : new int[][]{{-4, -4}, {4, 4}, {-4, 4}, {4, -4}})
            c.set(x + p[0], y0 + 1, z + p[1], Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), p[0] < 0 ? Direction.WEST : Direction.EAST, false));   // benches
    }

    /** The fields between the roads: rows of crops with water channels, fenced round. */
    void fields(Canvas c) {
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                int dx = wx - x, dz = wz - z;
                double d = Math.hypot(dx, dz);
                if (d < FIELD_IN || d > FIELD_OUT + 1.5 || Math.abs(dx) <= 3 || Math.abs(dz) <= 3) continue;
                if (d > FIELD_OUT) {
                    c.set(wx, y0 + 1, wz, style == Style.DESERT ? Blocks.SANDSTONE_WALL.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState());
                    continue;
                }
                if (Math.floorMod(dx + dz * 3, 6) == 0) {
                    c.set(wx, y0, wz, Blocks.WATER.defaultBlockState());
                    continue;
                }
                int quad = (dx > 0 ? 1 : 0) + (dz > 0 ? 2 : 0);
                Block crop = crops[(quad + (int) (seed & 3)) % 4];
                c.set(wx, y0, wz, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                int age = Canvas.hash(seed, wx, 0, wz) & 7;
                BlockState cs = crop.defaultBlockState();
                if (crop instanceof CropBlock cb) cs = cb.getStateForAge(Math.min(age, cb.getMaxAge()));
                c.set(wx, y0 + 1, wz, cs);
            }
    }

    /** A few trees round the plots: round broadleaves (temperate), dark firs (snow), palms (desert). */
    void trees(Canvas c) {
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.3;
            int tx = x + (int) Math.round(Math.cos(a) * 27), tz = z + (int) Math.round(Math.sin(a) * 27);
            if (!c.intersects(tx - 4, tz - 4, tx + 4, tz + 4)) continue;
            switch (style) {
                case DESERT -> Shapes.palm(c, tx, y0 + 1, tz, 7 + (k & 1) * 2, k * 5);
                case SNOW -> Shapes.cypress(c, tx, y0 + 1, tz, 7 + (k & 1));
                default -> {
                    BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    c.column(tx, tz, y0 + 1, y0 + 4, Blocks.SPRUCE_LOG.defaultBlockState());          // (spruce: the sweep leaves it be)
                    for (int dy = 3; dy <= 6; dy++) Shapes.disc(c, tx, tz, y0 + dy, dy == 6 ? 1.2 : dy == 3 ? 2.2 : 2.8, leaf);
                }
            }
        }
    }

    // ------------------------------------------------------------------ buildings

    static final String[] ROOFS = {"village_roof_red", "village_roof_blue", "village_roof_green", "village_roof_orange", "village_thatch"};

    void house(Canvas c, WestCity.F f, int variant) {
        switch (style) {
            case SNOW -> cabin(c, f, variant);
            case DESERT -> adobe(c, f, variant);
            default -> hut(c, f, variant);
        }
    }

    /** A round stucco hut under a tiled dome: round windows, a wooden door, two beds, a table, a stove. */
    void hut(Canvas c, WestCity.F f, int variant) {
        int cx = f.ox(), cz = f.oz();
        Shapes.disc(c, cx, cz, y0, 4.6, Blocks.OAK_PLANKS.defaultBlockState());
        for (int k = 1; k <= 3; k++) Shapes.ring(c, cx, cz, y0 + k, 4.6, 5.3, s((variant & 1) == 0 ? "village_plaster" : "village_plaster_white"));
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) if (dx * dx + dz * dz < 16) c.box(cx + dx, y0 + 1, cz + dz, cx + dx, y0 + 4, cz + dz, Canvas.AIR);
        for (int[] p : new int[][]{{5, 0}, {-5, 0}, {0, 5}, {3, 4}, {-3, 4}}) f.set(c, p[0], 2, p[1], s("village_window"));
        Shapes.dome(c, cx, cz, y0 + 4, 5.4, 4, 1.2, s(ROOFS[Math.floorMod(variant, ROOFS.length)]));
        door(c, f, -5, "village_door");
        interior(c, f, 3);
    }

    /** A log cabin: log walls on timber corners, small windows, a tiled hip roof under snow. */
    void cabin(Canvas c, WestCity.F f, int variant) {
        box(c, f, 4, 3, 3, "village_log", "village_timber");
        for (int[] p : new int[][]{{-2, -3}, {2, -3}, {-4, 0}, {4, 0}, {0, 3}}) f.set(c, p[0], 2, p[1], Blocks.GLASS_PANE.defaultBlockState());
        int x0 = f.wx(-5, -4), z0 = f.wz(-5, -4), x1 = f.wx(5, 4), z1 = f.wz(5, 4);
        Shapes.hipRoof(c, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), y0 + 4, variant % 2 == 0 ? "village_roof_blue" : "village_roof_red",
                1, s("village_timber"));
        door(c, f, -3, "village_door");
        interior(c, f, 2);
    }

    /** An adobe house: thick stucco walls, a flat roof with a parapet, deep small windows. */
    void adobe(Canvas c, WestCity.F f, int variant) {
        box(c, f, 4, 3, 4, "village_plaster", "village_plaster");
        f.box(c, -4, 5, -3, 4, 5, 3, s("village_plaster"));
        for (int lx = -4; lx <= 4; lx += 2) {
            f.set(c, lx, 6, -3, Canvas.slab(s("village_plaster_slab"), false));
            f.set(c, lx, 6, 3, Canvas.slab(s("village_plaster_slab"), false));
        }
        for (int[] p : new int[][]{{-2, -3}, {2, -3}, {-4, 0}, {4, 0}}) f.set(c, p[0], 3, p[1], Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
        door(c, f, -3, "village_door");
        interior(c, f, 2);
        if ((variant & 1) == 0) Shapes.palm(c, f.wx(6, 4), y0 + 1, f.wz(6, 4), 6, variant);
    }

    /** A rectangular shell: walls of {@code wall}, corners of {@code corner}, a floor; hollow inside. */
    void box(Canvas c, WestCity.F f, int hx, int hz, int h, String wall, String corner) {
        f.box(c, -hx, 0, -hz, hx, 0, hz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int dy = 1; dy <= h; dy++) {
            for (int lx = -hx; lx <= hx; lx++) {
                f.set(c, lx, dy, -hz, s(wall));
                f.set(c, lx, dy, hz, s(wall));
            }
            for (int lz = -hz; lz <= hz; lz++) {
                f.set(c, -hx, dy, lz, s(wall));
                f.set(c, hx, dy, lz, s(wall));
            }
            for (int[] p : new int[][]{{-hx, -hz}, {hx, -hz}, {-hx, hz}, {hx, hz}}) f.set(c, p[0], dy, p[1], corner.equals("village_timber")
                    ? s(corner) : s(wall));
        }
        f.box(c, -hx + 1, 1, -hz + 1, hx - 1, h, hz - 1, Canvas.AIR);
    }

    /** A home's things: two beds at the back, a table, a stove, a chest, a lantern overhead ({@code back}: the back wall's inside). */
    void interior(Canvas c, WestCity.F f, int back) {
        bed(c, f, -2, back - 1, Direction.SOUTH);
        bed(c, f, 2, back - 1, Direction.SOUTH);
        f.set(c, 0, 1, back, Blocks.CHEST.defaultBlockState());
        f.set(c, -3, 1, -1, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, f.dir(Direction.EAST)));
        f.set(c, 2, 1, -1, Blocks.OAK_FENCE.defaultBlockState());
        f.set(c, 2, 2, -1, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        f.set(c, 0, 3, 0, s("village_lantern"));
    }

    /** A shop: a counter across, shelves behind, an awning over the front and two stalls before it. */
    void shop(Canvas c, WestCity.F f) {
        String wall = style == Style.SNOW ? "village_log" : "village_plaster";
        box(c, f, 4, 3, 4, wall, style == Style.SNOW ? "village_timber" : wall);
        roofOver(c, f, 4, 3, 5);
        door(c, f, -3, "village_door");
        f.set(c, -2, 2, -3, s("village_window"));
        f.set(c, 2, 2, -3, s("village_window"));
        for (int lx = -3; lx <= 1; lx++) f.set(c, lx, 1, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int lx = -3; lx <= 3; lx++) {
            f.set(c, lx, 1, 2, Blocks.BARREL.defaultBlockState());
            f.set(c, lx, 2, 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        f.set(c, 0, 3, 0, s("village_lantern"));
        for (int lx = -4; lx <= 4; lx++) f.set(c, lx, 4, -4, Canvas.slab(s(awning() + "_slab"), false));
        for (int sx : new int[]{-3, 3}) {                                                       // stalls
            f.set(c, sx, 1, -6, Blocks.BARREL.defaultBlockState());
            f.set(c, sx, 2, -6, Blocks.OAK_FENCE.defaultBlockState());
            f.set(c, sx, 3, -6, Blocks.WHITE_WOOL.defaultBlockState());
        }
    }

    /** The inn: a bigger hall with tables, a counter and the kitchen behind. */
    void inn(Canvas c, WestCity.F f) {
        String wall = style == Style.SNOW ? "village_log" : "village_plaster";
        box(c, f, 5, 4, 4, wall, style == Style.DESERT ? wall : "village_timber");
        roofOver(c, f, 5, 4, 5);
        door(c, f, -4, "village_door");
        for (int[] p : new int[][]{{-3, -4}, {3, -4}, {-5, 0}, {5, 0}}) f.set(c, p[0], 2, p[1], s("village_window"));
        for (int tx : new int[]{-3, 3}) {
            f.set(c, tx, 1, -1, Blocks.OAK_FENCE.defaultBlockState());
            f.set(c, tx, 2, -1, Blocks.WHITE_CARPET.defaultBlockState());
            f.set(c, tx - 1, 1, -1, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), f.dir(Direction.WEST), false));
            f.set(c, tx + 1, 1, -1, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), f.dir(Direction.EAST), false));
        }
        for (int lx = -4; lx <= 1; lx++) f.set(c, lx, 1, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        f.set(c, -4, 1, 3, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, f.dir(Direction.NORTH)));
        f.set(c, -2, 1, 3, Blocks.BARREL.defaultBlockState());
        f.set(c, 0, 1, 3, Blocks.WATER_CAULDRON.defaultBlockState());
        f.set(c, 0, 3, -1, s("village_lantern"));
        f.set(c, 0, 3, 2, s("village_lantern"));
    }

    /** A small dojo: a raised floor under a tiled roof on red pillars, open at the front, punching bags. */
    void dojo(Canvas c, WestCity.F f) {
        f.box(c, -5, 0, -4, 5, 0, 4, Shapes.b("arena_tile"));
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -4; lz <= 4; lz++) {
                if (Math.abs(lx) != 5 && Math.abs(lz) != 4) continue;
                boolean pillar = Math.abs(lx) % 5 == 0 && Math.abs(lz) % 4 == 0 || lz == 4 && lx % 2 == 0;
                if (lz == -4 && !pillar) continue;
                for (int dy = 1; dy <= 3; dy++) f.set(c, lx, dy, lz, pillar ? Shapes.b("temple_red_pillar") : Shapes.b("temple_plaster"));
            }
        int x0 = f.wx(-6, -5), z0 = f.wz(-6, -5), x1 = f.wx(6, 5), z1 = f.wz(6, 5);
        Shapes.hipRoof(c, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), y0 + 4, "clay_roof_tiles", 1, Shapes.b("temple_gold"));
        f.set(c, -3, 1, 2, ModBlocks.PUNCHING_BAG.get().defaultBlockState());
        f.set(c, 3, 1, 2, ModBlocks.PUNCHING_BAG.get().defaultBlockState());
        f.set(c, 0, 1, 3, Shapes.b("banner_cloth_red"));
    }

    String awning() {
        return style == Style.SNOW ? "village_roof_blue" : style == Style.DESERT ? "village_roof_orange" : "village_roof_red";
    }

    /** A roof over a rectangular building: tiled hip (temperate, snow) or flat with a parapet (desert). */
    void roofOver(Canvas c, WestCity.F f, int hx, int hz, int y) {
        if (style == Style.DESERT) {
            f.box(c, -hx, y, -hz, hx, y, hz, s("village_plaster"));
            return;
        }
        int x0 = f.wx(-hx - 1, -hz - 1), z0 = f.wz(-hx - 1, -hz - 1), x1 = f.wx(hx + 1, hz + 1), z1 = f.wz(hx + 1, hz + 1);
        Shapes.hipRoof(c, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), y0 + y, awning(), 1, s("village_timber"));
    }

    /** A door in the front wall at local z {@code lz} (and the doorway cleared). */
    void door(Canvas c, WestCity.F f, int lz, String id) {
        f.box(c, 0, 1, lz - 1, 0, 2, lz + 1, Canvas.AIR);
        BlockState d = s(id).setValue(DoorBlock.FACING, f.dir(Direction.NORTH)).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        c.set(f.wx(0, lz), y0 + 1, f.wz(0, lz), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(f.wx(0, lz), y0 + 2, f.wz(0, lz), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        f.set(c, 0, 0, lz - 1, style == Style.DESERT ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
    }

    void bed(Canvas c, WestCity.F f, int lx, int lz, Direction headLocal) {
        Direction head = f.dir(headLocal);
        BlockState b = (style == Style.SNOW ? Blocks.LIGHT_BLUE_BED : Blocks.RED_BED).defaultBlockState().setValue(BedBlock.FACING, head);
        int wx = f.wx(lx, lz), wz = f.wz(lx, lz);
        c.set(wx - head.getStepX(), y0 + 1, wz - head.getStepZ(), b.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(wx, y0 + 1, wz, b.setValue(BedBlock.PART, BedPart.HEAD));
    }

    // ------------------------------------------------------------------ the living world

    /** How far each building's walls reach from its plot's centre (for its room on the map), and its door's local z. */
    int[] extent(Kind k) {
        return switch (k) {
            case HOUSE -> style == Style.EARTH ? new int[]{5, 5, -5} : new int[]{4, 3, -3};
            case SHOP -> new int[]{4, 3, -3};
            case INN -> new int[]{5, 4, -4};
            case DOJO -> new int[]{5, 4, -4};
        };
    }

    void layOutPlaces() {
        List<String> folk = new ArrayList<>();
        int houses = 0;
        for (Plot p : plots) {
            WestCity.F f = frame(p);
            int[] e = extent(p.kind);
            int door = way(f.wx(0, e[2] - 2), y0 + 1, f.wz(0, e[2] - 2));
            if (p.kind == Kind.HOUSE && style == Style.EARTH) roundRoom(p.px, p.pz, 5, y0, y0 + 9, door);
            else room(f, e[0], e[1], 9, door);
            switch (p.kind) {
                case HOUSE -> {
                    place(Poi.Kind.HOME, f.wx(0, 0), y0 + 1, f.wz(0, 0), "house");
                    houses++;
                }
                case SHOP -> {
                    place(Poi.Kind.SHOP, f.wx(-1, 1), y0 + 1, f.wz(-1, 1), "counter");
                    place(Poi.Kind.MARKET, f.wx(-1, -1), y0 + 1, f.wz(-1, -1), "shop floor");
                    place(Poi.Kind.MARKET, f.wx(0, -6), y0 + 1, f.wz(0, -6), "stalls");
                }
                case INN -> {
                    place(Poi.Kind.TAVERN, f.wx(-3, -2), y0 + 1, f.wz(-3, -2), "table");
                    place(Poi.Kind.TAVERN, f.wx(3, -2), y0 + 1, f.wz(3, -2), "table");
                    place(Poi.Kind.TAVERN, f.wx(0, -2), y0 + 1, f.wz(0, -2), "table");
                    place(Poi.Kind.TAVERN, f.wx(-3, 2), y0 + 1, f.wz(-3, 2), "kitchen");
                }
                case DOJO -> {
                    place(Poi.Kind.DOJO, f.wx(0, 2), y0 + 1, f.wz(0, 2), "master");
                    place(Poi.Kind.DOJO, f.wx(-2, 0), y0 + 1, f.wz(-2, 0), "floor");
                    place(Poi.Kind.DOJO, f.wx(2, 0), y0 + 1, f.wz(2, 0), "floor");
                    folk.addAll(List.of("master", "student", "student"));
                }
            }
        }
        for (int[] p : new int[][]{{4, 2}, {-4, -2}, {2, -4}, {-2, 4}}) place(Poi.Kind.PLAZA, x + p[0], y0 + 1, z + p[1], "plaza");
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            place(Poi.Kind.PATROL, x + d[0] * (RING - 6) + d[1] * 0, y0 + 1, z + d[1] * (RING - 6), "ring road");
            place(Poi.Kind.GATE, x + d[0] * (FLAT - 2), y0 + 1, z + d[1] * (FLAT - 2), "road out");
            if (style != Style.SNOW)
                for (int r : new int[]{33, 38}) place(Poi.Kind.FIELD, x + d[0] * r + d[1], y0 + 1, z + d[1] * r + d[0], "field");
        }
        // the map: the ring road, the four roads out, the plaza
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            way(x + (int) Math.round(Math.cos(a) * (RING - 6.5)), y0 + 1, z + (int) Math.round(Math.sin(a) * (RING - 6.5)));
        }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            for (int r = 7; r <= FLAT - 2; r += 7) way(x + d[0] * r, y0 + 1, z + d[1] * r);

        // the people: a guard, a merchant, a cook; farmers (no fields in the snow: woodcutters' families, citizens); citizens
        List<String> people = new ArrayList<>(List.of("merchant", "cook", "guard"));
        people.addAll(folk);
        for (int i = 0; i < (style == Style.SNOW ? 0 : 3); i++) people.add("farmer");
        while (people.size() < Math.min(2 * houses, 12)) people.add("citizen");
        this.people.addAll(people.subList(0, Math.min(people.size(), 2 * houses)));
    }

    void room(WestCity.F f, int hx, int hz, int h, int door) {
        room(f.wx(-hx, -hz), y0, f.wz(-hx, -hz), f.wx(hx, hz), y0 + h, f.wz(hx, hz), door);
    }
}
