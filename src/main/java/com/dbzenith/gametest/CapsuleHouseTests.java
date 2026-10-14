package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-50: the House Capsule goes up where there is room, packs away, and never takes what isn't its own. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class CapsuleHouseTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void houseCapsuleDeploysAndPacks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxBuildHeight() - 60);
        for (int x = -12; x <= 12; x++) for (int y = 0; y <= 7; y++) for (int z = -4; z <= 22; z++)   // a clean slate (a failed run may have left things)
            level.setBlockAndUpdate(o.offset(x, y, z), y == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        ServerPlayer p = TestPlayers.create(h);
        p.moveTo(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0, 0);           // facing +z (south)
        ItemStack capsule = new ItemStack(ModItems.CAPSULE_HOUSE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, capsule);

        capsule.getItem().use(level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(capsule.getTag() != null && capsule.getTag().getBoolean("Deployed"), "the house went up");
        int placed = capsule.getTag().getLongArray("Blocks").length;
        h.assertTrue(placed > 100, "a whole house: " + placed + " blocks");
        long concrete = java.util.Arrays.stream(capsule.getTag().getLongArray("Blocks")).filter(l -> level.getBlockState(BlockPos.of(l)).is(Blocks.WHITE_CONCRETE)).count();
        h.assertTrue(concrete > 30, "the dome is up: " + concrete);
        boolean door = java.util.Arrays.stream(capsule.getTag().getLongArray("Blocks")).anyMatch(l -> level.getBlockState(BlockPos.of(l)).is(Blocks.OAK_DOOR));
        h.assertTrue(door, "it has a door");

        BlockPos at = BlockPos.of(capsule.getTag().getLong("At"));
        BlockPos mine = at.offset(1, 1, 0);                                       // something of the player's, inside
        level.setBlockAndUpdate(mine, Blocks.GOLD_BLOCK.defaultBlockState());
        p.getCooldowns().removeCooldown(capsule.getItem());
        capsule.getItem().use(level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(!capsule.getTag().getBoolean("Deployed"), "packed away");
        boolean gone = java.util.stream.IntStream.rangeClosed(1, 5).allMatch(dy -> !level.getBlockState(at.above(dy).relative(net.minecraft.core.Direction.SOUTH, 3)).is(Blocks.WHITE_CONCRETE));
        h.assertTrue(gone && level.getBlockState(at).isAir(), "the house is gone");
        h.assertTrue(level.getBlockState(mine).is(Blocks.GOLD_BLOCK), "what was the player's stays");

        level.setBlockAndUpdate(at.above(2), Blocks.STONE.defaultBlockState());   // no room now
        p.getCooldowns().removeCooldown(capsule.getItem());
        capsule.getItem().use(level, p, InteractionHand.MAIN_HAND);
        h.assertTrue(!capsule.getTag().getBoolean("Deployed"), "no room: it does not go up");

        for (int x = -12; x <= 12; x++) for (int y = 0; y <= 7; y++) for (int z = -4; z <= 22; z++) level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
        TestPlayers.remove(h, p);
        h.succeed();
    }
}
