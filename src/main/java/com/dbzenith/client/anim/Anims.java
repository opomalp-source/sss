package com.dbzenith.client.anim;

import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;

import java.util.function.Consumer;

/**
 * Every player animation, keyframed in code (original choreography). Built once on the client.
 * <p>
 * Conventions (playerAnimator / vanilla model space, y points down, the face looks towards -z):
 * angles are given in degrees; arm pitch -90 points the arm forward, +40 swings it back; right-arm roll + moves it
 * outward, left-arm roll - moves it outward; a positive "body" pitch leans the whole figure forward; bend flexes
 * elbows and knees. Positions are offsets in pixels from the part's rest pivot. Parts that an animation never keys stay
 * under vanilla (or a lower layer's) control.
 */
public final class Anims {
    private Anims() {}

    // ------------------------------------------------------------------ looping states

    /** Powering up: wide stance, knees bent, fists low and out, the whole body trembling. */
    public static final KeyframeAnimation CHARGE = loop(8, a -> {
        a.rot("rightLeg", 0, -6, 0, 12, Ease.LINEAR).bend("rightLeg", 0, 25, Ease.LINEAR);
        a.rot("leftLeg", 0, -6, 0, -12, Ease.LINEAR).bend("leftLeg", 0, 25, Ease.LINEAR);
        a.rot("rightArm", 0, 10, 0, 22, Ease.LINEAR).bend("rightArm", 0, 22, Ease.LINEAR);
        a.rot("leftArm", 0, 10, 0, -22, Ease.LINEAR).bend("leftArm", 0, 22, Ease.LINEAR);
        a.rot("torso", 0, 8, 0, 0, Ease.LINEAR);
        a.rot("head", 0, -12, 0, 0, Ease.LINEAR);
        for (int t = 0; t <= 8; t += 2) a.pos("body", t, (t % 4 == 0 ? 0.25f : -0.25f), 1.5f, 0, Ease.LINEAR); // tremble, crouched
    });

    /** Guard: forearms crossed in front of the face, knees bent. */
    public static final KeyframeAnimation GUARD = loop(20, a -> {
        a.rot("rightArm", 0, -105, -38, 0, Ease.LINEAR).bend("rightArm", 0, 70, Ease.LINEAR);
        a.rot("leftArm", 0, -100, 38, 0, Ease.LINEAR).bend("leftArm", 0, 70, Ease.LINEAR);
        a.rot("rightLeg", 0, -8, 0, 6, Ease.LINEAR).bend("rightLeg", 0, 15, Ease.LINEAR);
        a.rot("leftLeg", 0, -8, 0, -6, Ease.LINEAR).bend("leftLeg", 0, 15, Ease.LINEAR);
        a.pos("body", 0, 0, 0.8f, 0, Ease.INOUTSINE).pos("body", 10, 0, 1.2f, 0, Ease.INOUTSINE).pos("body", 20, 0, 0.8f, 0, Ease.INOUTSINE);
    });

    /** Heavy strike wind-up: fist drawn back, shoulders twisted, the other arm guarding. */
    public static final KeyframeAnimation HEAVY_WINDUP = loop(10, a -> {
        a.rot("rightArm", 0, 45, 0, 18, Ease.LINEAR).bend("rightArm", 0, 85, Ease.LINEAR);
        a.rot("leftArm", 0, -65, 20, 0, Ease.LINEAR).bend("leftArm", 0, 60, Ease.LINEAR);
        a.rot("torso", 0, 4, -24, 0, Ease.LINEAR);
        a.rot("rightLeg", 0, 10, 0, 6, Ease.LINEAR).rot("leftLeg", 0, -14, 0, -4, Ease.LINEAR).bend("leftLeg", 0, 18, Ease.LINEAR);
        a.pos("body", 0, 0.15f, 0.6f, 0, Ease.LINEAR).pos("body", 5, -0.15f, 0.6f, 0, Ease.LINEAR).pos("body", 10, 0.15f, 0.6f, 0, Ease.LINEAR);
    });

    /** Hovering: one knee raised, arms loose, a slow bob. */
    public static final KeyframeAnimation FLY_HOVER = loop(40, a -> {
        a.rot("rightLeg", 0, -18, 0, 4, Ease.INOUTSINE).bend("rightLeg", 0, 40, Ease.INOUTSINE);
        a.rot("leftLeg", 0, 6, 0, -3, Ease.INOUTSINE).bend("leftLeg", 0, 12, Ease.INOUTSINE);
        a.rot("rightArm", 0, -8, 0, 14, Ease.INOUTSINE).rot("rightArm", 20, -4, 0, 18, Ease.INOUTSINE).rot("rightArm", 40, -8, 0, 14, Ease.INOUTSINE);
        a.rot("leftArm", 0, -8, 0, -14, Ease.INOUTSINE).rot("leftArm", 20, -4, 0, -18, Ease.INOUTSINE).rot("leftArm", 40, -8, 0, -14, Ease.INOUTSINE);
        a.bend("rightArm", 0, 20, Ease.LINEAR).bend("leftArm", 0, 20, Ease.LINEAR);
        a.pos("body", 0, 0, 0, 0, Ease.INOUTSINE).pos("body", 20, 0, -1.2f, 0, Ease.INOUTSINE).pos("body", 40, 0, 0, 0, Ease.INOUTSINE);
    });

