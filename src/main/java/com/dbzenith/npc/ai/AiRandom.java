package com.dbzenith.npc.ai;

/**
 * The AI's only source of variation, and a deterministic one: a hash of who is deciding (the NPC's UUID bits), which
 * decision (a counter) and what about (a salt). No {@code Math.random}, no world random: the same fight from the same
 * state makes the same choices, and a test can replay it.
 */
public final class AiRandom {
    private AiRandom() {}

    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /** A number in [0, 1) for this seed, decision and salt. */
    public static double unit(long seed, long counter, int salt) {
        long h = mix(seed ^ mix(counter * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L));
        return (h >>> 11) * 0x1.0p-53;
    }

    /** {@code true} with probability {@code p}. */
    public static boolean chance(long seed, long counter, int salt, double p) {
        return p > 0 && unit(seed, counter, salt) < p;
    }

    /** One of {@code n} choices. */
    public static int pick(long seed, long counter, int salt, int n) {
        return n <= 1 ? 0 : (int) Math.min(n - 1, Math.floor(unit(seed, counter, salt) * n));
    }
}
