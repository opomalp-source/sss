package com.dbzenith.npc.ai;

/**
 * How an NPC throws its blows (from its personality): how long its strings run before it finishes or backs off, how
 * often it guards and counters, which finisher it likes.
 * <ul>
 *   <li><b>aggressive</b>: long strings ending in a smash or a rush, guards little;</li>
 *   <li><b>defensive</b>: short strings, then back out of reach; guards and counters a lot;</li>
 *   <li><b>tactical</b>: middling strings ending in an uppercut or a sweep, punishes, picks its moment;</li>
 *   <li><b>balanced</b>: a bit of everything.</li>
 * </ul>
 */
public enum FightStyle {
    //         combo guard counter melee
    AGGRESSIVE(5, 0.15, 0.35, 0.85),
    DEFENSIVE(2, 0.55, 0.75, 0.55),
    TACTICAL(3, 0.35, 0.65, 0.55),
    BALANCED(4, 0.30, 0.50, 0.65);

    /** Lights before the finisher. */
    public final int comboLength;
    /** Inclination to guard and to counter, 0..1. */
    public final double guard, counter;
    /** Preference for melee over ki, 0..1. */
    public final double melee;

    FightStyle(int comboLength, double guard, double counter, double melee) {
        this.comboLength = comboLength;
        this.guard = guard;
        this.counter = counter;
        this.melee = melee;
    }
}