    /** Cruising flight: body tipped forward, arms swept back, legs trailing. */
    public static final KeyframeAnimation FLY_FORWARD = loop(20, a -> {
        a.tilt(0, 62, 0, Ease.INOUTSINE).tilt(10, 66, -0.6f, Ease.INOUTSINE).tilt(20, 62, 0, Ease.INOUTSINE);
        a.rot("head", 0, -55, 0, 0, Ease.LINEAR);
        a.rot("rightArm", 0, 28, 0, 12, Ease.LINEAR).rot("leftArm", 0, 28, 0, -12, Ease.LINEAR);
        a.rot("rightLeg", 0, 6, 0, 4, Ease.LINEAR).rot("leftLeg", 0, 12, 0, -4, Ease.LINEAR).bend("leftLeg", 0, 15, Ease.LINEAR);
    });

    /** Full-speed flight: horizontal, one fist punched ahead, the other arm swept back. */
    public static final KeyframeAnimation FLY_FAST = loop(10, a -> {
        a.tilt(0, 84, 0, Ease.LINEAR).tilt(5, 86, -0.3f, Ease.LINEAR).tilt(10, 84, 0, Ease.LINEAR);
        a.rot("head", 0, -78, 0, 0, Ease.LINEAR);
        a.rot("rightArm", 0, -172, 0, -4, Ease.LINEAR);
        a.rot("leftArm", 0, 22, 0, -10, Ease.LINEAR);
        a.rot("rightLeg", 0, 4, 0, 2, Ease.LINEAR).rot("leftLeg", 0, 4, 0, -2, Ease.LINEAR);
    });

    /** Meditating: sitting cross-legged in the air, hands on the knees, slow breathing. */
    public static final KeyframeAnimation MEDITATE = loop(60, a -> {
        a.rot("rightLeg", 0, -88, 32, 0, Ease.LINEAR).bend("rightLeg", 0, 110, Ease.LINEAR);
        a.rot("leftLeg", 0, -88, -32, 0, Ease.LINEAR).bend("leftLeg", 0, 110, Ease.LINEAR);
        a.rot("rightArm", 0, -32, 0, 12, Ease.LINEAR).bend("rightArm", 0, 30, Ease.LINEAR);
        a.rot("leftArm", 0, -32, 0, -12, Ease.LINEAR).bend("leftArm", 0, 30, Ease.LINEAR);
        a.pos("body", 0, 0, 8.5f, 0, Ease.INOUTSINE).pos("body", 30, 0, 7.8f, 0, Ease.INOUTSINE).pos("body", 60, 0, 8.5f, 0, Ease.INOUTSINE);
        a.rot("torso", 0, 2, 0, 0, Ease.INOUTSINE).rot("torso", 30, -2, 0, 0, Ease.INOUTSINE).rot("torso", 60, 2, 0, 0, Ease.INOUTSINE);
    });

    // ------------------------------------------------------------------ melee

