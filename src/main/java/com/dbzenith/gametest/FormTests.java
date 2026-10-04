package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FalseMoonEntity;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.GreatApe;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Overdrive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 2: forms, mastery, overdrive. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FormTests {
    private static final String EMPTY = "empty";

    private FormTests() {}

    /** A Saiyan at roughly the given character level (all in STR). */
    private static ServerPlayer saiyan(GameTestHelper helper, int level) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setRace(Race.SAIYAN);
        d.setAttribute(Attribute.STRENGTH, d.getAttribute(Attribute.STRENGTH) + level);
        d.recomputeIfStale();
        d.refill();
        return p;
    }

    @GameTest(template = EMPTY)
    public static void requirementsGateForms(GameTestHelper helper) {
        ServerPlayer weak = saiyan(helper, 0);
        PlayerData w = ModCapabilities.getOrThrow(weak);
        helper.assertTrue(FormHandler.problem(w, Forms.SUPER_SAIYAN) != null, "level 0 must not unlock Super Saiyan");
        helper.assertTrue(!FormHandler.transformUp(weak) && !w.isTransformed(), "transform key must fail when locked");

        ServerPlayer strong = saiyan(helper, 600);
        PlayerData s = ModCapabilities.getOrThrow(strong);
        helper.assertTrue(FormHandler.problem(s, Forms.SUPER_SAIYAN) == null, "level 600 unlocks Super Saiyan");
        helper.assertTrue(FormHandler.problem(s, Forms.SUPER_SAIYAN_2) != null, "SSJ2 needs SSJ mastery");
        s.setMastery("super_saiyan", 60);
        helper.assertTrue(FormHandler.problem(s, Forms.SUPER_SAIYAN_2) == null, "SSJ2 unlocked with mastery");
        helper.assertTrue(FormHandler.problem(s, Forms.SUPER_SAIYAN_GOD) != null, "God form needs the god_ki flag");
        helper.assertTrue(FormHandler.problem(s, Forms.GREAT_APE) != null, "Great Ape cannot be entered at will");

        s.setRace(Race.HUMAN);
        helper.assertTrue(FormHandler.problem(s, Forms.SUPER_SAIYAN) != null, "humans cannot go Super Saiyan");
        TestPlayers.remove(helper, weak);
        TestPlayers.remove(helper, strong);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void formMultipliesCombatStatsNotPools(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        double melee = d.getDerived().meleeDamage();
        double maxKi = d.getDerived().maxKi();
        helper.assertTrue(FormHandler.transformUp(p), "should transform");
        helper.assertTrue("super_saiyan".equals(d.getFormId()), "should be Super Saiyan, was " + d.getFormId());
        d.recomputeIfStale();
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() / melee - 2.0) < 1e-6, "SSJ doubles melee: " + d.getDerived().meleeDamage() / melee);
        helper.assertTrue(d.getDerived().maxKi() == maxKi, "pools are not multiplied");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void drainEndsFormAtZeroKi(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        FormHandler.transformUp(p);
        double ki = d.getKi();
        KiTicker.tick(p, d);
        helper.assertTrue(d.getKi() < ki, "forms drain ki");
        d.setKi(0.0001);
        KiTicker.tick(p, d);
        helper.assertTrue(!d.isTransformed(), "out of ki must drop back to base");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void masteryGrowsAndReducesDrain(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        FormHandler.transformUp(p);
        for (int t = 20; t <= 200; t += 20) FormHandler.tick(p, d, t);
        helper.assertTrue(d.getMastery("super_saiyan") > 0, "time in form should build mastery");
        double raw = Forms.SUPER_SAIYAN.kiDrainPercent();
        helper.assertTrue(FormMath.masteredDrain(raw, 100) < FormMath.masteredDrain(raw, 0), "mastery should reduce drain");
        double before = d.getDerived().meleeDamage();
        d.setMastery("super_saiyan", 100);
        d.recomputeIfStale();
        helper.assertTrue(d.getDerived().meleeDamage() > before, "mastery should raise the multiplier");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void targetFormSkipsStraightThere(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setMastery("super_saiyan", 60);
        d.setTargetForm("super_saiyan_2");
        helper.assertTrue(FormHandler.transformUp(p), "should transform");
        helper.assertTrue("super_saiyan_2".equals(d.getFormId()), "target form should be entered directly, got " + d.getFormId());
        FormHandler.revertOne(p);
        helper.assertTrue("super_saiyan".equals(d.getFormId()), "revert drops one tier, got " + d.getFormId());
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overdriveMultipliesDrainsAndBacklashes(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 0);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!Overdrive.raise(p), "overdrive is locked at low level");
        d.setFlag("overdrive", true);
        double melee = d.getDerived().meleeDamage();
        helper.assertTrue(Overdrive.raise(p), "overdrive unlocked by flag");
        helper.assertTrue(!Overdrive.raise(p), "level 2 needs mastery");
        d.recomputeIfStale();
        double ratio = d.getDerived().meleeDamage() / melee;
        helper.assertTrue(Math.abs(ratio - 2.0) < 1e-6, "overdrive x2 doubles melee, got " + ratio);
        double body = d.getBody();
        for (int t = 1; t <= 20; t++) Overdrive.tick(p, d, t);
        helper.assertTrue(d.getBody() < body, "overdrive drains body");
        double beforeStop = d.getBody();
        Overdrive.stop(p, d, true);
        helper.assertTrue(d.getBody() < beforeStop && d.getOverdriveLevel() == 0, "ending overdrive costs body");

        d.setMastery("super_saiyan", 0);
        d.setAttribute(Attribute.STRENGTH, 700);
        d.recomputeIfStale();
        FormHandler.transformUp(p);
        helper.assertTrue(!Overdrive.raise(p), "overdrive does not stack with Super Saiyan");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void falseMoonTriggersGreatApe(GameTestHelper helper) {
        ServerPlayer saiyan = saiyan(helper, 0);
        PlayerData d = ModCapabilities.getOrThrow(saiyan);
        ServerPlayer human = TestPlayers.create(helper);
        PlayerData h = ModCapabilities.getOrThrow(human);
        float normalHeight = saiyan.getBbHeight();
        GreatApe.spawnFalseMoon(helper.getLevel(), saiyan.getX(), saiyan.getY() + 3, saiyan.getZ());
        helper.runAfterDelay(FalseMoonEntity.RISE_TICKS + 5, () -> {
            helper.assertTrue(GreatApe.seesMoon(saiyan), "the risen false moon should be visible");
            GreatApe.tick(saiyan, d);
            GreatApe.tick(human, h);
            helper.assertTrue("great_ape".equals(d.getFormId()), "Saiyan with a tail should become a Great Ape, was " + d.getFormId());
            helper.assertTrue(!h.isTransformed(), "humans are unaffected");
            helper.assertTrue(saiyan.getBbHeight() > normalHeight * 2.5f, "Great Ape should be giant: " + saiyan.getBbHeight());
            d.setTail(false);
            GreatApe.tick(saiyan, d);
            helper.assertTrue(!d.isTransformed() && saiyan.getBbHeight() == normalHeight, "losing the tail ends the transformation");
            TestPlayers.remove(helper, saiyan);
            TestPlayers.remove(helper, human);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void greatApeBlocksTechniquesAndDeathReverts(GameTestHelper helper) {
        ServerPlayer p = saiyan(helper, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        FormHandler.enter(p, d, Forms.GREAT_APE);
        helper.assertTrue(TechniqueHandler.use(p, Techniques.KI_BLAST) == TechniqueHandler.Result.INVALID, "Great Ape cannot use techniques");
        ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(p, false);
        helper.assertTrue(!ModCapabilities.getOrThrow(respawned).isTransformed(), "death reverts to base");
        TestPlayers.remove(helper, respawned);
        helper.succeed();
    }
}
