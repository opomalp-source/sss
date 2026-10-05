package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.RacialSkill;
import com.dbzenith.race.RacialSkillEffects;
import com.dbzenith.race.RacialSkills;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import com.dbzenith.transform.Kaioken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-5: universal skills, learned with TP by any race. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UniversalSkillTests {
    private static final String EMPTY = "empty";

    private UniversalSkillTests() {}

    private static ServerPlayer fighter(GameTestHelper helper, Race race, int levels) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(d, race);
        d.setAttribute(Attribute.STRENGTH, d.getAttribute(Attribute.STRENGTH) + levels);
        d.recomputeIfStale();
        d.refill();
        return p;
    }

    @GameTest(template = EMPTY)
    public static void skillsAreLearnedWithTrainingPoints(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.HUMAN, 500);
        PlayerData d = ModCapabilities.getOrThrow(p);
        RacialSkill kk = RacialSkills.byId("kaioken");
        d.setTrainingPoints(0);
        helper.assertTrue(RacialSkills.learnProblem(d, kk) != null, "no TP, no Kaioken");
        d.setTrainingPoints(100_000);
        helper.assertTrue(RacialSkills.learn(d, kk) && d.getSkillLevel("kaioken") == 1, "learned");
        helper.assertTrue(d.getTrainingPoints() == 100_000 - kk.tpCost(1), "and paid for");
        helper.assertTrue(RacialSkills.learn(d, kk) && d.getSkillLevel("kaioken") == 2, "level 2 at character level 400");
        helper.assertTrue(!RacialSkills.learn(d, kk), "level 3 waits for character level 800");
        helper.assertTrue(RacialSkills.learnProblem(d, RacialSkills.byId("persistence")) != null, "racial skills cannot be bought");
        helper.assertTrue(RacialSkills.universal().size() == 9, "nine universal skills");
        helper.assertTrue(RacialSkills.forCharacter(Race.HUMAN, Variant.HUMAN).stream().noneMatch(RacialSkill::learned), "kept apart from racial kits");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kaiokenMultipliesAndBurns(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.SAIYAN, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        double melee = d.getDerived().meleeDamage();
        helper.assertTrue(!Kaioken.raise(p, d), "not without learning it");
        d.setSkillLevel("kaioken", 1);
        helper.assertTrue(Kaioken.raise(p, d) && Kaioken.raise(p, d) && !Kaioken.raise(p, d), "two stages at level 1, no more");
        d.recomputeIfStale();
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() / melee - 1.2) < 1e-6, "Kaioken x2: +20%, got x" + d.getDerived().meleeDamage() / melee);
        double body = d.getBody();
        for (int t = 1; t <= 20; t++) Kaioken.tick(p, d, t);
        helper.assertTrue(d.getBody() < body, "it burns the body");
        FormHandler.enter(p, d, Forms.SUPER_SAIYAN);
        helper.assertTrue(d.getKaiokenStage() == 0, "Super Saiyan and Kaioken do not mix");
        helper.assertTrue(!Kaioken.raise(p, d), "not even after");
        FormHandler.enter(p, d, Forms.BASE);
        d.setBody(d.getDerived().maxBody() * 0.11);
        Kaioken.raise(p, d);
        helper.assertTrue(d.getKaiokenStage() == 0, "too hurt to start it");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void limitBreakBarrierAndGambit(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.SAIYAN, 600);
        PlayerData d = ModCapabilities.getOrThrow(p);
        ServerPlayer h = fighter(helper, Race.HALF_SAIYAN, 600);
        PlayerData hd = ModCapabilities.getOrThrow(h);
        double m1 = d.getDerived().meleeDamage(), m2 = hd.getDerived().meleeDamage();
        d.setSkillLevel("limit_break", 1);
        hd.setSkillLevel("limit_break", 1);
        RacialSkill lb = RacialSkills.byId("limit_break");
        helper.assertTrue(RacialSkillEffects.use(p, d, lb) && RacialSkillEffects.use(h, hd, lb), "Limit Break");
        d.recomputeIfStale();
        hd.recomputeIfStale();
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() / m1 - 1.25) < 1e-6, "past the limit: x1.25");
        helper.assertTrue(Math.abs(hd.getDerived().meleeDamage() / m2 - 1.5) < 1e-6, "a Half-Saiyan goes twice as far: x1.5");

        d.setSkillLevel("ki_barrier", 1);
        double ki = RacialSkills.blowFactor(null, d, 1, false, true), blow = RacialSkills.blowFactor(null, d, 1, false, false);
        RacialSkillEffects.use(p, d, RacialSkills.byId("ki_barrier"));
        helper.assertTrue(Math.abs(RacialSkills.blowFactor(null, d, 1, false, true) / ki - 0.24) < 1e-6
                && Math.abs(RacialSkills.blowFactor(null, d, 1, false, false) / blow - 0.8) < 1e-6, "the barrier eats ki and softens blows");

        RacialSkill gambit = RacialSkills.byId("desperate_gambit");
        hd.setSkillLevel("desperate_gambit", 1);
        helper.assertTrue(!RacialSkillEffects.use(h, hd, gambit), "no gambit while healthy");
        hd.setBody(hd.getDerived().maxBody() * 0.2);
        helper.assertTrue(RacialSkillEffects.use(h, hd, gambit), "a gambit when desperate");
        LivingDeathEvent death = new LivingDeathEvent(h, h.damageSources().generic());
        RacialSkillEffects.onDeath(death);
        helper.assertTrue(death.isCanceled() && hd.getBody() > 0, "and no falling while it lasts");
        TestPlayers.remove(helper, p);
        TestPlayers.remove(helper, h);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void risingChargeAndEchoStrike(GameTestHelper helper) {
        ServerPlayer p = fighter(helper, Race.HUMAN, 300);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setCharging(true);
        for (int i = 0; i < 100; i++) d.tickCharge();
        d.setCharging(false);
        helper.assertTrue(RacialSkillEffects.risingChargeBonus(d) == 1.0, "nothing without Rising Charge");
        d.setSkillLevel("rising_charge", 1);
        d.setCharging(true);
        for (int i = 0; i < 100; i++) d.tickCharge();
        d.setCharging(false);
        helper.assertTrue(Math.abs(RacialSkillEffects.risingChargeBonus(d) - 1.5) < 1e-9, "five seconds charged: +50%");
        helper.assertTrue(RacialSkillEffects.risingChargeBonus(d) == 1.0, "spent on one technique");
        d.setCharging(true);
        for (int i = 0; i < 100; i++) d.tickCharge();
        d.setCharging(false);
        for (int i = 0; i < 41; i++) d.tickRisingCharge();
        helper.assertTrue(RacialSkillEffects.risingChargeBonus(d) == 1.0, "lost two seconds after letting go");

        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        float hp = z.getHealth();
        d.setSkillLevel("echo_strike", 3);
        long now = helper.getLevel().getGameTime();
        RacialSkillEffects.echoStrike(p, d, z, now);
        helper.assertTrue(z.getHealth() < hp, "Echo Strike hits back");
        helper.assertTrue(p.distanceTo(z) < 2.5, "from right behind");
        hp = z.getHealth();
        RacialSkillEffects.echoStrike(p, d, z, now + 10);
        helper.assertTrue(z.getHealth() == hp, "once every six seconds");
        z.discard();
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
