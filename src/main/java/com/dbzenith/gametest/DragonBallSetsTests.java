package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.dragonball.BallSet;
import com.dbzenith.dragonball.DragonBallData;
import com.dbzenith.dragonball.DragonBalls;
import com.dbzenith.dragonball.DragonSpiritEntity;
import com.dbzenith.dragonball.Wish;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Race;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 12d: the Black Star Dragon Balls and their curse, and the Super Dragon Balls. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DragonBallSetsTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    private DragonBallSetsTests() {}

    /** Sets out the seven of a set and summons; returns the dragon. */
    private static DragonSpiritEntity summon(GameTestHelper helper, ServerPlayer p, BallSet set) {
        for (int star = 1; star <= 7; star++) helper.setBlock(new BlockPos(star % 3, 1, star / 3), DragonBalls.block(set, star));
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 0));
        helper.assertTrue(DragonBalls.trySummon(helper.getLevel(), p, abs, set), "the seven summon their dragon: " + set);
        return helper.getLevel().getEntitiesOfClass(DragonSpiritEntity.class, new AABB(abs).inflate(8)).stream()
                .filter(d -> d.isSummoner(p) && !d.isRemoved()).findFirst().orElseThrow();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aBlackStarWishIsCursedUntilTheSevenComeBack(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        MinecraftServer server = helper.getLevel().getServer();
        d.setRace(Race.SAIYAN);
        d.setVariant(Variant.SAIYAN);
        DragonSpiritEntity dragon = summon(helper, p, BallSet.BLACK_STAR);
        helper.assertTrue(dragon.set() == BallSet.BLACK_STAR && !dragon.liftsCurse(), "the Black Star dragon");
        helper.assertTrue(!dragon.wishes().contains(Wish.POWER) && dragon.wishes().contains(Wish.REMAKE), "its own wishes: " + dragon.wishes());
        helper.assertTrue(!dragon.grant(p, Wish.POWER), "not one of its wishes");
        helper.assertTrue(dragon.grant(p, Wish.REMAKE), "remade");
        helper.assertTrue(d.getVariant() != Variant.SAIYAN && d.getVariant().race() == Race.SAIYAN, "another Saiyan: " + d.getVariant());
        DragonBallData data = DragonBallData.get(server, BallSet.BLACK_STAR);
        helper.assertTrue(data.isCursed() && !data.isDoom(), "the curse is on");

        DragonSpiritEntity again = summon(helper, p, BallSet.BLACK_STAR);
        helper.assertTrue(again.liftsCurse() && again.wishes().equals(java.util.List.of(Wish.LIFT_CURSE)), "while cursed, it only lifts the curse");
        helper.assertTrue(again.grant(p, Wish.LIFT_CURSE), "lifted");
        helper.assertTrue(!data.isCursed(), "no curse any more");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theCurseRunsOutIntoDoom(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        DragonBallData data = DragonBallData.get(server, BallSet.BLACK_STAR);
        long now = server.overworld().getGameTime();
        data.setCurse(now - 1, false);
        DragonBalls.tickCurse(server, now);
        helper.assertTrue(data.isDoom() && data.isCursed(), "time ran out: doom");
        DragonBalls.liftCurse(server);
        helper.assertTrue(!data.isCursed() && !data.isDoom(), "gathering them again ends it");
        ServerPlayer android = TestPlayers.create(helper);
        PlayerData ad = ModCapabilities.getOrThrow(android);
        ad.setRace(Race.ANDROID);
        helper.assertTrue(Wish.remake(ad, server.overworld().random) == null, "an Android has no other make: the wish gives power instead");
        TestPlayers.remove(helper, android);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void theSuperDragonGrantsTrueImmortality(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setGameMode(GameType.SURVIVAL);
        DragonSpiritEntity dragon = summon(helper, p, BallSet.SUPER);
        helper.assertTrue(dragon.set() == BallSet.SUPER, "the Super dragon");
        helper.assertTrue(dragon.wishes().contains(Wish.POWER) && dragon.wishes().contains(Wish.TRUE_IMMORTALITY) && !dragon.wishes().contains(Wish.REMAKE),
                "every one of Earth's wishes and its own: " + dragon.wishes());
        helper.assertTrue(dragon.grant(p, Wish.TRUE_IMMORTALITY), "granted");
        helper.assertTrue(d.getImmortalUntil() == Wish.FOREVER, "forever");
        Wish.IMMORTALITY.grant(p);
        helper.assertTrue(d.getImmortalUntil() == Wish.FOREVER, "the Eternal Dragon's lesser wish does not cut it short");
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
            p.hurt(p.damageSources().generic(), 100_000f);
            helper.assertTrue(p.isAlive() && d.getBody() >= 1, "truly immortal");
            Wish.MORTALITY.grant(p);
            helper.assertTrue(d.getImmortalUntil() < p.level().getGameTime(), "mortal again");
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }
}
