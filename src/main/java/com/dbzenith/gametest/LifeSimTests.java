package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.dragonball.Wish;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.world.LifeSim;
import com.dbzenith.world.TitleEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 7: aging and titles. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeSimTests {
    private static final String EMPTY = "empty";

    private LifeSimTests() {}

    @GameTest(template = EMPTY)
    public static void agingByRaceAndElderDecline(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        double age = d.getPhysicalAge();
        for (int i = 0; i < 100; i++) LifeSim.tick(p, d);
        helper.assertTrue(d.getPhysicalAge() > age, "humans age");
        d.setRace(Race.ANDROID);
        double androidAge = d.getPhysicalAge();
        for (int i = 0; i < 100; i++) LifeSim.tick(p, d);
        helper.assertTrue(d.getPhysicalAge() == androidAge, "androids never age");

        d.setRace(Race.HUMAN);
        d.setAttribute(Attribute.STRENGTH, 500);
        d.setAttribute(Attribute.KI_POWER, 500);
        d.setPhysicalAge(30);
        d.recomputeIfStale();
        double primeMelee = d.getDerived().meleeDamage();
        double primeKi = d.getDerived().kiDamage();
        d.setPhysicalAge(80);
        d.recomputeIfStale();
        helper.assertTrue(d.getDerived().meleeDamage() < primeMelee * 0.85, "an 80-year-old hits softer");
        helper.assertTrue(d.getDerived().kiDamage() == primeKi, "but ki power does not fade");
        Wish.ETERNAL_YOUTH.grant(p);
        d.recomputeIfStale();
        helper.assertTrue(d.getPhysicalAge() == 20 && d.getDerived().meleeDamage() == primeMelee, "the youth wish restores the prime");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void titlesMustBeEarned(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!TitleEvents.select(p, "dragon_summoner"), "unearned titles are refused");
        d.setFlag("summoned_dragon", true);
        helper.assertTrue(LifeSim.earnedTitles(d).contains(LifeSim.Title.DRAGON_SUMMONER), "earned by summoning");
        helper.assertTrue(TitleEvents.select(p, "dragon_summoner"), "and can then be chosen");
        helper.assertTrue(p.getDisplayName().getString().contains("Dragon Summoner") || p.getDisplayName().getString().contains("dragon_summoner"),
                "shown before the name: " + p.getDisplayName().getString());
        helper.assertTrue(TitleEvents.select(p, ""), "titles can be cleared");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
