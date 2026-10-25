package com.dbzenith.world.landmark;

import com.dbzenith.npc.ModNpcs;
import com.dbzenith.world.arch.CivilizationBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Arrays;

/**
 * A Red Ribbon Army field base (canon-inspired, docs/WORLD_STRUCTURES.md): a fenced compound of grey steel and army
 * olive. Its gate (south) between floodlights; four watchtowers with ladders; the headquarters (the army's emblem over
 * the door, consoles, the commander's chests, a radio mast); the barracks (bunks, lockers); a round-roofed hangar with
 * crates; a helipad; a flag. Garrisoned by Red Ribbon soldiers and an android unit, hostile to all comers; its chests
 * hold the army's supplies. Never on ground explored before it.
 */
final class RibbonBase extends LandmarkPlan {
    static final int HX = 22, HZ = 17, BLEND = 10;
    static final String LOOT = "dbzenith:chests/red_ribbon_base";
    final int y0;

    private RibbonBase(LandmarkSites.Site site, int y0) {
        super(site);
        this.y0 = y0;
    }

    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[25];
        int n = 0;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int px = s.x() + i * 12, pz = s.z() + j * 10, h = t.height(px, pz);
                if (t.wetOver(px, pz, h)) return null;
                hs[n++] = h;
            }
        Arrays.sort(hs);
        if (hs[22] - hs[2] > 10 || hs[12] <= t.seaLevel()) return null;
        return new RibbonBase(s, hs[12]);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + HZ + 8);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.abs(wx - x) <= HX + BLEND && Math.abs(wz - z) <= HZ + BLEND ? y0 : Integer.MAX_VALUE;
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    @Override
    public void build(Canvas c) {
        if (!c.intersects(x - HX - BLEND, z - HZ - BLEND, x + HX + BLEND, z + HZ + BLEND)) return;
        ground(c);
        fence(c);
        for (int[] p : new int[][]{{-HX + 2, -HZ + 2}, {HX - 2, -HZ + 2}, {-HX + 2, HZ - 2}, {HX - 2, HZ - 2}}) tower(c, x + p[0], z + p[1], p[0] < 0, p[1] < 0);
        headquarters(c, new WestCity.F(x, z - 10, y0, Direction.SOUTH));
        barracks(c, new WestCity.F(x - 14, z + 4, y0, Direction.EAST));
        hangar(c, x + 13, z - 2, z + 10);
        helipad(c, x - 14, z - 11);
        for (int[] p : new int[][]{{-4, 10}, {-5, 10}, {-4, 11}, {3, -3}, {4, -3}, {4, -2}})
            c.set(x + p[0], y0 + 1, z + p[1], s("ribbon_crate"));
        c.set(x - 4, y0 + 2, z + 10, s("ribbon_crate"));
        c.column(x + 6, z + HZ - 3, y0 + 1, y0 + 9, s("ribbon_steel_wall"));                    // the flag
        for (int dy = 6; dy <= 8; dy++) c.box(x + 7, y0 + dy, z + HZ - 3, x + 9, y0 + dy, z + HZ - 3, dy == 7 ? Blocks.WHITE_WOOL.defaultBlockState()
                : Blocks.RED_WOOL.defaultBlockState());
        // the garrison
        var soldier = ModNpcs.RED_RIBBON_SOLDIER.get();
        c.spawn(soldier, x + 0.5, y0 + 1, z + HZ - 4.5, 180f);
        c.spawn(soldier, x - 3.5, y0 + 1, z + 2.5, 90f);
        c.spawn(soldier, x + 8.5, y0 + 1, z + 1.5, 270f);
        c.spawn(soldier, x - HX + 2.5, y0 + 8, z + HZ - 2.5, 45f);
        c.spawn(soldier, x + HX - 1.5, y0 + 8, z - HZ + 1.5, 225f);
        c.spawn(ModNpcs.ANDROID_UNIT.get(), x + 0.5, y0 + 1, z - 8.5, 0f);
    }

    /** The yard levelled to y0 (gravel and packed earth), eased into the land round it. */
    void ground(Canvas c) {
        BlockState gravel = Blocks.GRAVEL.defaultBlockState(), earth = Blocks.COARSE_DIRT.defaultBlockState(), fill = Blocks.DIRT.defaultBlockState();
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                int dx = Math.abs(wx - x), dz = Math.abs(wz - z);
                double out = Math.max(dx - HX, dz - HZ);
                if (out > BLEND) continue;
                int target = y0;
                if (out > 0) {
                    double f = out / BLEND;
                    f = f * f * (3 - 2 * f);
                    target = (int) Math.round(y0 + (c.surface(wx, wz) - y0) * f);
                }
                BlockState top = out > 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Canvas.hash(seed, wx, 0, wz) % 3 == 0 ? earth : gravel;
                c.ground(wx, wz, target, top, fill, out > 0 ? 8 : 20);
                if (out <= 0 && (Math.abs(wx - x) <= 2 || Math.abs(wz - z - 2) <= 1)) c.set(wx, y0, wz, s("ribbon_concrete"));   // the roads in
            }
    }

    /** Iron fencing on steel posts all round, the gate (south) between two floodlit posts, hazard stripes across it. */
    void fence(Canvas c) {
        for (int dx = -HX; dx <= HX; dx++)
            for (int dz = -HZ; dz <= HZ; dz++) {
                if (Math.abs(dx) != HX && Math.abs(dz) != HZ) continue;
                int wx = x + dx, wz = z + dz;
                if (!c.contains(wx, wz)) continue;
                if (dz == HZ && Math.abs(dx) <= 3) {
                    c.set(wx, y0, wz, s("ribbon_hazard"));
                    if (Math.abs(dx) == 3) {
                        c.column(wx, wz, y0 + 1, y0 + 4, s("ribbon_steel"));
                        c.set(wx, y0 + 5, wz, s("ribbon_light"));
                    }
                    continue;
                }
                boolean post = (dx + dz) % 4 == 0 || Math.abs(dx) == HX && Math.abs(dz) == HZ;
                c.column(wx, wz, y0 + 1, y0 + (post ? 3 : 2), post ? s("ribbon_steel_wall") : Blocks.IRON_BARS.defaultBlockState());
            }
    }

    /** A watchtower: four steel legs, a ladder, a grated platform with a railing, a roof, a floodlight looking into the yard. */
    void tower(Canvas c, int tx, int tz, boolean west, boolean north) {
        if (!c.intersects(tx - 3, tz - 3, tx + 3, tz + 3)) return;
        for (int[] l : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) c.column(tx + l[0], tz + l[1], y0 + 1, y0 + 9, s("ribbon_steel_wall"));
        c.box(tx - 1, y0 + 7, tz - 1, tx + 1, y0 + 7, tz + 1, s("ribbon_grate"));
        c.set(tx, y0 + 7, tz, Canvas.AIR);
        for (int k = 1; k <= 7; k++) c.set(tx, y0 + k, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        c.column(tx, tz + 1, y0 + 1, y0 + 6, s("ribbon_steel"));                              // the ladder's backing
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    c.set(tx + dx, y0 + 7, tz + dz, s("ribbon_grate_slab"));
                    c.set(tx + dx, y0 + 8, tz + dz, Blocks.IRON_BARS.defaultBlockState());
                }
                c.set(tx + dx, y0 + 10, tz + dz, Canvas.slab(s("ribbon_steel_dark_slab"), false));
            }
        c.set(tx + (west ? 1 : -1), y0 + 9, tz + (north ? 1 : -1), s("ribbon_light"));
    }

    /** The headquarters: steel walls with a red band, the emblem over the doors, consoles, a map table, chests, a radio mast. */
    void headquarters(Canvas c, WestCity.F f) {
        if (!c.intersects(f.ox() - 10, f.oz() - 7, f.ox() + 10, f.oz() + 7)) return;
        shell(c, f, 8, 4, 5, "ribbon_steel");
        for (int lx = -8; lx <= 8; lx++) {
            f.set(c, lx, 4, -4, s("ribbon_red_band"));
            f.set(c, lx, 4, 4, s("ribbon_red_band"));
        }
        for (int lx : new int[]{-6, -3, 3, 6}) f.set(c, lx, 2, -4, s("ribbon_window"));
        doors(c, f, -4);
        f.set(c, 0, 4, -4, s("ribbon_emblem"));
        f.set(c, 1, 4, -4, s("ribbon_emblem"));
        for (int lx = -7; lx <= 7; lx += 2) f.set(c, lx, 1, 3, s("ribbon_console"));
        f.box(c, -2, 1, 0, 2, 1, 1, Canvas.slab(s("ribbon_steel_dark_slab"), true));            // the map table
        c.lootChest(f.wx(-7, -2), y0 + 1, f.wz(-7, -2), f.dir(Direction.EAST), LOOT);
        c.lootChest(f.wx(7, -2), y0 + 1, f.wz(7, -2), f.dir(Direction.WEST), LOOT);
        f.set(c, -4, 4, 0, s("ribbon_light"));
        f.set(c, 4, 4, 0, s("ribbon_light"));
        c.column(f.wx(6, 2), f.wz(6, 2), y0 + 7, y0 + 15, s("ribbon_steel_wall"));             // the radio mast
        c.set(f.wx(6, 2), y0 + 16, f.wz(6, 2), s("ribbon_light"));
    }

    /** The barracks: olive walls, bunks two high along the back, lockers, a chest. */
    void barracks(Canvas c, WestCity.F f) {
        if (!c.intersects(f.ox() - 8, f.oz() - 8, f.ox() + 8, f.oz() + 8)) return;
        shell(c, f, 6, 4, 4, "ribbon_olive");
        for (int lx : new int[]{-4, 4}) f.set(c, lx, 2, -4, s("ribbon_window"));
        doors(c, f, -4);
        for (int lx = -5; lx <= 5; lx += 2) {
            Direction head = f.dir(Direction.SOUTH);
            BlockState b = Blocks.GREEN_BED.defaultBlockState().setValue(BedBlock.FACING, head);
            c.set(f.wx(lx, 2), y0 + 1, f.wz(lx, 2), b.setValue(BedBlock.PART, BedPart.FOOT));
            c.set(f.wx(lx, 3), y0 + 1, f.wz(lx, 3), b.setValue(BedBlock.PART, BedPart.HEAD));
        }
        for (int lx = -5; lx <= 5; lx += 2) f.set(c, lx + 1, 1, 3, Blocks.BARREL.defaultBlockState());
        c.lootChest(f.wx(-5, -2), y0 + 1, f.wz(-5, -2), f.dir(Direction.EAST), LOOT);
        f.set(c, 0, 3, 0, s("ribbon_light"));
    }

    /** A hangar: a half-round roof of dark steel along z, closed at the back, open at the front with hazard stripes; crates. */
    void hangar(Canvas c, int hx, int z0, int z1) {
        if (!c.intersects(hx - 8, z0, hx + 8, z1)) return;
        for (int wz = z0; wz <= z1; wz++)
            for (int dx = -7; dx <= 7; dx++)
                for (int dy = 0; dy <= 7; dy++) {
                    double d = Math.hypot(dx, dy);
                    if (d > 7.4) continue;
                    boolean shell = d > 6.2;
                    BlockState st = dy == 0 ? (shell ? s("ribbon_steel_dark") : wz >= z1 - 1 ? s("ribbon_hazard") : s("ribbon_concrete"))
                            : shell ? (wz % 4 == 0 ? s("ribbon_red_band") : s("ribbon_steel_dark")) : wz == z0 ? s("ribbon_steel") : Canvas.AIR;
                    c.set(hx + dx, y0 + dy, wz, st);
                }
        for (int[] p : new int[][]{{-4, 1}, {-4, 2}, {-3, 1}, {4, 2}, {4, 3}})
            c.set(hx + p[0], y0 + 1, z0 + p[1], s("ribbon_crate"));
        c.set(hx - 4, y0 + 2, z0 + 1, s("ribbon_crate"));
        c.lootChest(hx + 5, y0 + 1, z0 + 1, Direction.SOUTH, LOOT);
        c.set(hx, y0 + 6, z0 + 6, s("ribbon_light"));
    }

    /** A helipad: a concrete disc ringed in hazard stripes with an H. */
    void helipad(Canvas c, int hx, int hz) {
        if (!c.intersects(hx - 5, hz - 5, hx + 5, hz + 5)) return;
        Shapes.disc(c, hx, hz, y0, 4.5, s("ribbon_concrete"));
        Shapes.ring(c, hx, hz, y0, 4, 4.5, s("ribbon_hazard"));
        for (int d = -2; d <= 2; d++) {
            c.set(hx - 1, y0, hz + d, Blocks.YELLOW_CONCRETE.defaultBlockState());
            c.set(hx + 1, y0, hz + d, Blocks.YELLOW_CONCRETE.defaultBlockState());
        }
        c.set(hx, y0, hz, Blocks.YELLOW_CONCRETE.defaultBlockState());
    }

    /** A building's shell: floor, walls of {@code wall} with steel corners, a flat concrete roof; hollow inside. */
    void shell(Canvas c, WestCity.F f, int hx, int hz, int h, String wall) {
        f.box(c, -hx, 0, -hz, hx, 0, hz, s("ribbon_concrete"));
        for (int dy = 1; dy <= h; dy++) {
            for (int lx = -hx; lx <= hx; lx++) {
                f.set(c, lx, dy, -hz, s(wall));
                f.set(c, lx, dy, hz, s(wall));
            }
            for (int lz = -hz; lz <= hz; lz++) {
                f.set(c, -hx, dy, lz, s(wall));
                f.set(c, hx, dy, lz, s(wall));
            }
            for (int[] p : new int[][]{{-hx, -hz}, {hx, -hz}, {-hx, hz}, {hx, hz}}) f.set(c, p[0], dy, p[1], s("ribbon_steel_dark"));
        }
        f.box(c, -hx + 1, 1, -hz + 1, hx - 1, h, hz - 1, Canvas.AIR);
        f.box(c, -hx, h + 1, -hz, hx, h + 1, hz, s("ribbon_concrete"));
    }

    /** A pair of blast doors in the front wall (local x 0 and 1). */
    void doors(Canvas c, WestCity.F f, int lz) {
        f.box(c, 0, 1, lz - 1, 1, 2, lz, Canvas.AIR);
        for (int lx = 0; lx <= 1; lx++) {
            BlockState d = s("ribbon_door").setValue(DoorBlock.FACING, f.dir(Direction.NORTH)).setValue(DoorBlock.HINGE, lx == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            c.set(f.wx(lx, lz), y0 + 1, f.wz(lx, lz), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            c.set(f.wx(lx, lz), y0 + 2, f.wz(lx, lz), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }
}
