package com.dbzenith.combat;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;

/**
 * The PvP balance curve (CX-19 phase 10): how long fights between players last, and how much a gap in power counts.
 * <p>
 * Left alone, blows between players grow slower than health as both level up (equal fighters at level 800 needed
 * four times the jabs of level 50), and a gap in power counts twice over: the stronger hits harder <i>and</i> takes less,
 * so a fighter four times the level won in a fortieth of the blows. The curve fixes both, for every blow one player
 * lands on another:
 * <ol>
 *   <li><b>The power ratio</b> {@code x}: how hard the attacker's plain blow (melee or ki, whichever this is) lands on the
 *       defender, against the defender's own plain blow on themselves. 1 between equals.</li>
 *   <li><b>The length of a fight</b>: between equals, a jab ({@code light_1}) takes {@code 1 / pvpEqualJabsToKo} of the
 *       foe's health at any level. Everything else about a blow (the move, a heavy, a Z-hit, a critical, combo scaling,
 *       the guard) keeps its weight on top.</li>
 *   <li><b>The curve</b>: the ratio counts as {@code clamp(x ^ pvpPowerExponent, 1 / pvpDominanceCap, pvpDominanceCap)}.
 *       With the defaults (0.5, cap 4) a fighter whose blows land 5 times harder lands them 2.2 times harder instead and
 *       takes them 2.2 times lighter: they win, about 5 times faster, not 25; the weaker is not helpless.</li>
 *   <li><b>The caps</b>: no one blow takes more than {@code pvpMaxHitFraction} of the foe's health, and once a combo has
 *       taken {@code pvpComboCapFraction}, the rest of it lands at a quarter (time to Burst out).</li>
 * </ol>
 */
public final class PvpBalance {
    /** A jab's multiplier, the yardstick for fight length. */
    static final double JAB = 0.8;

    private PvpBalance() {}

    static DBZConfig.Server c() {
        return DBZConfig.SERVER;
    }

    /** A jab-sized blow from {@code from} on {@code on}, after defense: the yardstick of power and of fight length. */
    public static double plainBlow(PlayerData from, PlayerData on, boolean ki) {
        from.recomputeIfStale();
        on.recomputeIfStale();
        double out = (ki ? DamageCalculator.kiOutgoing(from, 1.0) : DamageCalculator.meleeOutgoing(from, 1.0, 1)) * JAB;
        return Math.max(out * c().minDamageFraction.get(), out - on.getDerived().defense());
    }

    /** The power ratio: the attacker's plain blow on the defender against the defender's own. */
    public static double ratio(PlayerData attacker, PlayerData defender, boolean ki) {
        return plainBlow(attacker, defender, ki) / Math.max(1e-9, plainBlow(defender, defender, ki));
    }

    /** The curve itself: the ratio as it counts. */
    public static double effectiveRatio(double ratio) {
        double cap = Math.max(1, c().pvpDominanceCap.get());
        double r = Math.pow(Math.max(1e-9, ratio), c().pvpPowerExponent.get());
        return Math.max(1 / cap, Math.min(cap, r));
    }

    /** How a power ratio's weight changes: the curve over the raw ratio. 1 between equals. */
    public static double factor(double ratio) {
        return effectiveRatio(ratio) / Math.max(1e-9, ratio);
    }

    /**
     * A blow between players, after defense and guard: the fight-length scale and the curve, then the caps.
     * {@code comboSoFar} is what the running combo (by this attacker) has already done.
     */
    public static double apply(PlayerData attacker, PlayerData defender, double dealt, double comboSoFar, boolean ki) {
        if (dealt <= 0 || !c().pvpScaling.get()) return dealt;
        double maxBody = Math.max(1, defender.getDerived().maxBody());
        double self = Math.max(1e-9, plainBlow(defender, defender, ki));
        double length = maxBody / (c().pvpEqualJabsToKo.get() * self);       // equals: a jab is 1/N of the body
        double out = dealt * length * factor(ratio(attacker, defender, ki));
        out = Math.min(out, maxBody * c().pvpMaxHitFraction.get());
        if (comboSoFar >= maxBody * c().pvpComboCapFraction.get()) out *= 0.25;
        return out;
    }
}
