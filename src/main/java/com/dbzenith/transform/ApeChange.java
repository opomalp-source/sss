package com.dbzenith.transform;

/** Shared timing for the Great Ape's growth, coat, face and roar, in ticks. */
public final class ApeChange {
    public static final int GROWN = 92, ROAR = 106, ROAR_END = 142, END = 190, CAMERA_END = 210;

    private ApeChange() {}

    public static float phase(float tick, float from, float to) {
        float p = Math.max(0, Math.min(1, (tick - from) / (to - from)));
        return p * p * (3 - 2 * p);
    }

    public static float growth(float tick) { return 0.3f * phase(tick, 16, 72) + 0.7f * phase(tick, 36, GROWN); }
    public static float fur(float tick) { return phase(tick, 20, 76); }
    public static float face(float tick) { return phase(tick, 46, GROWN); }
}