    /** Right jab: a quick snap with the shoulder behind it. */
    public static final KeyframeAnimation JAB_RIGHT = once(7, a -> {
        a.rot("rightArm", 0, -40, 0, 8, Ease.OUTQUAD).rot("rightArm", 2, -92, -8, 0, Ease.OUTQUAD)
                .rot("rightArm", 4, -90, -8, 0, Ease.LINEAR).rot("rightArm", 7, -30, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 70, Ease.OUTQUAD).bend("rightArm", 2, 0, Ease.OUTQUAD).bend("rightArm", 4, 0, Ease.LINEAR).bend("rightArm", 7, 50, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -60, 20, 0, Ease.LINEAR).bend("leftArm", 0, 70, Ease.LINEAR);
        a.rot("torso", 0, 0, 0, 0, Ease.OUTQUAD).rot("torso", 2, 4, -16, 0, Ease.OUTQUAD).rot("torso", 7, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Left cross: the mirror of the jab with more twist. */
    public static final KeyframeAnimation CROSS_LEFT = once(7, a -> {
        a.rot("leftArm", 0, -40, 0, -8, Ease.OUTQUAD).rot("leftArm", 2, -94, 10, 0, Ease.OUTQUAD)
                .rot("leftArm", 4, -92, 10, 0, Ease.LINEAR).rot("leftArm", 7, -30, 0, -8, Ease.INOUTQUAD);
        a.bend("leftArm", 0, 70, Ease.OUTQUAD).bend("leftArm", 2, 0, Ease.OUTQUAD).bend("leftArm", 4, 0, Ease.LINEAR).bend("leftArm", 7, 50, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -60, -20, 0, Ease.LINEAR).bend("rightArm", 0, 70, Ease.LINEAR);
        a.rot("torso", 0, 0, 0, 0, Ease.OUTQUAD).rot("torso", 2, 4, 22, 0, Ease.OUTQUAD).rot("torso", 7, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Hook: the right fist sweeps across in an arc. */
    public static final KeyframeAnimation HOOK = once(9, a -> {
        a.rot("rightArm", 0, -70, 55, 30, Ease.INQUAD).rot("rightArm", 3, -88, -20, 10, Ease.OUTQUAD)
                .rot("rightArm", 5, -84, -40, 6, Ease.LINEAR).rot("rightArm", 9, -30, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 80, Ease.LINEAR).bend("rightArm", 9, 50, Ease.INOUTQUAD);
        a.rot("torso", 0, 2, 26, 0, Ease.INQUAD).rot("torso", 3, 4, -28, 0, Ease.OUTQUAD).rot("torso", 9, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -55, 20, 0, Ease.LINEAR).bend("leftArm", 0, 70, Ease.LINEAR);
    });

    /** Kick: a roundhouse with the right leg. */
    public static final KeyframeAnimation KICK = once(10, a -> {
        a.rot("rightLeg", 0, 0, 0, 0, Ease.INQUAD).rot("rightLeg", 3, -95, 0, 30, Ease.OUTQUAD)
                .rot("rightLeg", 6, -90, -20, 25, Ease.LINEAR).rot("rightLeg", 10, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 60, Ease.INQUAD).bend("rightLeg", 3, 0, Ease.OUTQUAD).bend("rightLeg", 10, 0, Ease.LINEAR);
        a.rot("torso", 0, 0, 0, 0, Ease.LINEAR).rot("torso", 3, -8, -18, -8, Ease.OUTQUAD).rot("torso", 10, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -40, 0, 30, Ease.LINEAR).rot("leftArm", 0, -50, 0, -35, Ease.LINEAR);
    });

    /** Heavy punch: a lunge with the whole body behind a straight right. */
    public static final KeyframeAnimation HEAVY_PUNCH = once(12, a -> {
        a.rot("rightArm", 0, 40, 0, 15, Ease.LINEAR).rot("rightArm", 3, -100, -10, 0, Ease.OUTEXPO)
                .rot("rightArm", 7, -95, -10, 0, Ease.LINEAR).rot("rightArm", 12, -30, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 85, Ease.LINEAR).bend("rightArm", 3, 0, Ease.OUTEXPO).bend("rightArm", 12, 40, Ease.INOUTQUAD);
        a.rot("torso", 0, 4, -24, 0, Ease.LINEAR).rot("torso", 3, 12, 20, 0, Ease.OUTEXPO).rot("torso", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 3, 0, 0.8f, -2.5f, Ease.OUTEXPO).pos("body", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftLeg", 0, -14, 0, -4, Ease.LINEAR).rot("leftLeg", 3, -30, 0, -4, Ease.OUTEXPO).rot("leftLeg", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightLeg", 0, 10, 0, 6, Ease.LINEAR).rot("rightLeg", 3, 22, 0, 4, Ease.OUTEXPO).rot("rightLeg", 12, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Launcher: a rising uppercut from a crouch. */
    public static final KeyframeAnimation LAUNCHER = once(12, a -> {
        a.rot("rightArm", 0, 30, 0, 10, Ease.LINEAR).rot("rightArm", 4, -172, -6, 0, Ease.OUTEXPO).rot("rightArm", 12, -40, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 90, Ease.LINEAR).bend("rightArm", 4, 20, Ease.OUTEXPO).bend("rightArm", 12, 40, Ease.INOUTQUAD);
        a.pos("body", 0, 0, 2.5f, 0, Ease.LINEAR).pos("body", 4, 0, -1.5f, 0, Ease.OUTEXPO).pos("body", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("torso", 0, 14, 0, 0, Ease.LINEAR).rot("torso", 4, -16, -10, 0, Ease.OUTEXPO).rot("torso", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 50, Ease.LINEAR).bend("rightLeg", 4, 0, Ease.OUTEXPO).bend("leftLeg", 0, 50, Ease.LINEAR).bend("leftLeg", 4, 0, Ease.OUTEXPO);
    });

    /** Spike: both fists raised overhead and hammered down. */
    public static final KeyframeAnimation SPIKE = once(12, a -> {
        a.rot("rightArm", 0, -165, 0, -10, Ease.OUTQUAD).rot("rightArm", 3, -175, 0, -12, Ease.LINEAR)
                .rot("rightArm", 6, -25, 0, -6, Ease.INEXPO).rot("rightArm", 12, -20, 0, 6, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -165, 0, 10, Ease.OUTQUAD).rot("leftArm", 3, -175, 0, 12, Ease.LINEAR)
                .rot("leftArm", 6, -25, 0, 6, Ease.INEXPO).rot("leftArm", 12, -20, 0, -6, Ease.INOUTQUAD);
        a.rot("torso", 0, -10, 0, 0, Ease.OUTQUAD).rot("torso", 6, 22, 0, 0, Ease.INEXPO).rot("torso", 12, 0, 0, 0, Ease.INOUTQUAD);
    });

    // ------------------------------------------------------------------ ki

    /** Ki blast: a palm thrust from the hip. */
    public static final KeyframeAnimation KI_BLAST = once(8, a -> {
        a.rot("rightArm", 0, 10, 0, 18, Ease.LINEAR).rot("rightArm", 2, -92, -6, 0, Ease.OUTEXPO).rot("rightArm", 5, -88, -6, 0, Ease.LINEAR)
                .rot("rightArm", 8, -20, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 70, Ease.LINEAR).bend("rightArm", 2, 0, Ease.OUTEXPO);
        a.rot("torso", 0, 0, 10, 0, Ease.LINEAR).rot("torso", 2, 2, -14, 0, Ease.OUTEXPO).rot("torso", 8, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Volley: both palms firing in quick alternation. */
    public static final KeyframeAnimation KI_VOLLEY = once(18, a -> {
        for (int t = 0; t <= 14; t += 4) {
            a.rot("rightArm", t, -92, -8, 0, Ease.OUTQUAD).rot("rightArm", t + 2, -60, 0, 10, Ease.INQUAD);
            a.rot("leftArm", t, -60, 0, -10, Ease.INQUAD).rot("leftArm", t + 2, -92, 8, 0, Ease.OUTQUAD);
            a.rot("torso", t, 2, -10, 0, Ease.OUTQUAD).rot("torso", t + 2, 2, 10, 0, Ease.OUTQUAD);
        }
        a.rot("rightArm", 18, -20, 0, 8, Ease.INOUTQUAD).rot("leftArm", 18, -20, 0, -8, Ease.INOUTQUAD).rot("torso", 18, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Beam: cupped hands drawn to the hip, then thrust forward and held while the beam burns. */
    public static KeyframeAnimation kiBeam(int holdTicks) {
        int end = Math.max(12, holdTicks + 6);
        return once(end, a -> {
            a.rot("rightArm", 0, 20, 30, 10, Ease.INOUTQUAD).rot("rightArm", 4, -90, 14, 0, Ease.OUTEXPO).rot("rightArm", end - 4, -88, 14, 0, Ease.LINEAR)
                    .rot("rightArm", end, -20, 0, 8, Ease.INOUTQUAD);
            a.rot("leftArm", 0, 20, -10, -4, Ease.INOUTQUAD).rot("leftArm", 4, -90, -14, 0, Ease.OUTEXPO).rot("leftArm", end - 4, -88, -14, 0, Ease.LINEAR)
                    .rot("leftArm", end, -20, 0, -8, Ease.INOUTQUAD);
            a.bend("rightArm", 0, 60, Ease.LINEAR).bend("rightArm", 4, 0, Ease.OUTEXPO).bend("leftArm", 0, 60, Ease.LINEAR).bend("leftArm", 4, 0, Ease.OUTEXPO);
            a.rot("torso", 0, 0, 35, 0, Ease.INOUTQUAD).rot("torso", 4, 8, 0, 0, Ease.OUTEXPO).rot("torso", end, 0, 0, 0, Ease.INOUTQUAD);
            a.rot("rightLeg", 0, 14, 0, 8, Ease.LINEAR).rot("leftLeg", 0, -16, 0, -6, Ease.LINEAR).bend("leftLeg", 0, 18, Ease.LINEAR);
            for (int t = 6; t < end - 4; t += 2) a.pos("body", t, (t % 4 == 0 ? 0.2f : -0.2f), 0.5f, 0, Ease.LINEAR); // straining
        });
    }

    /** Giant sphere: both arms raised overhead while it forms, then hurled forward. */
    public static KeyframeAnimation kiThrow(int holdTicks) {
        int end = holdTicks + 10;
        return once(end, a -> {
            a.rot("rightArm", 0, -40, 0, 20, Ease.OUTQUAD).rot("rightArm", 6, -176, 0, -18, Ease.OUTQUAD).rot("rightArm", holdTicks, -178, 0, -16, Ease.LINEAR)
                    .rot("rightArm", holdTicks + 3, -60, 0, -4, Ease.OUTEXPO).rot("rightArm", end, -20, 0, 8, Ease.INOUTQUAD);
            a.rot("leftArm", 0, -40, 0, -20, Ease.OUTQUAD).rot("leftArm", 6, -176, 0, 18, Ease.OUTQUAD).rot("leftArm", holdTicks, -178, 0, 16, Ease.LINEAR)
                    .rot("leftArm", holdTicks + 3, -60, 0, 4, Ease.OUTEXPO).rot("leftArm", end, -20, 0, -8, Ease.INOUTQUAD);
            a.rot("torso", 0, 0, 0, 0, Ease.LINEAR).rot("torso", 6, -12, 0, 0, Ease.OUTQUAD).rot("torso", holdTicks, -14, 0, 0, Ease.LINEAR)
                    .rot("torso", holdTicks + 3, 18, 0, 0, Ease.OUTEXPO).rot("torso", end, 0, 0, 0, Ease.INOUTQUAD);
            a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 6, -40, 0, 0, Ease.OUTQUAD).rot("head", holdTicks, -40, 0, 0, Ease.LINEAR).rot("head", holdTicks + 3, 0, 0, 0, Ease.OUTQUAD);
        });
    }

    /** Explosive wave: arms flung wide, chest thrown out. */
    public static final KeyframeAnimation KI_WAVE = once(14, a -> {
        a.rot("rightArm", 0, -20, 0, 10, Ease.INQUAD).rot("rightArm", 3, -10, 0, 85, Ease.OUTEXPO).rot("rightArm", 9, -10, 0, 80, Ease.LINEAR).rot("rightArm", 14, -10, 0, 10, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -20, 0, -10, Ease.INQUAD).rot("leftArm", 3, -10, 0, -85, Ease.OUTEXPO).rot("leftArm", 9, -10, 0, -80, Ease.LINEAR).rot("leftArm", 14, -10, 0, -10, Ease.INOUTQUAD);
        a.rot("torso", 0, 10, 0, 0, Ease.INQUAD).rot("torso", 3, -14, 0, 0, Ease.OUTEXPO).rot("torso", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("head", 0, 10, 0, 0, Ease.INQUAD).rot("head", 3, -25, 0, 0, Ease.OUTEXPO).rot("head", 14, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Self technique (heal, sense, absorb...): two fingers to the brow, focusing. */
    public static final KeyframeAnimation KI_FOCUS = once(14, a -> {
        a.rot("rightArm", 0, -20, 0, 8, Ease.OUTQUAD).rot("rightArm", 4, -150, -40, 0, Ease.OUTQUAD).rot("rightArm", 10, -150, -40, 0, Ease.LINEAR)
                .rot("rightArm", 14, -20, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 20, Ease.OUTQUAD).bend("rightArm", 4, 90, Ease.OUTQUAD).bend("rightArm", 14, 20, Ease.INOUTQUAD);
        a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 4, 12, 0, 0, Ease.OUTQUAD).rot("head", 14, 0, 0, 0, Ease.INOUTQUAD);
    });

    // ------------------------------------------------------------------ transformations and reactions

    /** Transformation: hunched and straining, then the burst: arms flung out, head thrown back in a roar. */
    public static final KeyframeAnimation TRANSFORM = once(40, a -> {
        a.rot("torso", 0, 0, 0, 0, Ease.OUTQUAD).rot("torso", 8, 24, 0, 0, Ease.OUTQUAD).rot("torso", 22, 26, 0, 0, Ease.LINEAR)
                .rot("torso", 25, -16, 0, 0, Ease.OUTEXPO).rot("torso", 34, -14, 0, 0, Ease.LINEAR).rot("torso", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("head", 0, 0, 0, 0, Ease.OUTQUAD).rot("head", 8, 18, 0, 0, Ease.OUTQUAD).rot("head", 22, 20, 0, 0, Ease.LINEAR)
                .rot("head", 25, -30, 0, 0, Ease.OUTEXPO).rot("head", 34, -28, 0, 0, Ease.LINEAR).rot("head", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 0, 0, 5, Ease.OUTQUAD).rot("rightArm", 8, 20, 0, 26, Ease.OUTQUAD).rot("rightArm", 22, 22, 0, 28, Ease.LINEAR)
                .rot("rightArm", 25, 10, 0, 70, Ease.OUTEXPO).rot("rightArm", 34, 10, 0, 66, Ease.LINEAR).rot("rightArm", 40, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -5, Ease.OUTQUAD).rot("leftArm", 8, 20, 0, -26, Ease.OUTQUAD).rot("leftArm", 22, 22, 0, -28, Ease.LINEAR)
                .rot("leftArm", 25, 10, 0, -70, Ease.OUTEXPO).rot("leftArm", 34, 10, 0, -66, Ease.LINEAR).rot("leftArm", 40, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 0, Ease.LINEAR).bend("rightArm", 8, 60, Ease.OUTQUAD).bend("rightArm", 25, 10, Ease.OUTEXPO).bend("rightArm", 40, 0, Ease.INOUTQUAD);
        a.bend("leftArm", 0, 0, Ease.LINEAR).bend("leftArm", 8, 60, Ease.OUTQUAD).bend("leftArm", 25, 10, Ease.OUTEXPO).bend("leftArm", 40, 0, Ease.INOUTQUAD);
        a.rot("rightLeg", 0, 0, 0, 0, Ease.OUTQUAD).rot("rightLeg", 8, -10, 0, 14, Ease.OUTQUAD).rot("rightLeg", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftLeg", 0, 0, 0, 0, Ease.OUTQUAD).rot("leftLeg", 8, -10, 0, -14, Ease.OUTQUAD).rot("leftLeg", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 8, 35, Ease.OUTQUAD).bend("rightLeg", 25, 10, Ease.OUTEXPO).bend("rightLeg", 40, 0, Ease.INOUTQUAD);
        a.bend("leftLeg", 8, 35, Ease.OUTQUAD).bend("leftLeg", 25, 10, Ease.OUTEXPO).bend("leftLeg", 40, 0, Ease.INOUTQUAD);
        for (int t = 10; t <= 22; t += 2) a.pos("body", t, (t % 4 == 0 ? 0.35f : -0.35f), 2.0f, 0, Ease.LINEAR);      // straining tremble
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 8, 0, 2.0f, 0, Ease.OUTQUAD).pos("body", 25, 0, -0.5f, 0, Ease.OUTEXPO).pos("body", 40, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Powering down: a long exhale. */
    public static final KeyframeAnimation POWER_DOWN = once(20, a -> {
        a.rot("torso", 0, 0, 0, 0, Ease.INOUTSINE).rot("torso", 8, 12, 0, 0, Ease.INOUTSINE).rot("torso", 20, 0, 0, 0, Ease.INOUTSINE);
        a.rot("head", 0, 0, 0, 0, Ease.INOUTSINE).rot("head", 8, 14, 0, 0, Ease.INOUTSINE).rot("head", 20, 0, 0, 0, Ease.INOUTSINE);
        a.rot("rightArm", 0, 0, 0, 5, Ease.INOUTSINE).rot("rightArm", 8, 8, 0, 2, Ease.INOUTSINE).rot("rightArm", 20, 0, 0, 5, Ease.INOUTSINE);
        a.rot("leftArm", 0, 0, 0, -5, Ease.INOUTSINE).rot("leftArm", 8, 8, 0, -2, Ease.INOUTSINE).rot("leftArm", 20, 0, 0, -5, Ease.INOUTSINE);
    });

    /** Light hit: a flinch backward. */
    public static final KeyframeAnimation HIT_LIGHT = once(8, a -> {
        a.rot("torso", 0, -12, 4, 0, Ease.OUTEXPO).rot("torso", 8, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("head", 0, -18, 6, 0, Ease.OUTEXPO).rot("head", 8, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -30, 0, 25, Ease.OUTEXPO).rot("rightArm", 8, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -30, 0, -25, Ease.OUTEXPO).rot("leftArm", 8, 0, 0, -5, Ease.INOUTQUAD);
    });

    /** Heavy hit: the body snaps back, arms flung, a stagger. */
    public static final KeyframeAnimation HIT_HEAVY = once(14, a -> {
        a.rot("torso", 0, -28, -8, 0, Ease.OUTEXPO).rot("torso", 8, -20, 0, 0, Ease.LINEAR).rot("torso", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("head", 0, -35, 0, 0, Ease.OUTEXPO).rot("head", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -120, 0, 40, Ease.OUTEXPO).rot("rightArm", 14, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -110, 0, -45, Ease.OUTEXPO).rot("leftArm", 14, 0, 0, -5, Ease.INOUTQUAD);
        a.rot("rightLeg", 0, -20, 0, 6, Ease.OUTEXPO).rot("rightLeg", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftLeg", 0, 15, 0, -6, Ease.OUTEXPO).rot("leftLeg", 14, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Dash: a burst lean with the arms swept back. */
    public static final KeyframeAnimation DASH = once(8, a -> {
        a.rot("torso", 0, 22, 0, 0, Ease.OUTEXPO).rot("torso", 8, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 45, 0, 18, Ease.OUTEXPO).rot("rightArm", 8, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 45, 0, -18, Ease.OUTEXPO).rot("leftArm", 8, 0, 0, -5, Ease.INOUTQUAD);
    });


    // ------------------------------------------------------------------ combat v3 (CombatMoves)

    /** Uppercut: dropped low, then the whole body rising behind a right that ends overhead. */
    public static final KeyframeAnimation UPPERCUT = once(12, a -> {
        a.pos("body", 0, 0, 3.0f, 0, Ease.OUTQUAD).pos("body", 4, 0, -2.0f, 0, Ease.OUTEXPO).pos("body", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 35, 0, 18, Ease.OUTQUAD).rot("rightArm", 4, -178, -10, -4, Ease.OUTEXPO).rot("rightArm", 12, -30, 0, 8, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 100, Ease.OUTQUAD).bend("rightArm", 4, 25, Ease.OUTEXPO).bend("rightArm", 12, 40, Ease.INOUTQUAD);
        a.rot("torso", 0, 18, 15, 0, Ease.OUTQUAD).rot("torso", 4, -18, -12, 0, Ease.OUTEXPO).rot("torso", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 55, Ease.OUTQUAD).bend("rightLeg", 4, 0, Ease.OUTEXPO).bend("leftLeg", 0, 55, Ease.OUTQUAD).bend("leftLeg", 4, 0, Ease.OUTEXPO);
        a.rot("leftArm", 0, -30, 0, -30, Ease.OUTQUAD).rot("leftArm", 12, 0, 0, -5, Ease.INOUTQUAD);
    });

    /** Rush: a shoulder-first charge, both fists driven forward. */
    public static final KeyframeAnimation RUSH = once(12, a -> {
        a.tilt(0, 0, 0, Ease.LINEAR).tilt(3, 28, 0, Ease.OUTEXPO).tilt(12, 0, 0, Ease.INOUTQUAD);
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 3, 0, 0.5f, -4.0f, Ease.OUTEXPO).pos("body", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 40, 0, 15, Ease.LINEAR).rot("rightArm", 3, -95, 8, -6, Ease.OUTEXPO).rot("rightArm", 12, -20, 0, 6, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 40, 0, -15, Ease.LINEAR).rot("leftArm", 3, -90, -8, 6, Ease.OUTEXPO).rot("leftArm", 12, -20, 0, -6, Ease.INOUTQUAD);
        a.rot("rightLeg", 0, 0, 0, 0, Ease.LINEAR).rot("rightLeg", 3, 35, 0, 0, Ease.OUTEXPO).rot("rightLeg", 12, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Sweep: dropped into a crouch, the leg swept round low across the ground. */
    public static final KeyframeAnimation SWEEP = once(14, a -> {
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 3, 0, 8.5f, 0, Ease.OUTEXPO).pos("body", 10, 0, 8.5f, 0, Ease.LINEAR).pos("body", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("body", 0, 0, 0, 0, Ease.LINEAR).rot("body", 3, 0, -40, 0, Ease.OUTQUAD).rot("body", 9, 0, 120, 0, Ease.OUTEXPO).rot("body", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightLeg", 0, 0, 0, 0, Ease.LINEAR).rot("rightLeg", 3, -80, 0, 55, Ease.OUTQUAD).rot("rightLeg", 9, -80, 0, 70, Ease.LINEAR).rot("rightLeg", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("leftLeg", 0, 0, Ease.LINEAR).bend("leftLeg", 3, 110, Ease.OUTQUAD).bend("leftLeg", 10, 110, Ease.LINEAR).bend("leftLeg", 14, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 3, -20, 0, 70, Ease.OUTQUAD).rot("rightArm", 14, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 3, -60, 0, -20, Ease.OUTQUAD).rot("leftArm", 14, 0, 0, -5, Ease.INOUTQUAD);
    });

    /** Z-hit: a flying knee off the dash, fists pulled back. */
    public static final KeyframeAnimation ZHIT = once(12, a -> {
        a.rot("rightLeg", 0, 0, 0, 0, Ease.LINEAR).rot("rightLeg", 3, -110, 0, 6, Ease.OUTEXPO).rot("rightLeg", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 0, Ease.LINEAR).bend("rightLeg", 3, 120, Ease.OUTEXPO).bend("rightLeg", 12, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 0, 0, 10, Ease.LINEAR).rot("rightArm", 3, 50, 0, 25, Ease.OUTEXPO).rot("rightArm", 12, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -10, Ease.LINEAR).rot("leftArm", 3, 50, 0, -25, Ease.OUTEXPO).rot("leftArm", 12, 0, 0, -5, Ease.INOUTQUAD);
        a.rot("torso", 0, 0, 0, 0, Ease.LINEAR).rot("torso", 3, -12, 0, 0, Ease.OUTEXPO).rot("torso", 12, 0, 0, 0, Ease.INOUTQUAD);
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 3, 0, -1.5f, -2.5f, Ease.OUTEXPO).pos("body", 12, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Breaker Wave: curled tight, then everything flung outward at once. */
    public static final KeyframeAnimation BREAKER = once(14, a -> {
        a.rot("rightArm", 0, -40, 0, -20, Ease.OUTQUAD).rot("rightArm", 3, -20, 0, 120, Ease.OUTEXPO).rot("rightArm", 14, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -40, 0, 20, Ease.OUTQUAD).rot("leftArm", 3, -20, 0, -120, Ease.OUTEXPO).rot("leftArm", 14, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 100, Ease.OUTQUAD).bend("rightArm", 3, 0, Ease.OUTEXPO).bend("leftArm", 0, 100, Ease.OUTQUAD).bend("leftArm", 3, 0, Ease.OUTEXPO);
        a.rot("torso", 0, 20, 0, 0, Ease.OUTQUAD).rot("torso", 3, -15, 0, 0, Ease.OUTEXPO).rot("torso", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("head", 0, 25, 0, 0, Ease.OUTQUAD).rot("head", 3, -30, 0, 0, Ease.OUTEXPO).rot("head", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightLeg", 3, 0, 0, 18, Ease.OUTEXPO).rot("rightLeg", 14, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftLeg", 3, 0, 0, -18, Ease.OUTEXPO).rot("leftLeg", 14, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** Spot dodge: a quick lean away from the blow, guard still up. */
    public static final KeyframeAnimation SPOT_DODGE = once(10, a -> {
        a.tilt(0, 0, 0, Ease.LINEAR).tilt(2, -22, 1.5f, Ease.OUTEXPO).tilt(7, -22, 1.5f, Ease.LINEAR).tilt(10, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -70, 0, -25, Ease.OUTEXPO).rot("rightArm", 10, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, -70, 0, 25, Ease.OUTEXPO).rot("leftArm", 10, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("rightLeg", 2, 35, Ease.OUTEXPO).bend("rightLeg", 10, 0, Ease.INOUTQUAD).bend("leftLeg", 2, 35, Ease.OUTEXPO).bend("leftLeg", 10, 0, Ease.INOUTQUAD);
    });

    public static final KeyframeAnimation SIDE_LEFT = sideStep(-1), SIDE_RIGHT = sideStep(1);

    /** Side step: a hop to one side ({@code side} -1 left, 1 right), the body leaning into it. */
    private static KeyframeAnimation sideStep(int side) {
        return once(8, a -> {
            a.rot("body", 0, 0, 0, 0, Ease.LINEAR).rot("body", 2, 0, 0, 28 * side, Ease.OUTEXPO).rot("body", 8, 0, 0, 0, Ease.INOUTQUAD);
            a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 2, -3.0f * side, 1.0f, 0, Ease.OUTEXPO).pos("body", 8, 0, 0, 0, Ease.INOUTQUAD);
            a.rot("rightArm", 2, -40, 0, 25, Ease.OUTEXPO).rot("rightArm", 8, 0, 0, 5, Ease.INOUTQUAD);
            a.rot("leftArm", 2, -40, 0, -25, Ease.OUTEXPO).rot("leftArm", 8, 0, 0, -5, Ease.INOUTQUAD);
        });
    }

    /** Snap recovery in the air: a tight flip back to upright. */
    public static final KeyframeAnimation AIR_RECOVER = once(10, a -> {
        a.tilt(0, 0, 0, Ease.LINEAR).tilt(5, -180, 0, Ease.LINEAR).tilt(10, -360, 0, Ease.OUTQUAD);
        a.bend("rightLeg", 0, 90, Ease.OUTQUAD).bend("rightLeg", 10, 0, Ease.INOUTQUAD).bend("leftLeg", 0, 90, Ease.OUTQUAD).bend("leftLeg", 10, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -30, 0, 10, Ease.OUTQUAD).rot("leftArm", 0, -30, 0, -10, Ease.OUTQUAD);
    });

    /** Ground slide: rolling up off the floor into a crouch. */
    public static final KeyframeAnimation ROLL_UP = once(12, a -> {
        a.tilt(0, -80, 9, Ease.LINEAR).tilt(6, 40, 4, Ease.OUTQUAD).tilt(12, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 100, Ease.LINEAR).bend("rightLeg", 12, 0, Ease.INOUTQUAD).bend("leftLeg", 0, 100, Ease.LINEAR).bend("leftLeg", 12, 0, Ease.INOUTQUAD);
    });

    /** Downed: flat on the back, a hand clutching at nothing. */
    public static final KeyframeAnimation DOWNED = loop(40, a -> {
        a.tilt(0, -90, 9.5f, Ease.INOUTSINE).tilt(20, -88, 9.3f, Ease.INOUTSINE).tilt(40, -90, 9.5f, Ease.INOUTSINE);
        a.rot("rightArm", 0, -10, 0, 40, Ease.INOUTSINE).rot("rightArm", 20, -25, 0, 35, Ease.INOUTSINE).rot("rightArm", 40, -10, 0, 40, Ease.INOUTSINE);
        a.rot("leftArm", 0, 0, 0, -25, Ease.LINEAR);
        a.rot("rightLeg", 0, -10, 0, 8, Ease.LINEAR).bend("rightLeg", 0, 30, Ease.LINEAR);
        a.rot("leftLeg", 0, 0, 0, -6, Ease.LINEAR);
        a.rot("head", 0, -15, 20, 0, Ease.LINEAR);
    });
    // ------------------------------------------------------------------ builder

    private static KeyframeAnimation loop(int length, Consumer<Keys> body) {
        return build(length, true, body);
    }

    private static KeyframeAnimation once(int length, Consumer<Keys> body) {
        return build(length, false, body);
    }

    private static KeyframeAnimation build(int length, boolean loop, Consumer<Keys> body) {
        KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        b.beginTick = 0;
        b.endTick = length;
        b.stopTick = loop ? length : length + 2;
        b.isLooped = loop;
        b.returnTick = 0;
        body.accept(new Keys(b));
        return b.build();
    }

    /** Keyframe helper: degrees in, radians out; positions relative to each part's rest pivot; keyed states enabled. */
    public static final class Keys {
        private final KeyframeAnimation.AnimationBuilder b;

        Keys(KeyframeAnimation.AnimationBuilder b) {
            this.b = b;
        }

        private static void key(KeyframeAnimation.StateCollection.State s, int tick, float value, Ease ease) {
            s.setEnabled(true);
            s.addKeyFrame(tick, value, ease);
        }

        public Keys rot(String part, int tick, float pitch, float yaw, float roll, Ease ease) {
            KeyframeAnimation.StateCollection p = b.getPart(part);
            key(p.pitch, tick, (float) Math.toRadians(pitch), ease);
            key(p.yaw, tick, (float) Math.toRadians(yaw), ease);
            key(p.roll, tick, (float) Math.toRadians(roll), ease);
            return this;
        }

        /**
         * Offset in model pixels (y down, -z forward). The whole-figure "body" part moves in blocks with y up,
         * so its offsets are converted here and every animation can think in pixels.
         */
        public Keys pos(String part, int tick, float dx, float dy, float dz, Ease ease) {
            KeyframeAnimation.StateCollection p = b.getPart(part);
            if (part.equals("body")) {
                dx /= 16f;
                dy /= -16f;
                dz /= 16f;
            }
            key(p.x, tick, p.x.defaultValue + dx, ease);
            key(p.y, tick, p.y.defaultValue + dy, ease);
            key(p.z, tick, p.z.defaultValue + dz, ease);
            return this;
        }

        /**
         * Tips the whole figure forward by {@code pitch} degrees about the middle of the figure. playerAnimator pivots the
         * body part 0.7 blocks (11.2 px) above the feet; the figure's middle sits a little higher, so this nudges for the gap.
         */
        public Keys tilt(int tick, float pitch, float dy, Ease ease) {
            double r = Math.toRadians(pitch), mid = 3.2;  // pixels from the pivot up to the middle of the figure
            rot("body", tick, pitch, 0, 0, ease);
            return pos("body", tick, 0, (float) (mid * (1 - Math.cos(r))) * -1 + dy, (float) (mid * Math.sin(r)), ease);
        }

        public Keys bend(String part, int tick, float degrees, Ease ease) {
            KeyframeAnimation.StateCollection p = b.getPart(part);
            if (p.isBendable) key(p.bend, tick, (float) Math.toRadians(degrees), ease);
            return this;
        }
    }
}
