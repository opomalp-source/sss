package com.dbzenith.transform;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;

import java.util.List;

/**
 * Transformation math in one place: effective multipliers (form x mastery x overdrive), drains and unlock levels.
 * {@link StatCalculator} calls {@link #attributeMultiplier} when computing derived stats.
 */
public final class FormMath {
    private FormMath() {}

    /**
     * Form multiplier. Fixed forms: 1 + (m - 1) * (1 + bonus * mastery/100). Ranged forms ({@link Form#growTo()}) grow
     * from their base to the top of the range with mastery instead. Rising forms add up to {@link Form#rising()} of the
     * multiplier over three minutes of continuous combat.
     */
    public static double formMultiplier(PlayerData data, Form form, Attribute attribute) {
        double m = form.multiplier(attribute);
        if (m == 1.0) return 1.0;
        double masteryFrac = data.getMastery(form.id()) / 100.0;
        double out;
        double primary = form.multiplier(Attribute.KI_POWER);
        if (form.growTo() > primary && primary > 0) {
            out = m * (1.0 + (form.growTo() / primary - 1.0) * masteryFrac);
        } else {
            out = 1.0 + (m - 1.0) * (1.0 + DBZConfig.SERVER.masteryMaxMultiplierBonus.get() * masteryFrac);
        }
        if (form.rising() > 0) out = 1.0 + (out - 1.0) * (1.0 + form.rising() * risingFraction(data));
        out *= GodKi.powerFactor(data, form);
        return out;
    }

    /** 0 out of combat, building to 1 over three minutes of it. */
    public static double risingFraction(PlayerData data) {
        return Math.min(1.0, data.getCombatTicks() / 3600.0);
    }

    /** Everything that multiplies an attribute: current form and overdrive. */
    public static double attributeMultiplier(PlayerData data, Attribute attribute) {
        double m = formMultiplier(data, Forms.byId(data.getFormId()), attribute);
        if (isCombatAttribute(attribute)) {
            m *= overdriveMultiplier(data) * data.getGearMultiplier(attribute) * com.dbzenith.world.LifeSim.ageMultiplier(data, attribute)
                    * (1.0 + DBZConfig.SERVER.majinAbsorbBonusPerStack.get() * data.getMajinStacks())
                    * (1.0 + DBZConfig.SERVER.prestigePowerBonus.get() * data.getPrestige())
                    * com.dbzenith.race.RacialSkills.attributeFactor(data, attribute);
        }
        return m;
    }

    public static boolean isCombatAttribute(Attribute a) {
        return a == Attribute.STRENGTH || a == Attribute.DEXTERITY || a == Attribute.KI_POWER;
    }

    /** Scales a drain by mastery: at 100 mastery it is reduced by masteryMaxDrainReduction. */
    public static double masteredDrain(double baseDrain, double mastery) {
        return baseDrain * (1.0 - DBZConfig.SERVER.masteryMaxDrainReduction.get() * mastery / 100.0);
    }

    public static int unlockLevel(Form form) {
        return (int) Math.round(form.unlockLevel() * DBZConfig.SERVER.unlockLevelScale.get());
    }

    // ------------------------------------------------------------------ overdrive

    public static final String OVERDRIVE_MASTERY = "overdrive";

    public static List<? extends Double> overdriveLevels() {
        return DBZConfig.SERVER.overdriveLevels.get();
    }

    public static double overdriveMultiplier(PlayerData data) {
        int level = data.getOverdriveLevel();
        if (level <= 0) return 1.0;
        List<? extends Double> levels = overdriveLevels();
        return levels.isEmpty() ? 1.0 : levels.get(Math.min(level, levels.size()) - 1);
    }

    /** Highest overdrive level the player may use right now (0 = locked). */
    public static int maxOverdriveLevel(PlayerData data) {
        boolean unlocked = data.hasFlag("overdrive") || StatCalculator.level(data) >= DBZConfig.SERVER.overdriveUnlockLevel.get();
        if (!unlocked) return 0;
        double per = DBZConfig.SERVER.overdriveMasteryPerLevel.get();
        int byMastery = per <= 0 ? Integer.MAX_VALUE : 1 + (int) (data.getMastery(OVERDRIVE_MASTERY) / per);
        return Math.min(overdriveLevels().size(), byMastery);
    }
}
