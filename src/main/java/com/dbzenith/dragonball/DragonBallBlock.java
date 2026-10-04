package com.dbzenith.dragonball;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One of the seven Dragon Balls as a small glowing block. Tracked by {@link DragonBalls}. */
public class DragonBallBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    private final int star;

    public DragonBallBlock(int star, Properties properties) {
        super(properties);
        this.star = star;
    }

    public int star() {
        return star;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) DragonBalls.onPlaced(sl, star, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel sl && !newState.is(this)) DragonBalls.onRemoved(sl, star, pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (!DragonBalls.trySummon(sl, sp, pos)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.need_all_balls"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
