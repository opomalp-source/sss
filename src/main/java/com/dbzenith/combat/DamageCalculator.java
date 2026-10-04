package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.DerivedStats;
import net.minecraft.util.RandomSource;

/**
 * The single place for damage math. All results are in "DBZ damage" units (= body points)
 * unless the method name says otherwise. Vanilla health scale is converted at the edges with
 * {@link #fromVanilla} / {@link #toVanilla}.
 *
 * <pre>
 * melee outgoing = weapon * vanillaDamageToBody + meleeDamage * release * combo * stamina
 * ki outgoing    = (kiBlastBase + kiDamage) * technique multiplier * release
 * vs a player    = max(raw * minDamageFraction, raw - defense), then guard, then evasion roll
 * vs a mob       = outgoing * dbzDamageVsMobsScale (vanilla HP)
 * </pre>
 */
public final class DamageCalculator {
    private DamageCalculator() {}

    public static double fromVanilla(double vanillaAmount) {
        return vanillaAmount * DBZConfig.SERVER.vanillaDamageToBody.get();
    }

    public static float toVanilla(double dbzDamage) {
        return (float) (dbzDamage * DBZConfig.SERVER.dbzDamageVsMobsScale.get());
    }

    public static double releaseFactor(PlayerData data) {
        return data.getReleasePercent() / 100.0;
    }

    public static double comboMultiplier(int comboHits) {
        return 1.0 + DBZConfig.SERVER.comboBonusPerHit.get() * Math.max(0, comboHits - 1);
    }

    /**
     * Outgoing melee damage. Release % scales only the DBZ (STR) part, so vanilla weapons
     * never get weaker than vanilla.
     */
    public static double meleeOutgoing(PlayerData attacker, double vanillaWeaponDamage, int comboHits) {
        DerivedStats s = attacker.getDerived();
        double dbzPart = s.meleeDamage() * releaseFactor(attacker);
        double raw = fromVanilla(vanillaWeaponDamage) + dbzPart;
        raw *= comboMultiplier(comboHits);
        if (attacker.getStamina() <= 0) raw *= DBZConfig.SERVER.exhaustedDamageMultiplier.get();
        return raw;
    }

    public static double kiOutgoing(PlayerData attacker, double techniqueMultiplier) {
        double base = DBZConfig.SERVER.kiBlastBaseDamage.get() + attacker.getDerived().kiDamage();
        return base * techniqueMultiplier * releaseFactor(attacker);
    }

    /** Defense, guard and evasion applied to a raw hit on a DBZ player. Returns body damage taken. */
    public static double againstPlayer(double raw, PlayerData defender, boolean canEvade, RandomSource random) {
        if (raw <= 0) return 0;
        DerivedStats s = defender.getDerived();
        if (canEvade && random.nextDouble() < s.evasion()) return 0;
        double minimum = raw * DBZConfig.SERVER.minDamageFraction.get();
        double dmg = Math.max(minimum, raw - s.defense());
        if (defender.isGuarding()) dmg *= 1.0 - DBZConfig.SERVER.guardDamageReduction.get();
        return dmg;
    }

    /** Body damage that guarding prevented, given the damage that got through the guard. */
    public static double guardPrevented(double damageTaken) {
        double r = DBZConfig.SERVER.guardDamageReduction.get();
        return r >= 1.0 ? 0 : damageTaken * r / (1.0 - r);
    }

    /** Ki cost of a technique after ki control and release scaling. */
    public static double kiCost(PlayerData user, double baseCost) {
        double control = 1.0 - user.getDerived().kiControl();
        double release = 1.0 + (DBZConfig.SERVER.kiCostReleaseScaling.get() - 1.0) * releaseFactor(user);
        double racial = 1.0 - com.dbzenith.race.Races.of(user.getRace()).kiCostReduction();
        return baseCost * control * release * racial;
    }
}
