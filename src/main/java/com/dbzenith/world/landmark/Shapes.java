package com.dbzenith.world.landmark;

import com.dbzenith.world.arch.ArchitectureBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Shapes the landmarks share (CX-33), all clipped to the canvas's chunk: hip roofs with stepped eaves and upturned
 * gold corners, domes and onion domes, and the plants (palms, cypresses) and street furniture that dress them.
 */
public final class Shapes {
    private Shapes() {}

    static BlockState b(String id) {
        return ArchitectureBlocks.get(id);
    }

    /**
     * A hip roof over the rectangle x0..x1, z0..z1 (eaves included), its eave at {@code y}: every step in from the edge
     * rises one block, tiled with {@code tile} (full blocks) and its stairs on the slopes' faces, a gold ridge.
     * {@code shell} blocks thick (the rest below is left open for the room under it).
     */
    public static void hipRoof(Canvas c, int x0, int z0, int x1, int z1, int y, String tile, int shell, BlockState ridge) {
        hipRoof(c, x0, z0, x1, z1, y, tile, shell, ridge, Integer.MAX_VALUE);
    }

    /** The same, its rise stopped at {@code cap} blocks: a flat top there (for a storey above). */
    public static void hipRoof(Canvas c, int x0, int z0, int x1, int z1, int y, String tile, int shell, BlockState ridge, int cap) {
        if (!c.intersects(x0, z0, x1, z1)) return;
        BlockState full = b(tile), stairs = b(tile + "_stairs");
        int ax = Math.max(Math.min(x0, x1), c.minX), bx = Math.min(Math.max(x0, x1), c.maxX);
        int az = Math.max(Math.min(z0, z1), c.minZ), bz = Math.min(Math.max(z0, z1), c.maxZ);
        int lx = Math.min(x0, x1), hx = Math.max(x0, x1), lz = Math.min(z0, z1), hz = Math.max(z0, z1);
        int maxIn = Math.min(hx - lx, hz - lz) / 2;
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                int dW = x - lx, dE = hx - x, dN = z - lz, dS = hz - z;
                int edge = Math.min(Math.min(dW, dE), Math.min(dN, dS));
                int in = Math.min(edge, cap);
                int top = y + in;
                for (int k = 0; k < shell; k++) c.set(x, top - k, z, full);
                if (edge >= cap) continue;                                           // the flat top under the next storey
                Direction out = in == dN ? Direction.NORTH : in == dS ? Direction.SOUTH : in == dW ? Direction.WEST : Direction.EAST;
                if (in >= maxIn) c.set(x, top + 1, z, ridge);                       // the ridge line
                else c.set(x, top + 1, z, Canvas.stairs(stairs, out.getOpposite(), false));
            }
    }

    /** Gold ornaments curling up from the four eave corners of a roof (and its ridge ends). */
    public static void roofCorners(Canvas c, int x0, int z0, int x1, int z1, int y) {
        BlockState gold = b("temple_gold");
        for (int[] p : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            c.set(p[0], y + 1, p[1], gold);
            c.set(p[0], y + 2, p[1], gold);
            int sx = p[0] == x0 ? -1 : 1, sz = p[1] == z0 ? -1 : 1;
            c.set(p[0] + sx, y + 2, p[1] + sz, gold);
            c.set(p[0] + sx, y + 3, p[1] + sz, b("temple_lantern"));
        }
    }

    /** A dome (a half ellipsoid) of radius {@code r} and height {@code h} from y upward, shell {@code t} thick. */
    public static void dome(Canvas c, int cx, int cz, int y, double r, double h, double t, BlockState shell) {
        int R = (int) Math.ceil(r);
        if (!c.intersects(cx - R, cz - R, cx + R, cz + R)) return;
        for (int x = Math.max(cx - R, c.minX); x <= Math.min(cx + R, c.maxX); x++)
            for (int z = Math.max(cz - R, c.minZ); z <= Math.min(cz + R, c.maxZ); z++) {
                double d = Math.hypot(x - cx, z - cz) / r;
                if (d > 1) continue;
                double top = h * Math.sqrt(1 - d * d), inner = Math.max(0, (h - t) * Math.sqrt(Math.max(0, 1 - Math.pow(Math.hypot(x - cx, z - cz) / Math.max(1, r - t), 2))));
                for (int k = (int) Math.floor(inner); k <= (int) Math.round(top); k++) if (k >= 0) c.set(x, y + k, z, shell);
            }
    }

    /**
     * An onion dome: swelling out past its base to {@code bulge} times the radius, then drawn up to a point.
     * Filled (it is seen, not entered), with a gold finial on top.
     */
    public static void onionDome(Canvas c, int cx, int cz, int y, double r, double h, BlockState shell, BlockState finial) {
        int R = (int) Math.ceil(r * 1.25);
        if (!c.intersects(cx - R, cz - R, cx + R, cz + R)) return;
        int H = (int) Math.ceil(h);
        for (int k = 0; k <= H; k++) {
            double f = k / h;
            double rad = r * (f < 0.35 ? 1.0 + 0.25 * Math.sin(f / 0.35 * Math.PI / 2) : 1.25 * Math.pow(Math.cos((f - 0.35) / 0.65 * Math.PI / 2), 0.9));
            disc(c, cx, cz, y + k, rad, shell);
        }
        c.column(cx, cz, y + H + 1, y + H + 4, finial);
        c.set(cx, y + H + 5, cz, b("temple_lantern"));
    }

    /** A filled disc. */
    public static void disc(Canvas c, int cx, int cz, int y, double r, BlockState s) {
        int R = (int) Math.ceil(r);
        if (!c.intersects(cx - R, cz - R, cx + R, cz + R)) return;
        for (int x = Math.max(cx - R, c.minX); x <= Math.min(cx + R, c.maxX); x++)
            for (int z = Math.max(cz - R, c.minZ); z <= Math.min(cz + R, c.maxZ); z++)
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r + 0.5) c.set(x, y, z, s);
    }

    /** A ring (annulus) between radii. */
    public static void ring(Canvas c, int cx, int cz, int y, double r0, double r1, BlockState s) {
        int R = (int) Math.ceil(r1);
        if (!c.intersects(cx - R, cz - R, cx + R, cz + R)) return;
        for (int x = Math.max(cx - R, c.minX); x <= Math.min(cx + R, c.maxX); x++)
            for (int z = Math.max(cz - R, c.minZ); z <= Math.min(cz + R, c.maxZ); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d >= r0 - 0.5 && d <= r1 + 0.5) c.set(x, y, z, s);
            }
    }

    /** Whether (dx, dz) lies in the disc of radius r round the origin. */
    public static boolean inDisc(int dx, int dz, double r) {
        return dx * dx + dz * dz <= (r + 0.5) * (r + 0.5);
    }

    /**
     * Whether (dx, dz) is on the rim of that disc: in it, with a neighbour (diagonals too) outside. The rim is
     * unbroken side to side, so a wall drawn on it is watertight and a balustrade on it joins up.
     */
    public static boolean onRim(int dx, int dz, double r) {
        if (!inDisc(dx, dz, r)) return false;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) if (!inDisc(dx + i, dz + j, r)) return true;
        return false;
    }

    /** Where (dx, dz) lies round the circle, as a distance along a circle of radius r (for spacing windows, posts). */
    public static int arc(int dx, int dz, double r) {
        return (int) Math.floor((Math.atan2(dz, dx) + Math.PI) * r);
    }

    /**
     * A balustrade on the rim of the disc of radius r at height y, its walls joined to each other; every
     * {@code postEvery} blocks round it a post carrying {@code postTop} (null for none).
     */
    public static void circleWall(Canvas c, int cx, int cz, int y, double r, BlockState wall, int postEvery, BlockState postTop) {
        int R = (int) Math.ceil(r) + 1;
        if (!c.intersects(cx - R, cz - R, cx + R, cz + R)) return;
        for (int x = Math.max(cx - R, c.minX); x <= Math.min(cx + R, c.maxX); x++)
            for (int z = Math.max(cz - R, c.minZ); z <= Math.min(cz + R, c.maxZ); z++) {
                int dx = x - cx, dz = z - cz;
                if (!onRim(dx, dz, r)) continue;
                boolean n = onRim(dx, dz - 1, r), s = onRim(dx, dz + 1, r), w = onRim(dx - 1, dz, r), e = onRim(dx + 1, dz, r);
                boolean straight = (n && s && !w && !e) || (w && e && !n && !s);
                boolean post = postEvery > 0 && Math.floorMod(arc(dx, dz, r), postEvery) == 0;
                c.set(x, y, z, wall.setValue(WallBlock.UP, !straight || post)
                        .setValue(WallBlock.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
                        .setValue(WallBlock.WEST_WALL, w ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.EAST_WALL, e ? WallSide.LOW : WallSide.NONE));
                if (post && postTop != null) c.set(x, y + 1, z, postTop);
            }
    }

    // ------------------------------------------------------------------ plants and furniture

    static BlockState leaves(BlockState l) {
        return l.setValue(LeavesBlock.PERSISTENT, true);
    }

    /** A palm: a trunk leaning a little as it rises, and a crown of long drooping fronds with coconuts. */
    public static void palm(Canvas c, int x, int y, int z, int height, int seed) {
        if (!c.intersects(x - 7, z - 7, x + 7, z + 7)) return;
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState leaf = leaves(Blocks.JUNGLE_LEAVES.defaultBlockState());
        double lx = ((seed & 3) - 1.5) * 0.5, lz = (((seed >> 2) & 3) - 1.5) * 0.5;
        int tx = x, tz = z;
        for (int k = 0; k < height; k++) {
            double f = (double) k / height;
            tx = x + (int) Math.round(lx * f * f * height * 0.35);
            tz = z + (int) Math.round(lz * f * f * height * 0.35);
            c.set(tx, y + k, tz, log);
        }
        int top = y + height;
        c.set(tx, top, tz, leaf);
        c.set(tx, top - 2, tz + 1, Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2)
                .setValue(net.minecraft.world.level.block.CocoaBlock.FACING, Direction.NORTH));     // coconuts, hung on the trunk
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int s = 1; s <= 5; s++) {                                        // fronds: out, then drooping
                int droop = s >= 3 ? (s - 2) : 0;
                c.set(tx + d.getStepX() * s, top - droop, tz + d.getStepZ() * s, leaf);
            }
            Direction cw = d.getClockWise();
            for (int s = 1; s <= 3; s++) {                                        // the diagonals
                int droop = s >= 2 ? s - 1 : 0;
                c.set(tx + (d.getStepX() + cw.getStepX()) * s, top - droop, tz + (d.getStepZ() + cw.getStepZ()) * s, leaf);
            }
        }
    }

    /** A cypress: a tall narrow flame of dark foliage on a short trunk. */
    public static void cypress(Canvas c, int x, int y, int z, int height) {
        if (!c.intersects(x - 2, z - 2, x + 2, z + 2)) return;
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaf = leaves(Blocks.SPRUCE_LEAVES.defaultBlockState());
        c.column(x, z, y, y + height - 2, log);
        for (int k = 2; k <= height; k++) {
            double f = (double) k / height;
            double r = 1.9 * Math.sin(Math.PI * Math.min(1, f * 1.15)) + 0.3;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= r * r) c.set(x + dx, y + k, z + dz, leaf);
        }
        c.set(x, y + height, z, leaf);
        c.set(x, y + height + 1, z, leaf);
    }

    /** A wall post running up (a pole) with the given wall block, connected to nothing. */
    public static void pole(Canvas c, int x, int y, int z, int height, BlockState wall) {
        BlockState post = wall.setValue(WallBlock.UP, true);
        c.column(x, z, y, y + height - 1, post);
    }

    /** A wall run along x or z between two points (inclusive), posts at the ends, connected sides between. */
    public static void wallLine(Canvas c, int x0, int z0, int x1, int z1, int y, BlockState wall) {
        boolean alongX = z0 == z1;
        int a = alongX ? Math.min(x0, x1) : Math.min(z0, z1), bEnd = alongX ? Math.max(x0, x1) : Math.max(z0, z1);
        for (int i = a; i <= bEnd; i++) {
            BlockState s = wall.setValue(WallBlock.UP, i == a || i == bEnd || (i - a) % 6 == 0);
            if (alongX) s = s.setValue(WallBlock.EAST_WALL, i < bEnd ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.WEST_WALL, i > a ? WallSide.LOW : WallSide.NONE);
            else s = s.setValue(WallBlock.SOUTH_WALL, i < bEnd ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.NORTH_WALL, i > a ? WallSide.LOW : WallSide.NONE);
            if (alongX) c.set(i, y, z0, s);
            else c.set(x0, y, i, s);
        }
    }

    /** A banner pole: a tall post with a long cloth hanging from its top on one side. */
    public static void bannerPole(Canvas c, int x, int y, int z, int height, BlockState cloth, Direction side) {
        if (!c.intersects(x - 2, z - 2, x + 2, z + 2)) return;
        pole(c, x, y, z, height, b("tournament_stone_wall"));
        c.set(x, y + height, z, b("temple_gold"));
        int ox = x + side.getStepX(), oz = z + side.getStepZ();
        for (int k = 0; k < 9; k++) {
            c.set(ox, y + height - 1 - k, oz, cloth);
            if (k < 7) c.set(ox + side.getStepX(), y + height - 1 - k, oz + side.getStepZ(), cloth);
        }
    }
}
