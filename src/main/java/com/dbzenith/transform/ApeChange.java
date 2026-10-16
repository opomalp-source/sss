package com.dbzenith.transform;

/** Shared timing for the Great Ape's growth, coat, face and roar, in ticks. */
public final class ApeChange {
    public static final int GROWN = 68, ROAR = 72, ROAR_END = 108, END = 160, CAMERA_END = 180;

    private ApeChange() {}

    public static float phase(float tick, float from, float to) {
        float p = Math.max(0, Math.min(1, (tick - from) / (to - from)));
        return p * p * (3 - 2 * p);
    }

    public static float growth(float tick) { return phase(tick, 8, GROWN); }
    public static float fur(float tick) { return phase(tick, 14, 58); }
    public static float face(float tick) { return phase(tick, 30, GROWN); }
}
