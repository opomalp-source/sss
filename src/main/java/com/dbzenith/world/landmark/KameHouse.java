package com.dbzenith.world.landmark;

import com.dbzenith.world.arch.CivilizationBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Kame House (canon; docs/WORLD_STRUCTURES.md): Master Roshi's house on its little island far out at sea. A pink
 * two-storey house under a red roof with "KAME HOUSE" across the front of the upper storey; downstairs the main room
 * (a television, a low table with cushions), the kitchen and a small washroom, a stair up to the one bedroom. A sandy
 * island with palms, a beach chair under an umbrella; Master Roshi on the porch and a sea turtle on the beach. One per
 * world, where the sea is open all round. The house's front faces south (+z).
 */
final class KameHouse extends LandmarkPlan {
    /** The island's rough radius, and the sea level (the island's top is a block above it). */
    static final int ISLAND = 12;
    final int sea, y;

    private KameHouse(LandmarkSites.Site site, int sea) {
        super(site);
        this.sea = sea;
        this.y = sea + 1;
    }

    /** Open sea: water at the spot and all round it, neither too shallow (no shore) nor too deep. */
    static LandmarkPlan plan(LandmarkSites.Site s, LandmarkSites.Terrain t) {
        int sea = t.seaLevel(), floor = t.height(s.x(), s.z());
        if (!t.wetOver(s.x(), s.z(), floor) || floor < sea - 24 || floor > sea - 3) return null;
        for (int i = 0; i < 24; i++) {                                                         // open sea all round, near and far
            double a = i * Math.PI / 4 + (i >= 16 ? Math.PI / 8 : 0), r = i < 8 ? 30 : i < 16 ? 56 : 90;
            int px = s.x() + (int) Math.round(Math.cos(a) * r), pz = s.z() + (int) Math.round(Math.sin(a) * r), h = t.height(px, pz);
            if (h > sea - 2 || !t.wetOver(px, pz, h)) return null;
        }
        return new KameHouse(s, sea);
    }

    @Override
    public BlockPos arrival() {
        return new BlockPos(x, y + 1, z + 12);
    }

    static BlockState s(String id) {
        return CivilizationBlocks.get(id);
    }

    /** The island's edge at an angle: a rough circle, the same for every chunk. */
    double edge(double angle) {
        return ISLAND + 1.6 * Math.sin(angle * 3 + (seed & 7)) + 0.9 * Math.sin(angle * 5 + ((seed >> 3) & 7));
    }

