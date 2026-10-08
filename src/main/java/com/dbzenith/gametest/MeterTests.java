package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.PvpRules;
import com.dbzenith.combat.meter.MeterLogic;
import com.dbzenith.combat.meter.MeterRules;
import com.dbzenith.combat.meter.Meters;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import com.dbzenith.transform.UltraInstinct;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** CX-20 phases 5 and 6: the PvP meters' values, the steps (studs) on each bar, filling, gating, draining and the rules. */
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

    // ------------------------------------------------------------------ phase 6: the meters at work

    private static ServerPlayer saiyan(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        for (Attribute a : Attribute.values()) d.setAttribute(a, 3000);
        d.recomputeIfStale();
        d.refill();
        d.setFormMeter(0);
        d.setTechMeter(0);
        return p;
    }

    @GameTest(template = EMPTY)
    public static void fightingFillsTheBars(GameTestHelper helper) {
        ServerPlayer a = saiyan(helper), v = saiyan(helper);
        PlayerData ad = ModCapabilities.getOrThrow(a), vd = ModCapabilities.getOrThrow(v);
        MeterRules.Rules r = MeterRules.get();
        MeterLogic.onBlow(a, v, 0.10);
        helper.assertTrue(Math.abs(ad.getFormMeter() - 10 * r.form().perPercentDealt()) < 1e-6, "dealing 10%: " + ad.getFormMeter());
        helper.assertTrue(Math.abs(vd.getFormMeter() - 10 * r.form().perPercentTaken()) < 1e-6, "taking 10%: " + vd.getFormMeter());
        MeterLogic.onPerfectGuard(v);
        helper.assertTrue(Math.abs(vd.getTechMeter() - r.tech().perfectGuard()) < 1e-6, "a perfect guard feeds the technique bar");
        double t = ad.getTechMeter();
        MeterLogic.onVanish(a);
        MeterLogic.onCounter(a);
        MeterLogic.onComboHit(a, 4);
        MeterLogic.onComboHit(a, 5);
        helper.assertTrue(Math.abs(ad.getTechMeter() - t - r.tech().vanish() - r.tech().counter() - r.tech().combo()) < 1e-6,
                "vanish, counter and the fifth hit of a combo: " + ad.getTechMeter());
        PvpRules.set(v, false, true);
        helper.assertTrue(vd.getFormMeter() == 0 && vd.getTechMeter() == 0, "leaving PvP empties the bars");
        MeterLogic.onBlow(a, v, 0.2);
        helper.assertTrue(vd.getFormMeter() == 0, "out of PvP nothing fills");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, v);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void formsInPvpNeedTheFormBar(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setTargetForm(Forms.SUPER_SAIYAN_2.id());
        d.setFlag(FormHandler.UNLOCK_FLAG + Forms.SUPER_SAIYAN_2.id(), true);
        List<Meters.Step> s = Meters.formSteps(d);
        helper.assertTrue(s.size() >= 2 && s.get(1).id().equals(Forms.SUPER_SAIYAN_2.id()), "Super Saiyan, then 2: " + s);
        helper.assertTrue(!FormHandler.transformUp(p, true) && !d.isTransformed(), "an empty bar: no form");
        d.setFormMeter(s.get(0).at());
        helper.assertTrue(FormHandler.transformUp(p, true) && d.getFormId().equals(Forms.SUPER_SAIYAN.id()),
                "the first stud reached: Super Saiyan, not the target beyond it (" + d.getFormId() + ")");
        d.setFormMeter(s.get(1).at());
        helper.assertTrue(FormHandler.transformUp(p, true) && d.getFormId().equals(Forms.SUPER_SAIYAN_2.id()), "the second: Super Saiyan 2");
        d.setFormMeter(s.get(0).at());
        MeterLogic.hold(p, d);
        helper.assertTrue(d.getFormId().equals(Forms.SUPER_SAIYAN.id()), "down to the stud below: drop a form");
        d.setFormMeter(0);
        MeterLogic.hold(p, d);
        helper.assertTrue(!d.isTransformed(), "empty: back to base");
        double before = d.getFormMeter();
        for (int i = 0; i < 20; i++) MeterLogic.tick(p, d, d.getLastCombatTick() + 1);
        helper.assertTrue(d.getFormMeter() == before, "base holds the bar");
        d.setFormMeter(50);
        FormHandler.enter(p, d, Forms.SUPER_SAIYAN);
        for (int i = 0; i < 20; i++) MeterLogic.tick(p, d, d.getLastCombatTick() + 1);
        helper.assertTrue(d.getFormMeter() < 50, "a held form drains it: " + d.getFormMeter());
        PvpRules.set(p, false, true);
        FormHandler.enter(p, d, Forms.BASE);
        helper.assertTrue(FormHandler.transformUp(p, true) && d.isTransformed(), "outside PvP: free");
        PvpRules.set(p, true, true);
        helper.assertTrue(d.getFormMeter() >= Meters.formSteps(d).get(0).at(), "into PvP transformed: the bar covers the form");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kaiokenAndUltraInstinctNeedTheTechniqueBar(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setSkillLevel(Kaioken.ID, 4);
        helper.assertTrue(!MeterLogic.techniqueUp(p, d) && d.getKaiokenStage() == 0, "an empty bar: no Kaioken");
        d.setTechMeter(25);
        helper.assertTrue(MeterLogic.techniqueUp(p, d) && MeterLogic.techniqueUp(p, d) && !MeterLogic.techniqueUp(p, d) && d.getKaiokenStage() == 2,
                "at 25: up to x2");
        d.setTechMeter(40);
        MeterLogic.techniqueUp(p, d);
        MeterLogic.techniqueUp(p, d);
        helper.assertTrue(d.getKaiokenStage() == 4, "at 40: x4");
        d.setTechMeter(24);
        MeterLogic.hold(p, d);
        helper.assertTrue(d.getKaiokenStage() == 2, "under 25 the x4 band gives way to x2: " + d.getKaiokenStage());
        d.setTechMeter(0);
        MeterLogic.hold(p, d);
        helper.assertTrue(d.getKaiokenStage() == 0, "empty: Kaioken off");

        d.setFlag(UltraInstinct.SIGN_FLAG, true);
        d.setFlag(FormHandler.UNLOCK_FLAG + Forms.ULTRA_INSTINCT_SIGN.id(), true);
        helper.assertTrue(Meters.formSteps(d).stream().noneMatch(s -> Meters.isTechnique(Forms.byId(s.id()))), "never on the form bar");
        d.setTargetForm(Forms.ULTRA_INSTINCT_SIGN.id());
        d.setFormMeter(100);
        FormHandler.transformUp(p, true);
        helper.assertTrue(!UltraInstinct.isIn(d), "J never goes to Ultra Instinct");
        FormHandler.enter(p, d, Forms.BASE);
        d.setTechMeter(MeterRules.get().ultraInstinctAt());
        helper.assertTrue(MeterLogic.techniqueUp(p, d) && UltraInstinct.isIn(d) && d.getKaiokenStage() == 0, "a full technique bar: O is Ultra Instinct");
        d.setTechMeter(0);
        MeterLogic.hold(p, d);
        helper.assertTrue(!UltraInstinct.isIn(d), "empty: it gives out");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theRulesComeFromData(GameTestHelper helper) {
        String saved = MeterRules.json();
        helper.assertTrue(MeterRules.get().kaioken().size() == 4 && MeterRules.get().form().perPercentTaken() == 1.5, "the default file is loaded");
        MeterRules.apply(MeterRules.merge(com.google.gson.JsonParser.parseString(saved).getAsJsonObject(),
                com.google.gson.JsonParser.parseString("{\"technique\":{\"ultra_instinct_at\":90,\"colors\":{\"kaioken\":\"#00FF00\"}}}").getAsJsonObject()).toString());
        boolean ok = MeterRules.get().ultraInstinctAt() == 90 && MeterRules.get().kaiokenColor() == 0x00FF00 && MeterRules.get().tech().vanish() == 6;
        MeterRules.apply(saved);
        helper.assertTrue(ok, "a later file overrides only what it gives");
        helper.assertTrue(MeterRules.get().ultraInstinctAt() == 100, "restored");
        helper.succeed();
    }
}
