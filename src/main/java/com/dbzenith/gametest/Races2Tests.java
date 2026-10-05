package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.Milestones;
import com.dbzenith.race.Races;
import com.dbzenith.race.Variant;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** CX-2 / CX-3: races, variants (clans, destinies, paths) and the new form lines. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Races2Tests {
    private static final String EMPTY = "empty";

    private Races2Tests() {}

    @GameTest(template = EMPTY)
    public static void everyRaceAndVariantIsComplete(GameTestHelper helper) {
        for (Race r : Race.values()) {
            helper.assertTrue(Races.of(r) != null, r + " has traits");
            helper.assertTrue(Variant.defaultFor(r).race() == r, r + " has a default variant");
            for (String id : Races.of(r).racialTechniques()) helper.assertTrue(Techniques.byId(id) != null, r + "'s racial " + id + " exists");
        }
        for (Variant v : Variant.values()) {
            if (v.race() == Race.ANDROID || v.race() == Race.CYBORG) continue;
            List<Form> line = Forms.forCharacter(v.race(), v);
            helper.assertTrue(!line.isEmpty(), v + " has forms");
            boolean rooted = line.stream().anyMatch(f -> f.parent().equals("base"));
            helper.assertTrue(rooted, v + " has a first form off its base");
            for (Form f : line) {
                helper.assertTrue(f.parent().equals("base") || Forms.exists(f.parent()), f.id() + "'s parent " + f.parent() + " exists");
                Form parent = Forms.byId(f.parent());
                helper.assertTrue(parent.isBase() || parent.allows(v) && parent.races().contains(v.race()),
                        f.id() + " is reachable for " + v + " (its parent is " + parent.id() + ")");
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyVariantStaysInBalance(GameTestHelper helper) {
        for (int level : new int[]{500, 1000, 1500, 2000}) {
            List<Double> best = new ArrayList<>();
            List<Variant> vs = new ArrayList<>();
            for (Variant v : Variant.values()) {
                best.add(BalanceReport.avgMultiplier(BalanceReport.bestForm(v.race(), v, level)));
                vs.add(v);
            }
            double median = BalanceReport2.median(best);
            for (int i = 0; i < vs.size(); i++) {
                double ratio = best.get(i) / median;
                helper.assertTrue(ratio >= 0.65 && ratio <= 1.45, vs.get(i) + " at level " + level + " is x" + ratio + " of the median variant");
                if (level == 2000) helper.assertTrue(best.get(i) >= 4.5, vs.get(i) + " tops out at x" + best.get(i) + " by level 2000");
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void destiniesAreRolledThenAwakened(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        CharacterCreation.applyRace(d, Race.SAIYAN);
        Random lucky = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;                                     // always rolls under the chance
            }
        };
        CharacterCreation.chooseVariant(d, "primal", lucky);
        helper.assertTrue(d.getVariant() == Variant.PRIMAL && d.getDestiny().equals("legendary_primal"), "Primal chosen, the legend hidden inside");
        CharacterCreation.chooseVariant(d, "legendary", lucky);
        helper.assertTrue(d.getVariant() == Variant.SAIYAN, "rare variants cannot simply be picked");
        Random unlucky = new Random() {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        CharacterCreation.chooseVariant(d, "primal", unlucky);
        helper.assertTrue(d.getDestiny().isEmpty(), "most characters carry no destiny");

        ServerPlayer p = TestPlayers.create(helper);
        PlayerData pd = ModCapabilities.getOrThrow(p);
        CharacterCreation.applyRace(pd, Race.FROST_DEMON);
        pd.setCharacterCreated(true);
        pd.setDestiny("mutant");
        Milestones.tick(p, pd);
        helper.assertTrue(pd.getVariant() == Variant.FROST_DEMON, "the destiny sleeps before the milestone");
        for (Attribute a : Attribute.values()) pd.setAttribute(a, 200);
        pd.recomputeIfStale();
        Milestones.tick(p, pd);
        helper.assertTrue(pd.getVariant() == Variant.MUTANT && pd.getDestiny().isEmpty(), "and awakens at it");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pathsAreChosenOnceAtTheMilestone(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        CharacterCreation.applyRace(d, Race.HALF_SAIYAN);
        d.setCharacterCreated(true);
        helper.assertTrue(!Milestones.choosePath(d, Variant.FUTURE_LINEAGE), "no path before the milestone");
        for (Attribute a : Attribute.values()) d.setAttribute(a, 200);
        d.recomputeIfStale();
        helper.assertTrue(Milestones.pathPending(d), "the milestone asks for a path");
        helper.assertTrue(!Milestones.choosePath(d, Variant.PEAK_HUMAN), "only your own race's paths");
        helper.assertTrue(Milestones.choosePath(d, Variant.FUTURE_LINEAGE) && d.getVariant() == Variant.FUTURE_LINEAGE, "a path is chosen");
        helper.assertTrue(!Milestones.choosePath(d, Variant.NEW_GENERATION), "and kept");
        helper.assertTrue(Forms.forCharacter(Race.HALF_SAIYAN, Variant.FUTURE_LINEAGE).contains(Forms.SUPER_SAIYAN_RAGE)
                && !Forms.forCharacter(Race.HALF_SAIYAN, Variant.FUTURE_LINEAGE).contains(Forms.SUPER_SAIYAN_BLUE), "Future Lineage: Rage instead of Blue");
        CharacterCreation.applyRace(d, Race.NAMEKIAN);
        helper.assertTrue(d.getVariant() == Variant.WARRIOR_CLAN, "changing race resets the variant");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rangedFormsGrowAndLegendsRise(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        Form ssj4 = Forms.SUPER_SAIYAN_4;
        double fresh = FormMath.formMultiplier(d, ssj4, Attribute.STRENGTH);
        d.setMastery(ssj4.id(), 100);
        double mastered = FormMath.formMultiplier(d, ssj4, Attribute.STRENGTH);
        helper.assertTrue(Math.abs(fresh - ssj4.multiplier(Attribute.STRENGTH)) < 1e-6 && Math.abs(mastered - ssj4.growTo()) < 1e-6,
                "Super Saiyan 4 grows from x" + fresh + " to x" + mastered + " with mastery");
        Form wrath = Forms.WRATHFUL;
        double calm = FormMath.formMultiplier(d, wrath, Attribute.STRENGTH);
        d.setCombatTicks(3600);
        double raging = FormMath.formMultiplier(d, wrath, Attribute.STRENGTH);
        helper.assertTrue(Math.abs((raging - 1) / (calm - 1) - 1.2) < 1e-6, "three minutes of fighting add 20%: " + calm + " -> " + raging);
        helper.succeed();
    }
}
