package com.dbzenith.world.landmark;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds the landmarks (CX-33): placed once in every overworld chunk, last of the decoration steps (after trees and
 * plants, so it can clear them), it draws the part of every landmark that reaches this chunk and nothing outside it.
 */
public class LandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public LandmarkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** How far into its neighbours a chunk's trees can reach (and so how far around the chunk the sweep looks). */
    private static final int SWEEP = 8;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        ServerLevel server = ctx.level().getLevel();
        if (server.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        int chunkX = ctx.origin().getX() >> 4, chunkZ = ctx.origin().getZ() >> 4;
        LandmarkSites.Terrain t = new LandmarkSites.Terrain(ctx.chunkGenerator(), server.getChunkSource().randomState(), ctx.level(),
                ctx.chunkGenerator().getSeaLevel());
        Canvas canvas = new Canvas(ctx.level(), chunkX, chunkZ);
        boolean[] any = {false};
        LandmarkSites.forChunk(ctx.level().getSeed(), t, chunkX, chunkZ, plan -> {
            plan.build(canvas);
            sweep(ctx.level(), plan, chunkX, chunkZ);
            any[0] = true;
        });
        return any[0];
    }

    /**
     * This chunk's trees were just placed and may hang into neighbours already built: clears natural leaves, vines
     * and plants from the chunk and the strip around it wherever the landmark owns the ground. Landmark foliage is
     * persistent, so it stays.
     */
    private static void sweep(WorldGenLevel level, LandmarkPlan plan, int chunkX, int chunkZ) {
        int x0 = (chunkX << 4) - SWEEP, z0 = (chunkZ << 4) - SWEEP, x1 = (chunkX << 4) + 15 + SWEEP, z1 = (chunkZ << 4) + 15 + SWEEP;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int floor = plan.ownedAbove(x, z);
                if (floor == Integer.MAX_VALUE) continue;
                // the _WG heightmaps stop updating once features start, so the plain one finds the trees' tops
                int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
                for (int y = floor + 1; y <= top; y++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)
                            || s.is(Blocks.VINE) || s.is(BlockTags.LOGS) && !s.is(Blocks.JUNGLE_LOG) && !s.is(Blocks.SPRUCE_LOG)
                            || s.is(Blocks.BEE_NEST))
                        level.setBlock(pos, Canvas.AIR, 2);
                }
            }
    }
}
