package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.PvpBalance;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** CX-19 phase 10: the PvP balance curve, its caps, and moves for some forms only. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BalanceTests {
    private static final String EMPTY = "empty";

    private BalanceTests() {}

    @GameTest(template = EMPTY)
    public static void theCurveTamesAPowerGap(GameTestHelper helper) {
        helper.assertTrue(Math.abs(PvpBalance.factor(1) - 1) < 1e-9, "equals: nothing changes");
        double strong = PvpBalance.effectiveRatio(10), weak = PvpBalance.effectiveRatio(0.1);
        helper.assertTrue(strong > 3 && strong < 3.3 && weak > 0.3 && weak < 0.33, "ten times the power counts about 3.2 times: " + strong + " / " + weak);
        helper.assertTrue(strong / weak < 11, "about ten times the pace to win, not a hundred: " + strong / weak);
        helper.assertTrue(PvpBalance.effectiveRatio(1000) == 4 && PvpBalance.effectiveRatio(0.001) == 0.25, "and never past the cap");
        helper.assertTrue(PvpBalance.effectiveRatio(2) > PvpBalance.effectiveRatio(1.5), "more power still counts for more");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void noOneShotsAndNoEndlessCombos(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        PlayerData da = ModCapabilities.getOrThrow(a), db = ModCapabilities.getOrThrow(b);
        da.setAttribute(Attribute.STRENGTH, 5000);                              // far stronger
        da.recomputeIfStale();
        db.recomputeIfStale();
        double max = db.getDerived().maxBody();
        double huge = PvpBalance.apply(da, db, max * 50, 0, false);
        helper.assertTrue(huge <= max * 0.35 + 1e-6, "one blow takes at most 35%: " + huge / max);
        double fresh = PvpBalance.apply(da, db, 1, 0, false), late = PvpBalance.apply(da, db, 1, max * 0.7, false);
        helper.assertTrue(Math.abs(late - fresh * 0.25) < 1e-6, "past 60% in one combo, a quarter");
        double maxA = da.getDerived().maxBody();
        double weakJab = PvpBalance.apply(db, da, PvpBalance.plainBlow(db, da, false), 0, false) / maxA;
        double strongJab = PvpBalance.apply(da, db, PvpBalance.plainBlow(da, db, false), 0, false) / max;
        helper.assertTrue(weakJab >= 0.25 / 40 - 1e-9, "the weaker side's jab still takes a quarter of an equal's share: " + weakJab);
        helper.assertTrue(strongJab <= 4.0 / 40 + 1e-9 && strongJab > weakJab, "the stronger's at most four times: " + strongJab);
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void movesForSomeFormsOnly(GameTestHelper helper) {
        List<Move> moves = new ArrayList<>(Moves.all());
        moves.add(Move.parse("golden_rush", JsonParser.parseString(
                "{\"button\":\"heavy\",\"direction\":\"forward\",\"priority\":50,\"forms\":[\"super_saiyan\"],\"form_damage\":{\"super_saiyan\":1.5}}").getAsJsonObject()));
        Moves.Input base = new Moves.Input(Move.Button.HEAVY, Move.Dir.FORWARD, false, false, true, "base");
        Moves.Input ssj = new Moves.Input(Move.Button.HEAVY, Move.Dir.FORWARD, false, false, true, "super_saiyan");
        helper.assertTrue(!Moves.select(moves, base, "start").id.equals("golden_rush"), "not in base form");
        Move m = Moves.select(moves, ssj, "start");
        helper.assertTrue(m.id.equals("golden_rush") && m.formMultiplier("super_saiyan") == 1.5 && m.formMultiplier("base") == 1, "a Super Saiyan's own rush, harder");
        helper.succeed();
    }
}
