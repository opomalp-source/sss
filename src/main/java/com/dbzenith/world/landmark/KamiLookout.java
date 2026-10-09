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
 * Korin Tower and Kami's Lookout (CX-33): a colossal carved grey tower rising out of the land, climbable all the way
 * by a stair winding round it, Korin's sanctuary swelling out at its top (a balcony round a domed room under a spire),
 * and high above it, hanging in the sky over the tower with its pole pointing down at the spire, the Lookout: a round
 * island banded red, white with long glowing windows, red again, then a teal hull curving down to the pole. On its
 * tiled deck stand Kami's palace (a gold onion dome over a round hall ringed by pink awnings, two smaller domed towers
 * and the wings joining them, all with rooms inside), tall white lamp towers round the edge, an avenue of cypresses to
 * the palace steps, a palm grove and a pool. Piccolo keeps the Lookout.
 * <p>
 * Heights are absolute (the Lookout hangs at the same height everywhere); offsets are from the site's centre, north
 * is -z.
 */
final class KamiLookout extends LandmarkPlan {
    // ---- the Lookout
    /** The deck's tiles. */
    static final int DECK = 276;
    /** The deck's radius. */
    static final int R = 56;
    /** Depths under the deck of the bands round the rim: red (1-3), trim, white with windows (5-12), trim, red (14-15). */
    static final int RED_TOP = 1, RED_BOT = 3, WHITE_TOP = 5, WHITE_BOT = 12, RED2_TOP = 14, RED2_BOT = 15;
    /** The teal hull below the bands: how deep it curves down (to the pole's radius) and how long the pole is. */
    static final int HULL_TOP = 16, HULL_DEPTH = 44, POLE = 12, POLE_R = 3;
    /** The palace platform's floor (you stand a block above). */
    static final int FLOOR = DECK + 3;
    static final int HALL_Z = -24, HALL_R = 11, HALL_H = 12, TOWER_X = 24, TOWER_R = 5, TOWER_H = 22;
    static final int PLATFORM_X = 36, PLATFORM_N = -44, PLATFORM_S = -4;
    static final int LAMP_RING = 48, LAMP_H = 22;

    // ---- Korin Tower
    static final int SHAFT_R = 6;
    /** Korin's floor: the balcony and the room on the tower's top. */
    static final int KORIN_FLOOR = 170;
    /** Where the tower starts to swell out into the sanctuary's underside, and how wide the balcony reaches. */
    static final int BULB_BOTTOM = KORIN_FLOOR - 14, SANCTUARY_R = 13, ROOM_R = 9, ROOM_H = 7;
    static final int PLINTH = 15, BLEND = 16;

    /** The ground at the tower's foot. */
    final int g;
    /** The stair round the tower, bottom to top: dx, dz, y, facing (the way up). */
    private final List<int[]> stair = new ArrayList<>();
    private final List<int[]> palms = new ArrayList<>();

    private KamiLookout(LandmarkSites.Site site, int g) {
        super(site);
        this.g = g;
        layStair();
        Random r = new Random(seed);
        for (int i = 0; i < 200 && palms.size() < 8; i++) {                 // the grove east of the avenue
            int px = 34 + r.nextInt(19) - 9, pz = 16 + r.nextInt(19) - 9;
            if (!Shapes.inDisc(px - 34, pz - 16, 8.5)) continue;
            boolean near = false;
            for (int[] p : palms) near |= Math.abs(p[0] - px) < 4 && Math.abs(p[1] - pz) < 4;
            if (!near) palms.add(new int[]{px, pz, 8 + r.nextInt(5), r.nextInt(16)});
        }
    }

