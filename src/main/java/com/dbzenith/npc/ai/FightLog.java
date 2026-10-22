package com.dbzenith.npc.ai;

/**
 * Short-term learning, for every fighter: what each action has paid in this fight. While an action runs, the share
 * of the foe's health it took counts for it and the share of its own health it lost counts against it; when the action
 * ends that payoff joins a running average for the action. {@link #bias} turns it into a small nudge on the action's
 * score (at most +-0.3, trusted more the more it was tried). Important fighters carry it over between fights through
 * {@link AiMemory}, as a prior that fresh evidence soon outweighs.
 */
public final class FightLog {
    static final int N = Action.values().length;

    final double[] payoff = new double[N];
    final double[] tries = new double[N];
    private Action running;
    private double dealt, taken;

    /** A blow it dealt (share of the victim's health). */
    void dealt(double share) {
        dealt += share;
    }

    /** A blow it took (share of its own health). */
    void taken(double share) {
        taken += share;
    }

    /** The action changed: the one that ran gets its payoff. */
    void switchTo(Action next) {
        if (running != null && running != Action.IDLE) {
            double p = dealt - 1.2 * taken;
            int i = running.ordinal();
            tries[i] += 1;
            payoff[i] += (p - payoff[i]) / Math.min(tries[i], 8);              // a running mean that keeps following
        }
        running = next;
        dealt = taken = 0;
    }

    /** The nudge for an action from what it paid: positive if it worked, negative if it cost. */
    public double bias(Action a) {
        int i = a.ordinal();
        double t = tries[i];
        if (t <= 0) return 0;
        double trust = t / (t + 3);
        return Math.max(-0.3, Math.min(0.3, payoff[i] * 4)) * trust;
    }

    /** Starts a new fight from what was remembered (tries capped so this fight soon decides). */
    void seed(float[] remembered, int[] rememberedTries) {
        clear();
        if (remembered == null) return;
        for (int i = 0; i < N && i < remembered.length; i++) {
            if (rememberedTries == null || i >= rememberedTries.length || rememberedTries[i] <= 0) continue;
            payoff[i] = remembered[i];
            tries[i] = Math.min(3, rememberedTries[i]);
        }
    }

    void clear() {
        java.util.Arrays.fill(payoff, 0);
        java.util.Arrays.fill(tries, 0);
        running = null;
        dealt = taken = 0;
    }

    boolean empty() {
        for (double t : tries) if (t > 0) return false;
        return true;
    }

    /** The best and worst learned so far, for the debug readout. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Action a : Action.values()) {
            double b = bias(a);
            if (Math.abs(b) < 0.02) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.id()).append(String.format(" %+.2f", b));
        }
        return sb.length() == 0 ? "nothing learned yet" : sb.toString();
    }
}
