package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.meter.Meters;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import com.dbzenith.transform.UltraInstinct;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** CX-20 phase 5: the PvP meters' values and the steps (studs) on each bar. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MeterTests {
    private static final String EMPTY = "empty";

    private MeterTests() {}

    @GameTest(template = EMPTY)
    public static void theMetersHoldTheirValues(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (int i = 0; i < 5; i++) d.tickSyncTimer(1);                         // drain what is pending
        d.setFormMeter(150);
        d.setTechMeter(-5);
        helper.assertTrue(d.getFormMeter() == 100 && d.getTechMeter() == 0, "clamped to 0..100");
        helper.assertTrue(d.tickSyncTimer(1) == PlayerData.SYNC_POOLS, "the meters go in the small packet");
        d.setFormMeter(37.5);
        d.setTechMeter(62);
        PlayerData copy = new PlayerData();
        copy.load(d.save());
        helper.assertTrue(copy.getFormMeter() == 37.5 && copy.getTechMeter() == 62, "saved and loaded");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theTechniqueBarHasKaiokenAndUltraInstinct(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setSkillLevel(Kaioken.ID, 0);
        d.setFlag(UltraInstinct.SIGN_FLAG, false);
        d.setFlag(UltraInstinct.MASTERED_FLAG, false);
        helper.assertTrue(Meters.techSteps(d).isEmpty(), "nothing learned: no studs");
        d.setSkillLevel(Kaioken.ID, 2);                                          // up to x4
        List<Meters.Step> s = Meters.techSteps(d);
        helper.assertTrue(s.size() == 2 && s.get(0).at() == 25 && s.get(1).at() == 40 && s.get(0).color() == Meters.KAIOKEN,
                "Kaioken x2 at 25 and x4 at 40, in red: " + s);
        d.setSkillLevel(Kaioken.ID, 4);
        d.setFlag(UltraInstinct.SIGN_FLAG, true);
        s = Meters.techSteps(d);
        helper.assertTrue(s.size() == 5 && s.get(4).at() == 100 && s.get(4).color() == Meters.UI_SIGN, "the Sign at the top, grey: " + s);
        helper.assertTrue(Meters.techColor(s, 50) == Meters.KAIOKEN && Meters.techColor(s, 100) == Meters.UI_SIGN, "the bar takes the reached step's colour");
        helper.assertTrue(Meters.techColor(s, 10) == Meters.KAIOKEN, "below the first: the first one's colour");
        d.setFlag(UltraInstinct.MASTERED_FLAG, true);
        s = Meters.techSteps(d);
        helper.assertTrue(s.get(s.size() - 1).color() == Meters.UI_MASTERED && s.size() == 5, "Mastered Ultra Instinct, white, in its place");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theFormBarHasTheUnlockedForms(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        for (Attribute a : Attribute.values()) d.setAttribute(a, 5);
        helper.assertTrue(Meters.formSteps(d).isEmpty(), "too weak for any form: no studs");
        for (Attribute a : Attribute.values()) d.setAttribute(a, 3000);
        List<Meters.Step> s = Meters.formSteps(d);
        helper.assertTrue(!s.isEmpty() && s.get(0).id().equals(Forms.SUPER_SAIYAN.id()), "Super Saiyan first: " + s);
        for (int i = 0; i < s.size(); i++) {
            helper.assertTrue(Math.abs(s.get(i).at() - 100.0 * (i + 1) / s.size()) < 1e-9, "spread evenly: " + s);
            helper.assertTrue(!Meters.isTechnique(Forms.byId(s.get(i).id())), "Ultra Instinct isn't a form here");
        }
        helper.assertTrue(Meters.reached(s, 0) == null && Meters.reached(s, 100) == s.get(s.size() - 1), "reached steps");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
