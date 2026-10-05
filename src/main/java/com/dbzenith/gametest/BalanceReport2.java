package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Balance pass 2: ki techniques against punching, Overdrive bursts, races against each other, gear against vanilla
 * armour. Writes {@code balance-report-2.md} next to the first report and asserts the pass-2 targets (BALANCE.md).
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BalanceReport2 {
    private static final String EMPTY = "empty";
    static final int[] GRID = {100, 200, 300, 400, 500, 700, 1000, 1300, 1500, 1800, 2000};

    private BalanceReport2() {}

    // ------------------------------------------------------------------ techniques

    record TechStats(Technique t, double cost, double damage, double cooldownSeconds) {
        double perKi() { return cost <= 0 ? 0 : damage / cost; }
        double dps() { return damage / Math.max(0.5, cooldownSeconds); }
    }

    /** Damage of one use (all volley shots, the whole beam) for a damaging technique, or null for utility ones. */
    static TechStats tech(PlayerData d, Technique t) {
        if (t.style() == Technique.Style.SELF && t.effect() != Technique.Effect.EXPLOSIVE_WAVE) return null;
        double total = t.damageMult() * (t.count() > 1 ? t.count() * 0.6 : 1);
        if (total <= 0.25 || t.effect() == Technique.Effect.KI_SEAL) return null;                  // candy, seal: effects, not damage
        double hits = t.count() > 1 ? t.count() * 0.6 : 1;                                        // volleys spread: assume 60% land
        double dmg = DamageCalculator.kiOutgoing(d, t.damageMult()) * hits;
        return new TechStats(t, DamageCalculator.kiCost(d, t.kiCost()), dmg, t.cooldownTicks() / 20.0);
    }

    static double punchDps(PlayerData d) {
        return DamageCalculator.meleeOutgoing(d, 1.0, 1) * 2.0;
    }

    static List<TechStats> damaging(PlayerData d) {
        List<TechStats> out = new ArrayList<>();
        for (Technique t : Techniques.all()) {
            TechStats s = tech(d, t);
            if (s != null) out.add(s);
        }
        return out;
    }

    static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        s.sort(Double::compare);
        return s.isEmpty() ? 0 : s.size() % 2 == 1 ? s.get(s.size() / 2) : (s.get(s.size() / 2 - 1) + s.get(s.size() / 2)) / 2;
    }

    // ------------------------------------------------------------------ overdrive

    /** Seconds Overdrive level {@code lvl} lasts from full body and stamina (mastery 0..100), and its extra output. */
    static double[] overdrive(PlayerData d, int lvl, double mastery) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double mult = c.overdriveLevels.get().get(lvl - 1);
        double over = mult - 1;
        double factor = 1.0 - 0.6 * mastery / 100.0;
        double bodySecs = (100 - c.overdriveMinBodyPercent.get()) / (c.overdriveBodyDrainPercent.get() * over * factor);
        double staSecs = 100 / (c.overdriveStaminaDrainPercent.get() * over * factor);
        double secs = Math.min(bodySecs, staSecs);
        return new double[]{mult, secs, over * secs};
    }

    // ------------------------------------------------------------------ races

    static double raceMultiplier(Race race, int level) {
        return BalanceReport.avgMultiplier(BalanceReport.bestForm(race, level));
    }

    static double raceMedian(int level) {
        List<Double> v = new ArrayList<>();
        for (Race r : Race.values()) v.add(raceMultiplier(r, level));
        return median(v);
    }

    // ------------------------------------------------------------------ gear

    static double setArmor(String... ids) {
        double a = 0;
        for (String id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item instanceof ArmorItem ai) a += ai.getDefense();
        }
        return a;
    }

    static double setToughness(String... ids) {
        double t = 0;
        for (String id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item instanceof ArmorItem ai) t += ai.getToughness();
        }
        return t;
    }

    /** Soldier hits to beat a level-{@code lvl} reference character wearing armour (vanilla armour applies to mob hits). */
    static double hitsToDie(PlayerData d, KiFighter soldier, double armor, double toughness, double reduction) {
        float vanilla = (float) soldier.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double kept = vanilla <= 0 ? 1 : CombatRules.getDamageAfterAbsorb(vanilla, (float) armor, (float) toughness) / vanilla;
        kept = 1 - (1 - kept) * DBZConfig.SERVER.fighterArmorEffect.get();                         // fighters hit through armour
        double raw = DamageCalculator.fromVanilla(vanilla) * kept;
        return d.getDerived().maxBody() / (BalanceReport.mobHitOnPlayer(d, raw) * (1 - reduction));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void writeReport2(GameTestHelper helper) {
        StringBuilder sb = new StringBuilder("# Balance report 2 (generated by the BalanceReport2 GameTest)\n\n");
        PlayerData d = BalanceReport.reference(1000);
        double punch = punchDps(d);
        sb.append(String.format(Locale.ROOT, "## Ki techniques at level 1000 (mastery 0); punching does %.0f damage a second\n\n", punch));
        sb.append("| Technique | Ki | Damage / use | Cooldown s | Damage per ki | Spam DPS vs punching | Uses per full ki |\n|---|---|---|---|---|---|---|\n");
        for (TechStats s : damaging(d)) {
            sb.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %.1f | %.2f | x%.2f | %.1f |\n", s.t().id(), s.cost(), s.damage(),
                    s.cooldownSeconds(), s.perKi(), s.dps() / punch, d.getDerived().maxKi() / Math.max(1, s.cost())));
        }
        sb.append("\n## Overdrive (from full body and stamina)\n\n| Level | x | Seconds (mastery 0) | Extra output (mastery 0) | Seconds (mastery 100) | Extra output (mastery 100) |\n|---|---|---|---|---|---|\n");
        for (int lvl = 1; lvl <= DBZConfig.SERVER.overdriveLevels.get().size(); lvl++) {
            double[] a = overdrive(d, lvl, 0), b = overdrive(d, lvl, 100);
            sb.append(String.format(Locale.ROOT, "| %d | %.0f | %.1f | %.0f mult-s | %.1f | %.0f mult-s |\n", lvl, a[0], a[1], a[2], b[1], b[2]));
        }
        sb.append("\n\"Extra output\" = (multiplier - 1) x seconds: what one full burst adds. A 100-second boss fight at x1 is 100 mult-s.\n");
        sb.append("\n## Races: best form multiplier by level, as a share of the median race (story forms count; wishes and the moon do not)\n\n| Race |");
        for (int lvl : GRID) sb.append(" L").append(lvl).append(" |");
        sb.append("\n|---|");
        for (int ignored : GRID) sb.append("---|");
        sb.append('\n');
        for (Race r : Race.values()) {
            sb.append("| ").append(r.id()).append(" |");
            for (int lvl : GRID) sb.append(String.format(Locale.ROOT, " %.2f |", raceMultiplier(r, lvl) / raceMedian(lvl)));
            sb.append('\n');
        }
        sb.append("\n## Gear at level 1000: soldier hits to beat you, and your damage\n\n| Set | Armour | Hits to beat you | Damage dealt |\n|---|---|---|---|\n");
        KiFighter soldier = BalanceReport.enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
        String[][] sets = {
                {"none"},
                {"iron", "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots"},
                {"diamond", "minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots"},
                {"netherite", "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots"},
                {"turtle gi", "dbzenith:turtle_top", "dbzenith:turtle_pants", "dbzenith:turtle_boots"},
                {"demon gi", "dbzenith:demon_top", "dbzenith:demon_pants", "dbzenith:demon_boots"},
                {"battle armor", "dbzenith:battle_armor_top", "dbzenith:battle_armor_pants", "dbzenith:battle_armor_boots"},
        };
        for (String[] set : sets) {
            String[] ids = java.util.Arrays.copyOfRange(set, 1, set.length);
            double dmg = gearDamage(set[0]);
            sb.append(String.format(Locale.ROOT, "| %s | %.0f | %.1f | x%.2f |\n", set[0], setArmor(ids), hitsToDie(d, soldier, setArmor(ids), setToughness(ids), gearReduction(set[0])), dmg));
        }
        soldier.discard();
        try {
            Path out = helper.getLevel().getServer().getServerDirectory().toPath().resolve("balance-report-2.md");
            Files.writeString(out, sb.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        helper.succeed();
    }

    /** Damage reduction of a full gi set by name (0 for vanilla armour). */
    static double gearReduction(String name) {
        for (com.dbzenith.item.GiArmorItem.Set s : com.dbzenith.item.GiArmorItem.Set.values()) {
            if (s.name().toLowerCase(Locale.ROOT).replace('_', ' ').equals(name.replace(" gi", ""))) return s.reduction();
        }
        return 0;
    }

    /** Average STR/KI multiplier a full gi set gives (the damage side of a set). */
    static double gearDamage(String name) {
        for (com.dbzenith.item.GiArmorItem.Set s : com.dbzenith.item.GiArmorItem.Set.values()) {
            if (s.name().toLowerCase(Locale.ROOT).replace('_', ' ').equals(name.replace(" gi", ""))) return (s.strMult() + s.kiMult()) / 2.0;
        }
        return 1.0;
    }

    /** Pass-2 targets (BALANCE.md). */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void pass2TargetsHold(GameTestHelper helper) {
        PlayerData d = BalanceReport.reference(1000);
        List<TechStats> techs = damaging(d);
        List<Double> perKi = new ArrayList<>();
        for (TechStats s : techs) if (s.cost() > 0) perKi.add(s.perKi());
        double mid = median(perKi);
        double punch = punchDps(d);
        for (TechStats s : techs) {
            if (s.cost() <= 0) continue;
            helper.assertTrue(s.perKi() >= mid * 0.6 && s.perKi() <= mid * 1.6,
                    s.t().id() + ": damage per ki " + s.perKi() + " is outside 0.6-1.6x the median " + mid);
            helper.assertTrue(s.dps() <= punch * 4.0, s.t().id() + ": spammed it out-damages punching x" + s.dps() / punch);
        }
        for (int lvl = 1; lvl <= DBZConfig.SERVER.overdriveLevels.get().size(); lvl++) {
            double extra = overdrive(d, lvl, 0)[2];
            helper.assertTrue(extra >= 30 && extra <= 75, "Overdrive level " + lvl + " adds " + extra + " mult-s (target 30-75)");
        }
        for (int lvl : GRID) {
            double m = raceMedian(lvl);
            for (Race r : Race.values()) {
                double share = raceMultiplier(r, lvl) / m;
                helper.assertTrue(share >= 0.75 && share <= 1.33, r.id() + " at level " + lvl + " is x" + share + " of the median race");
            }
        }
        KiFighter soldier = BalanceReport.enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
        double none = hitsToDie(d, soldier, 0, 0, 0);
        String[] netherite = {"minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots"};
        String[] battle = {"dbzenith:battle_armor_top", "dbzenith:battle_armor_pants", "dbzenith:battle_armor_boots"};
        double net = hitsToDie(d, soldier, setArmor(netherite), setToughness(netherite), 0);
        double ba = hitsToDie(d, soldier, setArmor(battle), setToughness(battle), gearReduction("battle armor"));
        soldier.discard();
        helper.assertTrue(net <= none * 2.0, "netherite should not more than double survival against fighters: x" + net / none);
        helper.assertTrue(ba >= none * 1.15, "Battle Armor should protect noticeably: x" + ba / none);
        helper.succeed();
    }
}
