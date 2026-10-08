package com.dbzenith.client.motion;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * One figure's animation state (CX-18), owned by {@link MotionEngine}. Ticked twenty times a second from what the
 * entity is doing; sampled every frame (interpolated) into a {@link Pose}. Nothing here allocates after creation.
 */
public final class Motion {
    final LivingEntity entity;

    // ---------------------------------------------------------------- the state machine
    final float[] weight = new float[State.COUNT], weightO = new float[State.COUNT];
    /** Time in each state's clip (ticks, unwrapped), and the shared gait phase (cycles, unwrapped). */
    final float[] time = new float[State.COUNT], timeO = new float[State.COUNT];
    float gait, gaitO;
    State state = State.IDLE, candidate = State.IDLE;
    int candidateTicks;
    final Clip[] clips = new Clip[State.COUNT];
    final Clip[] fightClips = new Clip[State.COUNT];   // the fighting set (CX-20): PvP on, or an NPC in a fight
    float fight, fightO;                              // how far into the fighting set, 0..1, eased
    int clipsVersion = -1;
    String raceKey, formKey, styleKey = "";

    // ---------------------------------------------------------------- what the entity is doing (sensors)
    double speed, speedPrev, vy, forward, lateral;
    float yawRate;
    boolean ground, flying, wasFlying;
    int airTicks, groundTicks;
    double fallSpeed;
    float sizeScale = 1f;

    // ---------------------------------------------------------------- procedural layers (value, previous tick)
    float engine, engineO;                    // the whole engine's weight against vanilla
    float lean, leanO, roll, rollO;           // on the ground: lean into speed, roll into turns
    float flyPitch, flyPitchO, bank, bankO;   // in the air
    float armLag, armLagO;
    float land, landO;                        // landing crouch, 1 on impact, fading
    float takeoff, takeoffO;                  // takeoff spring, 1 at lift-off, fading
    float fatigue;                            // breath after a sprint
    float flyBlend, flyBlendO;                // 0 grounded .. 1 flying, for the pivot
    final float[] mask = new float[Bone.COUNT], maskO = new float[Bone.COUNT];   // 1 = ours, 0 = vanilla's (items, swings)
    float walkAmp = 1f, walkAmpO = 1f;

    // ---------------------------------------------------------------- bookkeeping
    long lastTick;
    long lastSeen;
    boolean simple;                            // far away: no procedural layers
    final Pose pose = new Pose();
    float sampledAt = -1f;
    long sampledFrame = -1;
    MotionAnimation animation;
    Object applier;                            // playerAnimator's part applier, for NPC models

    Motion(LivingEntity entity) {
        this.entity = entity;
        weight[State.IDLE.ordinal()] = weightO[State.IDLE.ordinal()] = 1f;
        java.util.Arrays.fill(mask, 1f);
        java.util.Arrays.fill(maskO, 1f);
    }

    public State state() {
        return state;
    }

    public float engineWeight(float pt) {
        return Mth.lerp(pt, engineO, engine);
    }

    public LivingEntity entity() {
        return entity;
    }
}
