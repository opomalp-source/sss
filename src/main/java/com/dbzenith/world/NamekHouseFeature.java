package com.dbzenith.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Namekian dome house (original design): a white hemisphere on a stone platform, a doorway, round blue windows,
 * a skylight and a lantern inside; sometimes a smaller dome is joined on. Only on reasonably flat dry ground.
 */
public class NamekHouseFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState SHELL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState TRIM = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WINDOW = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public NamekHouseFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos origin = ctx.origin();
        if (!flatAndDry(level, origin, 5)) return false;
        Direction door = Direction.from2DDataValue(random.nextInt(4));
        dome(level, origin, 4.6, door, true);
        if (random.nextBoolean()) {                                                        // annex on a side without the door
            Direction side = door.getClockWise();
            BlockPos annex = origin.relative(side, 6);
            if (flatAndDry(level, annex, 3)) {
                dome(level, annex, 3.1, side.getOpposite(), false);
                for (int i = 3; i <= 4; i++) {                                              // passage between the domes
                    BlockPos p = origin.relative(side, i);
                    setBlock(level, p, AIR);
                    setBlock(level, p.above(), AIR);
                }
            }
        }
        return true;
    }

    private void dome(WorldGenLevel level, BlockPos c, double r, Direction door, boolean main) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir - 1; x <= ir + 1; x++)
            for (int z = -ir - 1; z <= ir + 1; z++) {
                double flat = Math.sqrt(x * x + z * z);
                if (flat > r + 0.6) continue;
                setBlock(level, c.offset(x, -1, z), FLOOR);                                   // platform
                for (int y = -2; y >= -5 && !level.getBlockState(c.offset(x, y, z)).isSolid(); y--) {
                    setBlock(level, c.offset(x, y, z), FLOOR);                                // fill under the edge
                }
                for (int y = 0; y <= ir; y++) {
                    double d = Math.sqrt(x * x + y * y * 1.1 + z * z);
                    BlockPos p = c.offset(x, y, z);
                    if (d <= r - 1) setBlock(level, p, AIR);
                    else if (d <= r) setBlock(level, p, y == 0 ? TRIM : SHELL);
                }
            }
        for (int y = 0; y <= 1; y++) {                                                       // doorway
            for (int i = 1; i <= ir; i++) setBlock(level, c.relative(door, i).above(y), AIR);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {                                    // round windows
            if (d == door) continue;
            BlockPos w = c.relative(d, ir - 1).above(main ? 2 : 1);
            for (int i = 0; i <= 1 && !level.getBlockState(w).isAir(); i++) w = w.relative(d);
            if (level.getBlockState(w).is(SHELL.getBlock())) setBlock(level, w, WINDOW);
        }
        if (main) {
            setBlock(level, c.above(ir), WINDOW);                                            // skylight
            setBlock(level, c.above(ir - 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    /** Surface heights around {@code c} within one block of it, and no water underfoot. */
    private static boolean flatAndDry(WorldGenLevel level, BlockPos c, int r) {
        int base = c.getY();
        for (int x = -r; x <= r; x += r) for (int z = -r; z <= r; z += r) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX() + x, c.getZ() + z);
            if (Math.abs(h - base) > 1) return false;
            if (!level.getFluidState(new BlockPos(c.getX() + x, h - 1, c.getZ() + z)).isEmpty()) return false;
        }
        return true;
    }
}
