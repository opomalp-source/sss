package com.dbzenith.world.landmark;

import com.dbzenith.living.Poi;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.world.arch.CivilizationBlocks;
import com.dbzenith.world.settle.SettlementPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * West City (canon; docs/WORLD_STRUCTURES.md): the great city of the west with Capsule Corporation at its heart, built
 * from the Capsule Corporation block family. One per world (a unique landmark), on fairly level dry land, never on
 * already explored ground.
 * <ul>
 *   <li>a round levelled site; avenues every 36 blocks with lane lines, sidewalks and street lamps;</li>
 *   <li>Capsule Corporation (canon): the great white dome with the company's mark on its front and rings of windows;
 *       inside a lobby with the reception desk, the atrium garden with a sparring ring, Dr. Brief's lab and the
 *       Capsule Room on the ground floor, the family's rooms upstairs, a storage room and workshop below with a time
 *       machine's pod; the lab wing; the West Tower; the round Gravity Room in the garden (the mod's gravity chamber
 *       inside); palms and a pond;</li>
 *   <li>round the compound: capsule houses (dome homes in five colours), rounded apartment blocks, a park with a
 *       fountain, a shop and a restaurant, the Sangokuichi Dojo (canon), research labs and a police post.</li>
 * </ul>
 * Twenty people live there (scientists, guards, merchants, cooks, a master and his students, citizens); every place
 * they use is registered with the living world. North is -z; heights are blocks above the ground level {@link #y0}.
 */
public final class WestCity extends SettlementPlan {
    static final int CITY = 120, BLEND = 16, STEP = 36, ROAD = 3, WALK = 2;
    /** Lot centres along each axis (between the avenues). */
    static final int[] LOTS = {-90, -54, 0, 54, 90};

    enum Kind { HOUSES, APARTMENTS, PARK, SHOPS, RESTAURANT, DOJO, LABS, POLICE }

    /** A lot: its centre, half sizes along x and z, what stands there, and which way its front faces (toward the city's middle). */
    record Lot(int lx, int lz, int hx, int hz, Kind kind, Direction front, int variant) {
        int half() {
            return Math.max(hx, hz);
        }

        boolean wide() {
            return half() > 20;
        }
    }

    /** The ground's level (the floor blocks' y). */
    final int y0;
    final List<Lot> lots = new ArrayList<>();

    private WestCity(LandmarkSites.Site site, int y0) {
        super(site);
        this.y0 = y0;
        for (int a : LOTS)
            for (int b : LOTS) {
                if (a == 0 && b == 0) continue;                                            // Capsule Corporation
                int hx = a == 0 ? 31 : 13, hz = b == 0 ? 31 : 13;
                if (Math.hypot(a, b) + 0.6 * Math.max(hx, hz) > CITY) continue;
                Direction front = Math.abs(a) >= Math.abs(b) ? (a > 0 ? Direction.WEST : Direction.EAST) : (b > 0 ? Direction.NORTH : Direction.SOUTH);
                lots.add(new Lot(x + a, z + b, hx, hz, kindOf(a, b), front, Canvas.hash(seed, a, 0, b) & 7));
            }
        layOutPlaces();
    }

    /** A West City at a chosen site and ground level, without the terrain checks (tests). */
    public static WestCity at(LandmarkSites.Site site, int y0) {
        return new WestCity(site, y0);
    }

    static Kind kindOf(int a, int b) {
        if (a == 0 && b == -54) return Kind.PARK;
        if (a == 0 && b == 54) return Kind.SHOPS;
        if (a == -54 && b == 0) return Kind.DOJO;
        if (a == 54 && b == 0) return Kind.LABS;
        if (a == 90 && b == 0) return Kind.POLICE;
        if (a == 0 || b == 0) return Kind.APARTMENTS;
        if (a == 54 && b == 90) return Kind.RESTAURANT;
        return Kind.HOUSES;
    }

    /** The site holds the city if the land there is dry and fairly level (it is levelled to the median and eased in at the edge). */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        // a 5 x 5 grid 48 apart (terrain samples are costly: the centre first, and a wet site is given up early)
        int[] hs = new int[25];
        int n = 0, wet = 0;
        for (int r = 0; r <= 2; r++)
            for (int i = -r; i <= r; i++)
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    int px = s.x() + i * 48, pz = s.z() + j * 48, h = t.height(px, pz);
                    if (t.wetOver(px, pz, h) && ++wet > 2) return null;
                    hs[n++] = h;
                }
        Arrays.sort(hs);
        if (hs[21] - hs[3] > 24 || hs[12] <= t.seaLevel()) return null;
        return new WestCity(s, hs[12]);
    }

    @Override
    public String name() {
        return "West City";
    }

    @Override
    public String style() {
        return "capsule";
    }

    @Override
    public int reach() {
        return CITY + BLEND;
    }

    @Override
    public BlockPos centre() {
        return new BlockPos(x, y0 + 1, z + 26);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + 34);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.hypot(wx - x, wz - z) <= CITY + BLEND ? y0 : Integer.MAX_VALUE;
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    static BlockState a(String id) {
        return Shapes.b(id);
    }

    // ------------------------------------------------------------------ a building's frame

    /** A building's own frame: its centre and the way its front faces (local -z is the front, local x its right... turned with it). */
    record F(int ox, int oz, int y, Direction front) {
        int turns() {
            return switch (front) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }

        int wx(int lx, int lz) {
            return switch (turns()) {
                case 1 -> ox - lz;
                case 2 -> ox - lx;
                case 3 -> ox + lz;
                default -> ox + lx;
            };
        }

        int wz(int lx, int lz) {
            return switch (turns()) {
                case 1 -> oz + lx;
                case 2 -> oz - lz;
                case 3 -> oz - lx;
                default -> oz + lz;
            };
        }

        Direction dir(Direction local) {
            Direction d = local;
            for (int i = 0; i < turns(); i++) d = d.getClockWise();
            return d;
        }

        void set(Canvas c, int lx, int dy, int lz, BlockState st) {
            c.set(wx(lx, lz), y + dy, wz(lx, lz), st);
        }

        void box(Canvas c, int lx0, int dy0, int lz0, int lx1, int dy1, int lz1, BlockState st) {
            c.box(wx(lx0, lz0), y + dy0, wz(lx0, lz0), wx(lx1, lz1), y + dy1, wz(lx1, lz1), st);
        }

        /** The frame of something at lx, lz in this one, facing {@code front} (a direction in this frame). */
        F sub(int lx, int lz, Direction localFront) {
            return new F(wx(lx, lz), wz(lx, lz), y, dir(localFront));
        }
    }

    F frame(Lot l) {
        return new F(l.lx, l.lz, y0, l.front);
    }

    /** The buildings of an apartment lot (two on a wide lot, side by side). */
    List<F> apartmentBlocks(Lot l) {
        F base = frame(l);
        return l.wide() ? List.of(base.sub(-12, 0, Direction.NORTH), base.sub(12, 0, Direction.NORTH)) : List.of(base);
    }

    /**
     * The four capsule houses of a lot, each door toward a paved road: out of the lot the nearer way (front or back),
     * else sideways; at the city's edge, where no road runs, inward onto the walk between the rows.
     */
    List<F> houseFrames(Lot l) {
        F base = frame(l);
        List<F> out = new ArrayList<>();
        for (int sx : new int[]{-7, 7})
            for (int sz : new int[]{-7, 7}) {
                Direction[] tries = {sz < 0 ? Direction.NORTH : Direction.SOUTH, sx < 0 ? Direction.WEST : Direction.EAST};
                F pick = base.sub(sx, sz, sz < 0 ? Direction.SOUTH : Direction.NORTH);
                for (Direction d : tries) {
                    F f = base.sub(sx, sz, d);
                    if (paved(f.wx(0, -7), f.wz(0, -7))) {
                        pick = f;
                        break;
                    }
                }
                out.add(pick);
            }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void build(Canvas c) {
        int R = CITY + BLEND;
        if (!c.intersects(x - R, z - R, x + R, z + R)) return;
        ground(c);
        roads(c);
        capsuleCorp(c);
        for (Lot l : lots) {
            int h = l.half() + 6;
            if (!c.intersects(l.lx - h, l.lz - h, l.lx + h, l.lz + h)) continue;
            switch (l.kind) {
                case HOUSES -> {
                    int i = 0;
                    for (F f : houseFrames(l)) house(c, f, l.variant + i++);
                }
                case APARTMENTS -> apartmentBlocks(l).forEach(f -> apartment(c, f));
                case PARK -> park(c, l);
                case SHOPS -> shops(c, l);
                case RESTAURANT -> restaurant(c, frame(l));
                case DOJO -> dojo(c, frame(l));
                case LABS -> labs(c, frame(l));
                case POLICE -> police(c, frame(l));
            }
        }
    }

    /** The site levelled: flat inside the city, eased into the land round it, lawn wherever the city doesn't pave. */
    void ground(Canvas c) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                double d = Math.hypot(wx - x, wz - z);
                if (d > CITY + BLEND) continue;
                int target = y0;
                if (d > CITY) {
                    double f = (d - CITY) / BLEND;
                    f = f * f * (3 - 2 * f);
                    target = (int) Math.round(y0 + (c.surface(wx, wz) - y0) * f);
                }
                c.ground(wx, wz, target, grass, dirt, d > CITY ? 12 : 40);
            }
    }

    /** How far a point is from the nearest avenue's middle, across x and across z. */
    static int[] offAvenue(int dx, int dz) {
        int ox = 99, oz = 99;
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            ox = Math.min(ox, Math.abs(dx - k * STEP));
            oz = Math.min(oz, Math.abs(dz - k * STEP));
        }
        return new int[]{ox, oz};
    }

    void roads(Canvas c) {
        BlockState road = a("city_road"), line = a("city_road_line"), walk = a("city_sidewalk");
        int band = ROAD + WALK;
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                int dx = wx - x, dz = wz - z;
                if (Math.hypot(dx, dz) > CITY - 2) continue;
                int[] o = offAvenue(dx, dz);
                int ox = o[0], oz = o[1];
                if (ox > band && oz > band) continue;
                boolean carriage = ox <= ROAD || oz <= ROAD;
                boolean dash = ox == 0 && oz > band && Math.floorMod(dz, 4) < 2 || oz == 0 && ox > band && Math.floorMod(dx, 4) < 2;
                c.set(wx, y0, wz, !carriage ? walk : dash ? line : road);
                boolean kerb = ox == ROAD + 1 && oz > band || oz == ROAD + 1 && ox > band;  // the sidewalk's road side (doors open on its other), not at crossings
                if (kerb && Math.floorMod(dx + dz, 14) == 0) lamp(c, wx, wz);
            }
    }

    /** Whether a point is on an avenue's paving (carriageway or sidewalk). */
    boolean paved(int wx, int wz) {
        int dx = wx - x, dz = wz - z;
        if (Math.hypot(dx, dz) > CITY - 2) return false;
        int[] o = offAvenue(dx, dz);
        return o[0] <= ROAD + WALK || o[1] <= ROAD + WALK;
    }

    void lamp(Canvas c, int wx, int wz) {
        c.column(wx, wz, y0 + 1, y0 + 3, s("capsule_metal_wall"));
        c.set(wx, y0 + 4, wz, a("city_streetlamp"));
    }

    // ------------------------------------------------------------------ Capsule Corporation

    static final int DOME_R = 17, DOME_H = 15, DOME_Z = -4;
    /** The lab wing's centre and radius (inside the grounds). */
    static final int WING_X = 23, WING_Z = -6, WING_R = 7;
    static final double DOME_T = 1.4;

    /** The main dome's outer surface height (blocks above its base) over a column dx, dz from its centre, or -1 outside it. */
    static int domeTop(int dx, int dz) {
        double d = Math.hypot(dx, dz) / DOME_R;
        return d > 1 ? -1 : (int) Math.round(DOME_H * Math.sqrt(1 - d * d));
    }

    /** A wall inside the main dome: from height dy0 to dy1 over its floor, kept under the dome's skin. */
    void domeWall(Canvas c, int wx, int wz, int dy0, int dy1, BlockState st) {
        int top = domeTop(wx - x, wz - (z + DOME_Z));
        if (top < 0) return;
        c.column(wx, wz, y0 + dy0, y0 + Math.min(dy1, top), st);
    }

    void capsuleCorp(Canvas c) {
        if (!c.intersects(x - 34, z - 34, x + 34, z + 34)) return;
        int cz = z + DOME_Z;
        BlockState shell = s("capsule_dome_white"), floor = s("capsule_lab_floor"), wall = s("capsule_white");
        // the grounds: open lawns edged with a low white kerb (a step anyone walks over), the path in, palms, a pond
        for (int wx = Math.max(c.minX, x - 31); wx <= Math.min(c.maxX, x + 31); wx++)
            for (int wz = Math.max(c.minZ, z - 31); wz <= Math.min(c.maxZ, z + 31); wz++) {
                int dx = wx - x, dz = wz - z;
                boolean edge = Math.abs(dx) == 31 || Math.abs(dz) == 31;
                if (edge && !(dz == 31 && Math.abs(dx) <= 2)) c.set(wx, y0 + 1, wz, Canvas.slab(s("capsule_white_slab"), false));
                if (Math.abs(dx) <= 2 && dz > 12 && dz <= 31) c.set(wx, y0, wz, a("city_sidewalk"));
                double pond = Math.hypot(dx + 18, dz - 18);
                if (pond <= 5) c.set(wx, y0, wz, Blocks.WATER.defaultBlockState());
                else if (pond <= 6) c.set(wx, y0, wz, s("capsule_white"));
            }
        for (int[] p : new int[][]{{-26, -24}, {-27, 4}, {24, -26}, {28, 9}, {-10, 26}, {9, 27}, {-25, 26}, {27, 27}})
            Shapes.palm(c, x + p[0], y0 + 1, z + p[1], 8 + Math.floorMod(p[0] + p[1], 4), p[0] * 31 + p[1]);

        // the main dome: floor, shell, window rings, the mark on its front, the doors under a canopy
        Shapes.disc(c, x, cz, y0, DOME_R - 0.5, floor);
        Shapes.dome(c, x, cz, y0 + 1, DOME_R, DOME_H, DOME_T, shell);
        for (int k : new int[]{2, 3, 8}) ringWindows(c, x, cz, y0 + 1 + k, DOME_R * Math.sqrt(1 - (double) (k * k) / (DOME_H * DOME_H)));
        logo(c, cz);
        c.box(x - 1, y0 + 1, cz + DOME_R - 4, x + 1, y0 + 4, cz + DOME_R + 1, Canvas.AIR);   // the entrance through the shell
        door(c, x - 1, y0 + 1, cz + DOME_R - 1, Direction.SOUTH, "capsule_door", DoorHingeSide.LEFT);
        door(c, x + 1, y0 + 1, cz + DOME_R - 1, Direction.SOUTH, "capsule_door", DoorHingeSide.RIGHT);
        c.column(x, cz + DOME_R - 1, y0 + 1, y0 + 2, s("capsule_metal"));
        c.box(x - 1, y0 + 3, cz + DOME_R - 1, x + 1, y0 + 4, cz + DOME_R - 1, s("capsule_window_band"));
        c.box(x - 3, y0 + 5, cz + DOME_R - 1, x + 3, y0 + 5, cz + DOME_R + 3, Canvas.slab(s("capsule_white_slab"), false));

        // ground floor: lobby and reception desk, the atrium garden with a sparring ring, a wall across to the lab and the Capsule Room
        for (int dx = -4; dx <= 4; dx++) if (dx != 0) c.set(x + dx, y0 + 1, cz + 8, s("capsule_metal"));
        c.set(x - 2, y0 + 2, cz + 8, s("capsule_tech_panel"));
        c.set(x + 2, y0 + 2, cz + 8, s("capsule_tech_panel"));
        for (int[] p : new int[][]{{-6, 11}, {6, 11}, {-9, 6}, {9, 6}}) c.set(x + p[0], y0 + 1, cz + p[1], Blocks.POTTED_FERN.defaultBlockState());
        for (int dx : new int[]{-7, -6, -5, 5, 6, 7}) c.set(x + dx, y0 + 1, cz + 12, Canvas.stairs(s("capsule_white_stairs"), Direction.SOUTH, false));
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        Shapes.disc(c, x, cz, y0, 4.5, Blocks.GRASS_BLOCK.defaultBlockState());
        c.column(x, cz, y0 + 1, y0 + 3, Blocks.SPRUCE_LOG.defaultBlockState());                     // (spruce: the sweep leaves it be)
        Shapes.disc(c, x, cz, y0 + 4, 2.5, leaf);
        Shapes.disc(c, x, cz, y0 + 5, 1.5, leaf);
        for (int[] p : new int[][]{{-3, 2}, {3, -2}, {2, 3}, {-2, -3}}) c.set(x + p[0], y0 + 1, cz + p[1], Blocks.POPPY.defaultBlockState());
        c.box(x - 13, y0, cz - 6, x - 8, y0, cz - 2, a("arena_tile"));                       // the sparring ring
        for (int dx = -16; dx <= 16; dx++) {
            if (Math.abs(dx) <= 1 || Math.abs(dx) == 8) continue;                          // doorways
            domeWall(c, x + dx, cz - 8, 1, 5, wall);
        }
        for (int dz2 = -16; dz2 <= -9; dz2++) domeWall(c, x, cz + dz2, 1, 5, wall);
        for (int dz2 = -15; dz2 <= -10; dz2++) {                                              // Dr. Brief's lab (west)
            c.set(x - 13 + (dz2 & 1), y0 + 1, cz + dz2, s("capsule_tech_panel"));
            if (dz2 % 2 == 0) c.set(x - 6, y0 + 1, cz + dz2, s("capsule_metal"));
        }
        c.set(x - 8, y0 + 1, cz - 12, Blocks.BREWING_STAND.defaultBlockState());
        c.set(x - 4, y0 + 1, cz - 14, Blocks.ANVIL.defaultBlockState());
        c.set(x - 3, y0 + 1, cz - 10, Blocks.CRAFTING_TABLE.defaultBlockState());
        for (int dx = 3; dx <= 11; dx += 2)                                                   // the Capsule Room (east): racks of capsules
            for (int h = 1; h <= 3; h++) c.set(x + dx, y0 + h, cz - 13, h == 2 ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.BARREL.defaultBlockState());

        // upper floor: the family's rooms either side of a hall; the stair comes up on the east side
        Shapes.disc(c, x, cz, y0 + 6, DOME_R - 2.2, floor);
        c.box(x + 4, y0 + 6, cz + 2, x + 8, y0 + 7, cz + 2, Canvas.AIR);
        for (int i = 0; i < 5; i++) c.set(x + 8 - i, y0 + 1 + i, cz + 2, Canvas.stairs(s("capsule_white_stairs"), Direction.WEST, false));
        for (int dz2 = -14; dz2 <= 12; dz2++) {
            if (dz2 == -6 || dz2 == 4) continue;                                             // doorways
            domeWall(c, x - 2, cz + dz2, 7, 10, wall);
            if (dz2 < 0 || dz2 > 3) domeWall(c, x + 2, cz + dz2, 7, 10, wall);
        }
        for (int dx = 3; dx <= 16; dx++) {
            domeWall(c, x - dx, cz - 1, 7, 10, wall);
            domeWall(c, x + dx, cz - 1, 7, 10, wall);
        }
        bed(c, x - 7, y0 + 7, cz - 9, Direction.NORTH, Blocks.BLUE_BED);
        bed(c, x - 10, y0 + 7, cz - 9, Direction.NORTH, Blocks.BLUE_BED);
        bed(c, x - 7, y0 + 7, cz + 6, Direction.SOUTH, Blocks.PINK_BED);
        bed(c, x - 10, y0 + 7, cz + 6, Direction.SOUTH, Blocks.PINK_BED);
        bed(c, x + 7, y0 + 7, cz - 9, Direction.NORTH, Blocks.CYAN_BED);
        bed(c, x + 10, y0 + 7, cz - 9, Direction.NORTH, Blocks.CYAN_BED);
        bed(c, x + 8, y0 + 7, cz + 8, Direction.SOUTH, Blocks.YELLOW_BED);
        bed(c, x + 11, y0 + 7, cz + 8, Direction.SOUTH, Blocks.YELLOW_BED);
        for (int[] p : new int[][]{{-5, -12}, {5, -12}, {-5, 9}, {6, 5}}) c.set(x + p[0], y0 + 7, cz + p[1], s("capsule_tech_panel"));
        for (int[] p : new int[][]{{-12, -4}, {12, -4}, {-12, 3}}) c.set(x + p[0], y0 + 7, cz + p[1], Blocks.CHEST.defaultBlockState());
        for (int[] p : new int[][]{{0, 0, 12}, {0, -12, 5}, {-10, 4, 5}, {10, -5, 5}, {-8, -6, 10}, {8, -6, 10}, {-8, 5, 10}, {8, 5, 10}})
            c.set(x + p[0], y0 + p[2], cz + p[1], s("capsule_light"));

        // below: the storage room and workshop, a stair down from the ground floor, the time machine's pod
        c.box(x - 13, y0 - 5, cz - 13, x + 9, y0 - 1, cz + 5, Canvas.AIR);
        c.box(x - 13, y0 - 6, cz - 13, x + 9, y0 - 6, cz + 5, floor);
        for (int i = 0; i <= 5; i++) c.set(x - 4 - i, y0 - i, cz + 2, Canvas.stairs(s("capsule_white_stairs"), Direction.EAST, false));
        c.box(x - 9, y0, cz + 2, x - 5, y0, cz + 2, Canvas.AIR);
        for (int dz2 = -12; dz2 <= -8; dz2++) {
            c.set(x - 12, y0 - 5, cz + dz2, Blocks.CHEST.defaultBlockState());
            c.set(x + 8, y0 - 5, cz + dz2, Blocks.BARREL.defaultBlockState());
        }
        c.set(x - 2, y0 - 5, cz - 12, Blocks.SMITHING_TABLE.defaultBlockState());
        c.set(x, y0 - 5, cz - 12, Blocks.ANVIL.defaultBlockState());
        c.set(x + 2, y0 - 5, cz - 12, s("capsule_tech_panel"));
        Shapes.dome(c, x + 4, cz - 2, y0 - 5, 2.6, 3, 1, s("capsule_white"));                // a capsule pod: the time machine's bay
        c.set(x + 4, y0 - 4, cz, s("capsule_window"));
        for (int[] p : new int[][]{{-8, -6}, {-4, 2}, {4, -8}}) c.set(x + p[0], y0 - 1, cz + p[1], s("capsule_light"));

        // the lab wing (east), joined to the dome by a passage, its own door onto the garden
        int wx0 = x + WING_X, wz0 = z + WING_Z;
        Shapes.disc(c, wx0, wz0, y0, WING_R - 0.5, floor);
        Shapes.dome(c, wx0, wz0, y0 + 1, WING_R, 6, 1.2, shell);
        ringWindows(c, wx0, wz0, y0 + 3, WING_R - 0.4);
        for (int wx = x + 14; wx <= x + 19; wx++)                                            // a solid neck where the two meet (no dead-end wedge either side)
            for (int wz = wz0 - 6; wz <= wz0 + 6; wz++)
                if (Math.hypot(wx - x, wz - cz) > DOME_R - 1.5 && Math.hypot(wx - wx0, wz - wz0) > WING_R - 1.5)
                    c.column(wx, wz, y0 + 1, y0 + 4, s("capsule_white"));
        c.box(x + 14, y0 + 1, wz0 - 1, x + 19, y0 + 3, wz0, Canvas.AIR);
        c.box(x + 14, y0, wz0 - 1, x + 19, y0, wz0, floor);
        c.box(wx0, y0 + 1, wz0 + 5, wx0, y0 + 2, wz0 + 7, Canvas.AIR);
        door(c, wx0, y0 + 1, wz0 + 6, Direction.SOUTH, "capsule_door", DoorHingeSide.LEFT);
        for (int[] p : new int[][]{{-4, -3}, {-4, 3}, {4, -3}, {4, 3}, {0, -5}}) c.set(wx0 + p[0], y0 + 1, wz0 + p[1], s("capsule_tech_panel"));
        c.box(wx0 - 1, y0 + 1, wz0 - 1, wx0 + 1, y0 + 1, wz0 + 1, s("capsule_metal"));
        c.set(wx0, y0 + 2, wz0, Blocks.BREWING_STAND.defaultBlockState());
        c.set(wx0, y0 + 6, wz0, s("capsule_light"));

        // the West Tower: a white drum with blue bands and windows under a yellow cap
        int tx = x - 22, tz = z - 14;
        Shapes.disc(c, tx, tz, y0, 5, floor);
        for (int k = 0; k <= 22; k++) {
            BlockState ring = k % 5 == 2 ? s("capsule_window_band") : k % 5 == 4 ? s("capsule_blue") : s("capsule_white");
            Shapes.ring(c, tx, tz, y0 + 1 + k, 4.6, 5.3, ring);
        }
        Shapes.dome(c, tx, tz, y0 + 24, 5.4, 4, 1.2, s("capsule_dome_yellow"));
        for (int f = 6; f <= 18; f += 6) Shapes.disc(c, tx, tz, y0 + f, 4.2, floor);   // its floors, a ladder up through them
        for (int k = 1; k <= 22; k++) c.set(tx - 3, y0 + k, tz, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        c.box(tx, y0 + 1, tz + 4, tx, y0 + 2, tz + 6, Canvas.AIR);
        door(c, tx, y0 + 1, tz + 5, Direction.SOUTH, "capsule_door", DoorHingeSide.LEFT);
        c.set(tx, y0 + 21, tz, s("capsule_light"));
        c.set(tx + 2, y0 + 1, tz - 2, Blocks.CHEST.defaultBlockState());

        // the Gravity Room in the garden: a white ball ringed with portholes, the gravity chamber inside
        int gx = x + 17, gz = z + 20, gy = y0 + 1;
        Shapes.disc(c, gx, gz, gy - 1, 4.6, floor);
        for (int wx = gx - 5; wx <= gx + 5; wx++)
            for (int wz = gz - 5; wz <= gz + 5; wz++)
                for (int dy = 0; dy <= 8; dy++) {
                    double d = Math.sqrt((wx - gx) * (wx - gx) + (wz - gz) * (wz - gz) + (dy - 2) * (dy - 2));
                    if (d <= 5.2 && d > 4.1) c.set(wx, gy + dy, wz, dy == 3 && ((wx + wz) & 1) == 0 ? s("capsule_window") : s("capsule_white"));
                    else if (d <= 4.1) c.set(wx, gy + dy, wz, Canvas.AIR);
                }
        c.set(gx, gy, gz, ModBlocks.GRAVITY_CHAMBER.get().defaultBlockState());
        c.set(gx, gy + 5, gz, s("capsule_light"));
        door(c, gx - 4, gy, gz, Direction.WEST, "capsule_door", DoorHingeSide.LEFT);
    }

    /** A ring of window bands round a dome or drum at height y (a solid post every fourth block). */
    void ringWindows(Canvas c, int cx, int cz, int y, double r) {
        int R = (int) Math.ceil(r) + 1;
        for (int wx = Math.max(c.minX, cx - R); wx <= Math.min(c.maxX, cx + R); wx++)
            for (int wz = Math.max(c.minZ, cz - R); wz <= Math.min(c.maxZ, cz + R); wz++) {
                double d = Math.hypot(wx - cx, wz - cz);
                if (d < r - 1.2 || d > r + 0.6) continue;
                if (c.get(wx, y, wz).isAir()) continue;
                if (Math.floorMod(Shapes.arc(wx - cx, wz - cz, r), 4) == 0) continue;
                c.set(wx, y, wz, s("capsule_window_band"));
            }
    }

    /** The company's mark, painted large on the dome's front: a ring with a big C and a small c in it (blue on white). */
    void logo(Canvas c, int cz) {
        String[] art = {
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#.#.....#",
                "#.#.##..#",
                "#.#.#...#",
                "#..###..#",
                ".#.....#.",
                "..#####.."};
        for (int row = 0; row < art.length; row++)
            for (int col = 0; col < 9; col++) {
                if (art[row].charAt(col) != '#') continue;
                int dx = col - 4, k = 13 - row;
                for (int dz = DOME_R; dz >= 0; dz--)                                       // the outermost shell block at that height
                    if (domeTop(dx, dz) >= k) {
                        c.set(x + dx, y0 + 1 + k, cz + dz, s("capsule_blue"));
                        break;
                    }
            }
    }

    // ------------------------------------------------------------------ the lots

    static final String[] ROOFS = {"capsule_dome_yellow", "capsule_dome_orange", "capsule_blue", "capsule_cyan", "capsule_dome_white"};

    /** A capsule house: a round white home under a coloured dome, portholes, a door; two beds, a table, a screen. */
    void house(Canvas c, F f, int variant) {
        int cx = f.wx(0, 0), cz = f.wz(0, 0);
        if (!c.intersects(cx - 7, cz - 7, cx + 7, cz + 7)) return;
        Shapes.disc(c, cx, cz, y0, 4.6, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int k = 1; k <= 3; k++) Shapes.ring(c, cx, cz, y0 + k, 4.6, 5.3, s("capsule_white"));
        portholes(c, cx, cz, y0 + 2, 5);
        Shapes.dome(c, cx, cz, y0 + 4, 5.4, 4, 1.2, s(ROOFS[Math.floorMod(variant, ROOFS.length)]));
        f.box(c, 0, 1, -6, 0, 2, -4, Canvas.AIR);
        door(c, f.wx(0, -5), y0 + 1, f.wz(0, -5), f.dir(Direction.NORTH), "capsule_door", DoorHingeSide.LEFT);
        f.set(c, 0, 0, -6, a("city_sidewalk"));
        bed(c, f.wx(-2, 1), y0 + 1, f.wz(-2, 1), f.dir(Direction.SOUTH), Blocks.LIGHT_BLUE_BED);
        bed(c, f.wx(2, 1), y0 + 1, f.wz(2, 1), f.dir(Direction.SOUTH), Blocks.ORANGE_BED);
        f.set(c, 0, 1, 3, s("capsule_tech_panel"));
        f.set(c, -3, 1, -1, Blocks.OAK_FENCE.defaultBlockState());
        f.set(c, -3, 2, -1, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        f.set(c, 3, 1, -1, Blocks.POTTED_RED_TULIP.defaultBlockState());
        f.set(c, 0, 6, 0, s("capsule_light"));
    }

    /** Portholes round a drum (every third block of its rim). */
    void portholes(Canvas c, int cx, int cz, int y, double r) {
        int R = (int) Math.ceil(r) + 1;
        for (int wx = Math.max(c.minX, cx - R); wx <= Math.min(c.maxX, cx + R); wx++)
            for (int wz = Math.max(c.minZ, cz - R); wz <= Math.min(c.maxZ, cz + R); wz++) {
                double d = Math.hypot(wx - cx, wz - cz);
                if (d < r - 0.4 || d > r + 0.3) continue;
                if (Math.floorMod(Shapes.arc(wx - cx, wz - cz, r), 3) != 0) continue;
                c.set(wx, y, wz, s("capsule_window"));
            }
    }

    static final int AP_X = 7, AP_Z = 5, AP_FLOORS = 3;

    /** A rounded apartment block: three floors, rounded corners, window bands, a stair inside, two flats a floor. */
    void apartment(Canvas c, F f) {
        int cx = f.wx(0, 0), cz = f.wz(0, 0);
        if (!c.intersects(cx - 14, cz - 14, cx + 14, cz + 14)) return;
        int top = AP_FLOORS * 4;
        f.box(c, -AP_X, 0, -AP_Z, AP_X, 0, AP_Z, s("capsule_lab_floor"));
        for (int dy = 1; dy <= top; dy++) {
            BlockState ring = dy % 4 == 2 ? s("capsule_window_band") : dy % 4 == 0 ? s("capsule_blue") : s("capsule_white_seam");
            for (int lx = -AP_X; lx <= AP_X; lx++) {
                f.set(c, lx, dy, -AP_Z, ring);
                f.set(c, lx, dy, AP_Z, ring);
            }
            for (int lz = -AP_Z; lz <= AP_Z; lz++) {
                f.set(c, -AP_X, dy, lz, ring);
                f.set(c, AP_X, dy, lz, ring);
            }
            BlockState corner = s("capsule_corner");                                         // rounded corners, all the way up
            f.set(c, -AP_X, dy, -AP_Z, corner.setValue(CivilizationBlocks.RoundCorner.FACING, f.dir(Direction.NORTH)));
            f.set(c, AP_X, dy, -AP_Z, corner.setValue(CivilizationBlocks.RoundCorner.FACING, f.dir(Direction.EAST)));
            f.set(c, AP_X, dy, AP_Z, corner.setValue(CivilizationBlocks.RoundCorner.FACING, f.dir(Direction.SOUTH)));
            f.set(c, -AP_X, dy, AP_Z, corner.setValue(CivilizationBlocks.RoundCorner.FACING, f.dir(Direction.WEST)));
        }
        f.box(c, -AP_X + 1, 1, -AP_Z + 1, AP_X - 1, top - 1, AP_Z - 1, Canvas.AIR);
        for (int k = 1; k <= AP_FLOORS; k++) {                                                // floors, each with its stair well
            int fy = k * 4;
            f.box(c, -AP_X + 1, fy, -AP_Z + 1, AP_X - 1, fy, AP_Z - 1, k == AP_FLOORS ? s("capsule_white") : s("capsule_lab_floor"));
            if (k < AP_FLOORS) f.box(c, -5, fy, 3, -2, fy, 3, Canvas.AIR);
        }
        for (int k = 0; k < AP_FLOORS - 1; k++)
            for (int i = 0; i < 4; i++) f.set(c, -5 + i, 4 * k + 1 + i, 3, Canvas.stairs(s("capsule_white_stairs"), f.dir(Direction.EAST), false));
        for (int k = 0; k < AP_FLOORS; k++) {                                                 // two flats a floor, a partition between
            int fy = 4 * k;
            f.box(c, 0, fy + 1, -1, 0, fy + 3, 1, s("capsule_white"));
            bed(c, f.wx(-5, -3), y0 + fy + 1, f.wz(-5, -3), f.dir(Direction.WEST), Blocks.WHITE_BED);
            bed(c, f.wx(5, -3), y0 + fy + 1, f.wz(5, -3), f.dir(Direction.EAST), Blocks.LIGHT_BLUE_BED);
            f.set(c, -2, fy + 1, -4, s("capsule_tech_panel"));
            f.set(c, 3, fy + 1, -4, Blocks.BOOKSHELF.defaultBlockState());
            f.set(c, -3, fy + 3, 0, s("capsule_light"));
            f.set(c, 3, fy + 3, 0, s("capsule_light"));
        }
        f.box(c, 0, 1, -AP_Z - 1, 0, 2, -AP_Z, Canvas.AIR);
        door(c, f.wx(0, -AP_Z), y0 + 1, f.wz(0, -AP_Z), f.dir(Direction.NORTH), "capsule_door", DoorHingeSide.LEFT);
        for (int lz = -12; lz <= -AP_Z - 1; lz++) f.set(c, 0, 0, lz, a("city_sidewalk"));
        Shapes.dome(c, cx, cz, y0 + top + 1, 3.4, 3, 1, s("capsule_dome_white"));             // a small dome on the roof, an aerial
        c.column(cx, cz, y0 + top + 4, y0 + top + 7, s("capsule_metal_wall"));
    }

    /** The city park: lawns, cross paths, a fountain, cypresses, benches and lamps. */
    void park(Canvas c, Lot l) {
        int cx = l.lx, cz = l.lz;
        for (int wx = Math.max(c.minX, cx - l.hx + 1); wx <= Math.min(c.maxX, cx + l.hx - 1); wx++)
            for (int wz = Math.max(c.minZ, cz - l.hz + 1); wz <= Math.min(c.maxZ, cz + l.hz - 1); wz++)
                if (Math.abs(wx - cx) <= 1 || Math.abs(wz - cz) <= 1) c.set(wx, y0, wz, a("city_sidewalk"));
        Shapes.disc(c, cx, cz, y0, 4.5, s("capsule_white"));
        Shapes.disc(c, cx, cz, y0, 3.5, Blocks.WATER.defaultBlockState());
        c.column(cx, cz, y0, y0 + 2, s("capsule_white"));
        c.set(cx, y0 + 3, cz, s("capsule_light"));
        for (int[] p : new int[][]{{-20, -8}, {-12, 9}, {14, -9}, {22, 7}, {-25, 4}, {26, -5}, {-6, -10}, {7, 10}})
            Shapes.cypress(c, cx + p[0], y0 + 1, cz + p[1], 6 + Math.floorMod(p[0], 3));
        for (int i : new int[]{-5, -4, -3, 3, 4, 5}) {
            c.set(cx + i, y0 + 1, cz - 6, Canvas.stairs(s("capsule_white_stairs"), Direction.NORTH, false));
            c.set(cx + i, y0 + 1, cz + 6, Canvas.stairs(s("capsule_white_stairs"), Direction.SOUTH, false));
        }
        for (int[] p : new int[][]{{-9, -2}, {9, 2}, {-2, 9}, {2, -9}}) lamp(c, cx + p[0], cz + p[1]);
    }

    /** A shop and a restaurant either side of a little market. */
    void shops(Canvas c, Lot l) {
        F base = frame(l);
        shop(c, base.sub(-12, 2, Direction.NORTH));
        restaurant(c, base.sub(12, 2, Direction.NORTH));
        for (int sx : new int[]{-4, 0, 4}) {                                                  // stalls on the square between
            int wx = base.wx(sx, -8), wz = base.wz(sx, -8);
            c.set(wx, y0 + 1, wz, Blocks.BARREL.defaultBlockState());
            c.set(wx, y0 + 2, wz, s("capsule_metal_wall"));
            c.set(wx, y0 + 3, wz, a(sx == 0 ? "banner_cloth_yellow" : "banner_cloth_blue"));
        }
    }

    /** A shop: a glass front under an awning, a counter across, shelves behind. */
    void shop(Canvas c, F f) {
        if (!c.intersects(f.ox() - 12, f.oz() - 12, f.ox() + 12, f.oz() + 12)) return;
        boxBuilding(c, f, 7, 5, 5, "capsule_white_seam", "capsule_cyan");
        for (int lx = -6; lx <= 6; lx++) if (Math.abs(lx) > 1) f.box(c, lx, 1, -5, lx, 3, -5, a("city_glass"));
        doubleDoor(c, f, -5);
        for (int lx = -7; lx <= 7; lx++) f.set(c, lx, 4, -6, Canvas.slab(a("lookout_awning_slab"), false));
        for (int lx = -5; lx <= 3; lx++) f.set(c, lx, 1, 0, s("capsule_metal"));
        for (int lx = -6; lx <= 6; lx++) {
            f.set(c, lx, 1, 4, Blocks.BARREL.defaultBlockState());
            f.set(c, lx, 2, 4, Blocks.BOOKSHELF.defaultBlockState());
        }
        f.set(c, -2, 2, 0, s("capsule_tech_panel"));
        f.set(c, 0, 5, 0, s("capsule_light"));
    }

    /** A restaurant: tables with chairs, a counter, the kitchen behind. */
    void restaurant(Canvas c, F f) {
        if (!c.intersects(f.ox() - 12, f.oz() - 12, f.ox() + 12, f.oz() + 12)) return;
        boxBuilding(c, f, 7, 5, 5, "capsule_white", "capsule_dome_orange");
        for (int lx = -6; lx <= 6; lx += 3) if (lx != 0) f.set(c, lx, 2, -5, s("capsule_window"));
        doubleDoor(c, f, -5);
        for (int tx : new int[]{-4, 4}) {
            f.set(c, tx, 1, -2, Blocks.OAK_FENCE.defaultBlockState());
            f.set(c, tx, 2, -2, Blocks.WHITE_CARPET.defaultBlockState());
            f.set(c, tx - 1, 1, -2, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), f.dir(Direction.WEST), false));
            f.set(c, tx + 1, 1, -2, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), f.dir(Direction.EAST), false));
        }
        for (int lx = -6; lx <= 2; lx++) f.set(c, lx, 1, 2, s("capsule_metal"));
        f.set(c, -5, 1, 4, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, f.dir(Direction.NORTH)));
        f.set(c, -3, 1, 4, Blocks.BARREL.defaultBlockState());
        f.set(c, 0, 5, 0, s("capsule_light"));
    }

    /** A plain building: floor, walls of {@code wall} with a band of {@code band} at the top, a flat roof with a rim. */
    void boxBuilding(Canvas c, F f, int hx, int hz, int h, String wall, String band) {
        f.box(c, -hx, 0, -hz, hx, 0, hz, s("capsule_lab_floor"));
        for (int dy = 1; dy <= h; dy++) {
            BlockState w = dy == h ? s(band) : s(wall);
            for (int lx = -hx; lx <= hx; lx++) {
                f.set(c, lx, dy, -hz, w);
                f.set(c, lx, dy, hz, w);
            }
            for (int lz = -hz; lz <= hz; lz++) {
                f.set(c, -hx, dy, lz, w);
                f.set(c, hx, dy, lz, w);
            }
        }
        f.box(c, -hx + 1, 1, -hz + 1, hx - 1, h, hz - 1, Canvas.AIR);
        f.box(c, -hx, h + 1, -hz, hx, h + 1, hz, s("capsule_white"));
        BlockState rim = s("capsule_metal_slab");
        for (int lx = -hx; lx <= hx; lx++) {
            f.set(c, lx, h + 2, -hz, rim);
            f.set(c, lx, h + 2, hz, rim);
        }
        for (int lz = -hz; lz <= hz; lz++) {
            f.set(c, -hx, h + 2, lz, rim);
            f.set(c, hx, h + 2, lz, rim);
        }
    }

    /** The Sangokuichi Dojo: a raised hall of red pillars and plaster under a tiled hip roof, a training floor, punching bags. */
    void dojo(Canvas c, F f) {
        if (!c.intersects(f.ox() - 16, f.oz() - 16, f.ox() + 16, f.oz() + 16)) return;
        f.box(c, -9, 0, -7, 9, 1, 7, a("tournament_stone"));
        f.box(c, -8, 1, -6, 8, 1, 6, a("arena_tile"));
        for (int lx = -2; lx <= 2; lx++) f.set(c, lx, 1, -8, Canvas.stairs(a("tournament_stone_stairs"), f.dir(Direction.SOUTH), false));
        for (int lx = -8; lx <= 8; lx++)
            for (int lz = -6; lz <= 6; lz++) {
                if (Math.abs(lx) != 8 && Math.abs(lz) != 6) continue;
                boolean pillar = Math.abs(lx) % 4 == 0 && Math.abs(lz) == 6 || Math.abs(lz) % 3 == 0 && Math.abs(lx) == 8;
                if (lz == -6 && !pillar) continue;                                             // the open front
                for (int dy = 2; dy <= 5; dy++)
                    f.set(c, lx, dy, lz, pillar ? a("temple_red_pillar") : dy == 3 ? a("temple_lantern") : a("temple_plaster"));
            }
        f.box(c, -8, 6, -6, 8, 6, 6, a("temple_gold_trim"));
        int x0 = f.wx(-10, -8), z0 = f.wz(-10, -8), x1 = f.wx(10, 8), z1 = f.wz(10, 8);
        Shapes.hipRoof(c, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), y0 + 7, "clay_roof_tiles", 1, a("temple_gold"));
        for (int[] p : new int[][]{{-6, 4}, {6, 4}, {-6, -2}, {6, -2}}) f.set(c, p[0], 2, p[1], ModBlocks.PUNCHING_BAG.get().defaultBlockState());
        f.set(c, 0, 3, 5, a("banner_cloth_red"));
        f.set(c, 0, 4, 5, a("banner_cloth_red"));
    }

    /** Research labs: a long white-and-blue building, rows of benches and consoles, the company's mark by the door. */
    void labs(Canvas c, F f) {
        if (!c.intersects(f.ox() - 16, f.oz() - 16, f.ox() + 16, f.oz() + 16)) return;
        boxBuilding(c, f, 10, 7, 6, "capsule_white_seam", "capsule_blue");
        for (int lx = -9; lx <= 9; lx++) if (Math.abs(lx) > 1 && lx % 3 != 0) f.set(c, lx, 2, -7, s("capsule_window_band"));
        doubleDoor(c, f, -7);
        for (int lz : new int[]{-2, 3})
            for (int lx = -8; lx <= 8; lx++) {
                if (Math.abs(lx) <= 1) continue;
                f.set(c, lx, 1, lz, s("capsule_metal"));
                if (lx % 3 == 0) f.set(c, lx, 2, lz, Blocks.BREWING_STAND.defaultBlockState());
            }
        for (int lx = -9; lx <= 9; lx += 2) f.set(c, lx, 1, 6, s("capsule_tech_panel"));
        for (int lx = -6; lx <= 6; lx += 6) f.set(c, lx, 6, 0, s("capsule_light"));
        f.set(c, 2, 1, -9, s("capsule_logo"));
    }

    /** A police post: a small blue-and-white station with a desk and two bunks. */
    void police(Canvas c, F f) {
        if (!c.intersects(f.ox() - 14, f.oz() - 14, f.ox() + 14, f.oz() + 14)) return;
        boxBuilding(c, f, 5, 4, 4, "capsule_blue", "capsule_white");
        doubleDoor(c, f, -4);
        for (int lx : new int[]{-4, -2, 2, 4}) f.set(c, lx, 2, -4, s("capsule_window"));
        f.box(c, -2, 1, -1, 2, 1, -1, s("capsule_metal"));
        f.set(c, 0, 2, -1, s("capsule_tech_panel"));
        bed(c, f.wx(-3, 2), y0 + 1, f.wz(-3, 2), f.dir(Direction.SOUTH), Blocks.BLUE_BED);
        bed(c, f.wx(3, 2), y0 + 1, f.wz(3, 2), f.dir(Direction.SOUTH), Blocks.BLUE_BED);
        f.set(c, 0, 4, 1, s("capsule_light"));
    }

    // ------------------------------------------------------------------ furniture

    void door(Canvas c, int wx, int wy, int wz, Direction facing, String id, DoorHingeSide hinge) {
        BlockState d = s(id).setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false);
        c.set(wx, wy, wz, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(wx, wy + 1, wz, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A pair of sliding doors in a building's front wall (at local z {@code lz}, x 0 and 1), wide enough for two to pass. */
    void doubleDoor(Canvas c, F f, int lz) {
        f.box(c, 0, 1, lz - 1, 1, 2, lz, Canvas.AIR);
        door(c, f.wx(0, lz), y0 + 1, f.wz(0, lz), f.dir(Direction.NORTH), "capsule_door", DoorHingeSide.LEFT);
        door(c, f.wx(1, lz), y0 + 1, f.wz(1, lz), f.dir(Direction.NORTH), "capsule_door", DoorHingeSide.RIGHT);
    }

    /** A bed whose foot is at wx, wz and its head one block toward {@code head}. */
    void bed(Canvas c, int wx, int wy, int wz, Direction head, Block block) {
        BlockState b = block.defaultBlockState().setValue(BedBlock.FACING, head);
        c.set(wx, wy, wz, b.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(wx + head.getStepX(), wy, wz + head.getStepZ(), b.setValue(BedBlock.PART, BedPart.HEAD));
    }

    // ------------------------------------------------------------------ who lives where (the living world)

    /**
     * Every place residents use, each where the drawing above leaves a floor to stand on: homes (the Briefs' rooms
     * first, then the police post, the houses a lot at a time, the flats), the shop and market, the restaurants, the labs, the dojo,
     * the park and the compound's garden, a patrol round the crossings, the city's four road ends. And who lives here.
     */
    void layOutPlaces() {
        int cz = z + DOME_Z;
        place(Poi.Kind.HOME, x - 9, y0 + 7, cz - 6, "Capsule Corporation, west room");
        place(Poi.Kind.HOME, x + 9, y0 + 7, cz - 6, "Capsule Corporation, east room");
        Lot police = lot(Kind.POLICE);
        if (police != null) {
            F f = frame(police);
            place(Poi.Kind.HOME, f.wx(0, 1), y0 + 1, f.wz(0, 1), "police post");
        }
        for (int i = 0; i < 4; i++)                                                           // houses one a lot in turn, so people live all over the city
            for (Lot l : lots)
                if (l.kind == Kind.HOUSES) {
                    F f = houseFrames(l).get(i);
                    place(Poi.Kind.HOME, f.wx(0, -1), y0 + 1, f.wz(0, -1), "capsule house");
                }
        for (Lot l : lots)
            if (l.kind == Kind.APARTMENTS)
                for (F f : apartmentBlocks(l))
                    for (int k = 0; k < AP_FLOORS; k++) {
                        place(Poi.Kind.HOME, f.wx(-3, -1), y0 + 4 * k + 1, f.wz(-3, -1), "flat");
                        place(Poi.Kind.HOME, f.wx(3, -1), y0 + 4 * k + 1, f.wz(3, -1), "flat");
                    }
        place(Poi.Kind.HOME, x - 9, y0 + 7, cz + 4, "Capsule Corporation, south-west room");
        place(Poi.Kind.HOME, x + 9, y0 + 7, cz + 5, "Capsule Corporation, south-east room");

        // work
        place(Poi.Kind.LAB, x - 9, y0 + 1, cz - 12, "Dr. Brief's lab");
        place(Poi.Kind.LAB, x + WING_X, y0 + 1, z + WING_Z + 3, "lab wing");
        place(Poi.Kind.LAB, x - 4, y0 - 5, cz - 9, "workshop");
        Lot labs = lot(Kind.LABS);
        if (labs != null) {
            F f = frame(labs);
            place(Poi.Kind.LAB, f.wx(-5, 0), y0 + 1, f.wz(-5, 0), "research labs");
            place(Poi.Kind.LAB, f.wx(5, 1), y0 + 1, f.wz(5, 1), "research labs");
        }
        Lot shops = lot(Kind.SHOPS);
        if (shops != null) {
            F base = frame(shops), shop = base.sub(-12, 2, Direction.NORTH);
            place(Poi.Kind.SHOP, shop.wx(-2, 2), y0 + 1, shop.wz(-2, 2), "counter");
            place(Poi.Kind.MARKET, shop.wx(-2, -2), y0 + 1, shop.wz(-2, -2), "shop floor");
            place(Poi.Kind.MARKET, base.wx(-2, -10), y0 + 1, base.wz(-2, -10), "stalls");
            place(Poi.Kind.MARKET, base.wx(3, -10), y0 + 1, base.wz(3, -10), "stalls");
            tavern(base.sub(12, 2, Direction.NORTH));
        }
        Lot rest = lot(Kind.RESTAURANT);
        if (rest != null) tavern(frame(rest));
        Lot dojo = lot(Kind.DOJO);
        if (dojo != null) {
            F f = frame(dojo);
            place(Poi.Kind.DOJO, f.wx(0, 3), y0 + 2, f.wz(0, 3), "master");
            for (int[] p : new int[][]{{-3, -1}, {3, -1}, {-3, 1}, {3, 1}}) place(Poi.Kind.DOJO, f.wx(p[0], p[1]), y0 + 2, f.wz(p[0], p[1]), "floor");
        }
        Lot park = lot(Kind.PARK);
        if (park != null)
            for (int[] p : new int[][]{{-6, -7}, {6, 7}, {0, -8}, {-10, 0}}) place(Poi.Kind.PLAZA, park.lx + p[0], y0 + 1, park.lz + p[1], "park");
        place(Poi.Kind.PLAZA, x - 10, y0 + 1, z + 22, "Capsule Corporation garden");
        place(Poi.Kind.PLAZA, x + 4, y0 + 1, cz + 10, "lobby");
        for (int[] p : new int[][]{{STEP, STEP}, {-STEP, STEP}, {-STEP, -STEP}, {STEP, -STEP}, {2 * STEP, STEP}, {-2 * STEP, -STEP}, {STEP, 2 * STEP}, {-STEP, -2 * STEP}})
            place(Poi.Kind.PATROL, x + p[0], y0 + 1, z + p[1], "crossing");
        int e = 110;                                                                          // where the avenues leave the city
        place(Poi.Kind.GATE, x + STEP, y0 + 1, z - e, "north road");
        place(Poi.Kind.GATE, x - STEP, y0 + 1, z + e, "south road");
        place(Poi.Kind.GATE, x + e, y0 + 1, z + STEP, "east road");
        place(Poi.Kind.GATE, x - e, y0 + 1, z - STEP, "west road");

        layOutWays();

        people.addAll(List.of("scientist", "scientist", "scientist", "guard", "guard", "guard", "merchant", "merchant", "cook", "cook",
                "master", "student", "student", "student", "citizen", "citizen", "citizen", "citizen", "citizen", "citizen"));
    }

    /**
     * The map for long walks ({@link com.dbzenith.living.Routes}): waypoints along every avenue, on Capsule
     * Corporation's lawns and before each door, and every building as a room entered by its door's waypoint.
     */
    void layOutWays() {
        int g = y0 + 1, cz = z + DOME_Z;
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            for (int t = -6 * STEP / 2; t <= 6 * STEP / 2; t += STEP / 2) {
                if (Math.hypot(k * STEP, t) <= CITY - 6) way(x + k * STEP, g, z + t);
                if (Math.hypot(t, k * STEP) <= CITY - 6) way(x + t, g, z + k * STEP);
            }
        }
        for (int[] p : new int[][]{{0, 28}, {0, -26}, {-27, -2}, {29, 8}, {-24, 24}, {-26, -27}, {26, -24}}) way(x + p[0], g, z + p[1]);
        // Capsule Corporation's buildings (its upper floor first: a room inside the dome, entered from the foot of its stair)
        roundRoom(x, cz, DOME_R, y0 + 6, y0 + DOME_H + 1, way(x + 10, g, cz + 2));
        room(x - 13, y0 - 6, cz - 13, x + 9, y0 - 1, cz + 5, way(x - 3, g, cz + 2));            // the basement, from the top of its stair
        roundRoom(x, cz, DOME_R, y0, y0 + 5, way(x + 1, g, cz + DOME_R - 3));                 // the ground floor, from just inside the doors (in line with one: a column stands between them)
        way(x + 1, g, cz + DOME_R + 3);                                                       // (and the step outside it)
        roundRoom(x + WING_X, z + WING_Z, WING_R, y0, y0 + 7, way(x + WING_X, g, z + WING_Z + 8));
        roundRoom(x - 22, z - 14, 5, y0, y0 + 28, way(x - 22, g, z - 7));             // the West Tower
        roundRoom(x + 17, z + 20, 5, y0, y0 + 9, way(x + 11, g, z + 20));            // the Gravity Room
        for (Lot l : lots) {
            F f = frame(l);
            switch (l.kind) {
                case HOUSES -> houseFrames(l).forEach(h -> roundRoom(h.ox(), h.oz(), 5, y0, y0 + 9, way(h.wx(0, -6), g, h.wz(0, -6))));
                case APARTMENTS -> apartmentBlocks(l).forEach(a -> room(a, AP_X, AP_Z, AP_FLOORS * 4 + 5, way(a.wx(0, -AP_Z - 2), g, a.wz(0, -AP_Z - 2))));
                case SHOPS -> {
                    F shop = f.sub(-12, 2, Direction.NORTH), rest = f.sub(12, 2, Direction.NORTH);
                    room(shop, 7, 5, 7, way(shop.wx(0, -7), g, shop.wz(0, -7)));
                    room(rest, 7, 5, 7, way(rest.wx(0, -7), g, rest.wz(0, -7)));
                }
                case RESTAURANT -> room(f, 7, 5, 7, way(f.wx(0, -7), g, f.wz(0, -7)));
                case LABS -> room(f, 10, 7, 8, way(f.wx(0, -9), g, f.wz(0, -9)));
                case POLICE -> room(f, 5, 4, 6, way(f.wx(0, -6), g, f.wz(0, -6)));
                case DOJO -> room(f, 9, 8, 12, way(f.wx(0, -10), g, f.wz(0, -10)));
                case PARK -> {
                    for (int[] p : new int[][]{{0, -9}, {0, 9}, {-12, 0}, {12, 0}}) way(l.lx + p[0], g, l.lz + p[1]);
                }
            }
        }
    }

    /** A building of {@code hx} by {@code hz} each way from its frame's centre, {@code h} high, entered by waypoint {@code door}. */
    void room(F f, int hx, int hz, int h, int door) {
        room(f.wx(-hx, -hz), y0, f.wz(-hx, -hz), f.wx(hx, hz), y0 + h, f.wz(hx, hz), door);
    }

    void tavern(F f) {
        place(Poi.Kind.TAVERN, f.wx(-4, -3), y0 + 1, f.wz(-4, -3), "table");
        place(Poi.Kind.TAVERN, f.wx(4, -3), y0 + 1, f.wz(4, -3), "table");
        place(Poi.Kind.TAVERN, f.wx(0, -1), y0 + 1, f.wz(0, -1), "table");
        place(Poi.Kind.TAVERN, f.wx(-4, 3), y0 + 1, f.wz(-4, 3), "kitchen");
    }

    Lot lot(Kind k) {
        for (Lot l : lots) if (l.kind == k) return l;
        return null;
    }
}
