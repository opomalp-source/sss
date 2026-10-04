package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.dragonball.DragonBallData;
import com.dbzenith.dragonball.DragonBalls;
import com.dbzenith.dragonball.DragonSpiritEntity;
import com.dbzenith.dragonball.Wish;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 2: Dragon Ball tracking, summoning and wishes. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DragonBallTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    private DragonBallTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void scatterPlacesATrackedBall(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        BlockPos pos = DragonBalls.scatter(server, 4);
        helper.assertTrue(pos != null, "scatter should find a spot");
        helper.assertTrue(server.overworld().getBlockState(pos).is(DragonBalls.block(4)), "the four-star ball is in the world");
        DragonBallData.Entry e = DragonBallData.get(server).entry(4);
        helper.assertTrue(e.state == DragonBallData.State.PLACED && e.pos.equals(pos), "and tracked at its position");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pickingUpMarksHeld(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, DragonBalls.block(2));
        var data = DragonBallData.get(helper.getLevel().getServer());
        helper.assertTrue(data.entry(2).state == DragonBallData.State.PLACED, "placing records PLACED");
        helper.setBlock(rel, Blocks.AIR);
        helper.assertTrue(data.entry(2).state == DragonBallData.State.HELD, "removing records HELD");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void sevenBallsSummonTheDragonOnce(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (int star = 1; star <= 6; star++) helper.setBlock(new BlockPos(star % 3, 1, star / 3), DragonBalls.block(star));
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 0));
        helper.assertTrue(!DragonBalls.trySummon(helper.getLevel(), p, abs), "six balls are not enough");
        helper.setBlock(new BlockPos(2, 1, 2), DragonBalls.block(7));
        helper.assertTrue(DragonBalls.trySummon(helper.getLevel(), p, abs), "seven balls summon the dragon");
        for (int star = 1; star <= 7; star++) {
            helper.assertTrue(DragonBallData.get(helper.getLevel().getServer()).entry(star).state == DragonBallData.State.INERT, "balls turn to stone");
        }
        DragonSpiritEntity dragon = helper.getLevel().getEntitiesOfClass(DragonSpiritEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(8)).stream()
                .filter(dr -> dr.isSummoner(p)).findFirst().orElse(null);
        helper.assertTrue(dragon != null, "the dragon appears");
        long tp = d.getTrainingPoints();
        helper.assertTrue(dragon.grant(p, Wish.POWER), "the summoner gets a wish");
        helper.assertTrue(d.getTrainingPoints() > tp, "power wish grants TP");
        helper.assertTrue(!dragon.grant(p, Wish.RICHES), "only one wish");
        helper.assertTrue(dragon.isRemoved(), "the dragon departs");
        DragonBallData.get(helper.getLevel().getServer()).setInertUntil(0); // let other tests and the world scatter again
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void immortalityWishPreventsDeath(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
            Wish.IMMORTALITY.grant(p);
            p.hurt(p.damageSources().generic(), 100_000f);
            helper.assertTrue(p.isAlive() && d.getBody() >= 1, "immortal players survive a lethal hit");
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }
}