    @Override
    public void build(Canvas c) {
        if (!c.intersects(x - 34, z - 34, x + 34, z + 34)) return;
        island(c);
        house(c);
        Shapes.palm(c, x - 8, y + 1, z - 6, 7, 3);
        Shapes.palm(c, x + 8, y + 1, z + 6, 8, 11);
        Shapes.palm(c, x + 9, y + 1, z - 5, 6, 6);
        // a beach chair under a red-and-white umbrella
        c.set(x + 5, y + 1, z + 8, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
        c.set(x + 5, y + 1, z + 9, Canvas.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), false));
        c.column(x + 7, z + 9, y + 1, y + 3, Blocks.OAK_FENCE.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                c.set(x + 7 + dx, y + 4, z + 9 + dz, (dx + dz & 1) == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
        c.spawn(com.dbzenith.npc.ModNpcs.MASTERS.get(com.dbzenith.style.MasterRoster.MASTER_ROSHI).get(), x + 2.5, y + 1, z + 6.5, 180f);
        c.spawn(EntityType.TURTLE, x - 6.5, y + 1, z + 9.5, 30f);
    }

    /** Sand up out of the sea: a beach all round sloping into the water, a sandstone core. */
    void island(Canvas c) {
        BlockState sand = Blocks.SAND.defaultBlockState(), core = Blocks.SANDSTONE.defaultBlockState();
        for (int wx = Math.max(c.minX, x - 34); wx <= Math.min(c.maxX, x + 34); wx++)
            for (int wz = Math.max(c.minZ, z - 34); wz <= Math.min(c.maxZ, z + 34); wz++) {
                double d = Math.hypot(wx - x, wz - z), e = edge(Math.atan2(wz - z, wx - x));
                int top;
                if (d <= e - 2) top = y;                                                      // dry land
                else if (d <= e) top = sea;                                                   // the beach at the water's edge
                else if (d <= e + 18) top = (int) Math.floor(sea - 1 - (d - e) * 1.2);          // the slope under water, down to the sea floor
                else continue;
                int floor = c.surface(wx, wz);
                if (top <= floor) continue;
                for (int k = floor + 1; k <= top; k++) c.set(wx, k, wz, k < top - 3 ? core : sand);
            }
    }

    // ------------------------------------------------------------------ the house

    void house(Canvas c) {
        BlockState pink = s("kame_pink"), trim = s("kame_trim"), planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), roofSlab = Canvas.slab(s("kame_roof_slab"), false);
        // ground storey: x -5..5, z -4..4, walls 4 high
        c.box(x - 5, y, z - 4, x + 5, y, z + 4, planks);
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                boolean rim = Math.abs(dx) == 5 || Math.abs(dz) == 4, corner = Math.abs(dx) == 5 && Math.abs(dz) == 4;
                c.box(x + dx, y + 1, z + dz, x + dx, y + 4, z + dz, rim ? (corner ? trim : pink) : Canvas.AIR);
            }
        for (int[] p : new int[][]{{-3, 4}, {3, 4}, {-5, 0}, {5, 0}, {-2, -4}, {2, -4}}) c.set(x + p[0], y + 2, z + p[1], s("kame_window"));
        c.box(x, y + 1, z + 4, x, y + 2, z + 4, Canvas.AIR);
        BlockState door = s("kame_door").setValue(DoorBlock.FACING, Direction.SOUTH);
        c.set(x, y + 1, z + 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(x, y + 2, z + 4, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        c.box(x - 1, y, z + 5, x + 1, y, z + 6, planks);                                     // the porch
        // the low red roof round the upper storey (the ground floor's ceiling), the upper floor inside it
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -5; dz <= 6; dz++) {
                boolean upper = Math.abs(dx) <= 3 && Math.abs(dz) <= 3;
                if (upper) c.set(x + dx, y + 5, z + dz, planks);
                else if (dz <= 5 || Math.abs(dx) <= 2) c.set(x + dx, y + 5, z + dz, roofSlab);   // eaves, and over the porch
            }
        // upper storey: x -3..3, z -3..3, walls 3 high, the sign across its front
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3, corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                c.box(x + dx, y + 6, z + dz, x + dx, y + 8, z + dz, rim ? (corner ? trim : pink) : Canvas.AIR);
            }
        for (int i = 0; i < 5; i++) c.set(x - 2 + i, y + 7, z + 3, s("kame_sign_" + (i + 1)));
        c.set(x - 3, y + 7, z, s("kame_window"));
        c.set(x + 3, y + 7, z, s("kame_window"));
        c.set(x, y + 7, z - 3, s("kame_window"));
        Shapes.hipRoof(c, x - 4, z - 4, x + 4, z + 4, y + 9, "kame_roof", 1, s("kame_trim"));
        // inside, downstairs: the stair up (west), the main room (a television, a low table, cushions), the kitchen (east), a washroom
        for (int i = 0; i < 4; i++) c.set(x - 2, y + 1 + i, z + 3 - i, Canvas.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.NORTH, false));
        c.box(x - 2, y + 5, z, x - 2, y + 5, z + 2, Canvas.AIR);
        c.set(x + 1, y + 1, z - 3, s("capsule_tech_panel"));                                  // the television
        c.set(x + 1, y + 1, z, Canvas.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), false));     // the low table
        for (int[] p : new int[][]{{0, 0}, {2, 0}, {1, 1}, {1, -1}}) c.set(x + p[0], y + 1, z + p[1], Blocks.RED_CARPET.defaultBlockState());
        c.set(x + 4, y + 1, z - 3, Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        c.set(x + 4, y + 1, z - 2, Blocks.BARREL.defaultBlockState());
        c.set(x + 3, y + 1, z - 3, Blocks.WATER_CAULDRON.defaultBlockState());
        c.box(x + 3, y + 1, z + 2, x + 3, y + 3, z + 3, trim);                                // the washroom's wall
        c.set(x + 4, y + 1, z + 3, Canvas.stairs(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH, false));
        c.set(x + 4, y + 1, z + 2, Blocks.CAULDRON.defaultBlockState());
        c.set(x + 2, y + 4, z - 1, s("village_lantern"));
        // upstairs: Master Roshi's bedroom
        bed(c, x + 1, y + 6, z - 1, Direction.NORTH);
        c.set(x - 1, y + 6, z - 2, Blocks.BOOKSHELF.defaultBlockState());
        c.set(x + 2, y + 6, z + 2, Blocks.CHEST.defaultBlockState());
        c.set(x, y + 8, z, s("village_lantern"));
    }

    void bed(Canvas c, int wx, int wy, int wz, Direction head) {
        BlockState b = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, head);
        c.set(wx, wy, wz, b.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(wx + head.getStepX(), wy, wz + head.getStepZ(), b.setValue(BedBlock.PART, BedPart.HEAD));
    }
}
