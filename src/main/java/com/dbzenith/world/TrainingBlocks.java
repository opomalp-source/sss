package com.dbzenith.world;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.registry.ModBlockEntities;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Training blocks: Gravity Chamber, Punching Bag, Time Chamber Door. */
public final class TrainingBlocks {
    private TrainingBlocks() {}

    /** Right-click: raise gravity (sneak: lower). Affects players within {@code training.gravityRadius}. */
    public static class GravityChamber extends BaseEntityBlock {
        public GravityChamber(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new GravityChamberBlockEntity(pos, state);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof GravityChamberBlockEntity be) {
                int g = be.cycle(player.isShiftKeyDown());
                player.displayClientMessage(Component.translatable("message.dbzenith.gravity_set", g), true);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 0.5f + Math.min(1.5f, g / 100f));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.GRAVITY_CHAMBER.get(), GravityChamberBlockEntity::serverTick);
        }
    }

    /** Hit it to train: TP per punch, scaled by your training multiplier; costs stamina. */
    public static class PunchingBag extends Block {
        public PunchingBag(Properties properties) {
            super(properties);
        }

        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 16, 12.5);

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                    net.minecraft.world.phys.shapes.CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public void attack(BlockState state, Level level, BlockPos pos, Player player) {
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
            punch(sp);
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.BLOCKS, 0.7f, 0.8f);
        }

        /** Shared with the GameTest. Returns true if the punch counted. */
        public static boolean punch(ServerPlayer player) {
            DBZConfig.Server c = DBZConfig.SERVER;
            return ModCapabilities.get(player).map(d -> {
                long now = player.level().getGameTime();
                if (d.isOnCooldown("punching_bag", now)) return false;
                d.setCooldown("punching_bag", now + c.punchCooldownTicks.get());
                d.setStamina(d.getStamina() - c.meleeStaminaCost.get());
                d.addTrainingProgress(StatCalculator.scaleTpGain(d, c.tpPerPunch.get()));
                return true;
            }).orElse(false);
        }
    }

    /** Right-click to enter the Hyperbolic Time Chamber, or to leave it from inside. */
    public static class TimeChamberDoor extends Block {
        public TimeChamberDoor(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                if (TimeChamber.isIn(sp)) TimeChamber.exit(sp);
                else TimeChamber.enter(sp);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
