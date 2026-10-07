package com.dbzenith.client.motion;

/**
 * What a figure is doing, as far as its body is concerned (CX-18). Each state names the clip its animation set plays
 * for it ({@link #key}) and how quickly it blends in.
 */
public enum State {
    IDLE("idle", Kind.GAIT),
    WALK("walk", Kind.GAIT),
    SPRINT("sprint", Kind.GAIT),
    JUMP("jump", Kind.AIR),
    FALL("fall", Kind.AIR),
    LAND("land", Kind.LAND),
    TAKEOFF("takeoff", Kind.LAND),
    HOVER("hover", Kind.FLY),
    CRUISE("cruise", Kind.FLY),
    FAST("fast", Kind.FLY),
    ASCEND("ascend", Kind.FLY),
    DESCEND("descend", Kind.FLY),
    BACKWARD("backward", Kind.FLY),
    CHARGE("charge", Kind.OTHER);

    public enum Kind { GAIT, AIR, LAND, FLY, OTHER }

    public static final State[] ALL = values();
    public static final int COUNT = ALL.length;

    public final String key;
    public final Kind kind;

    State(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    public boolean flying() {
        return kind == Kind.FLY;
    }

    /** Ticks to blend in. */
    public float blend() {
        return switch (kind) {
            case GAIT -> Tuning.blendGait;
            case AIR -> Tuning.blendAir;
            case LAND -> Tuning.blendLand;
            case FLY -> Tuning.blendFly;
            default -> Tuning.blendDefault;
        };
    }
}
