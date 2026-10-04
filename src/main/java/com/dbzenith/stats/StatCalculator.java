package com.dbzenith.stats;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.Races;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;

/**
 * The single place where base attributes become derived stats, and where TP costs are priced.
 * Damage math lives in {@code combat.DamageCalculator} (Phase 1); everything stat-shaped lives here.
 */
public final class StatCalculator {
    private StatCalculator() {}

    public static DerivedStats compute(PlayerData data) {
        DBZConfig.Server c = DBZConfig.SERVER;
        double str = effective(data, Attribute.STRENGTH);
        double dex = effective(data, Attribute.DEXTERITY);
        int baseDex = data.getAttribute(Attribute.DEXTERITY);
        double speedBonus = Forms.byId(data.getFormId()).speedBonus();
        int con = data.getAttribute(Attribute.CONSTITUTION);
        double kip = effective(data, Attribute.KI_POWER);
        int wil = data.getAttribute(Attribute.WILLPOWER);
        int mnd = data.getAttribute(Attribute.MIND);
        int spi = data.getAttribute(Attribute.SPIRIT);

        FightingPath path = data.getPath();
        boolean fighter = path == FightingPath.FIGHTER, spiritualist = path == FightingPath.SPIRITUALIST;
        double pool = c.pathPoolBonus.get(), dmg = c.pathDamageBonus.get();

        return new DerivedStats(
                c.baseBody.get() + con * c.bodyPerConstitution.get(),
                (c.baseStamina.get() + con * c.staminaPerConstitution.get()) * (fighter ? 1 + pool : 1),
                (c.baseKi.get() + wil * c.kiPerWillpower.get()) * (spiritualist ? 1 + pool : 1),
                str * c.meleeDamagePerStrength.get() * (fighter ? 1 + dmg : 1),
                kip * c.kiDamagePerKiPower.get() * (spiritualist ? 1 + dmg : 1),
                dex * c.defensePerDexterity.get() + con * c.defensePerConstitution.get(),
                Math.min(c.evasionCap.get(), dex * c.evasionPerDexterity.get()),
                Math.min(c.kiControlCap.get(), mnd * c.kiControlPerMind.get()),
                1.0 + spi * c.spiritModifierPerSpirit.get(),
                spi * c.kiTransferPerSpirit.get(),
                Math.min(c.attackSpeedCap.get(), baseDex * c.attackSpeedPerDexterity.get()),
                Math.max(-0.9, Math.min(c.moveSpeedCap.get(), baseDex * c.moveSpeedPerDexterity.get()) + speedBonus));
    }

    /** Attribute value after form and overdrive multipliers (combat attributes only). */
    public static double effective(PlayerData data, Attribute attribute) {
        return data.getAttribute(attribute) * FormMath.attributeMultiplier(data, attribute);
    }

    /** TP needed to raise {@code attribute} by one point from its current value. */
    public static long tpCost(PlayerData data, Attribute attribute) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int current = data.getAttribute(attribute);
        double cost = c.tpCostBase.get() + current * c.tpCostPerPoint.get();
        cost *= data.getPath().costWeight(attribute);
        cost *= Races.of(data.getRace()).costWeight(attribute);
        if (current >= c.attributeSoftCap.get()) cost *= c.tpSoftCapCostMultiplier.get();
        return Math.max(1L, Math.round(cost));
    }

    /** Applies the MIND bonus to a raw TP gain. */
    public static double scaleTpGain(PlayerData data, double rawGain) {
        double mult = (1.0 + data.getAttribute(Attribute.MIND) * DBZConfig.SERVER.tpGainPerMind.get())
                * (1.0 + Races.of(data.getRace()).tpGainBonus())
                * data.getTrainingMultiplier() // gravity, weights, Time Chamber (set by TrainingTicker)
                * (1.0 + DBZConfig.SERVER.prestigeTpBonus.get() * data.getPrestige())
                * (data.getPath() == FightingPath.HYBRID ? 1.0 + DBZConfig.SERVER.hybridTpBonus.get() : 1.0)
                * com.dbzenith.world.LifeSim.wisdomMultiplier(data);
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
        double sum = 0;
        for (Attribute a : Attribute.values()) sum += effective(data, a);
        return Math.round(sum * 10.0 * data.getReleasePercent() / 100.0);
    }
}
