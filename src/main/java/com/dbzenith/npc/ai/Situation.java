package com.dbzenith.npc.ai;

/**
 * Everything a fighter takes into account at one decision, read from the world by {@link NpcBrain#perceive} (or set
 * by hand in tests): its own state, the opponent's visible state, the opponent's profile, its allies, and what it has
 * learned. Plain fields, so {@link UtilityScorer} is a pure function of this.
 */
public final class Situation {
    // ---- who is deciding
    public Personality personality = Personality.BALANCED;
    public FightStyle style = FightStyle.BALANCED;
    /** How well it thinks, 0..1 (light 0.5, normal 0.75, elite 1). */
    public double quality = 0.75;
    /** Fights behind it, 0..1. */
    public double experience;
    public boolean boss, canFly, flying, meleeOnly, noRetreat;

    // ---- itself
    public double health = 1, ki = 1, stamina = 1, guard = 1;
    public boolean quickReady, bigReady, vanishReady, airborne;
    /** Its ki after paying for the next form and ten seconds of it (share of max). */
    public double kiAfterForm = 1;
    public boolean canTransformUp, inForm;
    /** What the next form would multiply its power by (1: none). */
    public double nextFormFactor = 1;

    // ---- the opponent
    public boolean hasTarget = true, lineOfSight = true;
    public double distance = 6, foeHealth = 1, foeKi = -1;
    /** My standing / theirs (power weighed by health). */
    public double ratio = 1;
    public boolean foeAttacking, foeRecovering, foeGuarding, foeCharging, foeStunned, foeExhausted, foeAirborne, foeDowned;
    /** Ticks until an incoming ki attack reaches it (-1: none coming). */
    public int incoming = -1;
    public boolean foeJustTransformed, foeNewTechnique;
    public OpponentProfile profile = new OpponentProfile();

    // ---- allies
    public int allies;
    public boolean allyInDanger, squadRetreat, rangedRole;

    // ---- learned, and what it is doing
    public final double[] bias = new double[FightLog.N];
    public Action current = Action.IDLE;
    public boolean committed;
}
