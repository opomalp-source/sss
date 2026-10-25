package com.dbzenith.world.landmark;

import com.dbzenith.npc.ModNpcs;
import com.dbzenith.world.arch.CivilizationBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Arrays;

/**
 * Muscle Tower (canon; docs/WORLD_STRUCTURES.md): the Red Ribbon Army's tower in the snowy north. A square steel tower
 * of six storeys, red bands at each floor and the army's emblem over the blast doors (south), climbed by switchback
 * stairs along its walls. Its floors as in the series: a garage of tools and supplies, a guard room, a machine on the
 * third (an android unit), a little garden and a room, a dark lair strewn with bones, and at the top the commander's
 * control room with his chest (the army was after the Dragon Balls: a Dragon Radar). On the roof a radio mast and
 * floodlights. One per world.
 */
final class MuscleTower extends LandmarkPlan {
    /** Half its width, the height of a storey, the storeys, and how far the ground is eased in round it. */
    static final int H = 7, STOREY = 6, STOREYS = 6, BLEND = 12;
    final int y0;

    private MuscleTower(LandmarkSites.Site site, int y0) {
        super(site);
        this.y0 = y0;
    }

    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int[] hs = new int[9];
        int n = 0;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                int px = s.x() + i * 10, pz = s.z() + j * 10, h = t.height(px, pz);
                if (t.wetOver(px, pz, h)) return null;
                hs[n++] = h;
            }
        Arrays.sort(hs);
        if (hs[8] - hs[0] > 12 || hs[4] <= t.seaLevel()) return null;
        return new MuscleTower(s, hs[4]);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y0 + 1, z + H + 10);
    }

    @Override
    public int ownedAbove(int wx, int wz) {
        return Math.max(Math.abs(wx - x), Math.abs(wz - z)) <= H + 3 + BLEND ? y0 : Integer.MAX_VALUE;
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    /** The floor of storey k (k = STOREYS is the roof). */
    int floor(int k) {
        return y0 + k * STOREY;
    }

    @Override
    public void build(Canvas c) {
        int R = H + 3 + BLEND;
        if (!c.intersects(x - R, z - R, x + R, z + R)) return;
        ground(c);
        BlockState wall = s("ribbon_steel"), band = s("ribbon_red_band"), corner = s("ribbon_steel_dark");
        int top = floor(STOREYS);
        for (int dx = -H; dx <= H; dx++)
            for (int dz = -H; dz <= H; dz++) {
                boolean rim = Math.abs(dx) == H || Math.abs(dz) == H, edge = Math.abs(dx) == H && Math.abs(dz) == H;
                for (int y = y0 + 1; y <= top + 1; y++) {
                    int dy = y - y0;
                    BlockState st;
                    if (rim) st = edge ? corner : dy % STOREY == 0 ? band : (dy % STOREY == 3 && (Math.abs(dx) == 3 || Math.abs(dz) == 3)) ? s("ribbon_window") : wall;
                    else st = dy % STOREY == 0 ? (dy == STOREYS * STOREY ? s("ribbon_concrete") : s("ribbon_grate")) : Canvas.AIR;
                    if (y == top + 1) st = rim ? s("ribbon_steel_wall") : Canvas.AIR;              // the parapet on the roof
                    c.set(x + dx, y, z + dz, st);
                }
            }
        // the doors (south) under the emblem, a step of hazard stripes before them
        for (int dx = 0; dx <= 1; dx++) {
            BlockState d = s("ribbon_door").setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, dx == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            c.set(x + dx, y0 + 1, z + H, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            c.set(x + dx, y0 + 2, z + H, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            c.set(x + dx, y0, z + H + 1, s("ribbon_hazard"));
        }
        c.set(x, y0 + 4, z + H, s("ribbon_emblem"));
        c.set(x + 1, y0 + 4, z + H, s("ribbon_emblem"));
        for (int k = 0; k < STOREYS; k++) storey(c, k);
        roof(c, top);
        garrison(c);
    }

    /** The ground round the tower levelled and eased into the land, snowy. */
    void ground(Canvas c) {
        for (int wx = c.minX; wx <= c.maxX; wx++)
            for (int wz = c.minZ; wz <= c.maxZ; wz++) {
                int out = Math.max(Math.abs(wx - x), Math.abs(wz - z)) - H - 3;
                if (out > BLEND) continue;
                int target = y0;
                if (out > 0) {
                    double f = (double) out / BLEND;
                    f = f * f * (3 - 2 * f);
                    target = (int) Math.round(y0 + (c.surface(wx, wz) - y0) * f);
                }
                c.ground(wx, wz, target, out > 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : s("ribbon_concrete"), Blocks.DIRT.defaultBlockState(), out > 0 ? 8 : 48);
            }
    }

    /**
     * Storey k: its stair up (west wall going north on even storeys, east wall going south on odd ones, so the stairs
     * switch back), a hole in the floor above it, a light, crates and a console.
     */
    void storey(Canvas c, int k) {
        int f = floor(k);
        boolean even = k % 2 == 0;
        int sx = even ? -(H - 1) : H - 1;
        for (int i = 0; i < STOREY; i++) {
            int sz = even ? H - 2 - i : -(H - 2) + i;
            c.set(x + sx, f + 1 + i, z + sz, Canvas.stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), even ? Direction.NORTH : Direction.SOUTH, false));
        }
        for (int i = 2; i < STOREY; i++) {                                                  // headroom: the floor above opened over the top steps
            int sz = even ? H - 2 - i : -(H - 2) + i;
            if (i < STOREY - 1) c.set(x + sx, f + STOREY, z + sz, Canvas.AIR);
        }
        if (k != 4) c.set(x, f + STOREY - 1, z, s("ribbon_light"));                          // (the lair is kept dark)
        int g = f + 1;
        switch (k) {
            case 0 -> {                                                                         // the garage: tools and supplies
                c.set(x + 3, g, z - 5, Blocks.SMITHING_TABLE.defaultBlockState());
                c.set(x + 4, g, z - 5, Blocks.ANVIL.defaultBlockState());
                c.set(x + 5, g, z - 5, Blocks.GRINDSTONE.defaultBlockState());
                for (int[] p : new int[][]{{4, 3}, {5, 3}, {4, 4}, {5, 4}, {5, 5}}) c.set(x + p[0], g, z + p[1], s("ribbon_crate"));
                c.set(x + 5, g + 1, z + 4, s("ribbon_crate"));
                c.set(x - 3, g, z - 5, Blocks.BARREL.defaultBlockState());
                c.set(x - 2, g, z - 5, Blocks.BARREL.defaultBlockState());
            }
            case 1 -> {                                                                         // a guard room
                for (int dx = -2; dx <= 2; dx += 2) c.set(x + dx, g, z - 5, s("ribbon_console"));
                c.set(x - 4, g, z + 4, s("ribbon_crate"));
            }
            case 2 -> {                                                                         // the machine's floor: scorched plating, a bank of consoles
                c.box(x - 3, f, z - 3, x + 3, f, z + 3, s("ribbon_steel_dark"));
                for (int dz = -4; dz <= 4; dz += 2) c.set(x - 5, g, z + dz, s("ribbon_console"));
            }
            case 3 -> {                                                                         // a little garden and a room
                c.box(x - 5, f, z + 1, x - 2, f, z + 4, Blocks.GRASS_BLOCK.defaultBlockState());
                for (int[] p : new int[][]{{-5, 1}, {-3, 2}, {-4, 4}, {-2, 3}}) c.set(x + p[0], g, z + p[1], Blocks.POPPY.defaultBlockState());
                c.set(x - 4, g, z + 2, Blocks.AZALEA.defaultBlockState());
                for (int dx = 1; dx <= 5; dx++) c.box(x + dx, g, z - 2, x + dx, g + 2, z - 2, dx == 3 ? Canvas.AIR : s("ribbon_steel"));
                c.set(x + 3, g + 1, z - 2, Canvas.AIR);
                c.set(x + 4, g, z - 5, Blocks.BOOKSHELF.defaultBlockState());
                c.set(x + 2, g, z - 5, Blocks.BARREL.defaultBlockState());
            }
            case 4 -> {                                                                         // the lair: dark, bones everywhere
                for (int i = 0; i < 14; i++) {
                    int bx = x - 5 + Canvas.hash(seed, i, k, 1) % 11, bz = z - 5 + Canvas.hash(seed, i, k, 2) % 11;
                    if (Math.abs(bx - x - sx) < 1) continue;
                    c.set(bx, g, bz, i % 3 == 0 ? Blocks.SKELETON_SKULL.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
            default -> {                                                                        // the top: the commander's control room
                for (int dx = -4; dx <= 4; dx++) c.set(x + dx, g, z - 5, s("ribbon_console"));
                for (int dz = -3; dz <= 3; dz += 2) c.set(x - 5, g, z + dz, s("ribbon_console"));
                c.box(x - 1, g, z - 1, x + 1, g, z, Canvas.slab(s("ribbon_steel_dark_slab"), true));
                c.lootChest(x + 3, g, z - 4, Direction.SOUTH, "dbzenith:chests/red_ribbon_tower");
            }
        }
    }

    /** The roof: the radio mast, floodlights at the corners. */
    void roof(Canvas c, int top) {
        c.column(x + 4, z - 4, top + 1, top + 12, s("ribbon_steel_wall"));
        c.set(x + 4, top + 13, z - 4, s("ribbon_light"));
        for (int[] p : new int[][]{{-H, -H}, {H, -H}, {-H, H}, {H, H}}) c.set(x + p[0], top + 2, z + p[1], s("ribbon_light"));
    }

    /**
     * Who holds each floor, as the canon tower's floors go: two in the garage, four in the guard room, an android unit
     * on the third (the tower's machine of a sergeant), one by the garden, two in the dark lair, two in the control room,
     * one on the roof by the mast.
     */
    void garrison(Canvas c) {
        var soldier = ModNpcs.RED_RIBBON_SOLDIER.get();
        double[][][] posts = {
                {{2.5, 1.5}, {-1.5, -2.5}},
                {{2.5, 1.5}, {-1.5, -2.5}, {0.5, 3.5}, {-2.5, 0.5}},
                {},
                {{1.5, 2.5}},
                {{2.5, 1.5}, {-1.5, -2.5}},
                {{2.5, 1.5}, {-2.5, 2.5}}};
        for (int k = 0; k < STOREYS; k++) {
            int f = floor(k) + 1;
            for (double[] p : posts[k]) c.spawn(soldier, x + p[0], f, z + p[1], 180f);
        }
        c.spawn(ModNpcs.ANDROID_UNIT.get(), x + 0.5, floor(2) + 1, z + 0.5, 180f);
        c.spawn(soldier, x - 3.5, floor(STOREYS) + 1, z + 3.5, 180f);
    }
}
