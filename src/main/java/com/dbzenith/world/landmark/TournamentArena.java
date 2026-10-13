package com.dbzenith.world.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The World Martial Arts Tournament (CX-33), built to anime scale on a grass field about 260 blocks across:
 * <ul>
 *   <li>the ring: 41 x 41, raised three blocks, white beveled tiles, steps on the south side, lanterns on its corners;</li>
 *   <li>the stands all round it: fifteen tiers of seats behind a red-banded rail, aisle staircases, a lit concourse
 *       under the seats, a tunnel gate through the middle of each side, and a 25-block back wall with arches, windows,
 *       a red band, a parapet and banner poles;</li>
 *   <li>the temple north of the stands: a raised platform, a two-storey hall with layered clay-tile hip roofs, gold
 *       corner ornaments and finial, red lacquer pillars, an interior with columns, lanterns and a dais, and two
 *       three-tier pagoda towers;</li>
 *   <li>the great south gate, lamp-lit tile paths out to the field's edge, and palms all round.</li>
 * </ul>
 * North is -z. Heights are blocks above the field ({@link #y0}).
 */
public final class TournamentArena extends LandmarkPlan {
    // ---- sizes (blocks from the centre)
    public static final int RING = 20, RING_H = 3;
    public static final int STANDS_IN = 34, SEATS_OUT = 64, WALL_OUT = 67, WALL_H = 24;
    static final int GATE_HALF = 4, GATE_H = 8;
    static final int FIELD = 134, BLEND = 22;
    static final int TEMPLE_FRONT = -76, TEMPLE_BACK = -132, TEMPLE_HALF = 58;

    /** The field's level (the ground block's y). */
    final int y0;
    private final List<int[]> palms = new ArrayList<>();

    private TournamentArena(LandmarkSites.Site site, int y0) {
        super(site);
        this.y0 = y0;
        Random r = new Random(seed);
        for (int i = 0; i < 400 && palms.size() < 46; i++) {               // palms on the field round the stands
            int px = r.nextInt(2 * FIELD - 20) - FIELD + 10, pz = r.nextInt(2 * FIELD - 20) - FIELD + 10;
            int d = Math.max(Math.abs(px), Math.abs(pz));
            if (d < WALL_OUT + 6 || d > FIELD - 8) continue;
            if (Math.abs(px) < 9 || Math.abs(pz) < 9) continue;             // keep the paths clear
            if (pz < TEMPLE_FRONT + 4 && Math.abs(px) < TEMPLE_HALF + 6) continue;
            if (pz > 66 && pz < 90 && Math.abs(px) < 24) continue;          // the south gate
            boolean near = false;
            for (int[] p : palms) near |= Math.abs(p[0] - px) < 7 && Math.abs(p[1] - pz) < 7;
            if (!near) palms.add(new int[]{px, pz, 9 + r.nextInt(6), r.nextInt(16)});
        }
    }

    /** The site holds an arena if the field there is dry and fairly level. */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[25];
        int n = 0, wet = 0;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int x = s.x() + i * 50, z = s.z() + j * 50;
                if (t.wet(x, z)) wet++;                                              // a stream or pond is filled in
                hs[n++] = t.height(x, z);
            }
        java.util.Arrays.sort(hs);
        // the field is levelled to the median and eased into the land round it, so only cliffs and lakes rule a site out
        if (wet > 3 || hs[21] - hs[3] > 30 || hs[12] <= t.seaLevel()) return null;
        return new TournamentArena(s, hs[12]);
    }

    /** Where the fighters stand: the middle of the ring, on its tiles. */
    public BlockPos ringCentre() {
        return new BlockPos(x, y0 + RING_H + 1, z);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + 98);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.max(Math.abs(wx - x), Math.abs(wz - z)) <= FIELD ? y0 : Integer.MAX_VALUE;
    }

    static BlockState b(String id) {
        return Shapes.b(id);
    }

    @Override
    public void build(Canvas c) {
        if (!c.intersects(x - FIELD - BLEND, z - FIELD - BLEND, x + FIELD + BLEND, z + FIELD + BLEND)) return;
        columns(c);
        temple(c);
        southGate(c);
        dressing(c);
        c.spawn(com.dbzenith.npc.ModNpcs.TOURNAMENT_ANNOUNCER.get(), x - 6.5, y0 + 1, z + RING + 5.5, 180f);   // by the ring's steps
    }

    // ------------------------------------------------------------------ the field, ring, stands and walls, column by column

    private void columns(Canvas c) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = b("tournament_stone"), tiles = b("tournament_tiles"), trim = b("tournament_trim"), red = b("tournament_red_band");
        BlockState arena = b("arena_tile"), seat = b("tournament_stone_stairs"), aisle = b("tournament_tiles_stairs");
        BlockState rail = b("tournament_stone_wall"), lantern = b("temple_lantern");
        int span = FIELD + BLEND;
        for (int wx = Math.max(x - span, c.minX); wx <= Math.min(x + span, c.maxX); wx++)
            for (int wz = Math.max(z - span, c.minZ); wz <= Math.min(z + span, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                // ---- the ground: level inside the field, eased back to the land round it
                if (d > FIELD) {
                    int natural = c.surface(wx, wz);
                    double w = smooth((d - FIELD) / (double) BLEND);
                    int target = (int) Math.round(y0 + (natural - y0) * w);
                    if (target != natural) c.ground(wx, wz, target, grass, dirt, 24);
                    continue;
                }
                c.ground(wx, wz, y0, grass, dirt, 48);
                boolean ns = Math.abs(dz) >= Math.abs(dx);
                int along = ns ? dx : dz;
                Direction out = ns ? (dz < 0 ? Direction.NORTH : Direction.SOUTH) : (dx < 0 ? Direction.WEST : Direction.EAST);

                // ---- the ring
                if (d <= RING) {
                    c.column(wx, wz, y0 + 1, y0 + RING_H - 1, d == RING ? trim : stone);
                    c.set(wx, y0 + RING_H, wz, arena);
                    if (d == RING && Math.abs(dx) == RING && Math.abs(dz) == RING) {      // corner posts and lanterns
                        Shapes.pole(c, wx, y0 + RING_H + 1, wz, 3, rail);
                        c.set(wx, y0 + RING_H + 4, wz, lantern);
                    }
                    continue;
                }
                // the ring's steps (south) and a tiled apron round it
                if (dz > RING && dz <= RING + 3 && Math.abs(dx) <= 4) {
                    int h = RING + RING_H + 1 - dz;                                     // 3, 2, 1
                    c.column(wx, wz, y0 + 1, y0 + h - 1, stone);
                    c.set(wx, y0 + h, wz, Canvas.stairs(aisle, Direction.NORTH, false));
                    continue;
                }
                if (d <= RING + 3) {
                    c.set(wx, y0, wz, tiles);
                    continue;
                }
                // ---- tile paths along the axes, from the ring out to the field's edge
                if (Math.abs(along) <= 3 && d < STANDS_IN) {
                    c.set(wx, y0, wz, Math.abs(along) == 3 ? stone : tiles);
                    continue;
                }
                if (d < STANDS_IN) continue;                                            // grass

                boolean gate = Math.abs(along) <= GATE_HALF;
                if (d <= WALL_OUT) {
                    stands(c, wx, wz, d - STANDS_IN, along, out, gate, stone, tiles, trim, red, seat, aisle, rail, lantern);
                    continue;
                }
                // ---- outside the stands: paths from the gates to the field's edge
                if (Math.abs(along) <= 3) c.set(wx, y0, wz, Math.abs(along) == 3 ? stone : tiles);
            }
    }

    /** One column of the stands, {@code r} blocks out from their inner rail (0..33). */
    private void stands(Canvas c, int wx, int wz, int r, int along, Direction out, boolean gate, BlockState stone, BlockState tiles,
                        BlockState trim, BlockState red, BlockState seat, BlockState aisle, BlockState rail, BlockState lantern) {
        int seatsDepth = SEATS_OUT - STANDS_IN;                                       // 30
        int aisleMod = Math.floorMod(along + 12, 24);
        boolean isAisle = aisleMod <= 1 && !gate;
        if (r <= seatsDepth) {
            if (r == 0) {                                                             // the front rail with its red band
                if (!isAisle && !gate) {
                    c.column(wx, wz, y0 + 1, y0 + 2, stone);
                    c.set(wx, y0 + 3, wz, red);
                    c.set(wx, y0 + 4, wz, rail.setValue(net.minecraft.world.level.block.WallBlock.UP, Math.floorMod(along, 4) == 0));
                }
            } else {
                int tier = (r - 1) / 2, h = 3 + tier;                                // top block at y0 + h
                c.column(wx, wz, y0 + 1, y0 + h - 1, stone);
                boolean front = (r - 1) % 2 == 0;
                if (isAisle) c.set(wx, y0 + h, wz, Canvas.stairs(aisle, out, false));
                else c.set(wx, y0 + h, wz, front ? Canvas.stairs(seat, out, false) : tiles);
                // the concourse under the seats: tiled, lit, with pillars
                if (r >= 12 && r <= 28 && h >= 9) {
                    boolean pillar = r == 20 && Math.floorMod(along, 8) == 0;
                    if (!pillar) {
                        c.column(wx, wz, y0 + 1, y0 + 6, Canvas.AIR);
                        c.set(wx, y0, wz, tiles);
                        if ((r == 16 || r == 24) && Math.floorMod(along, 4) == 2) c.set(wx, y0 + 7, wz, lantern);
                    }
                }
            }
        } else {
            // the back wall (r 31..33): arches into the concourse, windows, the red band, a parapet
            int t = r - seatsDepth - 1;                                               // 0 inner .. 2 outer
            c.column(wx, wz, y0 + 1, y0 + WALL_H - 1, stone);
            int am = Math.floorMod(along, 16);
            boolean arch = (am >= 6 && am <= 10) && !gate;
            if (arch) {
                c.column(wx, wz, y0 + 1, y0 + 5, Canvas.AIR);
                c.set(wx, y0, wz, tiles);
                c.set(wx, y0 + 6, wz, trim);
            }
            int wm = Math.floorMod(along, 8);
            if (t == 2 && (wm == 3 || wm == 4 || wm == 5) && !gate) c.column(wx, wz, y0 + 10, y0 + 12, Canvas.AIR);
            c.column(wx, wz, y0 + 17, y0 + 19, red);
            c.set(wx, y0 + 16, wz, trim);
            c.set(wx, y0 + 20, wz, trim);
            c.set(wx, y0 + WALL_H, wz, trim);
            if (t == 2 && Math.floorMod(along, 3) != 0) c.set(wx, y0 + WALL_H + 1, wz, stone);   // merlons
            if (t == 2 && Math.floorMod(along, 16) == 0 && !gate) {
                BlockState[] cloth = {b("banner_cloth_red"), b("banner_cloth_blue"), b("banner_cloth_yellow"), b("banner_cloth_green")};
                Shapes.bannerPole(c, wx, y0 + WALL_H + 1, wz, 14, cloth[Math.floorMod(along / 16 + out.ordinal(), 4)], out);
            }
        }
        // ---- the gate tunnel through the middle of each side
        if (gate) {
            c.column(wx, wz, y0 + 1, y0 + GATE_H, Canvas.AIR);
            c.set(wx, y0, wz, tiles);
            c.set(wx, y0 + GATE_H + 1, wz, trim);
            if (Math.abs(along) == GATE_HALF && r % 6 == 3) c.set(wx, y0 + GATE_H, wz, lantern);
        } else if (Math.abs(along) == GATE_HALF + 1 && r > 0) {
            c.column(wx, wz, y0 + 1, y0 + GATE_H + 1, r > SEATS_OUT - STANDS_IN ? stone : trim);   // the tunnel's walls
            if (r >= 17 && r <= 23) c.column(wx, wz, y0 + 1, y0 + 5, Canvas.AIR);             // a doorway into the concourse
            if (r == 16 || r == 24) c.set(wx, y0 + 4, wz, lantern);
        }
        if (r == WALL_OUT - STANDS_IN && Math.abs(along) <= GATE_HALF + 2) {           // the gate's face: a gold band and banners
            c.column(wx, wz, y0 + GATE_H + 1, y0 + GATE_H + 2, b("temple_gold_trim"));
            if (Math.abs(along) == GATE_HALF + 2) c.column(wx, wz, y0 + GATE_H + 3, y0 + GATE_H + 8, b("banner_cloth_red"));
        }
    }

    // ------------------------------------------------------------------ the temple

    private void temple(Canvas c) {
        int ax = x - TEMPLE_HALF - 8, bx = x + TEMPLE_HALF + 8, az = z + TEMPLE_BACK - 8, bz = z + TEMPLE_FRONT + 4;
        if (!c.intersects(ax, az, bx, bz)) return;
        BlockState stone = b("tournament_stone"), tiles = b("tournament_tiles"), trim = b("tournament_trim");
        int floor = y0 + 4;
        // the platform and its front steps
        c.box(x - TEMPLE_HALF, y0 + 1, z + TEMPLE_BACK, x + TEMPLE_HALF, y0 + 3, z + TEMPLE_FRONT, stone);
        c.box(x - TEMPLE_HALF, y0 + 3, z + TEMPLE_BACK, x + TEMPLE_HALF, y0 + 3, z + TEMPLE_FRONT, tiles);
        for (int wx = x - TEMPLE_HALF; wx <= x + TEMPLE_HALF; wx++) {
            c.column(wx, z + TEMPLE_FRONT, y0 + 1, y0 + 2, trim);
            c.column(wx, z + TEMPLE_BACK, y0 + 1, y0 + 2, trim);
        }
        BlockState steps = b("tournament_tiles_stairs");
        for (int k = 1; k <= 3; k++) c.box(x - 22, y0 + 4 - k, z + TEMPLE_FRONT + k, x + 22, y0 + 4 - k, z + TEMPLE_FRONT + k, Canvas.stairs(steps, Direction.NORTH, false));
        for (int k = 1; k <= 2; k++) c.box(x - 22, y0 + 1, z + TEMPLE_FRONT + k, x + 22, y0 + 3 - k, z + TEMPLE_FRONT + k, stone);

        // the great hall
        // (each roof's eave sits low enough that, rising a block a step, it meets the walls' top right over them)
        hall(c, x - 40, z - 124, x + 40, z - 88, floor, 14);
        Shapes.hipRoof(c, x - 47, z - 131, x + 47, z - 81, floor + 8, "clay_roof_tiles", 2, b("temple_gold_trim"), 8);
        Shapes.roofCorners(c, x - 47, z - 131, x + 47, z - 81, floor + 8);
        // the upper storey (a balcony all round it on the lower roof) and its roof
        int up = floor + 17;
        hall(c, x - 24, z - 116, x + 24, z - 96, up, 9);
        Shapes.hipRoof(c, x - 31, z - 122, x + 31, z - 90, up + 4, "clay_roof_tiles", 2, b("temple_gold_trim"));
        Shapes.roofCorners(c, x - 31, z - 122, x + 31, z - 90, up + 4);
        int ridgeTop = up + 4 + 16 + 1;
        c.column(x, z - 106, ridgeTop, ridgeTop + 6, b("temple_gold"));                 // the finial
        c.set(x, ridgeTop + 7, z - 106, b("temple_lantern"));
        // the throne dais inside, at the back of the hall
        c.box(x - 10, floor, z - 123, x + 10, floor + 1, z - 117, b("temple_gold_trim"));
        c.box(x - 9, floor + 2, z - 123, x + 9, floor + 2, z - 118, tiles);
        c.box(x - 2, floor + 3, z - 123, x + 2, floor + 6, z - 122, b("temple_gold"));
        for (int k = -1; k <= 1; k++) c.column(x + k * 8, z - 123, floor + 5, floor + 12, b("banner_cloth_red"));
        // pagoda towers either side
        pagoda(c, x - 50, z - 112, floor);
        pagoda(c, x + 50, z - 112, floor);
    }

    /**
     * A hall's walls and floor: plaster between red lacquer pillars every six blocks, a trim base and a red-and-gold
     * band under the eaves, tall windows, a door in the middle of the south wall, columns and lanterns inside.
     */
    private void hall(Canvas c, int x0, int z0, int x1, int z1, int floor, int height) {
        if (!c.intersects(x0, z0, x1, z1)) return;
        BlockState plaster = b("temple_plaster"), pillar = b("temple_red_pillar").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState band = b("temple_gold_trim"), trim = b("tournament_trim"), tiles = b("tournament_tiles"), lantern = b("temple_lantern");
        int cxm = (x0 + x1) / 2;
        c.box(x0, floor, z0, x1, floor, z1, tiles);
        c.box(x0 + 1, floor + 1, z0 + 1, x1 - 1, floor + height, z1 - 1, Canvas.AIR);
        for (int wx = Math.max(x0, c.minX); wx <= Math.min(x1, c.maxX); wx++)
            for (int wz = Math.max(z0, c.minZ); wz <= Math.min(z1, c.maxZ); wz++) {
                boolean edgeX = wx == x0 || wx == x1, edgeZ = wz == z0 || wz == z1;
                if (!edgeX && !edgeZ) {
                    // inside: a grid of columns, lanterns hanging between them
                    boolean col = Math.floorMod(wx - x0, 12) == 6 && Math.floorMod(wz - z0, 10) == 5 && wx > x0 + 3 && wx < x1 - 3 && wz > z0 + 3 && wz < z1 - 3;
                    if (col) c.column(wx, wz, floor + 1, floor + height, pillar);
                    if (Math.floorMod(wx - x0, 12) == 0 && Math.floorMod(wz - z0, 10) == 0) c.set(wx, floor + height - 2, wz, lantern);
                    // lamps bracketed to every column, at head height and under the beams
                    int colX = Math.floorMod(wx - x0, 12), colZ = Math.floorMod(wz - z0, 10);
                    if ((colX == 5 || colX == 7) && colZ == 5 || colX == 6 && (colZ == 4 || colZ == 6)) {
                        int ox = wx + (colX == 5 ? 1 : colX == 7 ? -1 : 0), oz = wz + (colZ == 4 ? 1 : colZ == 6 ? -1 : 0);
                        if (ox > x0 + 3 && ox < x1 - 3 && oz > z0 + 3 && oz < z1 - 3) {
                            c.set(wx, floor + 4, wz, lantern);
                            c.set(wx, floor + height - 3, wz, lantern);
                        }
                    }
                    continue;
                }
                int along = edgeZ ? wx - x0 : wz - z0;
                boolean post = Math.floorMod(along, 6) == 0 || (edgeX && edgeZ);
                c.column(wx, wz, floor + 1, floor + height, post ? pillar : plaster);
                c.set(wx, floor + 1, wz, post ? pillar : trim);
                c.column(wx, wz, floor + height - 1, floor + height, post ? pillar : band);
                int m = Math.floorMod(along, 6);
                if (!post && m >= 2 && m <= 4 && !(edgeX && edgeZ)) c.column(wx, wz, floor + 4, floor + height - 4, Canvas.AIR);   // windows
            }
        // the door, framed in gold
        c.box(cxm - 4, floor + 1, z1, cxm + 4, floor + Math.min(height - 3, 9), z1, Canvas.AIR);
        c.column(cxm - 5, z1, floor + 1, floor + Math.min(height - 3, 9) + 1, b("temple_gold"));
        c.column(cxm + 5, z1, floor + 1, floor + Math.min(height - 3, 9) + 1, b("temple_gold"));
        c.box(cxm - 5, floor + Math.min(height - 3, 9) + 1, z1, cxm + 5, floor + Math.min(height - 3, 9) + 1, z1, b("temple_gold"));
        c.set(cxm - 6, floor + 4, z1 + 1, lantern);
        c.set(cxm + 6, floor + 4, z1 + 1, lantern);
    }

    /** A three-tier pagoda on the platform: each storey a little smaller under its own flared roof. */
    private void pagoda(Canvas c, int px, int pz, int floor) {
        if (!c.intersects(px - 10, pz - 10, px + 10, pz + 10)) return;
        int y = floor;
        int top = y;
        for (int tier = 0; tier < 3; tier++) {
            int half = 5 - tier;
            hall(c, px - half, pz - half, px + half, pz + half, y, 6);
            int eave = y + 4;                                                         // meets the wall's top (y + 6) over it
            boolean last = tier == 2;
            Shapes.hipRoof(c, px - half - 3, pz - half - 3, px + half + 3, pz + half + 3, eave, "clay_roof_tiles", 1, b("temple_gold_trim"), last ? Integer.MAX_VALUE : 4);
            Shapes.roofCorners(c, px - half - 3, pz - half - 3, px + half + 3, pz + half + 3, eave);
            top = eave + half + 4;
            y = eave + 5;
        }
        c.column(px, pz, top, top + 5, b("temple_gold"));
        c.set(px, top + 6, pz, b("temple_lantern"));
    }

    // ------------------------------------------------------------------ the south gate and the dressing

    private void southGate(Canvas c) {
        int z0 = z + 72, z1 = z + 82;
        if (!c.intersects(x - 22, z0 - 4, x + 22, z1 + 4)) return;
        BlockState pillar = b("temple_red_pillar").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int side : new int[]{-1, 1}) {
            int px = x + side * 13;
            c.box(px - 2, y0 + 1, z0 + 3, px + 2, y0 + 18, z0 + 7, pillar);
            c.box(px - 3, y0 + 1, z0 + 2, px + 3, y0 + 2, z0 + 8, b("tournament_trim"));
            c.set(px, y0 + 6, z0 + 1, b("temple_lantern"));
            c.set(px, y0 + 6, z0 + 9, b("temple_lantern"));
        }
        c.box(x - 17, y0 + 15, z0 + 3, x + 17, y0 + 18, z0 + 7, b("temple_gold_trim"));
        c.box(x - 17, y0 + 19, z0 + 3, x + 17, y0 + 19, z0 + 7, b("tournament_trim"));
        Shapes.hipRoof(c, x - 20, z0 - 1, x + 20, z1 + 1, y0 + 20, "clay_roof_tiles", 1, b("temple_gold_trim"));
        Shapes.roofCorners(c, x - 20, z0 - 1, x + 20, z1 + 1, y0 + 20);
    }

    private void dressing(Canvas c) {
        for (int[] p : palms) Shapes.palm(c, x + p[0], y0 + 1, z + p[1], p[2], p[3]);
        BlockState rail = b("tournament_stone_wall"), lantern = b("temple_lantern");
        // lamp posts along the four paths outside the stands
        for (int s = WALL_OUT + 6; s <= FIELD - 4; s += 12)
            for (int side : new int[]{-1, 1}) {
                int[][] spots = {{x + side * 5, z + s}, {x + side * 5, z - s}, {x + s, z + side * 5}, {x - s, z + side * 5}};
                for (int[] p : spots) {
                    if (p[1] < z + TEMPLE_FRONT + 4 && Math.abs(p[0] - x) < TEMPLE_HALF + 6) continue;
                    if (!c.contains(p[0], p[1])) continue;
                    Shapes.pole(c, p[0], y0 + 1, p[1], 4, rail);
                    c.set(p[0], y0 + 5, p[1], lantern);
                }
            }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
