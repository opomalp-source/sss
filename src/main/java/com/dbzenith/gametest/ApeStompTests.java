package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.ApeChange;
import com.dbzenith.transform.ApeStomp;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApeStompTests {
    @GameTest(template = "empty")
    public static void stompWaveStaysNearTheGround(GameTestHelper h) {
        Vec3 foot = new Vec3(0, 10, 0);
        h.assertTrue(ApeStomp.inRange(foot, new Vec3(3, 10, 4), 5), "the edge is included");
        h.assertTrue(!ApeStomp.inRange(foot, new Vec3(4, 10, 4), 5), "square corners outside the radius are excluded");
        h.assertTrue(!ApeStomp.inRange(foot, new Vec3(0, 13, 0), 5), "fliers overhead are not struck");
        h.assertTrue(!ApeStomp.inRange(foot, new Vec3(0, 6, 0), 5), "fighters below a floor are not struck");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "ape_stomp", timeoutTicks = 300)
    public static void groundedStompHasWindupOneImpactAndRecovery(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        var d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        d.recomputeIfStale();
        d.refill();
        BlockPos anchor = h.absolutePos(new BlockPos(400, 1, 400));
        h.getLevel().getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        BlockPos base = new BlockPos(anchor.getX(), h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, anchor.getX(), anchor.getZ()) + 8, anchor.getZ());
        // Embedded test players have no real client holding chunk tickets. Keep these entity instances loaded.
        for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++)
            for (int z = (base.getZ() - 8) >> 4; z <= (base.getZ() + 8) >> 4; z++) h.getLevel().setChunkForced(x, z, true);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            h.getLevel().setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 22; y++) h.getLevel().setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        p.setYRot(0);
        p.setOnGround(true);
        FormHandler.enter(p, d, Forms.GOLDEN_APE);
        h.assertTrue(!ApeStomp.press(p), "no attack during the transformation");
        h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "growth plants the feet");
        Vec3 foot = ApeStomp.foot(p, -1);
        Cow near = cow(h, foot.add(0, 0, 2));
        Cow high = cow(h, foot.add(0, 5, 0));
        Cow wall = cow(h, foot.add(3.5, 0, 0));
        BlockPos screen = BlockPos.containing(foot.add(1.8, 0, 0));
        for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++)
            h.getLevel().setBlockAndUpdate(screen.offset(0, y, z), Blocks.STONE.defaultBlockState());
        float before = near.getHealth();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(!near.hurt(p.damageSources().playerAttack(p), 20), "ordinary weapon damage is refused");
        Delays.after(h, ApeChange.END + 3, () -> {
            p.setOnGround(false);
            h.assertTrue(!ApeStomp.press(p), "an airborne ape cannot start a stomp");
            p.setOnGround(true);
            h.assertTrue(ApeStomp.press(p), "grounded primary attack starts the stomp even with a weapon held");
            h.assertTrue(!ApeStomp.press(p), "spam cannot start a second stomp");
        });
        Delays.after(h, ApeChange.END + 3 + ApeStomp.CONTACT - 2, () ->
                h.assertTrue(near.getHealth() == before, "the wind-up deals no damage"));
        Delays.after(h, ApeChange.END + 3 + ApeStomp.CONTACT + 3, () -> {
            h.assertTrue(near.getHealth() < before, "the foot contact damages nearby grounded foes: id=" + near.getId() + " health=" + near.getHealth() + " pos=" + near.position() + " player=" + p.position());
            h.assertTrue(high.getHealth() == high.getMaxHealth(), "no damage overhead");
            h.assertTrue(wall.getHealth() == wall.getMaxHealth(), "no damage through a wall");
            h.assertTrue(!ApeStomp.press(p), "recovery cannot be cancelled with another press");
        });
        Delays.after(h, ApeChange.END + 3 + ApeStomp.DURATION + 3, () -> {
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "movement returns after recovery");
            FormHandler.revertToBase(p, d);
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "reversion clears the planted modifier");
            near.discard(); high.discard(); wall.discard();
            TestPlayers.remove(h, p);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
                for (int y = -1; y < 4; y++) h.getLevel().setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            for (int x = (base.getX() - 8) >> 4; x <= (base.getX() + 8) >> 4; x++)
                for (int z = (base.getZ() - 8) >> 4; z <= (base.getZ() + 8) >> 4; z++) h.getLevel().setChunkForced(x, z, false);
            h.succeed();
        });
    }

    private static Cow cow(GameTestHelper h, Vec3 at) {
        Cow cow = new Cow(EntityType.COW, h.getLevel());
        cow.moveTo(at.x, at.y, at.z, 0, 0);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.noPhysics = true;
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
        cow.setHealth(10000);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }
}
