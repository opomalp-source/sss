package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.world.TrainingTicker;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Balance model: reference characters (points spread evenly over the seven attributes) at several levels, measured
 * with the real formulas against the real enemies, plus TP income per activity. Writes {@code balance-report.md} into
 * the GameTest server directory and asserts the pacing targets the defaults are tuned for (see BALANCE.md).
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BalanceReport {
    private static final String EMPTY = "empty";
    static final int[] LEVELS = {0, 50, 150, 300, 500, 1000, 1500, 2000};

    private BalanceReport() {}

    /** A character at {@code level} with points spread evenly, 100% release, base form. */
    static PlayerData reference(int level) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        int start = DBZConfig.SERVER.startingAttribute.get();
        Attribute[] all = Attribute.values();
        for (int i = 0; i < all.length; i++) d.setAttribute(all[i], start + level / all.length + (i < level % all.length ? 1 : 0));
        d.setReleasePercent(100);
        d.recomputeIfStale();
        d.refill();
        return d;
    }

    /** TP to raise a reference character from level {@code from} to {@code to}, one point at a time, cheapest first. */
    static long tpBetween(int from, int to) {
        PlayerData d = reference(from);
        long total = 0;
        Attribute[] all = Attribute.values();
        for (int l = from; l < to; l++) {
            Attribute a = all[l % all.length];
            total += StatCalculator.tpCost(d, a);
            d.setAttribute(a, d.getAttribute(a) + 1);
        }
        return total;
    }

    static KiFighter enemy(GameTestHelper helper, EntityType<? extends KiFighter> type, int level) {
        KiFighter f = type.create(helper.getLevel());
        f.setFighterLevel(level);
        return f;
    }

    /** Body damage a hit of {@code vanillaAttack} (mob melee) does to {@code d} on average, after defense and evasion. */
    static double mobHitOnPlayer(PlayerData d, double rawDbz) {
        double c = DBZConfig.SERVER.minDamageFraction.get();
        double dmg = Math.max(rawDbz * c, rawDbz - d.getDerived().defense());
        return dmg * (1 - d.getDerived().evasion());
    }

    /** TP per minute of steady fighting against {@code type}: 2 punches a second plus 8 s finding the next foe. */
    static double fightingTpPerMinute(PlayerData d, KiFighter foe) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double punch = DamageCalculator.meleeOutgoing(d, 1.0, 1);
        double hits = Math.ceil(KiFighter.effectiveMaxHealth(foe) / DamageCalculator.toVanilla(punch));
        double seconds = hits / 2.0 + 8.0;
        double tpPerKill = StatCalculator.scaleTpGain(d, Math.sqrt(KiFighter.effectiveMaxHealth(foe)) * c.tpPerKill.get()
                + hits * Math.sqrt(punch) * c.tpPerHit.get());
        return tpPerKill * 60.0 / seconds;
    }

    static double punchingBagTpPerMinute(PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        return StatCalculator.scaleTpGain(d, c.tpPerPunch.get()) * 60.0 * 20.0 / Math.max(1, c.punchCooldownTicks.get());
    }

    static double chargingTpPerMinute(PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        return StatCalculator.scaleTpGain(d, c.tpPerChargeInterval.get()) * 60.0 * 20.0 / c.tpChargeTrainingInterval.get();
    }

    static double meditationTpPerMinute(PlayerData d) {
        return StatCalculator.scaleTpGain(d, DBZConfig.SERVER.tpPerMeditationSecond.get()) * 60.0;
    }

    /** The best training multiplier a player can stack (max gravity they can stand, heavy weights, Time Chamber). */
    static double bestTrainingMultiplier(PlayerData d) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double g = Math.min(c.gravityMax.get(), Math.floor(TrainingTicker.tolerance(d)));
        return Math.min(c.trainingMultiplierCap.get(), TrainingTicker.gravityMultiplier(Math.max(g, c.chamberGravity.get())) * 2.5 * c.chamberTrainingMultiplier.get());
    }

    static final int[] RACE_LEVELS = {150, 500, 1000, 2000};

    /** The strongest form a race reaches by {@code level} without flags or a moon, parents assumed mastered. */
    static com.dbzenith.transform.Form bestForm(com.dbzenith.stats.Race race, int level) {
        return bestForm(race, com.dbzenith.race.Variant.defaultFor(race), level);
    }

    /** As above, for one variant (sub-race, clan or path). */
    static com.dbzenith.transform.Form bestForm(com.dbzenith.stats.Race race, com.dbzenith.race.Variant variant, int level) {
        com.dbzenith.transform.Form best = com.dbzenith.transform.Forms.BASE;
        for (com.dbzenith.transform.Form f : com.dbzenith.transform.Forms.all()) {
            if (f.isBase() || !f.races().contains(race) || !f.allows(variant) || needsFlag(f)) continue;
            if (f == com.dbzenith.transform.Forms.GREAT_APE) continue;
            if (com.dbzenith.transform.FormMath.unlockLevel(f) > level) continue;
            if (avgMultiplier(f) > avgMultiplier(best)) best = f;
        }
        return best;
    }

    /** The form or any form before it needs a story flag other than god ki (which the Divine Ritual quest grants). */
    static boolean needsFlag(com.dbzenith.transform.Form f) {
        for (com.dbzenith.transform.Form x = f; !x.isBase(); x = com.dbzenith.transform.Forms.byId(x.parent())) {
            if (x.requiredFlag() != null && !x.requiredFlag().equals("god_ki")) return true; // god ki is a quest away
        }
        return false;
    }

    /** Average combat multiplier; a ranged form (grows with mastery) counts at the middle of its range. */
    static double avgMultiplier(com.dbzenith.transform.Form f) {
        double avg = (f.multiplier(Attribute.STRENGTH) + f.multiplier(Attribute.DEXTERITY) + f.multiplier(Attribute.KI_POWER)) / 3.0;
        double primary = f.multiplier(Attribute.KI_POWER);
        return f.growTo() > primary ? avg * (1 + f.growTo() / primary) / 2 : avg;
    }

    /** TP a Saiyan Zenkai saves (the points it adds, priced at current costs). */
    static long zenkaiWorth(PlayerData d, double ignored) {
        double pct = com.dbzenith.race.Races.of(com.dbzenith.stats.Race.SAIYAN).zenkaiPercent();
        long tp = 0;
        for (Attribute a : List.of(Attribute.STRENGTH, Attribute.DEXTERITY, Attribute.CONSTITUTION, Attribute.KI_POWER)) {
            int gain = com.dbzenith.race.RacePassives.zenkaiGain(d.getAttribute(a), pct);
            tp += gain * StatCalculator.tpCost(d, a);
        }
        return tp;
    }


    /** Jabs for {@code a} to knock out {@code d} (CX-19 phase 10): a light_1 at full release, after defense, through the PvP curve or not. */
    static double jabsToKo(PlayerData a, PlayerData d, boolean curve) {
        com.dbzenith.combat.engine.Move jab = com.dbzenith.combat.engine.Moves.get("light_1");
        double raw = DamageCalculator.meleeOutgoing(a, 1.0, 1) * (jab == null ? 0.8 : jab.damage);
        double dealt = Math.max(raw * DBZConfig.SERVER.minDamageFraction.get(), raw - d.getDerived().defense());
        if (curve) dealt = com.dbzenith.combat.PvpBalance.apply(a, d, dealt, 0, false);
        return Math.ceil(d.getDerived().maxBody() / Math.max(1e-9, dealt));
    }

    /** Player-vs-player pairs for the report and the targets: equal levels, and one four times the other. */
    static final int[][] PVP_PAIRS = {{50, 50}, {200, 200}, {800, 800}, {50, 200}, {200, 800}, {500, 2000}};
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void writeReport(GameTestHelper helper) {
        StringBuilder sb = new StringBuilder("# Balance report (generated by the BalanceReport GameTest)\n\n");
        sb.append("Reference character: points spread evenly over the 7 attributes, 100% release, base form, no gear.\n");
        sb.append("Enemy level is what spawns near that character. Hits are bare-handed punches.\n\n");
        sb.append("| Level | BP | Body | Punch | Ki blast | Foe lvl | Soldier HP | Punches to kill | Soldier hits to kill you | Tyrant punches | Tyrant hits to kill you | TP to +100 lvl |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int lvl : LEVELS) {
            PlayerData d = reference(lvl);
            int foeLevel = KiFighter.levelFor(StatCalculator.fullPower(d));
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), foeLevel);
            KiFighter tyrant = enemy(helper, ModNpcs.TYRANT_LORD.get(), foeLevel);
            double punch = DamageCalculator.meleeOutgoing(d, 1.0, 1);
            double blast = DamageCalculator.kiOutgoing(d, 1.0);
            double soldierHit = mobHitOnPlayer(d, DamageCalculator.fromVanilla(soldier.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            double tyrantHit = mobHitOnPlayer(d, DamageCalculator.fromVanilla(tyrant.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            sb.append(String.format(Locale.ROOT, "| %d | %,d | %.0f | %.0f | %.0f | %d | %.0f | %.0f | %.1f | %.0f | %.1f | %,d |\n",
                    lvl, StatCalculator.battlePower(d), d.getDerived().maxBody(), punch, blast, foeLevel, KiFighter.effectiveMaxHealth(soldier),
                    Math.ceil(KiFighter.effectiveMaxHealth(soldier) / DamageCalculator.toVanilla(punch)), d.getDerived().maxBody() / soldierHit,
                    Math.ceil(KiFighter.effectiveMaxHealth(tyrant) / DamageCalculator.toVanilla(punch)), d.getDerived().maxBody() / tyrantHit,
                    tpBetween(lvl, lvl + 100)));
            soldier.discard();
            tyrant.discard();
        }
        sb.append("\n## TP income per minute (no training multipliers unless stated)\n\n");
        sb.append("| Level | Fighting soldiers | Punching bag | Charging | Meditating | Best stacked multiplier | Minutes to +100 lvl (fighting) |\n|---|---|---|---|---|---|---|\n");
        for (int lvl : LEVELS) {
            PlayerData d = reference(lvl);
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
            double fight = fightingTpPerMinute(d, soldier);
            sb.append(String.format(Locale.ROOT, "| %d | %.0f | %.0f | %.0f | %.0f | x%.1f | %.0f |\n", lvl, fight, punchingBagTpPerMinute(d),
                    chargingTpPerMinute(d), meditationTpPerMinute(d), bestTrainingMultiplier(d), tpBetween(lvl, lvl + 100) / fight));
            soldier.discard();
        }
        sb.append("\n## Hours of steady fighting to reach a level (from a new character)\n\n| Level | Hours |\n|---|---|\n");
        double minutes = 0;
        for (int lvl = 0; lvl < 2000; lvl += 50) {
            PlayerData d = reference(lvl);
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
            minutes += tpBetween(lvl, lvl + 50) / fightingTpPerMinute(d, soldier);
            soldier.discard();
            if (List.of(150, 500, 1000, 2000).contains(lvl + 50)) {
                sb.append(String.format(Locale.ROOT, "| %d | %.1f |\n", lvl + 50, minutes / 60.0));
            }
        }
        sb.append("\n## Best form by race (average of STR/DEX/KI multipliers; forms needing a wish or a moon left out; god ki counts, the Divine Ritual quest grants it)\n\n| Race |");
        for (int lvl : RACE_LEVELS) sb.append(" L").append(lvl).append(" |");
        sb.append("\n|---|");
        for (int ignored : RACE_LEVELS) sb.append("---|");
        sb.append('\n');
        for (com.dbzenith.stats.Race race : com.dbzenith.stats.Race.values()) {
            sb.append("| ").append(race.id()).append(" |");
            for (int lvl : RACE_LEVELS) {
                com.dbzenith.transform.Form f = bestForm(race, lvl);
                sb.append(String.format(Locale.ROOT, " x%.2f %s |", avgMultiplier(f), f.isBase() ? "" : "(" + f.id() + ")"));
            }
            sb.append('\n');
        }
        sb.append("\n## Saiyan Zenkai, in TP it saves, per cooldown, against steady fighting over the same time\n\n| Level | Zenkai worth (TP) | Fighting in that time (TP) |\n|---|---|---|\n");
        for (int lvl : LEVELS) {
            PlayerData d = reference(lvl);
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
            sb.append(String.format(Locale.ROOT, "| %d | %,d | %,.0f |\n", lvl, zenkaiWorth(d, 0.0),
                    fightingTpPerMinute(d, soldier) * DBZConfig.SERVER.zenkaiCooldownTicks.get() / 1200.0));
            soldier.discard();
        }
        sb.append("\n## Player vs player (CX-19)\n\nJabs (light_1) to knock out, both ways, with the balance curve and (in brackets) without it.\n\n");
        sb.append("| Levels | BP ratio | A jabs to KO B | B jabs to KO A |\n|---|---|---|---|\n");
        for (int[] pair : PVP_PAIRS) {
            PlayerData a = reference(pair[0]), b = reference(pair[1]);
            sb.append(String.format(Locale.ROOT, "| %d vs %d | %.2f | %.0f (%.0f) | %.0f (%.0f) |\n", pair[0], pair[1],
                    (double) StatCalculator.battlePower(a) / Math.max(1, StatCalculator.battlePower(b)),
                    jabsToKo(a, b, true), jabsToKo(a, b, false), jabsToKo(b, a, true), jabsToKo(b, a, false)));
        }
        try {
            Path out = helper.getLevel().getServer().getServerDirectory().toPath().resolve("balance-report.md");
            Files.writeString(out, sb.toString());
            DBZenith.LOGGER.info("Balance report written to {}", out.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        helper.succeed();
    }

    /** Hours of steady fighting from a new character to {@code target} (see the report table). */
    static double hoursTo(GameTestHelper helper, int target) {
        double minutes = 0;
        for (int lvl = 0; lvl < target; lvl += 50) {
            PlayerData d = reference(lvl);
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
            minutes += tpBetween(lvl, lvl + 50) / fightingTpPerMinute(d, soldier);
            soldier.discard();
        }
        return minutes / 60.0;
    }

    /** The pacing and fairness targets of BALANCE.md. A failure here means a change moved the balance. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void defaultsMeetTheBalanceTargets(GameTestHelper helper) {
        double[][] pace = {{150, 0.7, 1.6}, {500, 3, 6}, {1000, 7, 14}, {2000, 17, 32}};
        for (double[] p : pace) {
            double h = hoursTo(helper, (int) p[0]);
            helper.assertTrue(h >= p[1] && h <= p[2], String.format(Locale.ROOT, "level %.0f should take %.1f-%.1f hours, takes %.1f", p[0], p[1], p[2], h));
        }
        for (int lvl : LEVELS) {
            PlayerData d = reference(lvl);
            KiFighter soldier = enemy(helper, ModNpcs.KI_SOLDIER.get(), KiFighter.levelFor(StatCalculator.fullPower(d)));
            double punch = DamageCalculator.meleeOutgoing(d, 1.0, 1);
            double punches = Math.ceil(KiFighter.effectiveMaxHealth(soldier) / DamageCalculator.toVanilla(punch));
            double hitsToDie = d.getDerived().maxBody() / mobHitOnPlayer(d, DamageCalculator.fromVanilla(soldier.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            helper.assertTrue(punches >= 8 && punches <= 25, "level " + lvl + ": a soldier should take 8-25 punches, takes " + punches);
            helper.assertTrue(hitsToDie >= 7 && hitsToDie <= 25, "level " + lvl + ": a soldier should need 7-25 hits to beat you, needs " + hitsToDie);
            double zenkai = zenkaiWorth(d, 0);
            double fighting = fightingTpPerMinute(d, soldier) * DBZConfig.SERVER.zenkaiCooldownTicks.get() / 1200.0;
            helper.assertTrue(zenkai <= fighting * 0.6, "level " + lvl + ": Zenkai worth " + zenkai + " TP vs " + fighting + " from fighting");
            helper.assertTrue(bestTrainingMultiplier(d) <= DBZConfig.SERVER.trainingMultiplierCap.get(), "training stacks are capped");
            soldier.discard();
        }
        for (int[] pair : PVP_PAIRS) {                                              // player vs player (CX-19)
            PlayerData a = reference(pair[0]), b = reference(pair[1]);
            double ab = jabsToKo(a, b, true), ba = jabsToKo(b, a, true);
            String what = pair[0] + " vs " + pair[1] + ": " + ab + " / " + ba + " jabs";
            if (pair[0] == pair[1]) helper.assertTrue(ab >= 30 && ab <= 50, "equals knock each other out in 30-50 jabs at every level: " + what);
            else {
                helper.assertTrue(ba < ab, "the stronger wins: " + what);
                helper.assertTrue(ab / ba <= 16.5, "but by at most the cap squared: " + what);
                helper.assertTrue(ba >= 3, "and never in one or two blows: " + what);
            }
        }
        for (com.dbzenith.stats.Race race : com.dbzenith.stats.Race.values()) {
            double top = avgMultiplier(bestForm(race, 2000));
            helper.assertTrue(top >= 4.5, race + " needs a late form: tops out at x" + top);
        }
        helper.succeed();
    }
}
