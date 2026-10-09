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
 * The Cell Games arena (CX-33): a great square ring of white tiles, raised and bevelled all round, a carved spire at
 * each corner, alone in a levelled rocky wasteland ringed by eroded rock formations (towering pillars, flat-topped
 * buttes, an arch) with boulders strewn about, eased back into the land round it. Cell waits in the middle.
 * <p>
 * North is -z; heights are blocks above the levelled ground ({@link #y0}).
 */
final class CellGamesArena extends LandmarkPlan {
    /** The ring: half its width, its height, the bevel's width (and drop). */
    static final int RING = 50, RING_H = 6, BEVEL = 3;
    /** The levelled wasteland's radius, and how far beyond it the ground eases back to the land. */
    static final int FLAT = 82, BLEND = 46;
    /** Where the rock formations stand (distance from the centre). */
    static final int ROCKS_IN = 86, ROCKS_OUT = 120;
    /** The spires: how far in from the ring's corners they stand, and how tall. */
    static final int SPIRE_IN = 4;

    final int y0;
    /** Formations: x, z (offsets), kind (0 pillar, 1 butte, 2 arch), radius (or half-span), height, base y, seed, angle (arch, x1000). */
    private final List<int[]> rocks = new ArrayList<>();
    /** Boulders: x, z, radius x10, base y. */
    private final List<int[]> boulders = new ArrayList<>();

    private CellGamesArena(LandmarkSites.Site site, int y0, LandmarkSites.Terrain t) {
        super(site);
        this.y0 = y0;
        Random r = new Random(seed);
        boolean arch = false;
        for (int i = 0; i < 300 && rocks.size() < 15; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = ROCKS_IN + r.nextDouble() * (ROCKS_OUT - ROCKS_IN);
            int fx = (int) Math.round(Math.cos(a) * d), fz = (int) Math.round(Math.sin(a) * d);
            int kind = !arch && r.nextInt(5) == 0 ? 2 : r.nextInt(3) == 0 ? 1 : 0;
            int rad = kind == 0 ? 4 + r.nextInt(4) : kind == 1 ? 9 + r.nextInt(6) : 13 + r.nextInt(6);
            int h = kind == 0 ? 26 + r.nextInt(22) : kind == 1 ? 12 + r.nextInt(10) : 18 + r.nextInt(8);
            boolean clash = false;
            for (int[] o : rocks) clash |= Math.hypot(o[0] - fx, o[1] - fz) < o[3] + rad + 8;
            if (clash) continue;
            if (kind == 2) arch = true;
            int base = blended(d, t.height(x + fx, z + fz)) - 3;
            rocks.add(new int[]{fx, fz, kind, rad, h, base, r.nextInt(), (int) (r.nextDouble() * 3142)});
        }
        for (int i = 0; i < 400 && boulders.size() < 46; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 58 + r.nextDouble() * (FLAT + BLEND - 68);
            int bx = (int) Math.round(Math.cos(a) * d), bz = (int) Math.round(Math.sin(a) * d);
            if (Math.max(Math.abs(bx), Math.abs(bz)) < RING + 8) continue;
            boolean clash = false;
            for (int[] o : rocks) clash |= Math.hypot(o[0] - bx, o[1] - bz) < o[3] + 4;
            if (clash) continue;
            boulders.add(new int[]{bx, bz, 15 + r.nextInt(20), blended(d, t.height(x + bx, z + bz))});
        }
    }

    /** The wasteland stands where the ground is dry and not too broken (it is levelled and eased into the rest). */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[25];
        int n = 0, wet = 0;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int px = s.x() + i * 36, pz = s.z() + j * 36;
                if (t.wet(px, pz)) wet++;
                hs[n++] = t.height(px, pz);
            }
        java.util.Arrays.sort(hs);
        if (wet > 2 || hs[21] - hs[3] > 28 || hs[12] <= t.seaLevel()) return null;
        return new CellGamesArena(s, hs[12], t);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + RING_H + 1, z + RING - 8);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Shapes.inDisc(wx - x, wz - z, FLAT) ? y0 : Integer.MAX_VALUE;
    }

    /** The ground's height at distance d from the centre, given the land's own height there. */
    int blended(double d, int natural) {
        if (d <= FLAT) return y0;
        return (int) Math.round(y0 + (natural - y0) * smooth((d - FLAT) / BLEND));
    }

    static BlockState b(String id) {
        return Shapes.b(id);
    }

    @Override
    public void build(Canvas c) {
        int reach = FLAT + BLEND;
        if (!c.intersects(x - reach, z - reach, x + reach, z + reach)) return;
        ground(c);
        ring(c);
        for (int sx : new int[]{-1, 1})
            for (int sz : new int[]{-1, 1}) spire(c, x + sx * (RING - SPIRE_IN), z + sz * (RING - SPIRE_IN));
        for (int[] f : rocks) formation(c, f);
        for (int[] bd : boulders) boulder(c, bd);
        c.spawn(com.dbzenith.npc.ModNpcs.MASTERS.get(com.dbzenith.style.MasterRoster.CELL).get(), x + 0.5, y0 + RING_H + 1, z + 0.5, 0f);
    }

    // ------------------------------------------------------------------ the wasteland

    private void ground(Canvas c) {
        BlockState rock = b("wasteland_rock"), rubble = b("wasteland_rock_slab"), gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState sand = b("frypan_sand");
        int reach = FLAT + BLEND;
        for (int wx = Math.max(x - reach, c.minX); wx <= Math.min(x + reach, c.maxX); wx++)
            for (int wz = Math.max(z - reach, c.minZ); wz <= Math.min(z + reach, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                double d = Math.hypot(dx, dz);
                if (d > reach) continue;
                int natural = c.surface(wx, wz);
                int target = blended(d, natural);
                // the wasteland's skin fades out into the land's own over the outer part of the blend
                double fade = (d - FLAT) / BLEND;
                double patch = Shapes.noise(seed, wx, 0, wz, 9);
                boolean waste = fade < 0.35 || fade < 0.8 && patch > (fade - 0.35) * 2.2 - 0.6;
                BlockState top = !waste ? c.get(wx, natural, wz) : patch > 0.45 ? gravel : patch < -0.55 ? sand : rock;
                if (top.isAir()) top = rock;
                if (target != natural || waste) c.ground(wx, wz, target, top, rock, d <= FLAT ? 30 : 16);
                if (waste && d > 12 && Math.floorMod(Canvas.hash(seed, wx, 7, wz), 53) == 0
                        && Math.max(Math.abs(dx), Math.abs(dz)) > RING + 2) c.set(wx, target + 1, wz, rubble);
            }
    }

    // ------------------------------------------------------------------ the ring

    private void ring(Canvas c) {
        if (!c.intersects(x - RING, z - RING, x + RING, z + RING)) return;
        BlockState tile = b("cell_tile"), stairs = b("cell_tile_stairs"), border = b("cell_spire_cap"), seam = b("cell_spire_cap");
        BlockState base = b("wasteland_rock"), band = b("cell_spire").setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        int top = y0 + RING_H;
        for (int wx = Math.max(x - RING, c.minX); wx <= Math.min(x + RING, c.maxX); wx++)
            for (int wz = Math.max(z - RING, c.minZ); wz <= Math.min(z + RING, c.maxZ); wz++) {
                int dx = wx - x, dz = wz - z;
                int e = RING - Math.max(Math.abs(dx), Math.abs(dz));                   // blocks in from the edge
                c.set(wx, y0, wz, base);
                if (e >= BEVEL) {
                    c.column(wx, wz, y0 + 1, top - 1, tile);
                    c.set(wx, top, wz, e == BEVEL ? border : tile);
                    // the great tiles' seams: a faint grid every eleven blocks
                    if (e > BEVEL + 2 && (Math.floorMod(dx, 11) == 0 || Math.floorMod(dz, 11) == 0)) c.set(wx, top, wz, seam);
                    continue;
                }
                // the bevel: three stepped stairs down to the side wall, a carved band along the foot
                int h = top - (BEVEL - e);
                Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                c.column(wx, wz, y0 + 1, h - 1, tile);
                if (e == 0) c.set(wx, y0 + 1, wz, Math.abs(dx) >= Math.abs(dz) ? band.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) : band);
                c.set(wx, h, wz, Canvas.stairs(stairs, in, false));
            }
        // steps up the middle of the south side
        BlockState steps = b("cell_tile_stairs");
        for (int k = 1; k <= RING_H; k++) {
            int sz = z + RING - BEVEL + k;
            c.box(x - 5, y0 + 1, sz, x + 5, top - k, sz, tile);
            c.box(x - 5, top - k + 1, sz, x + 5, top - k + 1, sz, Canvas.stairs(steps, Direction.NORTH, false));
            c.box(x - 5, top - k + 2, sz, x + 5, top + 1, sz, Canvas.AIR);
        }
    }

    /** A carved corner spire: a square plinth, then tiers narrowing upward between round flanges, to a point. */
    private void spire(Canvas c, int sx, int sz) {
        if (!c.intersects(sx - 5, sz - 5, sx + 5, sz + 5)) return;
        BlockState carved = b("cell_spire").setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), cap = b("cell_spire_cap");
        BlockState tile = b("cell_tile"), stairs = b("cell_tile_stairs");
        int y = y0 + RING_H;
        c.box(sx - 4, y0 + 1, sz - 4, sx + 4, y + 3, sz + 4, tile);                // the plinth, down through the bevel
        for (Direction d : Direction.Plane.HORIZONTAL)                             // its sloped shoulders
            for (int k = -4; k <= 4; k++) {
                int px = sx + d.getStepX() * 4 + (d.getAxis() == Direction.Axis.Z ? k : 0), pz = sz + d.getStepZ() * 4 + (d.getAxis() == Direction.Axis.X ? k : 0);
                c.set(px, y + 4, pz, Canvas.stairs(stairs, d.getOpposite(), false));
            }
        c.box(sx - 3, y + 4, sz - 3, sx + 3, y + 4, sz + 3, cap);
        int k = y + 5;
        k = tier(c, sx, sz, k, 2, 7, carved);
        Shapes.disc(c, sx, sz, k++, 3.2, cap);
        k = tier(c, sx, sz, k, 1, 10, carved);
        Shapes.disc(c, sx, sz, k++, 2.3, cap);
        Shapes.disc(c, sx, sz, k++, 1.5, cap);
        k = tier(c, sx, sz, k, 1, 5, carved);
        c.column(sx, sz, k, k + 6, carved);
        c.column(sx, sz, k + 7, k + 8, b("cell_spire_cap_wall").setValue(net.minecraft.world.level.block.WallBlock.UP, true));
    }

    private static int tier(Canvas c, int sx, int sz, int y, int half, int height, BlockState s) {
        c.box(sx - half, y, sz - half, sx + half, y + height - 1, sz + half, s);
        return y + height;
    }

    // ------------------------------------------------------------------ the rocks

    /** Rock: wasteland stone with tan strata running through it at a slight wave. */
    private BlockState rockAt(int wx, int y, int wz) {
        int wave = (int) Math.round(Shapes.noise(seed ^ 0x51L, wx, 0, wz, 24) * 3);
        int layer = Math.floorMod(y + wave, 9);
        return layer == 0 ? b("strata_tan") : layer == 1 && Math.floorMod(Canvas.hash(seed, wx, y, wz), 3) == 0 ? b("strata_brown") : b("wasteland_rock");
    }

    private void formation(Canvas c, int[] f) {
        int fx = x + f[0], fz = z + f[1], kind = f[2], rad = f[3], h = f[4], base = f[5];
        long s = seed ^ f[6];
        int reach = kind == 2 ? rad + 6 : (int) Math.ceil(rad * 1.7) + 4;
        if (!c.intersects(fx - reach, fz - reach, fx + reach, fz + reach)) return;
        double ang = f[7] / 1000.0, ca = Math.cos(ang), sa = Math.sin(ang);
        for (int wx = Math.max(fx - reach, c.minX); wx <= Math.min(fx + reach, c.maxX); wx++)
            for (int wz = Math.max(fz - reach, c.minZ); wz <= Math.min(fz + reach, c.maxZ); wz++) {
                int dx = wx - fx, dz = wz - fz;
                double d = Math.hypot(dx, dz);
                int ground = c.surface(wx, wz);
                for (int k = -4; k <= h + 2; k++) {
                    int y = base + k;
                    double rough = Shapes.noise(s, wx, y, wz, 5) * 0.22 + Shapes.noise(s ^ 7, 0, y, 0, 3) * 0.12;
                    boolean solid;
                    if (kind == 2) {
                        // an arch: two legs and the span between, along its own axis
                        double u = dx * ca + dz * sa, v = -dx * sa + dz * ca;
                        double t = u / rad;
                        if (Math.abs(t) > 1.15 || Math.abs(v) > 4.5) continue;
                        double crown = h * Math.sqrt(Math.max(0, 1 - Math.min(1, t * t)));
                        double thick = 4 + 3 * Math.abs(t);
                        boolean leg = Math.abs(t) >= 0.78 && k <= crown + 2;
                        boolean span = k >= crown - thick && k <= crown + 1.5;
                        solid = (leg || span) && Math.abs(v) <= 3.2 + rough * 6 - Math.max(0, k - crown) * 0.8;
                    } else {
                        double f01 = Math.max(0, k) / (double) h;
                        double rk;
                        if (kind == 0) rk = rad * (0.78 + 0.22 * (1 - f01)) * (f01 > 0.86 ? 1.12 : 1);   // a pillar, with an overhanging cap
                        else rk = rad * (1 - 0.18 * f01) + Math.max(0, 0.22 * h - k) * 0.9;              // a butte on its talus
                        rk *= 1 + rough;
                        solid = k <= h && d <= rk;
                    }
                    if (!solid) continue;
                    if (y <= ground && k > 0) continue;                                   // leave the ground's own top where it rises
                    c.set(wx, y, wz, rockAt(wx, y, wz));
                }
            }
        // a few dead bushes on top of the buttes
        if (kind == 1)
            for (int i = 0; i < 3; i++) {
                int bx = fx + (int) Math.round(Math.cos(i * 2.1 + f[6]) * rad * 0.4), bz = fz + (int) Math.round(Math.sin(i * 2.1 + f[6]) * rad * 0.4);
                if (!c.contains(bx, bz)) continue;
                int top = base + h;
                while (top > base && c.get(bx, top, bz).isAir()) top--;
                if (!c.get(bx, top, bz).isAir()) c.set(bx, top + 1, bz, Blocks.DEAD_BUSH.defaultBlockState());
            }
    }

    private void boulder(Canvas c, int[] bd) {
        int bx = x + bd[0], bz = z + bd[1];
        double r = bd[2] / 10.0;
        int R = (int) Math.ceil(r) + 1;
        if (!c.intersects(bx - R, bz - R, bx + R, bz + R)) return;
        int base = bd[3];
        for (int wx = Math.max(bx - R, c.minX); wx <= Math.min(bx + R, c.maxX); wx++)
            for (int wz = Math.max(bz - R, c.minZ); wz <= Math.min(bz + R, c.maxZ); wz++)
                for (int k = -1; k <= R; k++) {
                    double dd = Math.sqrt((wx - bx) * (wx - bx) + (k * 1.3) * (k * 1.3) + (wz - bz) * (wz - bz));
                    if (dd <= r * (1 + Shapes.noise(seed, wx, base + k, wz, 2) * 0.3)) c.set(wx, base + k, wz, rockAt(wx, base + k, wz));
                }
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