    /** The tower stands where the ground round its foot is dry, fairly level and low enough to leave the tower tall. */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        if (t.wet(s.x(), s.z())) return null;
        int[] hs = new int[9];
        int n = 0;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) hs[n++] = t.height(s.x() + i * 14, s.z() + j * 14);
        java.util.Arrays.sort(hs);
        if (hs[7] - hs[1] > 14 || hs[4] <= t.seaLevel() || hs[4] > KORIN_FLOOR - 70) return null;
        return new KamiLookout(s, hs[4]);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, DECK + 1, z + R - 8);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Shapes.inDisc(wx - x, wz - z, PLINTH + 4) ? g : Integer.MAX_VALUE;
    }

    static BlockState b(String id) {
        return Shapes.b(id);
    }

    @Override
    public void build(Canvas c) {
        if (c.intersects(x - PLINTH - BLEND, z - PLINTH - BLEND, x + PLINTH + BLEND, z + PLINTH + BLEND)) {
            foot(c);
            tower(c);
            sanctuary(c);
            climb(c);
        }
        if (c.intersects(x - R - 2, z - R - 2, x + R + 2, z + R + 2)) {
            hull(c);
            deck(c);
            palace(c);
            gardens(c);
            c.spawn(com.dbzenith.npc.ModNpcs.MASTERS.get(com.dbzenith.style.MasterRoster.PICCOLO).get(), x + 6.5, FLOOR + 1, z + HALL_Z + HALL_R + 3.5, 180f);
        }
    }

    // ================================================================== Korin Tower

    /** The stair: the ring of cells hugging the shaft, walked round anticlockwise, one block up per step. */
    private void layStair() {
        // the ring just outside the shaft, in order round it
        List<int[]> ring = new ArrayList<>();
        for (int dx = -SHAFT_R - 2; dx <= SHAFT_R + 2; dx++)
            for (int dz = -SHAFT_R - 2; dz <= SHAFT_R + 2; dz++) {
                if (Shapes.inDisc(dx, dz, SHAFT_R)) continue;
                boolean touches = false;
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) touches |= Shapes.inDisc(dx + i, dz + j, SHAFT_R);
                if (touches) ring.add(new int[]{dx, dz});
            }
        // walk it: from the south cell, always to the next unvisited side-neighbour round the circle
        List<int[]> loop = new ArrayList<>();
        int[] cur = null;
        for (int[] p : ring) if (p[0] == 0 && p[1] > 0) cur = p;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        while (cur != null) {
            loop.add(cur);
            seen.add(key(cur));
            int[] next = null;
            double best = Double.MAX_VALUE, a0 = Math.atan2(cur[1], cur[0]);
            for (int[] p : ring) {
                if (seen.contains(key(p)) || Math.abs(p[0] - cur[0]) + Math.abs(p[1] - cur[1]) != 1) continue;
                double da = Math.floorMod((long) Math.round((Math.atan2(p[1], p[0]) - a0) * 1000), Math.round(2 * Math.PI * 1000));
                if (da < best) {
                    best = da;
                    next = p;
                }
            }
            cur = next;
        }
        int n = loop.size(), steps = KORIN_FLOOR - g;                        // from g+1 up to the floor
        for (int k = 0; k < steps; k++) {
            int[] at = loop.get(Math.floorMod(k - (steps - 1), n)), to = loop.get(Math.floorMod(k - (steps - 1) + 1, n));
            Direction up = Direction.fromDelta(to[0] - at[0], 0, to[1] - at[1]);
            if (up == null) up = Direction.NORTH;
            stair.add(new int[]{at[0], at[1], g + 1 + k, up.ordinal()});
        }
    }

    private static long key(int[] p) {
        return ((long) p[0] << 32) ^ (p[1] & 0xffffffffL);
    }

    /** The ground round the foot: a round plinth of carved stone, eased into the land, with standing stones. */
    private void foot(Canvas c) {
        BlockState bricks = b("korin_bricks"), stone = b("korin_stone"), panel = b("korin_panel");
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        int span = PLINTH + BLEND;
        for (int wx = Math.max(x - span, c.minX); wx <= Math.min(x + span, c.maxX); wx++)
            for (int wz = Math.max(z - span, c.minZ); wz <= Math.min(z + span, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                double d = Math.hypot(dx, dz);
                if (d > span) continue;
                if (d <= PLINTH + 0.5) {
                    boolean rim = Shapes.onRim(dx, dz, PLINTH), ring = Math.abs(d - 10) < 0.6;
                    c.ground(wx, wz, g, rim || ring ? stone : bricks, stone, 40);
                    continue;
                }
                int natural = c.surface(wx, wz);
                double w = smooth((d - PLINTH) / BLEND);
                int target = (int) Math.round(g + (natural - g) * w);
                if (target != natural) c.ground(wx, wz, target, grass, dirt, 30);
            }
        // eight standing stones round the plinth, carved, with lamps
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) * Math.PI / 4;
            int sx = x + (int) Math.round(Math.cos(a) * 12.5), sz = z + (int) Math.round(Math.sin(a) * 12.5);
            c.column(sx, sz, g + 1, g + 6, panel);
            c.set(sx, g + 7, sz, b("korin_stone_slab"));
            c.set(sx, g + 1, sz, stone);
        }
    }

    /** The shaft: ribbed and panelled, ringed with bands every sixteen blocks, up to the sanctuary. */
    private void tower(Canvas c) {
        BlockState bricks = b("korin_bricks"), stone = b("korin_stone"), panel = b("korin_panel");
        for (int wx = Math.max(x - SHAFT_R, c.minX); wx <= Math.min(x + SHAFT_R, c.maxX); wx++)
            for (int wz = Math.max(z - SHAFT_R, c.minZ); wz <= Math.min(z + SHAFT_R, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                if (!Shapes.inDisc(dx, dz, SHAFT_R)) continue;
                if (!Shapes.onRim(dx, dz, SHAFT_R)) {
                    c.column(wx, wz, g - 3, BULB_BOTTOM, stone);
                    continue;
                }
                boolean rib = Math.floorMod(Shapes.arc(dx, dz, SHAFT_R), 4) == 0;
                for (int y = g - 3; y <= BULB_BOTTOM; y++) {
                    int band = Math.floorMod(y - g, 16);
                    BlockState s = y <= g + 3 ? stone : band <= 1 ? stone : band >= 14 ? bricks : rib ? bricks : panel;
                    c.set(wx, y, wz, s);
                }
            }
    }

    /** Korin's sanctuary: the swelling underside, the balcony, the round room, its dome and the spire. */
    private void sanctuary(Canvas c) {
        BlockState bricks = b("korin_bricks"), stone = b("korin_stone"), panel = b("korin_panel");
        int top = KORIN_FLOOR;
        int reach = SANCTUARY_R + 1;
        for (int wx = Math.max(x - reach, c.minX); wx <= Math.min(x + reach, c.maxX); wx++)
            for (int wz = Math.max(z - reach, c.minZ); wz <= Math.min(z + reach, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                // the underside: swelling from the shaft out to the balcony's edge, banded
                for (int y = BULB_BOTTOM; y < top; y++) {
                    double f = (y - BULB_BOTTOM) / (double) (top - BULB_BOTTOM);
                    double rad = SHAFT_R + (SANCTUARY_R - SHAFT_R) * Math.sin(f * Math.PI / 2);
                    if (!Shapes.inDisc(dx, dz, rad)) continue;
                    boolean rim = Shapes.onRim(dx, dz, rad);
                    c.set(wx, y, wz, !rim ? stone : (top - y) % 4 == 1 ? stone : Math.floorMod(Shapes.arc(dx, dz, rad), 3) == 0 ? bricks : panel);
                }
                if (!Shapes.inDisc(dx, dz, SANCTUARY_R)) continue;
                // the floor: bricks, a carved ring, a stone edge
                double d = Math.hypot(dx, dz);
                c.set(wx, top, wz, Shapes.onRim(dx, dz, SANCTUARY_R) || Math.abs(d - 5) < 0.6 ? stone : bricks);
                c.column(wx, wz, top + 1, top + ROOM_H + 9, Canvas.AIR);
                // the room's wall, with windows and doors out to the balcony
                if (Shapes.onRim(dx, dz, ROOM_R)) {
                    boolean door = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
                    boolean window = !door && Math.floorMod(Shapes.arc(dx, dz, ROOM_R), 7) == 3;
                    for (int y = top + 1; y <= top + ROOM_H; y++) {
                        BlockState s = y == top + 1 || y == top + ROOM_H ? stone : bricks;
                        if (door && y <= top + 4) s = Canvas.AIR;
                        else if (window && y >= top + 3 && y <= top + 5) s = Canvas.AIR;
                        else if (y == top + 5 && Math.floorMod(Shapes.arc(dx, dz, ROOM_R), 7) == 0 && !door) s = b("temple_lantern");
                        c.set(wx, y, wz, s);
                    }
                }
            }
        Shapes.circleWall(c, x, z, top + 1, SANCTUARY_R, b("korin_stone_wall"), 8, b("temple_lantern"));
        // the dome over the room and the spire on it
        Shapes.dome(c, x, z, top + ROOM_H + 1, ROOM_R + 1, 6, 1.5, stone);
        Shapes.ring(c, x, z, top + ROOM_H + 1, ROOM_R - 0.5, ROOM_R + 1, panel);
        int sp = top + ROOM_H + 8;
        Shapes.disc(c, x, z, sp, 2, stone);
        for (int k = 1; k <= 3; k++) Shapes.disc(c, x, z, sp + k, 1.5, bricks);
        for (int k = 4; k <= 7; k++) Shapes.disc(c, x, z, sp + k, 0.5, panel);
        c.column(x, z, sp + 8, sp + 10, b("temple_gold"));
        c.set(x, sp + 11, z, b("temple_lantern"));
        // inside: the pedestal where the Sacred Water once stood, and lamps hanging over it
        c.set(x, top + 1, z, panel);
        c.set(x, top + 2, z, b("temple_gold"));
        for (Direction d : Direction.Plane.HORIZONTAL) c.set(x + d.getStepX() * 4, top + ROOM_H, z + d.getStepZ() * 4, b("lookout_lamp"));
    }

    /** The stair winding up the tower: stone steps with a railing post every few, cut through the sanctuary's underside. */
    private void climb(Canvas c) {
        BlockState step = b("korin_stone_stairs"), rail = b("korin_stone_wall");
        for (int i = 0; i < stair.size(); i++) {
            int[] s = stair.get(i);
            int wx = x + s[0], wz = z + s[1], y = s[2];
            if (!c.intersects(wx - 1, wz - 1, wx + 1, wz + 1)) continue;
            c.set(wx, y, wz, Canvas.stairs(step, Direction.values()[s[3]], false));
            if (y >= BULB_BOTTOM - 3) c.column(wx, wz, y + 1, y + 3, Canvas.AIR);    // the tunnel through the underside
            if (i % 3 == 0 && y < BULB_BOTTOM - 3) {                                // a railing post on the outside
                int ox = Integer.signum(s[0]) * (Math.abs(s[0]) >= Math.abs(s[1]) ? 1 : 0), oz = Integer.signum(s[1]) * (Math.abs(s[1]) > Math.abs(s[0]) ? 1 : 0);
                Shapes.pole(c, wx + ox, y, wz + oz, 2, rail);
            }
            if (y >= BULB_BOTTOM && i % 6 == 0) {                                   // lamps in the tunnel, set in the shaft side
                int ix = -Integer.signum(s[0]) * (Math.abs(s[0]) >= Math.abs(s[1]) ? 1 : 0), iz = -Integer.signum(s[1]) * (Math.abs(s[1]) > Math.abs(s[0]) ? 1 : 0);
                c.set(wx + ix, y + 2, wz + iz, b("temple_lantern"));
            }
        }
    }

    // ================================================================== Kami's Lookout

    /** The island under the deck: the rim's bands, the teal hull curving down, and the pole. */
    private void hull(Canvas c) {
        BlockState red = b("lookout_red_band"), white = b("lookout_hull_white"), window = b("lookout_hull_window");
        BlockState trim = b("lookout_hull_trim"), teal = b("lookout_hull_teal"), gold = b("lookout_gold_dome");
        int reach = R + 2;
        for (int wx = Math.max(x - reach, c.minX); wx <= Math.min(x + reach, c.maxX); wx++)
            for (int wz = Math.max(z - reach, c.minZ); wz <= Math.min(z + reach, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                if (!Shapes.inDisc(dx, dz, R + 1)) continue;
                for (int depth = 1; depth <= HULL_TOP + HULL_DEPTH; depth++) {
                    double rad = radiusAt(depth);
                    if (!Shapes.inDisc(dx, dz, rad)) {
                        if (depth >= HULL_TOP) break;                                  // the hull only narrows from here
                        continue;
                    }
                    boolean rim = Shapes.onRim(dx, dz, rad);
                    BlockState s;
                    if (depth <= RED_BOT) s = red;
                    else if (depth == 4 || depth == WHITE_BOT + 1) s = trim;
                    else if (depth <= WHITE_BOT) {
                        int a = Math.floorMod(Shapes.arc(dx, dz, rad), 9);
                        s = rim && depth >= WHITE_TOP + 1 && depth <= WHITE_BOT - 1 && a >= 3 && a <= 5 ? window : white;
                    } else if (depth <= RED2_BOT) s = red;
                    else s = rim && (depth - HULL_TOP) % 9 == 8 ? trim : teal;
                    c.set(wx, DECK - depth, wz, s);
                }
            }
        // the pole, banded in gold, pointing down at Korin's spire
        int bottom = DECK - HULL_TOP - HULL_DEPTH;
        for (int k = 1; k <= POLE; k++) {
            boolean ring = k % 4 == 0;
            Shapes.disc(c, x, z, bottom - k, k > POLE - 3 ? 0.5 : ring ? 1.6 : 1, k > POLE - 3 || ring ? gold : trim);
        }
    }

    /** The island's radius at a depth under the deck. */
    static double radiusAt(int depth) {
        if (depth <= 4) return R + 1;
        if (depth <= WHITE_BOT + 1) return R;
        if (depth <= RED2_BOT) return R - 1;
        double t = (depth - HULL_TOP) / (double) HULL_DEPTH;
        return POLE_R + (R - 2 - POLE_R) * Math.pow(1 - t, 1.25) * (1 + 0.6 * t);     // a shallow bowl drawing in to a point
    }

    /** The deck: tiles with marble walks (an outer ring, a ring round the middle, the avenue), the balustrade, its lamps. */
    private void deck(Canvas c) {
        BlockState tiles = b("lookout_deck_tiles"), marble = b("lookout_marble"), trim = b("lookout_hull_trim");
        for (int wx = Math.max(x - R - 1, c.minX); wx <= Math.min(x + R + 1, c.maxX); wx++)
            for (int wz = Math.max(z - R - 1, c.minZ); wz <= Math.min(z + R + 1, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                if (!Shapes.inDisc(dx, dz, R + 1)) continue;
                double d = Math.hypot(dx, dz);
                BlockState s;
                if (!Shapes.inDisc(dx, dz, R)) s = trim;                             // the lip
                else if (d > R - 4 && d <= R - 2) s = marble;
                else if (Math.abs(d - 30) <= 1) s = marble;
                else if (Math.abs(dx) <= 3 && dz > 0) s = Math.abs(dx) == 3 ? trim : marble;
                else s = tiles;
                c.set(wx, DECK, wz, s);
                c.column(wx, wz, DECK + 1, DECK + 3, Canvas.AIR);
            }
        Shapes.circleWall(c, x, z, DECK + 1, R, b("lookout_marble_wall"), 11, b("lookout_lamp"));
    }

    /** Kami's palace on its platform at the north of the deck. */
    private void palace(Canvas c) {
        if (!c.intersects(x - PLATFORM_X - 2, z + PLATFORM_N - 2, x + PLATFORM_X + 2, z + PLATFORM_S + 4)) return;
        BlockState marble = b("lookout_marble"), tiles = b("lookout_deck_tiles"), trim = b("lookout_hull_trim");
        // the platform, its steps and balustrade
        c.box(x - PLATFORM_X, DECK + 1, z + PLATFORM_N, x + PLATFORM_X, FLOOR - 1, z + PLATFORM_S, marble);
        c.box(x - PLATFORM_X, FLOOR, z + PLATFORM_N, x + PLATFORM_X, FLOOR, z + PLATFORM_S, tiles);
        for (int wx = x - PLATFORM_X; wx <= x + PLATFORM_X; wx++) c.set(wx, DECK + 1, z + PLATFORM_S, trim);
        BlockState steps = b("lookout_marble_stairs");
        for (int k = 1; k <= 3; k++)
            c.box(x - 9, FLOOR - k + 1, z + PLATFORM_S + k, x + 9, FLOOR - k + 1, z + PLATFORM_S + k, Canvas.stairs(steps, Direction.NORTH, false));
        for (int k = 1; k <= 2; k++) c.box(x - 9, DECK + 1, z + PLATFORM_S + k, x + 9, FLOOR - k, z + PLATFORM_S + k, marble);
        BlockState rail = b("lookout_marble_wall");
        Shapes.wallLine(c, x - PLATFORM_X, z + PLATFORM_S, x - 10, z + PLATFORM_S, FLOOR + 1, rail);
        Shapes.wallLine(c, x + 10, z + PLATFORM_S, x + PLATFORM_X, z + PLATFORM_S, FLOOR + 1, rail);
        Shapes.wallLine(c, x - PLATFORM_X, z + PLATFORM_N, x - PLATFORM_X, z + PLATFORM_S, FLOOR + 1, rail);
        Shapes.wallLine(c, x + PLATFORM_X, z + PLATFORM_N, x + PLATFORM_X, z + PLATFORM_S, FLOOR + 1, rail);
        Shapes.wallLine(c, x - PLATFORM_X, z + PLATFORM_N, x + PLATFORM_X, z + PLATFORM_N, FLOOR + 1, rail);
        for (int sx : new int[]{-10, 10}) {
            c.set(x + sx, FLOOR + 1, z + PLATFORM_S, marble);
            c.set(x + sx, FLOOR + 2, z + PLATFORM_S, b("lookout_lamp"));
        }

        wings(c);
        hall(c);
        for (int side : new int[]{-1, 1}) tower(c, x + side * TOWER_X, z + HALL_Z);
        // passages from the hall through the wings into the towers
        for (int side : new int[]{-1, 1})
            c.box(x + side * (HALL_R - 2), FLOOR + 1, z + HALL_Z - 1, x + side * (TOWER_X - TOWER_R + 2), FLOOR + 4, z + HALL_Z + 1, Canvas.AIR);
    }

    /** The great round hall: a colonnade under a pink awning, glowing windows, the drum and the gold onion dome. */
    private void hall(Canvas c) {
        int cx = x, cz = z + HALL_Z;
        BlockState marble = b("lookout_marble"), pillar = b("lookout_marble_pillar").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState red = b("lookout_red_band"), trim = b("lookout_hull_trim"), window = b("lookout_hull_window"), tiles = b("lookout_deck_tiles");
        BlockState awning = b("lookout_awning"), awningStairs = b("lookout_awning_stairs"), gold = b("lookout_gold_dome"), lamp = b("lookout_lamp");
        int reach = HALL_R + 4;
        if (!c.intersects(cx - reach, cz - reach, cx + reach, cz + reach)) return;
        for (int wx = Math.max(cx - reach, c.minX); wx <= Math.min(cx + reach, c.maxX); wx++)
            for (int wz = Math.max(cz - reach, c.minZ); wz <= Math.min(cz + reach, c.maxZ); wz++) {
                int dx = wx - cx, dz = wz - cz;
                double d = Math.hypot(dx, dz);
                if (Shapes.inDisc(dx, dz, HALL_R)) {
                    if (Shapes.onRim(dx, dz, HALL_R)) {
                        int a = Math.floorMod(Shapes.arc(dx, dz, HALL_R), 8);
                        boolean door = dz > 0 && Math.abs(dx) <= 2;
                        for (int y = FLOOR + 1; y <= FLOOR + HALL_H; y++) {
                            BlockState s = y == FLOOR + 1 ? trim : y >= FLOOR + HALL_H - 1 ? red : marble;
                            if (door && y <= FLOOR + 6) s = Canvas.AIR;
                            else if (door && y == FLOOR + 7) s = gold;
                            else if (!door && a >= 3 && a <= 4 && y >= FLOOR + 4 && y <= FLOOR + 9) s = window;
                            c.set(wx, y, wz, s);
                        }
                    } else {
                        // inside: a gold ring and a marble star set in the floor, the ceiling
                        BlockState floor = Math.abs(d - 3) < 0.6 ? gold : Math.abs(d - 7) < 0.6 ? b("lookout_marble") : tiles;
                        c.set(wx, FLOOR, wz, floor);
                        c.column(wx, wz, FLOOR + 1, FLOOR + HALL_H, Canvas.AIR);
                        c.set(wx, FLOOR + HALL_H + 1, wz, marble);
                    }
                    continue;
                }
                // the colonnade and the awning skirting the hall
                if (d <= HALL_R + 3.5) {
                    if (d <= HALL_R + 1.5) c.set(wx, FLOOR + HALL_H - 1, wz, awning);
                    else if (d <= HALL_R + 2.5) c.set(wx, FLOOR + HALL_H - 2, wz, Canvas.stairs(awningStairs, inward(dx, dz), false));
                    else c.set(wx, FLOOR + HALL_H - 3, wz, Canvas.slab(b("lookout_awning_slab"), true));
                    if (Math.abs(d - (HALL_R + 2)) < 0.5 && Math.floorMod(Shapes.arc(dx, dz, HALL_R + 2), 6) == 0 && !(dz > 0 && Math.abs(dx) <= 3))
                        c.column(wx, wz, FLOOR + 1, FLOOR + HALL_H - 3, pillar);
                }
            }
        // inside: a ring of columns with lamps, Kami's dais at the back
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) * Math.PI / 4;
            int px = cx + (int) Math.round(Math.cos(a) * 7), pz = cz + (int) Math.round(Math.sin(a) * 7);
            c.column(px, pz, FLOOR + 1, FLOOR + HALL_H, pillar);
            c.set(px, FLOOR + HALL_H - 2, pz, lamp);
        }
        c.set(cx, FLOOR + HALL_H, cz, lamp);
        c.box(cx - 4, FLOOR + 1, cz - 9, cx + 4, FLOOR + 1, cz - 7, marble);
        c.box(cx - 4, FLOOR + 1, cz - 6, cx + 4, FLOOR + 1, cz - 6, Canvas.stairs(b("lookout_marble_stairs"), Direction.NORTH, false));
        c.column(cx, cz - 9, FLOOR + 2, FLOOR + 3, b("lookout_marble_pillar").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        c.set(cx, FLOOR + 4, cz - 9, gold);
        // the drum and the dome
        Shapes.ring(c, cx, cz, FLOOR + HALL_H + 1, HALL_R - 1, HALL_R, red);
        Shapes.disc(c, cx, cz, FLOOR + HALL_H + 2, HALL_R - 1.5, trim);
        Shapes.onionDome(c, cx, cz, FLOOR + HALL_H + 3, 8.5, 14, gold, b("temple_gold"));
    }

    /** A side tower: round, two pink awnings, windows, a room inside, its own small onion dome. */
    private void tower(Canvas c, int cx, int cz) {
        int reach = TOWER_R + 3;
        if (!c.intersects(cx - reach, cz - reach, cx + reach, cz + reach)) return;
        BlockState marble = b("lookout_marble"), red = b("lookout_red_band"), trim = b("lookout_hull_trim"), window = b("lookout_hull_window");
        BlockState awning = b("lookout_awning_stairs"), lamp = b("lookout_lamp"), tiles = b("lookout_deck_tiles");
        for (int wx = Math.max(cx - reach, c.minX); wx <= Math.min(cx + reach, c.maxX); wx++)
            for (int wz = Math.max(cz - reach, c.minZ); wz <= Math.min(cz + reach, c.maxZ); wz++) {
                int dx = wx - cx, dz = wz - cz;
                if (Shapes.inDisc(dx, dz, TOWER_R)) {
                    if (Shapes.onRim(dx, dz, TOWER_R)) {
                        boolean slit = Math.abs(dx) == 0 || Math.abs(dz) == 0;
                        for (int y = FLOOR + 1; y <= FLOOR + TOWER_H; y++) {
                            int k = y - FLOOR;
                            BlockState s = k == 1 ? trim : k == 9 || k == 17 || k == TOWER_H ? red : marble;
                            if (slit && (k >= 3 && k <= 6 || k >= 11 && k <= 15)) s = window;
                            c.set(wx, y, wz, s);
                        }
                    } else {
                        c.set(wx, FLOOR, wz, tiles);
                        c.column(wx, wz, FLOOR + 1, FLOOR + TOWER_H - 1, Canvas.AIR);
                        c.set(wx, FLOOR + 9, wz, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Canvas.AIR : marble);   // an upper floor, open in the middle
                        c.set(wx, FLOOR + TOWER_H, wz, marble);
                    }
                    continue;
                }
                double d = Math.hypot(dx, dz);
                if (d <= TOWER_R + 1.5) {
                    c.set(wx, FLOOR + 10, wz, Canvas.stairs(awning, inward(dx, dz), false));
                    c.set(wx, FLOOR + 18, wz, Canvas.stairs(awning, inward(dx, dz), false));
                }
            }
        c.set(cx, FLOOR + 8, cz, lamp);
        c.set(cx, FLOOR + TOWER_H - 1, cz, lamp);
        Shapes.ring(c, cx, cz, FLOOR + TOWER_H + 1, TOWER_R - 1, TOWER_R, trim);
        Shapes.onionDome(c, cx, cz, FLOOR + TOWER_H + 2, 3.6, 7, b("lookout_gold_dome"), b("temple_gold"));
    }

    /** The wings joining hall and towers: long rooms with glowing windows under pink awnings. */
    private void wings(Canvas c) {
        BlockState marble = b("lookout_marble"), red = b("lookout_red_band"), trim = b("lookout_hull_trim"), window = b("lookout_hull_window");
        BlockState awning = b("lookout_awning_stairs"), lamp = b("lookout_lamp"), tiles = b("lookout_deck_tiles");
        int z0 = z + HALL_Z - 5, z1 = z + HALL_Z + 5, h = 8;
        for (int side : new int[]{-1, 1}) {
            int x0 = x + side * (HALL_R - 3), x1 = x + side * (TOWER_X - 2);
            int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
            if (!c.intersects(ax, z0 - 2, bx, z1 + 2)) continue;
            c.box(ax, FLOOR + 1, z0, bx, FLOOR + h, z1, marble);
            c.box(ax, FLOOR + 1, z0, bx, FLOOR + 1, z1, trim);
            c.box(ax, FLOOR + h, z0, bx, FLOOR + h, z1, red);
            c.box(ax + 1, FLOOR + 1, z0 + 1, bx - 1, FLOOR + h - 1, z1 - 1, Canvas.AIR);
            c.box(ax + 1, FLOOR, z0 + 1, bx - 1, FLOOR, z1 - 1, tiles);
            c.box(ax, FLOOR + h + 1, z0, bx, FLOOR + h + 1, z1, Canvas.slab(b("lookout_marble_slab"), false));
            for (int wx = ax + 1; wx < bx; wx++) {
                if (Math.floorMod(wx - x, 3) == 0) {
                    c.column(wx, z1, FLOOR + 3, FLOOR + 6, window);
                    c.column(wx, z0, FLOOR + 3, FLOOR + 6, window);
                }
                c.set(wx, FLOOR + h - 1, z1 + 1, Canvas.stairs(awning, Direction.NORTH, false));    // the awnings along the front and back
                c.set(wx, FLOOR + h - 2, z1 + 2, Canvas.slab(b("lookout_awning_slab"), true));
                c.set(wx, FLOOR + h - 1, z0 - 1, Canvas.stairs(awning, Direction.SOUTH, false));
                if (Math.floorMod(wx - x, 6) == 0) c.set(wx, FLOOR + h - 1, z, lamp);
            }
        }
    }

    /** The deck's gardens and furniture: lamp towers round the edge, the cypress avenue, the palm grove, the pool. */
    private void gardens(Canvas c) {
        BlockState marble = b("lookout_marble"), pillar = b("lookout_marble_pillar").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState lamp = b("lookout_lamp"), grass = Blocks.GRASS_BLOCK.defaultBlockState();
        // the tall white lamp towers
        for (int i = 0; i < 15; i++) {
            double a = (i + 0.5) * 2 * Math.PI / 15 + Math.PI / 2;            // half a step off the avenue
            int lx = x + (int) Math.round(Math.cos(a) * LAMP_RING), lz = z + (int) Math.round(Math.sin(a) * LAMP_RING);
            if (Math.abs(lx - x) <= PLATFORM_X + 4 && lz - z < PLATFORM_S + 4) continue;     // not on the palace platform
            if (!c.intersects(lx - 2, lz - 2, lx + 2, lz + 2)) continue;
            c.box(lx - 1, DECK + 1, lz - 1, lx + 1, DECK + 2, lz + 1, marble);
            c.box(lx - 1, DECK + 3, lz - 1, lx + 1, DECK + 3, lz + 1, Canvas.slab(b("lookout_marble_slab"), false));
            c.column(lx, lz, DECK + 3, DECK + LAMP_H - 3, pillar);
            for (Direction d : Direction.Plane.HORIZONTAL)
                c.set(lx + d.getStepX(), DECK + LAMP_H - 3, lz + d.getStepZ(), Canvas.stairs(b("lookout_marble_stairs"), d.getOpposite(), true));
            c.column(lx, lz, DECK + LAMP_H - 2, DECK + LAMP_H - 1, lamp);
            c.set(lx, DECK + LAMP_H, lz, Canvas.slab(b("lookout_marble_slab"), false));
        }
        // the cypress avenue up to the palace steps
        for (int dz = 8; dz <= R - 8; dz += 6)
            for (int side : new int[]{-7, 7}) {
                int tx = x + side, tz = z + dz;
                if (!c.intersects(tx - 2, tz - 2, tx + 2, tz + 2)) continue;
                c.set(tx, DECK, tz, grass);
                Shapes.cypress(c, tx, DECK + 1, tz, 12 + Math.floorMod(Canvas.hash(seed, tx, 0, tz), 3));
            }
        // the palm grove (east) on a lawn ringed in marble
        int gx = x + 34, gz = z + 16;
        if (c.intersects(gx - 14, gz - 14, gx + 14, gz + 14)) {
            for (int wx = Math.max(gx - 10, c.minX); wx <= Math.min(gx + 10, c.maxX); wx++)
                for (int wz = Math.max(gz - 10, c.minZ); wz <= Math.min(gz + 10, c.maxZ); wz++) {
                    int dx = wx - gx, dz = wz - gz;
                    if (!Shapes.inDisc(dx, dz, 10)) continue;
                    c.set(wx, DECK, wz, Shapes.onRim(dx, dz, 10) ? marble : grass);
                    if (!Shapes.onRim(dx, dz, 10) && Math.floorMod(Canvas.hash(seed, wx, 1, wz), 9) == 0)
                        c.set(wx, DECK + 1, wz, Blocks.GRASS.defaultBlockState());
                }
            for (int[] p : palms) Shapes.palm(c, x + p[0], DECK + 1, z + p[1], p[2], p[3]);
        }
        // the pool (west), rimmed in marble, palms leaning over it
        int px = x - 34, pz = z + 16;
        if (c.intersects(px - 14, pz - 14, px + 14, pz + 14)) {
            for (int wx = Math.max(px - 10, c.minX); wx <= Math.min(px + 10, c.maxX); wx++)
                for (int wz = Math.max(pz - 10, c.minZ); wz <= Math.min(pz + 10, c.maxZ); wz++) {
                    int dx = wx - px, dz = wz - pz;
                    if (!Shapes.inDisc(dx, dz, 9)) continue;
                    if (Shapes.onRim(dx, dz, 9)) {
                        c.set(wx, DECK, wz, marble);
                        c.set(wx, DECK + 1, wz, Canvas.slab(b("lookout_marble_slab"), false));
                    } else {
                        c.set(wx, DECK, wz, Blocks.WATER.defaultBlockState());
                        c.set(wx, DECK - 1, wz, Blocks.WATER.defaultBlockState());
                        c.set(wx, DECK - 2, wz, marble);
                    }
                }
            Shapes.palm(c, px + 10, DECK + 1, pz - 8, 10, 5);
            Shapes.palm(c, px - 9, DECK + 1, pz + 9, 9, 10);
            Shapes.palm(c, px + 8, DECK + 1, pz + 10, 11, 3);
        }
    }

    /** The side direction pointing from (dx, dz) back towards the centre. */
    private static Direction inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
