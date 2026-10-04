package com.dbzenith.stats;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;

/**
 * The single place where base attributes become derived stats, and where TP costs are priced.
 * Damage math lives in {@code combat.DamageCalculator} (Phase 1); everything stat-shaped lives here.
 */
public final class StatCalculator {
    private StatCalculator() {}

    public static DerivedStats compute(PlayerData data) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int str = data.getAttribute(Attribute.STRENGTH);
        int dex = data.getAttribute(Attribute.DEXTERITY);
        int con = data.getAttribute(Attribute.CONSTITUTION);
        int kip = data.getAttribute(Attribute.KI_POWER);
        int wil = data.getAttribute(Attribute.WILLPOWER);
        int mnd = data.getAttribute(Attribute.MIND);
        int spi = data.getAttribute(Attribute.SPIRIT);

        return new DerivedStats(
                c.baseBody.get() + con * c.bodyPerConstitution.get(),
                c.baseStamina.get() + con * c.staminaPerConstitution.get(),
                c.baseKi.get() + wil * c.kiPerWillpower.get(),
                str * c.meleeDamagePerStrength.get(),
                kip * c.kiDamagePerKiPower.get(),
                dex * c.defensePerDexterity.get() + con * c.defensePerConstitution.get(),
                Math.min(c.evasionCap.get(), dex * c.evasionPerDexterity.get()),
                Math.min(c.kiControlCap.get(), mnd * c.kiControlPerMind.get()),
                1.0 + spi * c.spiritModifierPerSpirit.get(),
                spi * c.kiTransferPerSpirit.get(),
                Math.min(c.attackSpeedCap.get(), dex * c.attackSpeedPerDexterity.get()),
                Math.min(c.moveSpeedCap.get(), dex * c.moveSpeedPerDexterity.get()));
    }

    /** TP needed to raise {@code attribute} by one point from its current value. */
    public static long tpCost(PlayerData data, Attribute attribute) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int current = data.getAttribute(attribute);
        double cost = c.tpCostBase.get() + current * c.tpCostPerPoint.get();
        cost *= data.getPath().costWeight(attribute);
        if (current >= c.attributeSoftCap.get()) cost *= c.tpSoftCapCostMultiplier.get();
        return Math.max(1L, Math.round(cost));
    }

    /** Applies the MIND bonus to a raw TP gain. */
    public static double scaleTpGain(PlayerData data, double rawGain) {
        double mult = 1.0 + data.getAttribute(Attribute.MIND) * DBZConfig.SERVER.tpGainPerMind.get();
        return rawGain * mult;
    }

    /**
     * "Character level" in the DBC sense: total attribute points above the starting value.
     */
    public static int level(PlayerData data) {
        int start = DBZConfig.SERVER.startingAttribute.get();
        int total = 0;
        for (Attribute a : Attribute.values()) total += data.getAttribute(a) - start;
        return Math.max(0, total);
    }

    /**
     * Battle power as shown by a scouter: grows with every attribute and scales with the current release %.
     */
    public static long battlePower(PlayerData data) {
        long sum = 0;
        for (Attribute a : Attribute.values()) sum += data.getAttribute(a);
        return Math.round(sum * 10.0 * data.getReleasePercent() / 100.0);
    }
}
