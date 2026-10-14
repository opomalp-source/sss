package com.dbzenith.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A House Capsule (CX-50): throw it and, in a burst of smoke, a round domed house pops up a few blocks in front of you,
 * its door facing you (a white dome with a yellow band, round windows, a bed, a table, a furnace, a lantern). Use it
 * again close by and the house packs back into the capsule. Only blocks still as the house placed them are taken back,
 * so nothing of yours is ever removed, and it only goes up where there is room.
 */
public class HouseCapsuleItem extends Item {
    /** The dome: footprint radius and height (blocks). */
    static final int R = 4, H = 5;
    /** How close you must be to pack it up. */
    static final double PACK_RANGE = 24;

    public HouseCapsuleItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.sidedSuccess(stack, true);
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getBoolean("Deployed")) pack(server, player, stack, tag);
        else deploy(server, player, tag);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private static void deploy(ServerLevel level, Player player, CompoundTag tag) {
        Direction facing = player.getDirection();                               // the house is built ahead, its door toward you
        Vec3 ahead = player.position().add(facing.getStepX() * (R + 2.5), 0, facing.getStepZ() * (R + 2.5));
        BlockPos ground = null;
        for (int dy = 0; dy >= -5; dy--) {                                      // the ground there, from your feet down
            BlockPos p = BlockPos.containing(ahead.x, player.getY() + dy, ahead.z);
            if (level.getBlockState(p).isFaceSturdy(level, p, Direction.UP) && level.getBlockState(p.above()).canBeReplaced()) {
                ground = p;
                break;
            }
        }
        if (ground == null) {
            player.displayClientMessage(Component.translatable("message.dbzenith.capsule_house.ground"), true);
            return;
        }
        Map<BlockPos, BlockState> plan = plan(ground.above(), facing.getOpposite());
        for (BlockPos p : plan.keySet()) {
            if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.dbzenith.capsule_house.space"), true);
                return;
            }
        }
        List<Long> positions = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {                                  // the shell first, then what hangs on it
            for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
                if (e.getValue().isAir()) continue;                              // the room inside is already empty
                boolean dependent = !e.getValue().isCollisionShapeFullBlock(level, e.getKey());
                if ((pass == 0) == dependent) continue;
                level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                positions.add(e.getKey().asLong());
                states.add(Block.getId(e.getValue()));
            }
        }
        tag.putBoolean("Deployed", true);
        tag.putString("Dim", level.dimension().location().toString());
        tag.putLongArray("Blocks", positions);
        tag.putIntArray("States", states);
        tag.putLong("At", ground.above().asLong());
        poof(level, Vec3.atCenterOf(ground.above(H / 2)));
        player.displayClientMessage(Component.translatable("message.dbzenith.capsule_house.deployed"), true);
    }

    private static void pack(ServerLevel level, Player player, ItemStack stack, CompoundTag tag) {
        BlockPos at = BlockPos.of(tag.getLong("At"));
        if (!tag.getString("Dim").equals(level.dimension().location().toString()) || player.distanceToSqr(Vec3.atCenterOf(at)) > PACK_RANGE * PACK_RANGE) {
            player.displayClientMessage(Component.translatable("message.dbzenith.capsule_house.far"), true);
            return;
        }
        long[] blocks = tag.getLongArray("Blocks");
        int[] states = tag.getIntArray("States");
        for (int i = blocks.length - 1; i >= 0; i--) {                         // in reverse: what hangs comes off first
            BlockPos p = BlockPos.of(blocks[i]);
            if (i < states.length && Block.getId(level.getBlockState(p)) == states[i])
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
        tag.remove("Deployed");
        tag.remove("Blocks");
        tag.remove("States");
        poof(level, Vec3.atCenterOf(at.above(H / 2)));
        player.displayClientMessage(Component.translatable("message.dbzenith.capsule_house.packed"), true);
    }

    /** The capsule bursting: a cloud of smoke, a pop. */
    private static void poof(ServerLevel level, Vec3 c) {
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 80, R * 0.6, H * 0.5, R * 0.6, 0.06);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 40, R * 0.5, H * 0.4, R * 0.5, 0.05);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1f, 0.6f);
    }

    /**
     * The house, standing on {@code base} (its floor), the door on its {@code door} side. Local x across, z from the
     * door (-R) to the back (+R).
     */
    static Map<BlockPos, BlockState> plan(BlockPos base, Direction door) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        Direction right = door.getCounterClockWise();
        for (int dy = 0; dy <= H; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double outer = (dx * dx + dz * dz) / (double) (R * R + R) + Math.pow(dy / (double) H, 2);
            double inner = (dx * dx + dz * dz) / (double) ((R - 1) * (R - 1) + R - 1) + Math.pow(dy / (double) (H - 1), 2);
            if (outer > 1) continue;
            BlockState s;
            if (dy == 0) s = Blocks.SMOOTH_QUARTZ.defaultBlockState();
            else if (inner <= 1) s = Blocks.AIR.defaultBlockState();
            else if (dy == 1) s = Blocks.YELLOW_CONCRETE.defaultBlockState();                // the band
            else if (dy == 2 && (dx == 0 || dz == 0) && !(dz < 0 && dx == 0)) s = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();   // round windows
            else s = Blocks.WHITE_CONCRETE.defaultBlockState();
            out.put(at(base, door, right, dx, dy, dz), s);
        }
        // the doorway, facing out
        int front = -doorDepth();
        out.put(at(base, door, right, 0, 1, front), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        out.put(at(base, door, right, 0, 2, front), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // inside: a bed at the back, a table and a furnace to the sides, a lantern from the top
        out.put(at(base, door, right, -1, 1, 2), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, door.getOpposite()).setValue(BedBlock.PART, BedPart.HEAD));
        out.put(at(base, door, right, -1, 1, 1), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, door.getOpposite()).setValue(BedBlock.PART, BedPart.FOOT));
        out.put(at(base, door, right, 2, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        out.put(at(base, door, right, 2, 1, -1), Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, right.getOpposite()));
        out.put(at(base, door, right, 0, H - 1, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        return out;
    }

    /** How far from the middle the door sits: the shell's front at door height. */
    private static int doorDepth() {
        for (int dz = R; dz > 0; dz--) {
            double outer = dz * dz / (double) (R * R + R) + Math.pow(1.0 / H, 2);
            if (outer <= 1) return dz;
        }
        return R;
    }

    private static BlockPos at(BlockPos base, Direction door, Direction right, int dx, int dy, int dz) {
        return base.relative(right, dx).relative(door, -dz).above(dy);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Deployed");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        boolean out = stack.hasTag() && stack.getTag().getBoolean("Deployed");
        lines.add(Component.translatable(out ? "item.dbzenith.capsule_house.out" : "item.dbzenith.capsule_house.hint"));
    }
}
