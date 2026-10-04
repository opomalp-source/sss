package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.CombatEvents;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.TechniqueMastery;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Prestige;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 5 slice 3: technique mastery, prestige, god ki. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressionTests {
    private static final String EMPTY = "empty";

    private ProgressionTests() {}

    @GameTest(template = EMPTY)
    public static void techniqueMasteryGrowsAndPays(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.CREATIVE);
        p.teleportTo(p.getX(), p.getY() + 40, p.getZ());
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (int i = 0; i < 4; i++) {
            d.setCooldown(Techniques.KI_BLAST.id(), 0);
            TechniqueHandler.use(p, Techniques.KI_BLAST, true);
        }
        helper.assertTrue(TechniqueMastery.get(d, Techniques.KI_BLAST) > 1.5, "mastery grows with use: " + TechniqueMastery.get(d, Techniques.KI_BLAST));
        d.setMastery(TechniqueMastery.key(Techniques.KI_BLAST), 100);
        helper.assertTrue(Math.abs(TechniqueMastery.costMultiplier(d, Techniques.KI_BLAST) - 0.7) < 1e-9, "mastered: 30% cheaper");
        helper.assertTrue(Math.abs(TechniqueMastery.damageMultiplier(d, Techniques.KI_BLAST) - 1.25) < 1e-9, "mastered: 25% stronger");
        helper.assertTrue(TechniqueMastery.cooldownTicks(d, Techniques.KI_BLAST) < Techniques.KI_BLAST.cooldownTicks(), "mastered: shorter cooldown");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void prestigeStartsOverStronger(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!Prestige.prestige(p), "too low to prestige");
        int str = d.getAttribute(Attribute.STRENGTH);
        d.recomputeIfStale();
        d.setAttribute(Attribute.STRENGTH, 2500);
        d.setTrainingPoints(777);
        helper.assertTrue(Prestige.eligible(d), "level 2000+ can prestige");
        helper.assertTrue(Prestige.prestige(p), "prestiged");
        helper.assertTrue(d.getPrestige() == 1 && d.getTrainingPoints() == 0, "TP gone, prestige 1");
        int expected = str + com.dbzenith.race.Races.of(d.getRace()).startBonus(Attribute.STRENGTH)
                + com.dbzenith.race.CharacterCreation.bodyBonus(d.getBodyType(), Attribute.STRENGTH);
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == expected, "STR back to the start: " + d.getAttribute(Attribute.STRENGTH));
        double tpGain = StatCalculator.scaleTpGain(d, 100);
        d.setPrestige(0); // the same character without the prestige, for comparison
        d.recomputeIfStale();
        double melee = d.getDerived().meleeDamage();
        double tpGainBefore = StatCalculator.scaleTpGain(d, 100);
        d.setPrestige(1);
        d.recomputeIfStale();
        helper.assertTrue(Math.abs(tpGain - tpGainBefore * 1.25) < 1e-6, "+25% TP gains");
        helper.assertTrue(Math.abs(d.getDerived().meleeDamage() - melee * 1.05) < 1e-6, "+5% power");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void godKiOutclassesOrdinaryKi(GameTestHelper helper) {
        ServerPlayer god = TestPlayers.create(helper);
        ServerPlayer mortal = TestPlayers.create(helper);
        PlayerData g = ModCapabilities.getOrThrow(god);
        PlayerData m = ModCapabilities.getOrThrow(mortal);
        g.setFlag("god_ki", true);
        helper.assertTrue(CombatEvents.godKiFactor(g, m) == 1.25, "god ki hits ordinary ki harder");
        helper.assertTrue(CombatEvents.godKiFactor(m, g) == 0.75, "and shrugs off its hits");
        helper.assertTrue(CombatEvents.godKiFactor(g, null) == 1.25, "creatures too");
        helper.assertTrue(CombatEvents.godKiFactor(m, null) == 1.0, "ordinary vs ordinary is even");
        helper.assertTrue(PublicStatePacket.of(god.getId(), g).battlePower() < 0, "scouters cannot read god ki");
        helper.assertTrue(PublicStatePacket.of(mortal.getId(), m).battlePower() > 0, "but can read ordinary ki");
        TestPlayers.remove(helper, god);
        TestPlayers.remove(helper, mortal);
        helper.succeed();
    }
}
