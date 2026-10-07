package com.dbzenith.client.motion;

/**
 * The humanoid rig (CX-18): the six model parts and the whole figure ("body"), named as playerAnimator names them so
 * the same pose drives players and NPCs. Each bone has seven channels: pitch, yaw, roll (degrees), x, y, z (pixels,
 * y down, -z forward) and bend (degrees; elbows and knees).
 */
public enum Bone {
    HEAD("head"), TORSO("torso"), RIGHT_ARM("rightArm"), LEFT_ARM("leftArm"), RIGHT_LEG("rightLeg"), LEFT_LEG("leftLeg"), BODY("body");

    public static final int PITCH = 0, YAW = 1, ROLL = 2, X = 3, Y = 4, Z = 5, BEND = 6, CHANNELS = 7;
    public static final Bone[] ALL = values();
    public static final int COUNT = ALL.length;

    public final String id;

    Bone(String id) {
        this.id = id;
    }

    /** Slot of a channel in a {@link Pose}. */
    public int at(int channel) {
        return ordinal() * CHANNELS + channel;
    }

    /** The bone a playerAnimator part name refers to, or null. */
    public static Bone byId(String id) {
        return switch (id) {
            case "head" -> HEAD;
            case "torso" -> TORSO;
            case "rightArm" -> RIGHT_ARM;
            case "leftArm" -> LEFT_ARM;
            case "rightLeg" -> RIGHT_LEG;
            case "leftLeg" -> LEFT_LEG;
            case "body" -> BODY;
            default -> null;
        };
    }

    /** The other side's twin (for mirroring), or itself. */
    public Bone twin() {
        return switch (this) {
            case RIGHT_ARM -> LEFT_ARM;
            case LEFT_ARM -> RIGHT_ARM;
            case RIGHT_LEG -> LEFT_LEG;
            case LEFT_LEG -> RIGHT_LEG;
            default -> this;
        };
    }
}
