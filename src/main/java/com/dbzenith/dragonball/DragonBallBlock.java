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

/** One of the seven balls of a set ({@link BallSet}) as a glowing block. Tracked by {@link DragonBalls}. */
public class DragonBallBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);
    /** A Super Dragon Ball fills its block. */
    private static final VoxelShape BIG = Block.box(0, 0, 0, 16, 16, 16);
    /** Namek's balls are larger than Earth's (v0.167.0). */
    private static final VoxelShape LARGE = Block.box(2, 0, 2, 14, 12, 14);

    private final BallSet set;
    private final int star;

    public DragonBallBlock(BallSet set, int star, Properties properties) {
        super(properties);
        this.set = set;
        this.star = star;
    }

    public BallSet set() {
        return set;
    }

    public int star() {
        return star;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return set == BallSet.SUPER ? BIG : set == BallSet.NAMEK ? LARGE : SHAPE;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) DragonBalls.onPlaced(sl, set, star, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel sl && !newState.is(this)) DragonBalls.onRemoved(sl, set, star, pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            String problem = DragonBalls.summonProblem(sl, sp, pos, set);           // too few, or (Namek's) no one to speak the words
            if (problem != null) sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(problem), true);
            else DragonBalls.trySummon(sl, sp, pos, set);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
