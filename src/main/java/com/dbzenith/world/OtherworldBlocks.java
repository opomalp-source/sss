package com.dbzenith.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Blocks of the other world (CX-12). */
public final class OtherworldBlocks {
    private OtherworldBlocks() {}

    /** The golden cloud sea: soft enough to sink through, slowly, towards Limbo far below. */
    public static class Cloud extends Block {
        public Cloud(Properties p) {
            super(p);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            entity.makeStuckInBlock(state, new Vec3(0.85, 0.35, 0.85));
            entity.resetFallDistance();
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
            return neighbour.is(this) || super.skipRendering(state, neighbour, side);
        }

        @Override
        public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1f;
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }
    }

    /** A spring of godly ki: shimmering water to wade and meditate in (see Otherworld). */
    public static class Spring extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

        public Spring(Properties p) {
            super(p);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            entity.clearFire();
            entity.resetFallDistance();
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 0.9, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
            }
        }

        @Override
        public boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
            return neighbour.is(this) || super.skipRendering(state, neighbour, side);
        }
    }
}
