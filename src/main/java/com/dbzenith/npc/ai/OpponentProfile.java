package com.dbzenith.npc.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a fighter has seen its opponent do, built up slowly from what anyone watching could see: blows thrown, ki
 * fired, techniques and how often the same one comes back, time spent guarding, charging and in the air, dodges,
 * rushing in and keeping away, transformations. No inputs, no hidden numbers, no foresight.
 * <p>
 * Event counts become rates per second and every reading is a slow moving average (about six seconds to follow a
 * change), so one lucky moment never flips the picture; {@link #confidence} grows with what has been seen. The
 * derived traits ({@link #meleeHeavy} ...) are 0..1 and already weighted by confidence.
 */
public final class OpponentProfile {
    /** Moving-average weight of one reading (one decision). */
    static final double ALPHA = 0.06;

    double melee, ki, guard, dodge, charge, rush, kite, flying, transforms;
    int samples;
    private int meleeEvents, kiEvents, dodgeEvents, transformEvents;
    final Map<String, Integer> techniques = new LinkedHashMap<>();
    String lastTechnique = "";
    int repeatStreak;
    String lastForm = "";
    double lastDistance = -1;

    void onMelee() {
        meleeEvents++;
    }

    void onKi(String technique) {
        kiEvents++;
        if (technique == null || technique.isEmpty()) return;
        repeatStreak = technique.equals(lastTechnique) ? repeatStreak + 1 : 1;
        lastTechnique = technique;
        techniques.merge(technique, 1, Integer::sum);
        if (techniques.size() > 8) techniques.remove(techniques.keySet().iterator().next());
    }

    void onDodge() {
        dodgeEvents++;
    }

    void onTransform() {
        transformEvents++;
    }

    /**
     * A reading at a decision, {@code dt} ticks after the last: what the opponent is doing now (guarding, charging,
     * flying), how the distance changed, and the events since.
     */
    void sample(int dt, boolean guarding, boolean charging, boolean airborne, double distance, boolean firingAtRange) {
        double secs = Math.max(0.05, dt / 20.0);
        melee = ema(melee, Math.min(3, meleeEvents / secs));
        ki = ema(ki, Math.min(3, kiEvents / secs));
        dodge = ema(dodge, Math.min(3, dodgeEvents / secs));
        transforms = ema(transforms, Math.min(1, transformEvents / secs));
        guard = ema(guard, guarding ? 1 : 0);
        charge = ema(charge, charging ? 1 : 0);
        flying = ema(flying, airborne ? 1 : 0);
        if (lastDistance >= 0) {
            double closing = (lastDistance - distance) / secs;                 // blocks a second toward us
            rush = ema(rush, lastDistance > 5 && closing > 4 ? 1 : 0);
            kite = ema(kite, distance > 8 && closing < 1.5 && (firingAtRange || closing < -1) ? 1 : 0);
        }
        lastDistance = distance;
        meleeEvents = kiEvents = dodgeEvents = transformEvents = 0;
        samples++;
    }

    private static double ema(double v, double x) {
        return v + (x - v) * ALPHA;
    }

    /** Its habits as numbers (kept between fights by the Nemesis system). */
    public double[] habits() {
        return new double[]{melee, ki, guard, dodge, charge, rush, kite, flying, transforms};
    }

    /**
     * Starts from habits remembered from earlier fights, trusted as much as {@code weight} (0..1: a clever fighter
     * recalls more): the readings carry on from there, so a changed style still shows, only later.
     */
    public void seed(double[] h, double weight) {
        if (h == null || h.length < 9) return;
        melee = h[0];
        ki = h[1];
        guard = h[2];
        dodge = h[3];
        charge = h[4];
        rush = h[5];
        kite = h[6];
        flying = h[7];
        transforms = h[8];
        double w = Math.max(0, Math.min(0.9, weight));
        samples = Math.max(samples, (int) Math.round(40 * w / (1 - w)));
    }

    public int samples() {
        return samples;
    }

    /** Techniques seen, and how often. */
    public java.util.Map<String, Integer> techniques() {
        return techniques;
    }

    /** Tests: one blow seen. */
    public void observeMeleeForTest() {
        onMelee();
    }

    /** Tests: one reading. */
    public void sampleForTest(int dt, boolean guarding, boolean charging, boolean airborne, double distance) {
        sample(dt, guarding, charging, airborne, distance, false);
    }

    /** 0 at first sight, near 1 after a minute of watching. */
    public double confidence() {
        return samples / (samples + 40.0);
    }

    public double meleeHeavy() {
        return sat(melee / (melee + ki + 0.4) * Math.min(1, melee / 0.8));
    }

    public double blastSpam() {
        return sat(ki / (melee + ki + 0.4) * Math.min(1, ki / 0.6));
    }

    public double turtle() {
        return sat(guard * 1.6);
    }

    public double dodgy() {
        return sat(dodge * 2.5);
    }

    public double rusher() {
        return sat(rush * 2.0);
    }

    public double kiter() {
        return sat(kite * 2.0);
    }

    public double charger() {
        return sat(charge * 2.0);
    }

    public double transformer() {
        return sat(transforms * 8.0);
    }

    public double flier() {
        return sat(flying * 1.4);
    }

    /** The technique it keeps coming back to, if it has used the same one at least twice running. */
    public String repeated() {
        return repeatStreak >= 2 ? lastTechnique : "";
    }

    private double sat(double v) {
        return Math.max(0, Math.min(1, v)) * confidence();
    }

    /** One line for the debug readout. */
    public String summary() {
        return String.format("seen %d | melee %.2f ki %.2f turtle %.2f dodgy %.2f rush %.2f kite %.2f charge %.2f transform %.2f fly %.2f%s",
                samples, meleeHeavy(), blastSpam(), turtle(), dodgy(), rusher(), kiter(), charger(), transformer(), flier(),
                repeated().isEmpty() ? "" : " | repeats " + repeated());
    }
}
