package com.dbzenith.world.landmark;

import com.dbzenith.living.Occupation;
import com.dbzenith.living.Poi;
import com.dbzenith.living.Settlement;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.world.arch.CivilizationBlocks;
import com.dbzenith.world.settle.SettlementPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Namekian village (canon-inspired, docs/WORLD_STRUCTURES.md): a few white dome houses round a spring, round
 * windows and a blue band at their feet; to the north the elder's house, a great dome between two small ones, the
 * elder's seat inside; an Ajisa grove the farmers tend; a ring of trodden earth where the warriors train; lamps on
 * white posts. Namekians live on water alone: no shop and no inn, the spring is where they gather. Named for its elder
 * (as Elder Moori's village is), and its elder bears the name. Namek only; small and far apart.
 */
public final class NamekVillage extends SettlementPlan {
    static final int FLAT = 34, BLEND = 12, RING = 18;
    /** The elder's house: its centre, a little north of the spring. */
    static final int ELDER_Z = -17;
    /** The warriors' training ground (between two houses, out past them). */
    static final int TX = 14, TZ = 25;
    /** The pedestal for the village's Dragon Ball, beside the elder's seat (from the elder's house centre; v0.167.0). */
    static final int BALL_DX = 2, BALL_DZ = -4;
    /** Village elders' names (original: after shellfish, as Namekian names are after snails). */
    static final String[] ELDERS = {"Sazae", "Nagaru", "Kisago", "Tokobushi", "Hinagai", "Ishidatami", "Akagai", "Bai", "Torigai", "Mirugai", "Hotategai",
            "Asari", "Shijimi", "Hamaguri", "Awabi", "Tsubugai", "Kibisago", "Uminina"};

    record House(int hx, int hz, Direction front, boolean twin) {}

    final int y0;
    final String elder;
    final boolean training;
    final List<House> houses = new ArrayList<>();

    private NamekVillage(LandmarkSites.Site site, int y0) {
        super(site);
        this.y0 = y0;
        this.elder = ELDERS[(int) Math.floorMod(seed >>> 9, ELDERS.length)];
        this.training = Math.floorMod(seed >>> 17, 3) == 0;
        for (int deg : new int[]{30, 90, 150, 210, 330}) {
            double a = Math.toRadians(deg);
            int hx = x + (int) Math.round(Math.cos(a) * RING), hz = z + (int) Math.round(Math.sin(a) * RING);
            double tx = -Math.cos(a), tz = -Math.sin(a);
            Direction front = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? Direction.EAST : Direction.WEST) : (tz > 0 ? Direction.SOUTH : Direction.NORTH);
            houses.add(new House(hx, hz, front, Canvas.hash(seed, deg, 2, 0) % 3 == 0));
        }
        layOutPlaces();
    }

    /** A village at a chosen site and ground level, without the terrain checks (tests). */
    public static NamekVillage at(LandmarkSites.Site site, int y0) {
        return new NamekVillage(site, y0);
    }

    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[25];
        int n = 0, wet = 0;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int px = s.x() + i * 16, pz = s.z() + j * 16, h = t.height(px, pz);
                if (t.wetOver(px, pz, h) && ++wet > 1) return null;
                hs[n++] = h;
            }
        Arrays.sort(hs);
        if (hs[21] - hs[3] > 8 || hs[12] <= t.seaLevel()) return null;
        return new NamekVillage(s, hs[12]);
    }

    @Override
    public String name() {
        return elder + "'s Village";
    }

    @Override
    public String style() {
        return "namek";
    }

    @Override
    public int reach() {
        return FLAT + BLEND;
    }

    @Override
    public BlockPos centre() {
        return new BlockPos(x, y0 + 1, z + 6);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + FLAT - 2);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.hypot(wx - x, wz - z) <= FLAT + BLEND ? y0 : Integer.MAX_VALUE;
    }

    /** Where the village's Dragon Ball rests: on the pedestal beside the elder's seat (com.dbzenith.dragonball.NamekBalls puts it there). */
    public BlockPos ballSpot() {
        return new BlockPos(x + BALL_DX, y0 + 2, z + ELDER_Z + BALL_DZ);
    }

    /** The block the ball rests on. */
    public static BlockState pedestal() {
        return s("namek_pillar");
    }

    /** The elder house's floor (a village drawn before the pedestals gets one set on it). */
    public static BlockState floor() {
        return s("namek_floor");
    }

    /** Its elder takes the village's elder's name (and the elder's house). */
    @Override
    public void settled(Settlement s, List<NamedFighter> residents) {
        for (NamedFighter c : residents)
            if (Occupation.of(c) == Occupation.ELDER) {
                c.name = elder;
                c.epithet = "the Elder";
                return;
            }
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void build(Canvas c) {
        int R = FLAT + BLEND;
        if (!c.intersects(x - R, z - R, x + R, z + R)) return;
        ground(c);
        paths(c);
        spring(c);
        grove(c);
        if (training) Shapes.disc(c, x + TX, z + TZ, y0, 4.5, Blocks.COARSE_DIRT.defaultBlockState());
        elderHouse(c);
        for (House h : houses) house(c, h);
        for (int k = 0; k < 6; k++) {                                                          // lamps round the spring
            double a = k * Math.PI / 3;                                                       // (between the paths)
            lamp(c, x + (int) Math.round(Math.cos(a) * 9), z + (int) Math.round(Math.sin(a) * 9));
        }
    }

    void ground(Canvas c) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
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
                c.ground(wx, wz, target, grass, dirt, d > FLAT ? 10 : 24);
            }
    }

    /** Trodden paths from the spring to each door. */
    void paths(Canvas c) {
        List<int[]> ends = new ArrayList<>();
        for (House h : houses) {
            WestCity.F f = new WestCity.F(h.hx, h.hz, y0, h.front);
            ends.add(new int[]{f.wx(0, -6), f.wz(0, -6)});
        }
        ends.add(new int[]{x, z + ELDER_Z + 8});
        ends.add(new int[]{x, z + FLAT - 1});
        BlockState path = Blocks.DIRT_PATH.defaultBlockState();
        for (int[] e : ends) {
            int steps = (int) Math.ceil(Math.hypot(e[0] - x, e[1] - z));
            for (int k = 0; k <= steps; k++) {
                double t = (double) k / steps;
                int px = (int) Math.round(x + (e[0] - x) * t), pz = (int) Math.round(z + (e[1] - z) * t);
                if (Math.hypot(px - x, pz - z) < 6) continue;
                for (int[] d : new int[][]{{0, 0}, {1, 0}, {0, 1}}) {
                    c.set(px + d[0], y0, pz + d[1], path);
                    c.set(px + d[0], y0 + 1, pz + d[1], Canvas.AIR);
                }
            }
        }
    }

    /** The spring: clear water ringed in white stone, a lamp on a pillar in its middle. */
    void spring(Canvas c) {
        if (!c.intersects(x - 7, z - 7, x + 7, z + 7)) return;
        Shapes.disc(c, x, z, y0, 5.5, s("namek_temple"));
        Shapes.disc(c, x, z, y0, 4.2, Blocks.WATER.defaultBlockState());
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) if (Shapes.inDisc(dx, dz, 4.2)) c.column(x + dx, z + dz, y0 - 2, y0 - 1, Blocks.WATER.defaultBlockState());
        Shapes.ring(c, x, z, y0 + 1, 5, 5.4, s("namek_temple_slab"));
        c.column(x, z, y0 - 2, y0 + 2, s("namek_pillar"));
        c.set(x, y0 + 3, z, s("namek_lamp"));
    }

    void lamp(Canvas c, int lx, int lz) {
        c.column(lx, lz, y0 + 1, y0 + 2, s("namek_temple_wall"));
        c.set(lx, y0 + 3, lz, s("namek_lamp"));
    }

    /** An Ajisa grove south-west: rows of slender trees with round crowns. */
    void grove(Canvas c) {
        for (int gx = -30; gx <= -14; gx += 5)
            for (int gz = 12; gz <= 30; gz += 5) {
                if (Math.hypot(gx, gz) > FLAT - 3 || Math.hypot(gx, gz) < 22) continue;
                ajisa(c, x + gx + Canvas.hash(seed, gx, 3, gz) % 2, z + gz, 4 + Canvas.hash(seed, gx, 4, gz) % 3);
            }
    }

    /** An Ajisa tree: a thin trunk and a round crown. */
    void ajisa(Canvas c, int tx, int tz, int height) {
        if (!c.intersects(tx - 3, tz - 3, tx + 3, tz + 3)) return;
        BlockState log = ModBlocks.NAMEK_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaf = ModBlocks.NAMEK_LEAVES.get().defaultBlockState();
        if (leaf.hasProperty(LeavesBlock.PERSISTENT)) leaf = leaf.setValue(LeavesBlock.PERSISTENT, true);
        c.column(tx, tz, y0 + 1, y0 + height, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    if (dx * dx + dz * dz + dy * dy * 1.6 <= 5.2) c.set(tx + dx, y0 + height + 1 + dy, tz + dz, leaf);
    }

    /** A Namekian house: a white dome rising from a blue band, round windows, a door toward the spring; inside, beds, water, a chest. */
    void house(Canvas c, House h) {
        if (!c.intersects(h.hx - 9, h.hz - 9, h.hx + 9, h.hz + 9)) return;
        WestCity.F f = new WestCity.F(h.hx, h.hz, y0, h.front);
        dome(c, h.hx, h.hz, 4.6, 7);
        if (h.twin) dome(c, f.wx(4, 3), f.wz(4, 3), 2.8, 4.5);                                  // a small dome beside it
        f.box(c, 0, 1, -6, 0, 2, -3, Canvas.AIR);
        door(c, f.wx(0, -4), f.wz(0, -4), f.dir(Direction.NORTH));
        bed(c, f, -2, 2, Direction.SOUTH);
        bed(c, f, 2, 2, Direction.SOUTH);
        f.set(c, 0, 1, 3, Blocks.WATER_CAULDRON.defaultBlockState());
        f.set(c, -3, 1, -1, Blocks.CHEST.defaultBlockState());
        f.set(c, 0, 5, 0, s("namek_lamp"));
    }

    /** A dome from the ground: shell, a blue band at its foot, a ring of round windows, a floor; hollow. */
    void dome(Canvas c, int cx, int cz, double r, double h) {
        Shapes.dome(c, cx, cz, y0 + 1, r, h, 1.1, s("namek_shell"));
        Shapes.ring(c, cx, cz, y0 + 1, r - 0.7, r + 0.1, s("namek_band"));
        int R = (int) Math.ceil(r) + 1;
        for (int wx = Math.max(c.minX, cx - R); wx <= Math.min(c.maxX, cx + R); wx++)
            for (int wz = Math.max(c.minZ, cz - R); wz <= Math.min(c.maxZ, cz + R); wz++) {
                double d = Math.hypot(wx - cx, wz - cz);
                double rk = r * Math.sqrt(1 - Math.pow(2.0 / h, 2));
                if (d <= rk + 0.4 && d >= rk - 0.9 && Math.floorMod(Shapes.arc(wx - cx, wz - cz, rk), 3) == 0) c.set(wx, y0 + 3, wz, s("namek_window"));
            }
        Shapes.disc(c, cx, cz, y0, r - 0.4, s("namek_floor"));
    }

    void door(Canvas c, int wx, int wz, Direction facing) {
        BlockState d = s("namek_door").setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        c.set(wx, y0 + 1, wz, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(wx, y0 + 2, wz, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    void bed(Canvas c, WestCity.F f, int lx, int lz, Direction headLocal) {
        Direction head = f.dir(headLocal);
        BlockState b = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, head);
        int wx = f.wx(lx, lz), wz = f.wz(lx, lz);
        c.set(wx - head.getStepX(), y0 + 1, wz - head.getStepZ(), b.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(wx, y0 + 1, wz, b.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** The elder's house: a great dome between two small ones, the elder's seat at the back, rugs, a bed and a chest in the side rooms. */
    void elderHouse(Canvas c) {
        int ex = x, ez = z + ELDER_Z;
        if (!c.intersects(ex - 12, ez - 9, ex + 12, ez + 9)) return;
        dome(c, ex, ez, 6.2, 9.5);
        dome(c, ex - 7, ez, 3.6, 5.5);
        dome(c, ex + 7, ez, 3.6, 5.5);
        c.box(ex - 6, y0 + 1, ez, ex + 6, y0 + 2, ez, Canvas.AIR);                            // through to the side rooms
        c.box(ex - 6, y0, ez, ex + 6, y0, ez, s("namek_floor"));
        c.box(ex, y0 + 1, ez + 5, ex, y0 + 2, ez + 7, Canvas.AIR);
        door(c, ex, ez + 6, Direction.SOUTH);
        for (int dx = -1; dx <= 1; dx++) c.set(ex + dx, y0 + 1, ez - 4, Canvas.stairs(s("namek_temple_stairs"), Direction.NORTH, false));   // the seat
        c.set(ex, y0 + 2, ez - 5, s("namek_pattern"));
        c.set(ex + BALL_DX, y0 + 1, ez + BALL_DZ, s("namek_pillar"));                       // the Dragon Ball's pedestal (the ball: NamekBalls)
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) <= 3) c.set(ex + dx, y0 + 1, ez + dz, Blocks.CYAN_CARPET.defaultBlockState());
        c.set(ex, y0 + 7, ez, s("namek_lamp"));
        BlockState bedState = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        c.set(ex - 7, y0 + 1, ez - 1, bedState.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(ex - 7, y0 + 1, ez - 2, bedState.setValue(BedBlock.PART, BedPart.HEAD));
        c.set(ex - 8, y0 + 1, ez + 1, Blocks.WATER_CAULDRON.defaultBlockState());
        c.set(ex + 7, y0 + 1, ez - 2, Blocks.CHEST.defaultBlockState());
        c.set(ex + 8, y0 + 1, ez, Blocks.BOOKSHELF.defaultBlockState());
    }

    // ------------------------------------------------------------------ the living world

    void layOutPlaces() {
        int ex = x, ez = z + ELDER_Z;
        place(Poi.Kind.HOME, ex - 7, y0 + 1, ez + 1, "the elder's house");                    // (in the west room)
        for (House h : houses) {
            WestCity.F f = new WestCity.F(h.hx, h.hz, y0, h.front);
            place(Poi.Kind.HOME, f.wx(0, 0), y0 + 1, f.wz(0, 0), "house");
        }
        place(Poi.Kind.PLAZA, ex, y0 + 1, ez - 3, "elder");                                    // the elder's seat
        for (int k = 0; k < 4; k++) {                                                           // round the spring
            double a = k * Math.PI / 2 + 0.8;
            place(Poi.Kind.PLAZA, x + (int) Math.round(Math.cos(a) * 7), y0 + 1, z + (int) Math.round(Math.sin(a) * 7), "spring");
        }
        for (int[] p : new int[][]{{-20, 18}, {-25, 14}, {-17, 26}, {-23, 23}}) place(Poi.Kind.FIELD, x + p[0], y0 + 1, z + p[1], "grove");
        place(Poi.Kind.PATROL, x + 12, y0 + 1, z, "lookout");
        place(Poi.Kind.PATROL, x - 11, y0 + 1, z + 2, "lookout");
        place(Poi.Kind.PATROL, x, y0 + 1, z + 26, "lookout");
        place(Poi.Kind.GATE, x, y0 + 1, z + FLAT - 2, "the way out");
        if (training) {
            place(Poi.Kind.DOJO, x + TX, y0 + 1, z + TZ, "master");
            place(Poi.Kind.DOJO, x + TX - 2, y0 + 1, z + TZ - 2, "floor");
            place(Poi.Kind.DOJO, x + TX + 2, y0 + 1, z + TZ + 2, "floor");
        }
        // the map: round the spring, along each path, the step before each door; every house a room
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            way(x + (int) Math.round(Math.cos(a) * 7.5), y0 + 1, z + (int) Math.round(Math.sin(a) * 7.5));
        }
        for (House h : houses) {
            WestCity.F f = new WestCity.F(h.hx, h.hz, y0, h.front);
            roundRoom(h.hx, h.hz, 5, y0, y0 + 8, way(f.wx(0, -6), y0 + 1, f.wz(0, -6)));
        }
        room(ex - 11, y0, ez - 7, ex + 11, y0 + 10, ez + 6, way(ex, y0 + 1, ez + 8));
        way(x, y0 + 1, z + 20);
        way(x, y0 + 1, z + FLAT - 3);
        // its people
        people.addAll(List.of("elder", "guard", "farmer", "farmer"));
        if (training) people.addAll(List.of("master", "student"));
        while (people.size() < 11) people.add("citizen");
    }
}
