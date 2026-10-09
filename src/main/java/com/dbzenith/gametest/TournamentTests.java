package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.tournament.Roster;
import com.dbzenith.tournament.Tournament;
import com.dbzenith.tournament.TournamentFighter;
import com.dbzenith.tournament.TournamentGrounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-17c: entering, the bracket draw, a real match to a knockout, the loser paid and the tournament moving on. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TournamentTests {
    private static final String EMPTY = "empty";

    private TournamentTests() {}

    @GameTest(template = EMPTY)
    public static void bracketShape(GameTestHelper helper) {
        helper.assertTrue(Tournament.round(0) == 0 && Tournament.round(3) == 0 && Tournament.round(4) == 1 && Tournament.round(6) == 2, "rounds");
        helper.assertTrue(Roster.byId("mr_satan") == Roster.MR_SATAN && Roster.values().length == 7, "seven on the roster");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void aMatchEndsInAKnockout(GameTestHelper helper) {
        Tournament.cancel();
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        // the match is staged on a floor high over this test (the real arena is a landmark far off in the world, its
        // chunks not generated; high up, its no-PvP grounds leave the tests round it alone)
        net.minecraft.core.BlockPos centre = helper.absolutePos(new net.minecraft.core.BlockPos(1, 120, 1));
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -4; dz <= 4; dz++)
                p.server.overworld().setBlock(centre.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
        TournamentGrounds.useRing(p.server.overworld(), centre);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setCooldown(Tournament.COOLDOWN, 0);
        helper.assertTrue(Tournament.join(p), "entered");
        helper.assertTrue(!Tournament.join(p), "only once");
        Tournament t = Tournament.current();
        t.closeSignup();
        helper.assertTrue(t.slots().size() == 8 && t.slots().get(7).npc() == Roster.MR_SATAN, "eight drawn, the champion in the far half");
        int mine = -1;
        for (int i = 0; i < 8; i++) if (t.slots().get(i).isPlayer()) mine = i;
        helper.assertTrue(mine == 0, "the only player opens the first match: " + mine);
        long tp = d.getTrainingPoints();
        helper.succeedWhen(() -> {
            helper.assertTrue(t.phase() == Tournament.Phase.FIGHTING, "the bell");
            helper.assertTrue(t.fighter(0) == p && t.fighter(1) instanceof TournamentFighter, "the player against one of the roster");
            TournamentFighter foe = (TournamentFighter) t.fighter(1);
            p.hurt(p.damageSources().fellOutOfWorld(), 1.0e6f);                  // a blow that would kill
            helper.assertTrue(p.isAlive(), "only knocked out");
            helper.assertTrue(t.winner(0) == 1 && t.match() == 1, "the roster fighter goes through");
            helper.assertTrue(d.getTrainingPoints() > tp, "paid for the quarterfinal");
            helper.assertTrue(foe.isRemoved(), "the fighter leaves the ring");
            Tournament.cancel();
            TestPlayers.remove(helper, p);
            TournamentGrounds.useRing(p.server.overworld(), null);
        });
    }
}
