package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.dragonball.BallSet;
import com.dbzenith.dragonball.DragonBallData;
import com.dbzenith.dragonball.DragonBalls;
import com.dbzenith.dragonball.DragonSpiritEntity;
import com.dbzenith.dragonball.NamekBalls;
import com.dbzenith.dragonball.Wish;
import com.dbzenith.living.Settlement;
import com.dbzenith.network.RadarPacket;
import com.dbzenith.stats.Race;
import com.dbzenith.world.landmark.Landmark;
import com.dbzenith.world.landmark.LandmarkSites;
import com.dbzenith.world.landmark.NamekVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Namek's Dragon Balls and Porunga (v0.167.0): kept by the village elders on pedestals, handed over to those they trust,
 * seen by the radar; Porunga answers only the Namekian words, grants three wishes, and the seven turn to stone until they
 * go back to their elders.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NamekBallsTests {
    private static final String EMPTY = "empty";

    private NamekBallsTests() {}

    /** Sets out the seven and summons Porunga; returns him. */
    private static DragonSpiritEntity summon(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = setOut(helper);
        helper.assertTrue(DragonBalls.trySummon(helper.getLevel(), p, abs, BallSet.NAMEK), "the seven call Porunga");
        return helper.getLevel().getEntitiesOfClass(DragonSpiritEntity.class, new AABB(abs).inflate(8)).stream()
                .filter(d -> d.isSummoner(p) && !d.isRemoved()).findFirst().orElseThrow();
    }

    private static BlockPos setOut(GameTestHelper helper) {
        for (int star = 1; star <= 7; star++) helper.setBlock(new BlockPos(star % 3, 1, star / 3), DragonBalls.block(BallSet.NAMEK, star));
        return helper.absolutePos(new BlockPos(1, 1, 0));
    }

    /** Porunga answers only the Namekian words: an Earthling must learn them first (a Namekian knows them). */
    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "namek_porunga")
    public static void porungaHearsOnlyTheNamekianWords(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.HUMAN);
        BlockPos abs = setOut(helper);
        helper.assertTrue("message.dbzenith.namek_words_needed".equals(DragonBalls.summonProblem(helper.getLevel(), p, abs, BallSet.NAMEK)),
                "the seven are there, but no one speaks the words");
        helper.assertTrue(!DragonBalls.trySummon(helper.getLevel(), p, abs, BallSet.NAMEK), "no Porunga without them");
        helper.assertTrue(helper.getLevel().getBlockState(abs).is(DragonBalls.block(BallSet.NAMEK, 1)), "the balls stay where they are");
        NamekBalls.teachWords(p);
        helper.assertTrue(NamekBalls.knowsWords(p) && DragonBalls.summonProblem(helper.getLevel(), p, abs, BallSet.NAMEK) == null, "taught by an elder");
        d.setFlag(NamekBalls.WORDS_FLAG, false);
        d.setRace(Race.NAMEKIAN);
        helper.assertTrue(NamekBalls.knowsWords(p), "a Namekian knows them");
        DragonSpiritEntity porunga = summon(helper, p);
        porunga.discard();
        DragonBallData.get(helper.getLevel().getServer(), BallSet.NAMEK).setInertUntil(0);
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    /** Porunga grants three wishes, one at a time, then leaves; the seven turn to stone until their time is up. */
    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "namek_porunga")
    public static void porungaGrantsThreeWishes(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.NAMEKIAN);
        MinecraftServer server = helper.getLevel().getServer();
        DragonSpiritEntity porunga = summon(helper, p);
        helper.assertTrue(porunga.set() == BallSet.NAMEK && porunga.wishesLeft() == NamekBalls.WISHES, "Porunga, three wishes");
        List<Wish> wishes = porunga.wishes();
        helper.assertTrue(wishes.get(0) == Wish.REVIVE_ONE && wishes.contains(Wish.POWER) && wishes.contains(Wish.IMMORTALITY)
                && !wishes.contains(Wish.SENZU) && !wishes.contains(Wish.REVIVE), "his own wishes and a few of Earth's: " + wishes);
        DragonBallData data = DragonBallData.get(server, BallSet.NAMEK);
        for (int star = 1; star <= 7; star++) helper.assertTrue(data.entry(star).state == DragonBallData.State.INERT, "stone at once");
        helper.assertTrue(data.getInertUntil() > helper.getLevel().getGameTime(), "for a while");

        ServerPlayer fallen = TestPlayers.create(helper);                                      // one waits in the other world
        ModCapabilities.getOrThrow(fallen).setDead(true, 0, false);
        long tp = d.getTrainingPoints();
        helper.assertTrue(porunga.grant(p, Wish.POWER) && porunga.wishesLeft() == 2 && !porunga.isRemoved(), "the first wish; he waits");
        helper.assertTrue(d.getTrainingPoints() > tp, "power");
        helper.assertTrue(porunga.grant(p, Wish.REVIVE_ONE) && !ModCapabilities.getOrThrow(fallen).isDead(), "one brought back");
        helper.assertTrue(!porunga.grant(p, Wish.REVIVE_ONE) || porunga.wishesLeft() == 0, "no one else waits (or someone else did)");
        if (porunga.wishesLeft() > 0) helper.assertTrue(porunga.grant(p, Wish.RESTORATION), "the third wish");
        helper.assertTrue(porunga.wishesLeft() == 0 && porunga.isRemoved(), "three granted: he departs");
        helper.assertTrue(!porunga.grant(p, Wish.POWER), "no fourth");

        DragonBalls.maintain(server, BallSet.NAMEK, helper.getLevel().getGameTime());          // still stone: nothing moves
        for (int star = 1; star <= 7; star++) helper.assertTrue(data.entry(star).state == DragonBallData.State.INERT, "they sleep their time out");
        data.setInertUntil(0);                                                               // (they go home with the next maintenance pass)
        TestPlayers.remove(helper, fallen);
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    /** An elder keeps a ball on the pedestal by its seat, speaks of it, hands it to a trusted player; the radar sees it there. */
    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "namek_balls")              // apart from the summons, which turn all seven to stone
    public static void eldersKeepTheBalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos spot = helper.absolutePos(new BlockPos(2, 3, 2));
        int y0 = spot.getY() - 2;
        NamekVillage v = NamekVillage.at(new LandmarkSites.Site(Landmark.NAMEK_VILLAGE, 0, 0, spot.getX() - 2, spot.getZ() + 21, 0x5eed), y0);
        helper.assertTrue(v.ballSpot().equals(spot), "the pedestal's top: " + v.ballSpot());
        helper.setBlock(new BlockPos(2, 1, 2), NamekVillage.floor());                            // a village from before the pedestals
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);

        DragonBallData data = DragonBallData.get(server, BallSet.NAMEK);
        int star = 5;
        BlockPos at = NamekBalls.keep(level, v, star);
        helper.assertTrue(spot.equals(at) && level.getBlockState(spot).is(DragonBalls.block(BallSet.NAMEK, star)), "set on its place");
        helper.assertTrue(level.getBlockState(spot.below()).is(NamekVillage.pedestal().getBlock()), "on a new pedestal");
        helper.assertTrue(data.entry(star).state == DragonBallData.State.PLACED && data.entry(star).keeper.equals(v.settlementId().toString()), "kept by the village");
        helper.assertTrue(spot.equals(NamekBalls.keep(level, v, star)), "its own ball, found there, is simply taken back");

        ServerPlayer p = TestPlayers.create(helper);
        p.teleportTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 0.5);
        List<RadarPacket.Blip> blips = DragonBalls.blipsFor(p);
        helper.assertTrue(blips.stream().anyMatch(b -> b.star() == star + BallSet.NAMEK.radarOffset() && b.x() == spot.getX() && b.z() == spot.getZ()),
                "the radar sees it: " + blips);

        Settlement town = new Settlement(v.settlementId());
        town.name = v.name();
        town.dimension = level.dimension().location().toString();
        town.center = spot;
        town.radius = 40;
        helper.assertTrue("kept".equals(NamekBalls.state(server, town)) && "the Five-Star Ball".equals(NamekBalls.ballName(server, town)), "the elder speaks of it");
        helper.assertTrue(!NamekBalls.trusts(p, 0, 0) && NamekBalls.trusts(p, NamekBalls.TRUST_AFFINITY, 0), "trust is earned");
        helper.assertTrue(NamekBalls.give(p, town), "entrusted");
        helper.assertTrue(level.getBlockState(spot).isAir() && p.getInventory().contains(new ItemStack(DragonBalls.item(BallSet.NAMEK, star))), "in the player's hands");
        helper.assertTrue(data.entry(star).state == DragonBallData.State.HELD && "taken".equals(NamekBalls.state(server, town)), "carried away");
        helper.assertTrue(!NamekBalls.give(p, town), "only once");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);                                  // someone built over the pedestal
        helper.assertTrue(NamekBalls.keep(level, v, star) == null && level.getBlockState(spot).is(Blocks.STONE), "never over what stands there");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        Settlement other = new Settlement(java.util.UUID.randomUUID());
        helper.assertTrue("none".equals(NamekBalls.state(server, other)), "other elders keep none of this one");

        data.set(star, DragonBallData.State.UNSET, null, null);                                // let the world send it home again
        data.entry(star).keeper = "";
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    /** Scattering on Namek itself: a ball goes to the pedestal in one of the seven elders' houses (the chunk generated as needed). */
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "namek_balls")
    public static void theBallsGoHomeToNamek(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel namek = NamekBalls.namek(server);
        helper.assertTrue(namek != null, "Namek is there");
        List<NamekVillage> keepers = NamekBalls.keepersNow(namek);
        helper.assertTrue(keepers.size() == 7, "seven elders keep them: " + keepers.size());
        helper.assertTrue(keepers.stream().map(NamekVillage::settlementId).distinct().count() == 7 && keepers.equals(NamekBalls.keepersNow(namek)),
                "seven villages, the same every time");
        int star = 2;
        DragonBallData data = DragonBallData.get(server, BallSet.NAMEK);
        DragonBallData.Entry e = data.entry(star);
        if (e.state == DragonBallData.State.PLACED) {                                          // the world may have sent it home already
            ServerLevel l = server.getLevel(e.dimension);
            if (l != null && l.getBlockState(e.pos).is(DragonBalls.block(BallSet.NAMEK, star))) l.setBlockAndUpdate(e.pos, Blocks.AIR.defaultBlockState());
        }
        NamekVillage keeper = keepers.get(star - 1);
        BlockPos[] at = {null};
        helper.succeedWhen(() -> {                                                             // its chunk loads (generates) in the background
            if (at[0] == null) at[0] = DragonBalls.scatter(server, BallSet.NAMEK, star);
            helper.assertTrue(at[0] != null, "waiting for the keeper's chunk");
            helper.assertTrue(keeper.ballSpot().equals(at[0]), "on its keeper's pedestal: " + at[0] + " for " + keeper.name() + " at " + keeper.ballSpot());
            helper.assertTrue(namek.getBlockState(at[0]).is(DragonBalls.block(BallSet.NAMEK, star))
                    && namek.getBlockState(at[0].below()).is(NamekVillage.pedestal().getBlock()), "the ball on the pedestal");
            helper.assertTrue(e.state == DragonBallData.State.PLACED && e.dimension == Landmark.Keys.NAMEK && e.keeper.equals(keeper.settlementId().toString()),
                    "tracked there");
        });
    }
}
