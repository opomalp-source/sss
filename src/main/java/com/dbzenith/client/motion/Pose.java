package com.dbzenith.client.motion;

import java.util.Arrays;

/** A whole-figure pose: every bone's seven channels in one flat array (see {@link Bone}). Reused, never reallocated. */
public final class Pose {
    public final float[] v = new float[Bone.COUNT * Bone.CHANNELS];

    public void zero() {
        Arrays.fill(v, 0f);
    }

    public float get(Bone b, int channel) {
        return v[b.at(channel)];
    }

    public void set(Bone b, int channel, float value) {
        v[b.at(channel)] = value;
    }

    public void add(Bone b, int channel, float value) {
        v[b.at(channel)] += value;
    }

    public void copyFrom(Pose o) {
        System.arraycopy(o.v, 0, v, 0, v.length);
    }
}
