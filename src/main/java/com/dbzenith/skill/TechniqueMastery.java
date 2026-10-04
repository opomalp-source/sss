package com.dbzenith.skill;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;

/**
 * Technique mastery (0-100), stored in the player's mastery map under {@code technique:<id>}. Every use raises it
 * (faster with SPI); mastered techniques hit harder, cost less ki and come back sooner.
 */
public final class TechniqueMastery {
    private TechniqueMastery() {}

    public static String key(Technique t) {
        return "technique:" + t.id();
    }

    public static double get(PlayerData data, Technique t) {
        return data.getMastery(key(t));
    }

    static void gain(PlayerData data, Technique t) {
        double spirit = data.getDerived().spiritModifier();
        data.setMastery(key(t), get(data, t) + DBZConfig.SERVER.techniqueMasteryPerUse.get() * spirit);
    }

    public static double damageMultiplier(PlayerData data, Technique t) {
        return 1.0 + DBZConfig.SERVER.techniqueMasteryDamageBonus.get() * get(data, t) / 100.0;
    }

    public static double costMultiplier(PlayerData data, Technique t) {
        return 1.0 - DBZConfig.SERVER.techniqueMasteryCostReduction.get() * get(data, t) / 100.0;
    }

    public static int cooldownTicks(PlayerData data, Technique t) {
        return (int) Math.round(t.cooldownTicks() * (1.0 - DBZConfig.SERVER.techniqueMasteryCooldownReduction.get() * get(data, t) / 100.0));
    }
}
