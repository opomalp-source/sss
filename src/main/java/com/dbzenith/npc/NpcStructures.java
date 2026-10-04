package com.dbzenith.npc;

import com.dbzenith.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Small buildings for the quest NPCs, built from code (no structure files): an open-fronted training dojo for the
 * Martial Arts Master and a Galactic Patrol outpost. Both are 7x7, open to the north (-Z).
 */
public final class NpcStructures {
    public enum Kind { DOJO, OUTPOST }

    private static final int R = 3;

    private NpcStructures() {}

    /** Builds on the surface at {@code center}. Returns where the NPC should stand. */
    public static BlockPos build(ServerLevel level, BlockPos center, Kind kind) {
        level.getChunk(center.getX() >> 4, center.getZ() >> 4);
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()) - 1;
        BlockPos base = new BlockPos(center.getX(), y0, center.getZ());
        clear(level, base);
        foundation(level, base, kind == Kind.DOJO ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
        if (kind == Kind.DOJO) dojo(level, base);
        else outpost(level, base);
        return base.above();
    }

    private static void clear(ServerLevel level, BlockPos base) {
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++)
                for (int y = 1; y <= 8; y++) set(level, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
    }

    /** The floor ring is solid down to the ground, so nothing floats. */
    private static void foundation(ServerLevel level, BlockPos base, BlockState fill) {
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++)
                for (int y = -1; y >= -6; y--) {
                    BlockPos p = base.offset(x, y, z);
                    if (level.getBlockState(p).isSolidRender(level, p)) break;
                    set(level, p, fill);
                }
    }

    private static void dojo(ServerLevel level, BlockPos b) {
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState wall = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) set(level, b.offset(x, 0, z), floor);
        for (int y = 1; y <= 3; y++) {
            for (int x = -R; x <= R; x++) {
                set(level, b.offset(x, y, R), y == 2 && Math.abs(x) == 1 ? Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState() : wall);
            }
            for (int z = -R + 1; z < R; z++) {
                boolean window = y == 2 && z == 0;
                set(level, b.offset(-R, y, z), window ? Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState() : wall);
                set(level, b.offset(R, y, z), window ? Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState() : wall);
            }
            for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) set(level, b.offset(c[0], y, c[1]), pillar);
        }
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                int edge = Math.max(Math.abs(x), Math.abs(z));
                set(level, b.offset(x, 4, z), edge == R + 1 ? slab : roof);
                if (edge <= R - 1) set(level, b.offset(x, 5, z), edge == R - 1 ? slab : roof);
                if (edge <= R - 2) set(level, b.offset(x, 6, z), slab);
            }
        set(level, b.offset(-R + 1, 3, -R), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, b.offset(R - 1, 3, -R), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, b.offset(2, 1, 2), ModBlocks.PUNCHING_BAG.get().defaultBlockState());
        set(level, b.offset(-2, 1, 2), Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private static void outpost(ServerLevel level, BlockPos b) {
        BlockState floor = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState wall = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                set(level, b.offset(x, 0, z), floor);
                set(level, b.offset(x, 4, z), Blocks.BLUE_CONCRETE.defaultBlockState());
            }
        for (int y = 1; y <= 3; y++)
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    boolean edge = Math.abs(x) == R || Math.abs(z) == R;
                    if (!edge) continue;
                    boolean door = z == -R && Math.abs(x) <= 1 && y <= 2;
                    boolean window = y == 2 && (Math.abs(x) == R || z == R) && Math.abs(x) + Math.abs(z) < 2 * R;
                    set(level, b.offset(x, y, z), door ? Blocks.AIR.defaultBlockState() : window ? glass : wall);
                }
        set(level, b.offset(0, 4, 0), Blocks.SEA_LANTERN.defaultBlockState());
        set(level, b.offset(0, 5, 0), Blocks.LIGHTNING_ROD.defaultBlockState());
        set(level, b.offset(2, 1, 2), Blocks.LECTERN.defaultBlockState());
        set(level, b.offset(-2, 1, 2), Blocks.BLUE_BANNER.defaultBlockState());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }
}
