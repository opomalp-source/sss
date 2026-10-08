package com.dbzenith.client.anim;

import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every player animation, keyframed in code (original choreography). Built once on the client.
 * <p>
 * Conventions (playerAnimator / vanilla model space, y points down, the face looks towards -z):
 * angles are given in degrees; arm pitch -90 points the arm forward, +40 swings it back; right-arm roll + moves it
 * outward, left-arm roll - moves it outward; leg pitch - swings the leg forward; a positive torso yaw brings the right
 * shoulder forward; a positive "body" pitch leans the whole figure forward; bend flexes elbows and knees. Positions are
 * offsets in pixels from the part's rest pivot (y down, -z forward). Parts that an animation never keys stay under
 * vanilla (or a lower layer's) control.
 * <p>
 * Animation v4 (CX-15): the fighting moves are built from whole-body {@link Pose}s, so every key sets the head, torso,
 * both arms, both legs and the body's offset together: anticipation before each strike, a fast extension, a short
 * overshoot and hold, then follow-through that settles back into the fighting stance instead of dropping to neutral.
 * Unless a pose turns the head itself, the head turns back against the torso so the eyes stay on the target.
 */
public final class Anims {
    private Anims() {}

    /**
     * An artist's animation in place of a built-in clip (CX-19 phase 10): a playerAnimator emote file at
     * {@code assets/dbzenith/player_animation/<name>.json} (in the mod or a resource pack) replaces the clip of that
     * name (lower case: {@code hit_heavy}, {@code uppercut}...). Without one, {@code fallback}.
     */
    private static java.util.Map<KeyframeAnimation, String> names;

    /** A built-in clip's name (its field: {@code HIT_HEAVY}), or null. */
    public static String nameOf(KeyframeAnimation anim) {
        if (names == null) {
            java.util.Map<KeyframeAnimation, String> m = new java.util.IdentityHashMap<>();
            for (java.lang.reflect.Field f : Anims.class.getFields()) {
                if (f.getType() != KeyframeAnimation.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    m.put((KeyframeAnimation) f.get(null), f.getName());
                } catch (IllegalAccessException ignored) {
                }
            }
            names = m;
        }
        return names.get(anim);
    }

    /** A built-in clip, or an artist's file in its place. */
    public static KeyframeAnimation custom(KeyframeAnimation builtIn) {
        String name = builtIn == null ? null : nameOf(builtIn);
        return name == null ? builtIn : custom(name, builtIn);
    }

    public static KeyframeAnimation custom(String name, KeyframeAnimation fallback) {
        try {
            KeyframeAnimation a = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(
                    new net.minecraft.resources.ResourceLocation(com.dbzenith.DBZenith.MOD_ID, name.toLowerCase(java.util.Locale.ROOT)));
            return a != null ? a : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ poses

    private static final String[] PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] LIMBS = {"rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final float[] ZERO = {0, 0, 0};

    /** A whole-body pose: each part's pitch / yaw / roll and bend (degrees), the body's offset (pixels) and forward tilt. */
    static final class Pose {
        final Map<String, float[]> rot = new HashMap<>();
        final Map<String, Float> bend = new HashMap<>();
        final Set<String> free = new HashSet<>();
        float dx, dy, dz, tilt;
        boolean headSet;

        Pose copy() {
            Pose p = new Pose();
            rot.forEach((k, v) -> p.rot.put(k, v.clone()));
            p.bend.putAll(bend);
            p.free.addAll(free);
            p.dx = dx;
            p.dy = dy;
            p.dz = dz;
            p.tilt = tilt;
            p.headSet = headSet;
            return p;
        }

        Pose r(String part, float pitch, float yaw, float roll) {
            rot.put(part, new float[]{pitch, yaw, roll});
            if (part.equals("head")) headSet = true;
            return this;
        }

        Pose b(String part, float degrees) {
            bend.put(part, degrees);
            return this;
        }

        /** Arm or leg in one go: rotation and bend. */
        Pose limb(String part, float pitch, float yaw, float roll, float bend) {
            return r(part, pitch, yaw, roll).b(part, bend);
        }

        Pose at(float x, float y, float z) {
            dx = x;
            dy = y;
            dz = z;
            return this;
        }

        Pose move(float x, float y, float z) {
            dx += x;
            dy += y;
            dz += z;
            return this;
        }

        Pose tilt(float pitch) {
            tilt = pitch;
            return this;
        }

        /** Leave these parts to vanilla or a lower layer (a stance that should still let you look round and walk). */
        Pose freeing(String... parts) {
            free.addAll(java.util.List.of(parts));
            return this;
        }
    }

    /**
     * The fighting stance every strike starts from and settles back into: orthodox, left foot forward, knees soft,
     * the weight low, the rear fist by the chin and the lead fist up in front.
     */
    static final Pose FIGHT = new Pose().at(0, 1.0f, 0)
            .r("torso", 5, -16, 0)
            .limb("rightArm", -36, -8, 12, 118)
            .limb("leftArm", -60, 10, -8, 92)
            .limb("rightLeg", 14, 0, 8, 20)
            .limb("leftLeg", -16, 0, -5, 22);

    static Pose fight() {
        return FIGHT.copy();
    }

    /** A wide, low base for firing ki: feet apart, knees bent. */
    static Pose wide() {
        return fight().at(0, 1.8f, 0).r("torso", 6, 0, 0).limb("rightLeg", 8, 0, 14, 26).limb("leftLeg", -8, 0, -14, 26);
    }

    // ------------------------------------------------------------------ looping states

    /** Powering up: a wide, low stance, fists clenched down and out, the head thrown back, the whole body shaking. */
    public static final KeyframeAnimation CHARGE = loop(8, a -> {
        Pose base = new Pose().at(0, 2.2f, 0).r("torso", 8, 0, 0).r("head", -16, 0, 0)
                .limb("rightArm", 14, 0, 24, 35).limb("leftArm", 14, 0, -24, 35)
                .limb("rightLeg", -4, 0, 16, 30).limb("leftLeg", -4, 0, -16, 30);
        for (int t = 0; t <= 8; t += 2) {
            float s = t % 4 == 0 ? 1 : -1;
            a.pose(t, base.copy().move(0.3f * s, 0, 0).limb("rightArm", 14, 0, 24 + 2 * s, 35).limb("leftArm", 14, 0, -24 + 2 * s, 35)
                    .r("head", -16 + s, 2 * s, 0), Ease.LINEAR);
        }
    });

    /** Guard: forearms crossed high in front of the face, chin tucked, knees bent, breathing behind the guard. */
    public static final KeyframeAnimation GUARD = loop(20, a -> {
        Pose g = fight().at(0, 1.6f, 0).r("torso", 10, -6, 0).r("head", 8, 4, 0)
                .limb("rightArm", -108, -40, 0, 78).limb("leftArm", -102, 40, 0, 78);
        a.pose(0, g, Ease.INOUTSINE).pose(10, g.copy().move(0, 0.4f, 0).r("torso", 12, -6, 0), Ease.INOUTSINE).pose(20, g, Ease.INOUTSINE);
    });

    /** Charging a blast (CX-23): the lead hand thrust out with the palm open, the other hand gripping that wrist, braced. */
    public static final KeyframeAnimation TECH_HOLD_BLAST = loop(8, a -> {
        Pose p = new Pose().at(0, 1.2f, 0).r("torso", 4, -12, 0).r("head", -4, 10, 0)
                .limb("rightArm", -88, -6, 4, 10).limb("leftArm", -72, 40, 0, 62).freeing("rightLeg", "leftLeg");
        for (int t = 0; t <= 8; t += 2) a.pose(t, p.copy().move(t % 4 == 0 ? 0.08f : -0.08f, 0, 0), Ease.LINEAR);
    });

    /** Charging a beam (CX-23): turned away, both hands cupped together at the hip, gathering it there, knees bent. */
    public static final KeyframeAnimation TECH_HOLD_BEAM = loop(8, a -> {
        Pose p = new Pose().at(0, 1.6f, 0).r("torso", 10, 32, 0).r("head", -4, -26, 0)
                .limb("rightArm", 24, -20, 20, 96).limb("leftArm", -10, 64, -6, 112).freeing("rightLeg", "leftLeg");
        for (int t = 0; t <= 8; t += 2) a.pose(t, p.copy().move(t % 4 == 0 ? 0.08f : -0.08f, 0, 0), Ease.LINEAR);
    });

    /** Charging something huge overhead (CX-23): both arms raised high, palms up under it, leaning back, head up. */
    public static final KeyframeAnimation TECH_HOLD_OVERHEAD = loop(8, a -> {
        Pose p = new Pose().at(0, 0.8f, 0).r("torso", -8, 0, 0).r("head", -22, 0, 0)
                .limb("rightArm", -170, -10, 10, 14).limb("leftArm", -170, 10, -10, 14).freeing("rightLeg", "leftLeg");
        for (int t = 0; t <= 8; t += 2) a.pose(t, p.copy().move(t % 4 == 0 ? 0.08f : -0.08f, 0, 0), Ease.LINEAR);
    });

    /** The hold pose for a kind of charge (KiCharge.KIND_*). */
    public static KeyframeAnimation techHold(int kind) {
        return switch (kind) {
            case com.dbzenith.skill.KiCharge.KIND_BEAM -> custom("tech_hold_beam", TECH_HOLD_BEAM);
            case com.dbzenith.skill.KiCharge.KIND_OVERHEAD -> custom("tech_hold_overhead", TECH_HOLD_OVERHEAD);
            default -> custom("tech_hold_blast", TECH_HOLD_BLAST);
        };
    }

    /** Heavy strike wind-up: fist drawn far back, shoulders twisted, weight sunk on the back leg, straining. */
    public static final KeyframeAnimation HEAVY_WINDUP = loop(10, a -> {
        Pose w = heavyLoad();
        for (int t = 0; t <= 10; t += 2) a.pose(t, w.copy().move(t % 4 == 0 ? 0.18f : -0.18f, 0, 0), Ease.LINEAR);
    });

    private static Pose heavyLoad() {
        return fight().at(0, 2.2f, 1.0f).r("torso", 2, -38, 0)
                .limb("rightArm", 35, 12, 22, 105).limb("leftArm", -92, 18, 0, 35)
                .limb("rightLeg", 18, 0, 8, 26).limb("leftLeg", -14, 0, -5, 20);
    }

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

    /** Right straight: a short load, the fist snapping out with the shoulder and a half step behind it, then back to guard. */
    public static final KeyframeAnimation JAB_RIGHT = once(9, a -> a
            .pose(0, fight(), Ease.LINEAR)
            .pose(1, fight().r("torso", 4, -24, 0).limb("rightArm", -50, -14, 18, 115).move(0, 0.2f, 0.4f), Ease.OUTQUAD)
            .pose(3, strikeRight(-95, -10, 2, 0), Ease.OUTEXPO)
            .pose(4, strikeRight(-93, -10, 2, 4), Ease.LINEAR)
            .pose(9, fight(), Ease.INOUTQUAD));

    private static Pose strikeRight(float pitch, float yaw, float roll, float bend) {
        return fight().r("torso", 8, 16, 0).limb("rightArm", pitch, yaw, roll, bend).limb("leftArm", -50, 12, -10, 115)
                .limb("leftLeg", -24, 0, -5, 28).limb("rightLeg", 20, 0, 6, 6).move(0, 0.4f, -1.4f);
    }

    /** Left straight: the lead hand driven through with the whole torso turning behind it. */
    public static final KeyframeAnimation CROSS_LEFT = once(10, a -> {
        Pose hit = fight().r("torso", 8, -34, 0).limb("leftArm", -96, 8, -2, 0).limb("rightArm", -40, -10, 12, 120)
                .limb("leftLeg", -26, 0, -5, 30).limb("rightLeg", 22, 0, 6, 4).move(0, 0.4f, -1.6f);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(1, fight().r("torso", 4, -4, 0).limb("leftArm", -60, 20, -14, 115).move(0, 0.2f, 0.3f), Ease.OUTQUAD)
                .pose(3, hit, Ease.OUTEXPO)
                .pose(4, hit.copy().limb("leftArm", -94, 8, -2, 4), Ease.LINEAR)
                .pose(10, fight(), Ease.INOUTQUAD);
    });

    /** Hook: elbow lifted wide, then the fist whipped across with the hips, a little past, and back. */
    public static final KeyframeAnimation HOOK = once(11, a -> {
        Pose hit = fight().r("torso", 8, 30, 0).limb("rightArm", -90, -38, 10, 85).limb("leftArm", -48, 12, -8, 115)
                .limb("rightLeg", 18, 0, 10, 8).limb("leftLeg", -20, 0, -6, 26).move(-0.4f, 0.5f, -1.0f);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(2, fight().r("torso", 2, -36, 0).limb("rightArm", -70, 40, 50, 95).move(0.3f, 0.3f, 0.3f), Ease.OUTQUAD)
                .pose(4, hit, Ease.OUTEXPO)
                .pose(5, hit.copy().r("torso", 8, 38, 0).limb("rightArm", -88, -46, 8, 85), Ease.OUTQUAD)
                .pose(11, fight(), Ease.INOUTQUAD);
    });

    /** Front kick: the knee chambered high, the leg snapped straight out at chest height with the body leaning back, re-chambered, set down. */
    public static final KeyframeAnimation KICK = once(14, a -> {
        Pose out = fight().r("torso", -18, -8, 0).limb("rightLeg", -100, 0, 8, 0).limb("leftLeg", 4, 0, -4, 14)
                .limb("rightArm", -20, 0, 30, 70).limb("leftArm", -55, 12, -24, 100).move(0, -0.6f, 0.8f);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(2, fight().r("torso", -8, -10, 0).limb("rightLeg", -85, 0, 6, 110).limb("leftLeg", 2, 0, -4, 14)
                        .limb("rightArm", -30, 0, 25, 80).move(0, -0.6f, 0.3f), Ease.OUTQUAD)
                .pose(5, out, Ease.OUTEXPO)
                .pose(7, out.copy().limb("rightLeg", -98, 0, 8, 4), Ease.LINEAR)
                .pose(10, fight().r("torso", -6, -12, 0).limb("rightLeg", -70, 0, 6, 95).limb("leftLeg", 2, 0, -4, 14), Ease.INOUTQUAD)
                .pose(14, fight(), Ease.INOUTQUAD);
    });

    /** Heavy punch: loaded deep on the back leg, then a lunging straight right with the other arm torn back for power. */
    public static final KeyframeAnimation HEAVY_PUNCH = once(15, a -> {
        Pose hit = fight().at(0, 1.8f, -3.4f).r("torso", 14, 30, 0).limb("rightArm", -98, -12, 0, 0).limb("leftArm", 25, 10, -25, 95)
                .limb("leftLeg", -36, 0, -5, 34).limb("rightLeg", 30, 0, 6, 0);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(3, heavyLoad(), Ease.OUTQUAD)
                .pose(5, hit, Ease.OUTEXPO)
                .pose(7, hit.copy().limb("rightArm", -96, -12, 2, 3).move(0, 0, -0.2f), Ease.LINEAR)
                .pose(15, fight(), Ease.INOUTQUAD);
    });

    /** A rising uppercut: dropped into a crouch, then the legs drive the whole body up behind a fist that ends overhead. */
    private static KeyframeAnimation uppercut(float depth) {
        return once(13, a -> {
            Pose up = fight().at(0, 1.0f - depth * 0.7f, -0.8f).r("torso", -22, 18, 0).r("head", -30, -10, 0)
                    .limb("rightArm", -176, -8, -6, 22).limb("leftArm", 10, 10, -30, 60)
                    .limb("rightLeg", 16, 0, 4, 0).limb("leftLeg", -30, 0, -4, 20);
            a.pose(0, fight(), Ease.LINEAR)
                    .pose(3, fight().at(0, 1.0f + depth, 0.2f).r("torso", 22, -24, 0).limb("rightArm", 28, 8, 20, 115)
                            .limb("leftArm", -70, 20, -10, 100).limb("rightLeg", 10, 0, 10, 55).limb("leftLeg", -24, 0, -8, 60), Ease.OUTQUAD)
                    .pose(5, up, Ease.OUTEXPO)
                    .pose(7, up.copy().limb("rightArm", -178, -8, -6, 18), Ease.LINEAR)
                    .pose(13, fight(), Ease.INOUTQUAD);
        });
    }

    /** Launcher: the uppercut that sends the target flying. */
    public static final KeyframeAnimation LAUNCHER = uppercut(2.4f);
    /** Uppercut (Combat v3): dropped lower, rising higher. */
    public static final KeyframeAnimation UPPERCUT = uppercut(3.2f);

    /** Spike: both fists raised overhead with the back arched, then hammered down with the whole body. */
    public static final KeyframeAnimation SPIKE = once(13, a -> {
        Pose smash = fight().at(0, 3.0f, -1.2f).r("torso", 34, 0, 0).r("head", 10, 0, 0)
                .limb("rightArm", -28, 0, -8, 10).limb("leftArm", -28, 0, 8, 10)
                .limb("rightLeg", 12, 0, 8, 40).limb("leftLeg", -18, 0, -6, 44);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(3, fight().at(0, 0, 0.6f).r("torso", -16, 0, 0).r("head", -20, 0, 0).limb("rightArm", -170, 0, -12, 30)
                        .limb("leftArm", -170, 0, 12, 30).limb("rightLeg", 8, 0, 6, 6).limb("leftLeg", -10, 0, -6, 8), Ease.OUTQUAD)
                .pose(5, smash, Ease.OUTEXPO)
                .pose(7, smash, Ease.LINEAR)
                .pose(13, fight(), Ease.INOUTQUAD);
    });

    /** Z-hit: sunk for the spring, then a flying knee with both fists torn back, and a landing into guard. */
    public static final KeyframeAnimation ZHIT = once(12, a -> {
        Pose knee = fight().at(0, -1.8f, -2.8f).r("torso", -14, 0, 0).limb("rightLeg", -115, 0, 6, 125).limb("leftLeg", 20, 0, -4, 10)
                .limb("rightArm", 40, 0, 25, 60).limb("leftArm", 40, 0, -25, 60);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(2, fight().at(0, 2.5f, 0.5f).limb("rightLeg", 6, 0, 8, 45).limb("leftLeg", -10, 0, -5, 45), Ease.OUTQUAD)
                .pose(4, knee, Ease.OUTEXPO)
                .pose(6, knee, Ease.LINEAR)
                .pose(12, fight(), Ease.INOUTQUAD);
    });

    /** Breaker Wave: curled tight around the gathering ki, then everything flung outward at once. */
    public static final KeyframeAnimation BREAKER = once(15, a -> {
        Pose burst = new Pose().at(0, 0.2f, 0).r("torso", -18, 0, 0).r("head", -30, 0, 0)
                .limb("rightArm", -20, 0, 118, 0).limb("leftArm", -20, 0, -118, 0)
                .limb("rightLeg", 0, 0, 20, 8).limb("leftLeg", 0, 0, -20, 8);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(3, new Pose().at(0, 2.8f, 0).r("torso", 26, 0, 0).r("head", 24, 0, 0)
                        .limb("rightArm", -60, -40, -10, 120).limb("leftArm", -60, 40, 10, 120)
                        .limb("rightLeg", -6, 0, 10, 60).limb("leftLeg", -6, 0, -10, 60), Ease.OUTQUAD)
                .pose(5, burst, Ease.OUTEXPO)
                .pose(9, burst.copy().limb("rightArm", -20, 0, 112, 4).limb("leftArm", -20, 0, -112, 4), Ease.LINEAR)
                .pose(15, fight(), Ease.INOUTQUAD);
    });

    /** Spot dodge: a sharp sway back and to the side under the blow, guard still up, then back in. */
    public static final KeyframeAnimation SPOT_DODGE = once(11, a -> {
        Pose sway = fight().tilt(-16).move(1.5f, 1.0f, 1.2f).r("torso", -6, -26, 12)
                .limb("rightArm", -96, -34, 4, 90).limb("leftArm", -100, 34, -4, 90)
                .limb("rightLeg", 22, 0, 10, 36).limb("leftLeg", -8, 0, -8, 30);
        a.pose(0, fight(), Ease.LINEAR).pose(2, sway, Ease.OUTEXPO).pose(7, sway, Ease.LINEAR).pose(11, fight(), Ease.INOUTQUAD);
    });

    // ------------------------------------------------------------------ ki

    /** Ki blast: the palm chambered at the hip, thrust out with a half step, a kick of recoil, back to guard. */
    public static final KeyframeAnimation KI_BLAST = once(10, a -> {
        Pose shot = fight().r("torso", 6, 14, 0).limb("rightArm", -92, -6, 0, 0).limb("leftArm", -48, 12, -10, 115)
                .limb("leftLeg", -24, 0, -5, 28).move(0, 0.4f, -1.0f);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(2, fight().r("torso", 2, -30, 0).limb("rightArm", 18, 18, 30, 80).move(0, 0.2f, 0.4f), Ease.OUTQUAD)
                .pose(4, shot, Ease.OUTEXPO)
                .pose(5, shot.copy().limb("rightArm", -80, -6, 2, 10).move(0, 0, 0.7f), Ease.OUTQUAD)
                .pose(10, fight(), Ease.INOUTQUAD);
    });

    /** Volley: planted wide, palms firing in turn, each shot kicking the arm back as the other drives out. */
    public static final KeyframeAnimation KI_VOLLEY = once(20, a -> {
        a.pose(0, fight(), Ease.LINEAR);
        for (int t = 2; t <= 14; t += 4) {
            boolean right = (t / 4) % 2 == 0;
            Pose fire = wide().r("torso", 6, right ? 12 : -12, 0)
                    .limb(right ? "rightArm" : "leftArm", -92, right ? -6 : 6, 0, 0)
                    .limb(right ? "leftArm" : "rightArm", -55, right ? 10 : -10, right ? -14 : 14, 70);
            Pose recoil = fire.copy().limb(right ? "rightArm" : "leftArm", -76, 0, right ? 8 : -8, 30).move(0, 0, 0.3f);
            a.pose(t, fire, Ease.OUTEXPO).pose(t + 2, recoil, Ease.OUTQUAD);
        }
        a.pose(20, fight(), Ease.INOUTQUAD);
    });

    /**
     * Beam: both hands drawn together to the hip with the body coiled and sunk, then thrust out and held, straining
     * against the force while the beam burns.
     */
    public static KeyframeAnimation kiBeam(int holdTicks) {
        int end = Math.max(14, holdTicks + 10);
        return once(end, a -> {
            Pose gather = new Pose().at(0, 2.0f, 0.6f).r("torso", 4, -48, 0)
                    .limb("rightArm", 22, 40, 18, 80).limb("leftArm", 10, 50, 10, 70)
                    .limb("rightLeg", 16, 0, 14, 30).limb("leftLeg", -22, 0, -12, 34);
            Pose fire = new Pose().at(0, 2.0f, -1.4f).r("torso", 10, 8, 0)
                    .limb("rightArm", -90, 12, 0, 0).limb("leftArm", -90, -12, 0, 0)
                    .limb("rightLeg", 26, 0, 12, 4).limb("leftLeg", -30, 0, -12, 38);
            a.pose(0, fight(), Ease.LINEAR).pose(3, gather, Ease.OUTQUAD).pose(5, fire, Ease.OUTEXPO);
            for (int t = 7; t < end - 4; t += 2) a.pose(t, fire.copy().move(t % 4 == 1 ? 0.25f : -0.25f, 0, 0), Ease.LINEAR);   // straining
            a.pose(end, fight(), Ease.INOUTQUAD);
        });
    }

    /** Giant sphere: rising with both arms raised while it forms, a last lean back, then hurled with a step. */
    public static KeyframeAnimation kiThrow(int holdTicks) {
        int end = holdTicks + 12;
        return once(end, a -> {
            Pose raise = new Pose().at(0, -0.5f, 0.6f).r("torso", -12, 0, 0).r("head", -40, 0, 0)
                    .limb("rightArm", -176, 0, -16, 15).limb("leftArm", -176, 0, 16, 15)
                    .limb("rightLeg", 6, 0, 8, 4).limb("leftLeg", -6, 0, -8, 4);
            Pose hurl = fight().at(0, 1.6f, -2.0f).r("torso", 24, 0, 0)
                    .limb("rightArm", -60, 0, -4, 0).limb("leftArm", -60, 0, 4, 0)
                    .limb("leftLeg", -30, 0, -6, 30).limb("rightLeg", 24, 0, 6, 4);
            a.pose(0, fight(), Ease.LINEAR).pose(6, raise, Ease.OUTQUAD).pose(holdTicks, raise, Ease.LINEAR)
                    .pose(holdTicks + 2, raise.copy().r("torso", -20, 0, 0).move(0, 0, 0.6f), Ease.INOUTQUAD)
                    .pose(holdTicks + 4, hurl, Ease.OUTEXPO)
                    .pose(end, fight(), Ease.INOUTQUAD);
        });
    }

    /** Explosive wave: curled around the ki, then arms and chest flung open as it bursts out. */
    public static final KeyframeAnimation KI_WAVE = once(16, a -> {
        Pose open = new Pose().at(0, 0.4f, 0).r("torso", -16, 0, 0).r("head", -26, 0, 0)
                .limb("rightArm", -10, 0, 88, 0).limb("leftArm", -10, 0, -88, 0)
                .limb("rightLeg", 0, 0, 16, 8).limb("leftLeg", 0, 0, -16, 8);
        a.pose(0, fight(), Ease.LINEAR)
                .pose(3, new Pose().at(0, 2.6f, 0).r("torso", 24, 0, 0).limb("rightArm", -50, -40, -6, 110).limb("leftArm", -50, 40, 6, 110)
                        .limb("rightLeg", -4, 0, 10, 50).limb("leftLeg", -4, 0, -10, 50), Ease.OUTQUAD)
                .pose(5, open, Ease.OUTEXPO).pose(11, open, Ease.LINEAR)
                .pose(16, fight(), Ease.INOUTQUAD);
    });

    /** Self technique (heal, sense, absorb...): feet together, two fingers to the brow, head bowed in focus. */
    public static final KeyframeAnimation KI_FOCUS = once(16, a -> {
        Pose focus = new Pose().at(0, 0.3f, 0).r("torso", 4, 0, 0).r("head", 14, 0, 0)
                .limb("rightArm", -150, -40, 0, 100).limb("leftArm", 0, 0, -6, 10);
        a.pose(0, fight(), Ease.LINEAR).pose(5, focus, Ease.OUTQUAD).pose(12, focus.copy().r("head", 16, 0, 0), Ease.LINEAR)
                .pose(16, fight(), Ease.INOUTQUAD);
    });

    // ------------------------------------------------------------------ transformations and reactions

    /**
     * Transformation: hunched and straining with fists clenched at the chest, shaking harder and harder, then the burst:
     * arms torn down and out, chest thrown open, head flung back in a roar, held while the power settles.
     */
    public static final KeyframeAnimation TRANSFORM = once(44, a -> {
        Pose strain = new Pose().at(0, 2.6f, 0).r("torso", 26, 0, 0).r("head", 22, 0, 0)
                .limb("rightArm", -40, -30, 20, 110).limb("leftArm", -40, 30, -20, 110)
                .limb("rightLeg", -4, 0, 14, 40).limb("leftLeg", -4, 0, -14, 40);
        Pose roar = new Pose().at(0, 0.4f, 0).r("torso", -18, 0, 0).r("head", -34, 0, 0)
                .limb("rightArm", 12, 0, 62, 8).limb("leftArm", 12, 0, -62, 8)
                .limb("rightLeg", 0, 0, 20, 6).limb("leftLeg", 0, 0, -20, 6);
        a.pose(0, new Pose().at(0, 1.0f, 0), Ease.LINEAR).pose(6, strain, Ease.OUTQUAD);
        for (int t = 8; t <= 24; t += 2) {
            float s = (t % 4 == 0 ? 1 : -1) * (0.2f + 0.4f * (t - 8) / 16f);             // shaking harder as it builds
            a.pose(t, strain.copy().move(s, 0, 0).r("head", 22, s * 6, 0), Ease.LINEAR);
        }
        a.pose(27, roar, Ease.OUTEXPO)
                .pose(29, roar.copy().r("torso", -20, 0, 0).limb("rightArm", 12, 0, 68, 6).limb("leftArm", 12, 0, -68, 6), Ease.OUTQUAD)
                .pose(38, roar, Ease.INOUTSINE)
                .pose(44, fight(), Ease.INOUTQUAD);
    });

    /** Light hit: the head snaps aside, the torso recoils half a step back, then the guard comes back up. */
    public static final KeyframeAnimation HIT_LIGHT = once(10, a -> {
        Pose hit = new Pose().at(0, 1.6f, 1.2f).r("torso", -14, 8, 0).r("head", -22, 14, 0)
                .limb("rightArm", -40, 0, 30, 60).limb("leftArm", -50, 0, -25, 70)
                .limb("rightLeg", 12, 0, 6, 10).limb("leftLeg", -6, 0, -4, 14);
        a.pose(0, hit, Ease.OUTEXPO).pose(3, hit.copy().r("torso", -6, 4, 0).r("head", -8, 6, 0), Ease.OUTQUAD).pose(10, fight(), Ease.INOUTQUAD);
    });

    /** Heavy hit: snapped back off the feet, arms flung, then a stagger forward before the guard returns. */
    public static final KeyframeAnimation HIT_HEAVY = once(18, a -> {
        Pose blown = new Pose().at(0, 1.0f, 2.8f).r("torso", -32, -10, 0).r("head", -42, -14, 0)
                .limb("rightArm", -120, 0, 40, 20).limb("leftArm", -110, 0, -45, 20)
                .limb("rightLeg", -24, 0, 8, 0).limb("leftLeg", 16, 0, -8, 30);
        Pose stagger = new Pose().at(0, 2.2f, 1.0f).r("torso", 18, 6, 0).r("head", 10, 0, 0)
                .limb("rightArm", 10, 0, 12, 20).limb("leftArm", 10, 0, -12, 20)
                .limb("rightLeg", 10, 0, 6, 35).limb("leftLeg", -14, 0, -6, 35);
        a.pose(0, blown, Ease.OUTEXPO).pose(4, blown.copy().r("torso", -36, -10, 0).move(0, 0.4f, 0.4f), Ease.OUTQUAD)
                .pose(8, stagger, Ease.INOUTQUAD).pose(12, stagger.copy().r("torso", 10, 4, 0), Ease.INOUTQUAD)
                .pose(18, fight(), Ease.INOUTQUAD);
    });

    /** Dazed (CX-19e): hunched over, arms slack, swaying on unsteady legs while a stun lasts. */
    public static final KeyframeAnimation STUNNED = loop(24, a -> {
        Pose p = new Pose().at(0, 1.4f, 0).r("torso", 14, 0, 0).r("head", 24, 0, 0)
                .limb("rightArm", 8, 0, 10, 15).limb("leftArm", 6, 0, -10, 15)
                .limb("rightLeg", -10, 0, 4, 22).limb("leftLeg", -6, 0, -4, 18);
        Pose left = p.copy().r("torso", 14, 0, -6).r("head", 26, 8, -10), right = p.copy().r("torso", 12, 0, 6).r("head", 22, -8, 10);
        a.pose(0, left, Ease.INOUTSINE).pose(12, right, Ease.INOUTSINE).pose(24, left, Ease.INOUTSINE);
    });

    /** Launched (CX-19e): thrown through the air, back arched, arms and legs trailing behind. */
    public static final KeyframeAnimation LAUNCHED = loop(16, a -> {
        Pose p = new Pose().at(0, 1.0f, 1.5f).r("torso", -38, 0, 0).r("head", -30, 0, 0)
                .limb("rightArm", -150, 0, 30, 15).limb("leftArm", -140, 0, -35, 15)
                .limb("rightLeg", 28, 0, 10, 25).limb("leftLeg", 12, 0, -10, 40);
        a.pose(0, p, Ease.INOUTSINE)
                .pose(8, p.copy().r("torso", -44, 0, 4).limb("rightArm", -160, 0, 38, 10).limb("leftLeg", 20, 0, -12, 55), Ease.INOUTSINE)
                .pose(16, p, Ease.INOUTSINE);
    });
    /** Just fought: the fighting stance with a light bounce on the balls of the feet. Head and legs stay free to look and walk. */
    public static final KeyframeAnimation COMBAT_STANCE = loop(24, a -> {
        Pose s = fight().freeing("head", "rightLeg", "leftLeg").at(0, 0.5f, 0);
        a.pose(0, s, Ease.INOUTSINE)
                .pose(12, s.copy().move(0, 0.45f, 0).limb("rightArm", -33, -8, 13, 115).limb("leftArm", -56, 10, -9, 90).r("torso", 7, -16, 0), Ease.INOUTSINE)
                .pose(24, s, Ease.INOUTSINE);
    });

    /** Standing still: a slow breath through the chest and shoulders. */
    public static final KeyframeAnimation IDLE_BREATHE = loop(60, a -> {
        a.rot("torso", 0, 0, 0, 0, Ease.INOUTSINE).rot("torso", 30, 2.5f, 0, 0, Ease.INOUTSINE).rot("torso", 60, 0, 0, 0, Ease.INOUTSINE);
        a.rot("rightArm", 0, 0, 0, 3, Ease.INOUTSINE).rot("rightArm", 30, -3, 0, 6, Ease.INOUTSINE).rot("rightArm", 60, 0, 0, 3, Ease.INOUTSINE);
        a.rot("leftArm", 0, 0, 0, -3, Ease.INOUTSINE).rot("leftArm", 30, -3, 0, -6, Ease.INOUTSINE).rot("leftArm", 60, 0, 0, -3, Ease.INOUTSINE);
        a.pos("body", 0, 0, 0, 0, Ease.INOUTSINE).pos("body", 30, 0, -0.25f, 0, Ease.INOUTSINE).pos("body", 60, 0, 0, 0, Ease.INOUTSINE);
    });

    /** Powering down: a long exhale. */
    public static final KeyframeAnimation POWER_DOWN = once(20, a -> {
        a.rot("torso", 0, 0, 0, 0, Ease.INOUTSINE).rot("torso", 8, 12, 0, 0, Ease.INOUTSINE).rot("torso", 20, 0, 0, 0, Ease.INOUTSINE);
        a.rot("head", 0, 0, 0, 0, Ease.INOUTSINE).rot("head", 8, 14, 0, 0, Ease.INOUTSINE).rot("head", 20, 0, 0, 0, Ease.INOUTSINE);
        a.rot("rightArm", 0, 0, 0, 5, Ease.INOUTSINE).rot("rightArm", 8, 8, 0, 2, Ease.INOUTSINE).rot("rightArm", 20, 0, 0, 5, Ease.INOUTSINE);
        a.rot("leftArm", 0, 0, 0, -5, Ease.INOUTSINE).rot("leftArm", 8, 8, 0, -2, Ease.INOUTSINE).rot("leftArm", 20, 0, 0, -5, Ease.INOUTSINE);
    });

    /** Dash: a burst lean with the arms swept back. */
    public static final KeyframeAnimation DASH = once(8, a -> {
        a.rot("torso", 0, 22, 0, 0, Ease.OUTEXPO).rot("torso", 8, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 45, 0, 18, Ease.OUTEXPO).rot("rightArm", 8, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 45, 0, -18, Ease.OUTEXPO).rot("leftArm", 8, 0, 0, -5, Ease.INOUTQUAD);
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

    public static final KeyframeAnimation SIDE_LEFT = sideStep(-1), SIDE_RIGHT = sideStep(1);

    /** Side step: a hop to one side ({@code side} -1 left, 1 right), the body leaning into it. */
    private static KeyframeAnimation sideStep(int side) {
        return once(8, a -> {
            a.rot("body", 0, 0, 0, 0, Ease.LINEAR).rot("body", 2, 0, 0, -28 * side, Ease.OUTEXPO).rot("body", 8, 0, 0, 0, Ease.INOUTQUAD);
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

    /** Namekian idle: arms folded across the chest, weight on one leg, a slow breath. The head stays free. */
    public static final KeyframeAnimation IDLE_CROSSED = loop(60, a -> {
        Pose p = new Pose().freeing("head").r("torso", -2, 0, 0)
                .limb("rightArm", -72, -48, 0, 112).limb("leftArm", -66, 52, 0, 108)
                .limb("rightLeg", 0, 0, 3, 0).limb("leftLeg", -4, 0, -4, 10).move(0.3f, 0, 0);
        a.pose(0, p, Ease.INOUTSINE).pose(30, p.copy().r("torso", 0, 0, 0).move(0, -0.25f, 0), Ease.INOUTSINE).pose(60, p, Ease.INOUTSINE);
    });

    /** Regal idle (Frost Demons, Core People, machines): hands clasped behind the back, chest out, perfectly still but for breath. */
    public static final KeyframeAnimation IDLE_REGAL = loop(60, a -> {
        Pose p = new Pose().freeing("head").r("torso", -5, 0, 0)
                .limb("rightArm", 24, 28, 6, 70).limb("leftArm", 24, -28, -6, 70)
                .limb("rightLeg", 0, 0, 2, 0).limb("leftLeg", 0, 0, -2, 0);
        a.pose(0, p, Ease.INOUTSINE).pose(30, p.copy().r("torso", -3, 0, 0).move(0, -0.2f, 0), Ease.INOUTSINE).pose(60, p, Ease.INOUTSINE);
    });

    /** Loose idle (Majin, Vampires, Bio-Androids, Gen Aliens): shoulders rolled forward, arms hanging loose, a lazy sway. */
    public static final KeyframeAnimation IDLE_LOOSE = loop(48, a -> {
        Pose p = new Pose().freeing("head").r("torso", 10, 0, 0).move(0, 0.8f, 0)
                .limb("rightArm", -14, 0, 12, 20).limb("leftArm", -14, 0, -12, 20)
                .limb("rightLeg", -2, 0, 6, 12).limb("leftLeg", -2, 0, -6, 12);
        a.pose(0, p.copy().r("torso", 10, 0, 4).move(-0.4f, 0, 0), Ease.INOUTSINE)
                .pose(24, p.copy().r("torso", 10, 0, -4).move(0.4f, 0, 0), Ease.INOUTSINE)
                .pose(48, p.copy().r("torso", 10, 0, 4).move(-0.4f, 0, 0), Ease.INOUTSINE);
    });

    /** The idle that fits a race. */
    public static KeyframeAnimation idleFor(com.dbzenith.stats.Race race) {
        if (race == null) return IDLE_BREATHE;
        return switch (race) {
            case NAMEKIAN -> IDLE_CROSSED;
            case FROST_DEMON, CORE_PERSON, ANDROID, CYBORG, TUFFLE -> IDLE_REGAL;
            case MAJIN, VAMPIRE, BIO_ANDROID, GEN_ALIEN -> IDLE_LOOSE;
            default -> IDLE_BREATHE;
        };
    }

    /** Sprinting flat out: leaning in, arms trailing straight back. The legs keep vanilla's stride. */
    public static final KeyframeAnimation SPRINT = loop(12, a -> {
        a.rot("torso", 0, 20, 0, 0, Ease.INOUTSINE).rot("torso", 6, 23, 0, 0, Ease.INOUTSINE).rot("torso", 12, 20, 0, 0, Ease.INOUTSINE);
        a.rot("rightArm", 0, 58, 0, 14, Ease.INOUTSINE).rot("rightArm", 6, 64, 0, 16, Ease.INOUTSINE).rot("rightArm", 12, 58, 0, 14, Ease.INOUTSINE);
        a.rot("leftArm", 0, 58, 0, -14, Ease.INOUTSINE).rot("leftArm", 6, 64, 0, -16, Ease.INOUTSINE).rot("leftArm", 12, 58, 0, -14, Ease.INOUTSINE);
        a.bend("rightArm", 0, 8, Ease.LINEAR).bend("leftArm", 0, 8, Ease.LINEAR);
        a.rot("head", 0, -16, 0, 0, Ease.LINEAR);
    });

    /** Flying straight up: arms down at the sides, legs together, looking up at where you are going. */
    public static final KeyframeAnimation FLY_ASCEND = loop(20, a -> {
        a.rot("rightArm", 0, 12, 0, 14, Ease.INOUTSINE).rot("rightArm", 10, 16, 0, 18, Ease.INOUTSINE).rot("rightArm", 20, 12, 0, 14, Ease.INOUTSINE);
        a.rot("leftArm", 0, 12, 0, -14, Ease.INOUTSINE).rot("leftArm", 10, 16, 0, -18, Ease.INOUTSINE).rot("leftArm", 20, 12, 0, -14, Ease.INOUTSINE);
        a.rot("rightLeg", 0, 6, 0, -2, Ease.LINEAR).rot("leftLeg", 0, 6, 0, 2, Ease.LINEAR);
        a.rot("torso", 0, -6, 0, 0, Ease.LINEAR);
        a.rot("head", 0, -22, 0, 0, Ease.LINEAR);
    });

    /** Dropping down: knees drawn up to land, arms out for balance. */
    public static final KeyframeAnimation FLY_DESCEND = loop(20, a -> {
        a.rot("rightArm", 0, -20, 0, 38, Ease.INOUTSINE).rot("rightArm", 10, -24, 0, 44, Ease.INOUTSINE).rot("rightArm", 20, -20, 0, 38, Ease.INOUTSINE);
        a.rot("leftArm", 0, -20, 0, -38, Ease.INOUTSINE).rot("leftArm", 10, -24, 0, -44, Ease.INOUTSINE).rot("leftArm", 20, -20, 0, -38, Ease.INOUTSINE);
        a.rot("rightLeg", 0, -24, 0, 4, Ease.LINEAR).bend("rightLeg", 0, 40, Ease.LINEAR);
        a.rot("leftLeg", 0, -10, 0, -4, Ease.LINEAR).bend("leftLeg", 0, 30, Ease.LINEAR);
        a.rot("torso", 0, 8, 0, 0, Ease.LINEAR);
        a.rot("head", 0, 18, 0, 0, Ease.LINEAR);
    });

    /** Getting up off the floor: sit up on one hand, knee under, stand. */
    public static final KeyframeAnimation GET_UP = once(16, a -> {
        a.tilt(0, -88, 9.5f, Ease.LINEAR).tilt(5, -45, 7.5f, Ease.OUTQUAD).tilt(10, 25, 5f, Ease.INOUTQUAD).tilt(16, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, -10, 0, 40, Ease.LINEAR).rot("rightArm", 5, 30, 0, 30, Ease.OUTQUAD).rot("rightArm", 16, 0, 0, 5, Ease.INOUTQUAD);
        a.bend("rightLeg", 0, 30, Ease.LINEAR).bend("rightLeg", 8, 110, Ease.OUTQUAD).bend("rightLeg", 16, 0, Ease.INOUTQUAD);
        a.bend("leftLeg", 0, 0, Ease.LINEAR).bend("leftLeg", 8, 70, Ease.OUTQUAD).bend("leftLeg", 16, 0, Ease.INOUTQUAD);
    });

    /** Victory: a fist thrust at the sky, the other on the hip, a small hop. */
    public static final KeyframeAnimation VICTORY = once(40, a -> {
        a.rot("rightArm", 0, 0, 0, 5, Ease.LINEAR).rot("rightArm", 6, -172, -10, -6, Ease.OUTEXPO).rot("rightArm", 30, -168, -10, -6, Ease.LINEAR).rot("rightArm", 40, 0, 0, 5, Ease.INOUTQUAD);
        a.bend("rightArm", 0, 0, Ease.LINEAR).bend("rightArm", 6, 15, Ease.OUTEXPO).bend("rightArm", 40, 0, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -5, Ease.LINEAR).rot("leftArm", 6, 15, 0, -42, Ease.OUTQUAD).rot("leftArm", 34, 15, 0, -42, Ease.LINEAR).rot("leftArm", 40, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("leftArm", 6, 80, Ease.OUTQUAD).bend("leftArm", 34, 80, Ease.LINEAR).bend("leftArm", 40, 0, Ease.INOUTQUAD);
        a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 6, -20, 0, 0, Ease.OUTQUAD).rot("head", 34, -18, 0, 0, Ease.LINEAR).rot("head", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("torso", 0, 0, 0, 0, Ease.LINEAR).rot("torso", 6, -8, 0, 0, Ease.OUTQUAD).rot("torso", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 4, 0, 1.5f, 0, Ease.OUTQUAD).pos("body", 8, 0, -3f, 0, Ease.OUTQUAD)
                .pos("body", 13, 0, 0, 0, Ease.INQUAD).pos("body", 40, 0, 0, 0, Ease.LINEAR);
    });

    /** A regal transformation (Frost Demons, Core People, machines): rising calmly, arms opening wide, chin up. */
    public static final KeyframeAnimation TRANSFORM_REGAL = once(40, a -> {
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 20, 0, -2.5f, 0, Ease.INOUTSINE).pos("body", 34, 0, -3f, 0, Ease.LINEAR).pos("body", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 0, 0, 5, Ease.LINEAR).rot("rightArm", 20, -30, 0, 80, Ease.INOUTSINE).rot("rightArm", 34, -30, 0, 85, Ease.LINEAR).rot("rightArm", 40, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -5, Ease.LINEAR).rot("leftArm", 20, -30, 0, -80, Ease.INOUTSINE).rot("leftArm", 34, -30, 0, -85, Ease.LINEAR).rot("leftArm", 40, 0, 0, -5, Ease.INOUTQUAD);
        a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 20, -20, 0, 0, Ease.INOUTSINE).rot("head", 34, -22, 0, 0, Ease.LINEAR).rot("head", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("rightLeg", 20, 0, 0, 6, Ease.INOUTSINE).rot("rightLeg", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.rot("leftLeg", 20, 0, 0, -6, Ease.INOUTSINE).rot("leftLeg", 40, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** A calm transformation (Namekians): arms crossed, head bowed, then the eyes come up. */
    public static final KeyframeAnimation TRANSFORM_CALM = once(40, a -> {
        a.rot("rightArm", 0, 0, 0, 5, Ease.LINEAR).rot("rightArm", 8, -70, 40, 0, Ease.OUTQUAD).rot("rightArm", 32, -70, 40, 0, Ease.LINEAR).rot("rightArm", 40, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -5, Ease.LINEAR).rot("leftArm", 8, -76, -40, 0, Ease.OUTQUAD).rot("leftArm", 32, -76, -40, 0, Ease.LINEAR).rot("leftArm", 40, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("rightArm", 8, 90, Ease.OUTQUAD).bend("rightArm", 40, 0, Ease.INOUTQUAD).bend("leftArm", 8, 90, Ease.OUTQUAD).bend("leftArm", 40, 0, Ease.INOUTQUAD);
        a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 8, 28, 0, 0, Ease.OUTQUAD).rot("head", 26, 28, 0, 0, Ease.LINEAR)
                .rot("head", 30, -6, 0, 0, Ease.OUTEXPO).rot("head", 40, 0, 0, 0, Ease.INOUTQUAD);
        for (int t = 10; t <= 26; t += 4) a.pos("body", t, (t % 8 == 2 ? 0.15f : -0.15f), 0.3f, 0, Ease.LINEAR);
        a.pos("body", 0, 0, 0, 0, Ease.LINEAR).pos("body", 40, 0, 0, 0, Ease.INOUTQUAD);
    });

    /** A feral transformation (Majin, Vampires, Bio-Androids, Gen Aliens): hunched low, clawed hands, the head thrashing. */
    public static final KeyframeAnimation TRANSFORM_FERAL = once(40, a -> {
        a.tilt(0, 0, 0, Ease.LINEAR).tilt(8, 32, 2.5f, Ease.OUTQUAD).tilt(28, 34, 2.5f, Ease.LINEAR).tilt(34, -12, 0, Ease.OUTEXPO).tilt(40, 0, 0, Ease.INOUTQUAD);
        a.rot("rightArm", 0, 0, 0, 5, Ease.LINEAR).rot("rightArm", 8, -50, 0, 40, Ease.OUTQUAD).rot("rightArm", 28, -55, 0, 45, Ease.LINEAR).rot("rightArm", 34, -10, 0, 75, Ease.OUTEXPO).rot("rightArm", 40, 0, 0, 5, Ease.INOUTQUAD);
        a.rot("leftArm", 0, 0, 0, -5, Ease.LINEAR).rot("leftArm", 8, -50, 0, -40, Ease.OUTQUAD).rot("leftArm", 28, -55, 0, -45, Ease.LINEAR).rot("leftArm", 34, -10, 0, -75, Ease.OUTEXPO).rot("leftArm", 40, 0, 0, -5, Ease.INOUTQUAD);
        a.bend("rightArm", 8, 70, Ease.OUTQUAD).bend("rightArm", 40, 0, Ease.INOUTQUAD).bend("leftArm", 8, 70, Ease.OUTQUAD).bend("leftArm", 40, 0, Ease.INOUTQUAD);
        for (int t = 10; t <= 28; t += 3) a.rot("head", t, 10, (t % 6 == 1 ? 25 : -25), 0, Ease.LINEAR);   // thrashing
        a.rot("head", 0, 0, 0, 0, Ease.LINEAR).rot("head", 34, -30, 0, 0, Ease.OUTEXPO).rot("head", 40, 0, 0, 0, Ease.INOUTQUAD);
        a.bend("rightLeg", 8, 45, Ease.OUTQUAD).bend("rightLeg", 40, 0, Ease.INOUTQUAD).bend("leftLeg", 8, 45, Ease.OUTQUAD).bend("leftLeg", 40, 0, Ease.INOUTQUAD);
    });

    /** The transformation flourish that fits a race. */
    public static KeyframeAnimation transformFor(com.dbzenith.stats.Race race) {
        return switch (race) {
            case FROST_DEMON, CORE_PERSON, ANDROID, CYBORG, TUFFLE -> TRANSFORM_REGAL;
            case NAMEKIAN -> TRANSFORM_CALM;
            case MAJIN, VAMPIRE, BIO_ANDROID, GEN_ALIEN -> TRANSFORM_FERAL;
            default -> TRANSFORM;
        };
    }
    // ------------------------------------------------------------------ fusion (12c)


    /** Body offsets in pixels: the model's x for "to the figure's right" (playerAnimator body space). */
    static final float RIGHT_X = 1f;
    /** playerAnimator turns the whole figure about a point this many pixels above the feet. */
    static final float BODY_PIVOT = 11.2f;

    /**
     * The whole figure leaning sideways ({@code lean} degrees, + tips the top to the figure's right) with the feet kept
     * where they were, then shifted {@code right} and {@code down} pixels.
     */
    private static void body(Keys a, int t, float lean, float right, float down, Ease e) {
        double r = Math.toRadians(lean);
        float compRight = (float) (BODY_PIVOT * Math.sin(r)), compDown = (float) (BODY_PIVOT * (1 - Math.cos(r)));
        a.rot("body", t, 0, 0, -lean, e);                                // playerAnimator's body roll tips the top to the left
        a.pos("body", t, RIGHT_X * (right + compRight), down + compDown, 0, e);
    }

    private static void part(Keys a, String part, int t, float pitch, float yaw, float roll, float bend, Ease e) {
        a.rot(part, t, pitch, yaw, roll, e);
        if (!part.equals("head") && !part.equals("torso")) a.bend(part, t, bend, e);
    }

    /**
     * The Fusion Dance, the half danced on the left (the partner on your right; the other half is this mirrored).
     * Ticks follow {@link com.dbzenith.fusion.FusionDance}: the beats FU, SION and HA land on 30, 45 and 58.
     * <ol>
     * <li>Ready (0-7): standing three steps out, both arms swing out straight to the side away from the partner.</li>
     * <li>FU (10-30): three shuffling side-steps in, the inside foot leading, while both arms sweep up over the head in
     * one arc and come down pointing at the partner.</li>
     * <li>SION (30-45): the arms sweep back over to the outside, and the inside knee comes up high.</li>
     * <li>HA (45-58): a dip, then the lean in: the inside arm straight out so the fingertips meet the partner's, the
     * outside arm arched over the head, the inside knee bent under the weight and the outside leg stretched out
     * straight, one line from the outside foot to the fingertip. Held, trembling, until the light (66).</li>
     * </ol>
     */
    private static void fusionDance(Keys a) {
        int[] beat = com.dbzenith.fusion.FusionDance.BEATS;
        int fu = beat[0], sion = beat[1], ha = beat[2], end = com.dbzenith.fusion.FusionDance.LENGTH;
        float out = 24f;                                                   // three 8-pixel steps out from where they end
        Ease io = Ease.INOUTSINE, oq = Ease.OUTQUAD;

        // --- the body: steps in, then the lean
        body(a, 0, 0, -out, 0, Ease.LINEAR);
        body(a, 7, -3, -out, 0.4f, io);
        body(a, 10, -3, -out, 0.4f, io);
        int[] stepAt = {11, 17, 23};
        for (int s = 0; s < 3; s++) {
            int t0 = stepAt[s];
            float o = -out + 8 * s;
            body(a, t0 + 2, -1 + s * 1.5f, o + 2, 0.6f, oq);
            body(a, t0 + 4, 0 + s * 1.5f, o + 4.5f, 0.9f, io);
            body(a, t0 + 6, 1 + s * 1.5f, o + 8, 0.3f, io);
        }
        body(a, fu, 6, 0, 0.5f, oq);
        body(a, sion - 6, -2, 0, 0.6f, io);
        body(a, sion, -8, -0.5f, 0, oq);
        body(a, ha - 9, -4, 0, 1.6f, io);                                 // the dip before the lean
        body(a, ha - 3, 26, 1.5f, 0.6f, Ease.OUTBACK);
        body(a, ha, 24, 1.2f, 0.6f, io);
        for (int t = ha + 2; t <= end; t += 2) body(a, t, 24 + ((t / 2) % 2 == 0 ? 0.6f : -0.6f), 1.2f, 0.6f, Ease.LINEAR);

        // --- the arms: out, the arc over to the partner, back over, then the touch
        part(a, "leftArm", 0, 0, 0, -4, 0, Ease.LINEAR);
        part(a, "rightArm", 0, 0, 0, 4, 0, Ease.LINEAR);
        part(a, "leftArm", 7, 0, 0, -88, 0, oq);                          // out to the side, away from the partner
        part(a, "rightArm", 7, -38, 0, -88, 10, oq);                      // across the chest, the same way
        part(a, "leftArm", 10, 0, 0, -92, 0, io);
        part(a, "rightArm", 10, -38, 0, -92, 10, io);
        part(a, "leftArm", 20, -12, 0, -180, 0, io);                      // over the head...
        part(a, "rightArm", 20, -12, 0, -180, 0, io);
        part(a, "leftArm", fu, -38, 0, -268, 10, oq);                     // ...and down, pointing at the partner
        part(a, "rightArm", fu, 0, 0, -268, 0, oq);
        part(a, "leftArm", fu + 3, -38, 0, -264, 10, io);
        part(a, "rightArm", fu + 3, 0, 0, -264, 0, io);
        part(a, "leftArm", sion - 7, -10, 0, -180, 0, io);                // back over the head...
        part(a, "rightArm", sion - 7, -10, 0, -180, 0, io);
        part(a, "leftArm", sion, 0, 0, -102, 0, oq);                      // ...and out to the far side again
        part(a, "rightArm", sion, -38, 0, -102, 12, oq);
        part(a, "leftArm", sion + 2, 0, 0, -98, 0, io);
        part(a, "rightArm", sion + 2, -38, 0, -98, 12, io);
        part(a, "leftArm", ha - 9, -40, 0, 20, 40, io);                   // gathered in, crossed low, for the lean
        part(a, "rightArm", ha - 9, -32, 0, -16, 36, io);
        part(a, "leftArm", ha - 3, 0, -90, -150, 58, Ease.OUTBACK);       // arched over the head (turned so the elbow bends sideways)
        part(a, "rightArm", ha - 3, 0, 0, 116, 0, Ease.OUTBACK);           // straight out to the fingertips
        part(a, "leftArm", ha, 0, -90, -146, 55, io);
        part(a, "rightArm", ha, 0, 0, 113, 0, io);
        part(a, "leftArm", end, 0, -90, -146, 55, Ease.LINEAR);
        part(a, "rightArm", end, 0, 0, 113, 0, Ease.LINEAR);

        // --- the legs: three side-steps, the knee, the long line
        part(a, "rightLeg", 0, 0, 0, 1, 0, Ease.LINEAR);
        part(a, "leftLeg", 0, 0, 0, -1, 0, Ease.LINEAR);
        part(a, "rightLeg", 7, 0, 0, 1, 8, io);
        part(a, "leftLeg", 7, 0, 0, -1, 8, io);
        for (int s = 0; s < 3; s++) {
            int t0 = stepAt[s];
            part(a, "rightLeg", t0, 0, 0, 1, 8, io);
            part(a, "leftLeg", t0, 0, 0, -1, 8, io);
            part(a, "rightLeg", t0 + 2, -14, 0, 20, 30, oq);              // the inside foot lifts and reaches
            part(a, "leftLeg", t0 + 2, 0, 0, -4, 12, oq);
            part(a, "rightLeg", t0 + 4, 0, 0, 15, 6, io);                 // planted wide
            part(a, "leftLeg", t0 + 4, 0, 0, -15, 6, io);
            part(a, "leftLeg", t0 + 5, -6, 0, -8, 18, io);                // the outside foot drawn in after it
            part(a, "rightLeg", t0 + 6, 0, 0, 1, 8, io);
            part(a, "leftLeg", t0 + 6, 0, 0, -1, 8, io);
        }
        part(a, "rightLeg", fu, 0, 0, 2, 10, io);
        part(a, "leftLeg", fu, 0, 0, -2, 10, io);
        part(a, "rightLeg", sion - 6, -20, 0, 8, 30, io);
        part(a, "leftLeg", sion - 6, 0, 0, -3, 12, io);
        part(a, "rightLeg", sion, -72, 0, 40, 100, Ease.OUTBACK);        // the inside knee up high
        part(a, "leftLeg", sion, 0, 0, 4, 10, oq);
        part(a, "rightLeg", sion + 3, -68, 0, 38, 96, io);
        part(a, "leftLeg", sion + 3, 0, 0, 4, 10, io);
        part(a, "rightLeg", ha - 9, -10, 0, 10, 40, io);                  // down for the dip
        part(a, "leftLeg", ha - 9, 0, 0, -6, 34, io);
        part(a, "rightLeg", ha - 3, -8, 0, 22, 34, Ease.OUTBACK);         // the inside knee takes the weight
        part(a, "leftLeg", ha - 3, 0, 0, -14, 0, Ease.OUTBACK);           // the outside leg stretched out straight
        part(a, "rightLeg", ha, -8, 0, 20, 32, io);
        part(a, "leftLeg", ha, 0, 0, -12, 0, io);
        part(a, "rightLeg", end, -8, 0, 20, 32, Ease.LINEAR);
        part(a, "leftLeg", end, 0, 0, -12, 0, Ease.LINEAR);

        // --- torso and head: following the arms, eyes front, then on the fingertips
        part(a, "torso", 0, 0, 0, 0, 0, Ease.LINEAR);
        part(a, "torso", 7, 0, -6, 0, 0, io);
        part(a, "torso", fu, 0, 8, 0, 0, io);
        part(a, "torso", sion, 4, -10, 0, 0, io);
        part(a, "torso", ha - 9, 10, 0, 0, 0, io);
        part(a, "torso", ha, 0, 4, 0, 0, io);
        part(a, "torso", end, 0, 4, 0, 0, Ease.LINEAR);
        part(a, "head", 0, 0, 0, 0, 0, Ease.LINEAR);
        part(a, "head", 7, 0, 6, 2, 0, io);
        part(a, "head", fu, -4, -10, -4, 0, io);
        part(a, "head", sion, 0, 10, 6, 0, io);
        part(a, "head", ha - 9, 8, 0, 0, 0, io);
        part(a, "head", ha, -4, -14, 14, 0, io);
        part(a, "head", end, -4, -14, 14, 0, Ease.LINEAR);
    }

    /** The Fusion Dance: the half on the left (the partner on your right). */
    public static final KeyframeAnimation FUSION_DANCE_A = once(com.dbzenith.fusion.FusionDance.LENGTH, Anims::fusionDance);
    /** The Fusion Dance: the half on the right, the mirror image. */
    public static final KeyframeAnimation FUSION_DANCE_B = onceMirrored(com.dbzenith.fusion.FusionDance.LENGTH, Anims::fusionDance);

    /**
     * The Potara, the same for both (they face each other): a hand to the ear to clip the earring on, the jolt as it
     * catches, then dragged forward by the ear, the head leading, arms and legs trailing and flailing, until they meet.
     */
    public static final KeyframeAnimation POTARA = once(com.dbzenith.fusion.FusionDance.POTARA_LENGTH, a -> {
        int pull = com.dbzenith.fusion.FusionDance.POTARA_PULL, end = com.dbzenith.fusion.FusionDance.POTARA_LENGTH;
        Ease io = Ease.INOUTSINE;
        Pose stand = new Pose().r("head", 0, 0, 0).limb("rightArm", 0, 0, 5, 0).limb("leftArm", 0, 0, -5, 0)
                .limb("rightLeg", 0, 0, 1, 0).limb("leftLeg", 0, 0, -1, 0);
        Pose clip = stand.copy().r("head", 0, 0, -10).limb("rightArm", -150, 10, 38, 128).limb("leftArm", -10, 0, -12, 20);
        Pose jolt = clip.copy().r("head", -14, 0, -18).limb("leftArm", -30, 0, -40, 20).move(0, -0.6f, 0.4f);
        a.pose(0, stand, Ease.LINEAR).pose(4, clip, Ease.OUTQUAD).pose(6, clip, io).pose(pull - 1, jolt, Ease.OUTEXPO);
        for (int t = pull + 2, i = 0; t <= end; t += 3, i++) {
            float s = i % 2 == 0 ? 1 : -1;
            Pose dragged = stand.copy().tilt(38).r("head", -40, 0, s * 4).r("torso", 0, s * 6, 0)
                    .limb("rightArm", 40 + 14 * s, 0, 34, 30).limb("leftArm", 40 - 14 * s, 0, -34, 30)
                    .limb("rightLeg", 42 + 12 * s, 0, 8, 34 + 12 * s).limb("leftLeg", 42 - 12 * s, 0, -8, 34 - 12 * s);
            a.pose(t, dragged, i == 0 ? Ease.OUTQUAD : io);
        }
    });

    /**
     * The fused warrior steps out of the light: crouched behind crossed arms, then flung open (fists down and out,
     * chest up, a shout) with the power shaking through, and settling into a cocky stance.
     */
    public static final KeyframeAnimation FUSED_ENTRANCE = once(34, a -> {
        Pose crouch = new Pose().at(0, 4.5f, 0).r("torso", 18, 0, 0).r("head", 20, 0, 0)
                .limb("rightArm", -100, -42, 0, 70).limb("leftArm", -100, 42, 0, 70)
                .limb("rightLeg", -30, 0, 10, 70).limb("leftLeg", -30, 0, -10, 70);
        Pose burst = new Pose().at(0, -0.6f, 0).r("torso", -14, 0, 0).r("head", -26, 0, 0)
                .limb("rightArm", 14, 0, 56, 6).limb("leftArm", 14, 0, -56, 6)
                .limb("rightLeg", 0, 0, 16, 4).limb("leftLeg", 0, 0, -16, 4);
        Pose cocky = new Pose().at(0, 0.4f, 0).r("torso", 0, -12, 0).r("head", -4, 10, 4)
                .limb("rightArm", -60, -10, 14, 120).limb("leftArm", -6, 0, -26, 82)
                .limb("rightLeg", 6, 0, 6, 8).limb("leftLeg", -10, 0, -10, 12);
        a.pose(0, crouch, Ease.LINEAR).pose(6, crouch.copy().move(0, 0.4f, 0), Ease.LINEAR).pose(9, burst, Ease.OUTEXPO);
        for (int t = 11; t <= 21; t += 2) a.pose(t, burst.copy().move(t % 4 == 1 ? 0.25f : -0.25f, 0, 0), Ease.LINEAR);
        a.pose(28, cocky, Ease.INOUTQUAD).pose(34, cocky, Ease.LINEAR);
    });

    /** A botched fusion, the fat way: plopped down wide-legged, belly out, hands patting it, a wobble. */
    public static final KeyframeAnimation FUSED_FAT = once(36, a -> {
        Pose plop = new Pose().at(0, 3.5f, 0).r("torso", -14, 0, 0).r("head", -6, 0, 0)
                .limb("rightArm", -10, 0, 40, 30).limb("leftArm", -10, 0, -40, 30)
                .limb("rightLeg", -10, 0, 22, 40).limb("leftLeg", -10, 0, -22, 40);
        Pose pat = plop.copy().at(0, 1.5f, 0).limb("rightArm", -40, -30, 10, 70).limb("leftArm", -40, 30, -10, 70);
        a.pose(0, plop.copy().move(0, -1.5f, 0), Ease.LINEAR).pose(4, plop, Ease.OUTBOUNCE);
        for (int t = 10, i = 0; t <= 28; t += 3, i++) a.pose(t, pat.copy().move(0, i % 2 == 0 ? 0.6f : 0, 0).r("head", -6, 0, i % 2 == 0 ? 6 : -6), Ease.INOUTSINE);
        a.pose(36, pat, Ease.INOUTSINE);
    });

    /** A botched fusion, the thin way: the knees buckle, hunched over, arms dangling, a hacking cough or two. */
    public static final KeyframeAnimation FUSED_THIN = once(40, a -> {
        Pose slump = new Pose().at(0, 2.2f, 0).tilt(6).r("torso", 26, 0, 0).r("head", 18, 0, 0)
                .limb("rightArm", -14, 0, 4, 12).limb("leftArm", -14, 0, -4, 12)
                .limb("rightLeg", -8, 0, -4, 30).limb("leftLeg", -8, 0, 4, 30);
        Pose cough = slump.copy().r("torso", 36, 0, 0).r("head", 30, 0, 0).limb("rightArm", -70, -20, 0, 100).move(0, 0.6f, 0);
        a.pose(0, new Pose().r("head", 0, 0, 0), Ease.LINEAR).pose(8, slump, Ease.OUTBOUNCE)
                .pose(14, cough, Ease.OUTQUAD).pose(17, slump, Ease.INQUAD).pose(21, cough, Ease.OUTQUAD).pose(24, slump, Ease.INQUAD)
                .pose(40, slump, Ease.LINEAR);
    });

    // ------------------------------------------------------------------ builder

    private static KeyframeAnimation loop(int length, Consumer<Keys> body) {
        return build(length, true, body);
    }

    private static KeyframeAnimation once(int length, Consumer<Keys> body) {
        return build(length, false, body);
    }

    private static KeyframeAnimation build(int length, boolean loop, Consumer<Keys> body) {
        return build(length, loop, false, body);
    }

    /** The same choreography with left and right swapped (the partner's half of a duet). */
    private static KeyframeAnimation onceMirrored(int length, Consumer<Keys> body) {
        return build(length, false, true, body);
    }

    private static KeyframeAnimation build(int length, boolean loop, boolean mirror, Consumer<Keys> body) {
        KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);
        b.beginTick = 0;
        b.endTick = length;
        b.stopTick = loop ? length : length + 4;                  // a short blend back to whatever lies underneath
        b.isLooped = loop;
        b.returnTick = 0;
        Keys k = new Keys(b);
        k.mirror = mirror;
        body.accept(k);
        return b.build();
    }

    /** Keyframe helper: degrees in, radians out; positions relative to each part's rest pivot; keyed states enabled. */
    public static final class Keys {
        private final KeyframeAnimation.AnimationBuilder b;
        /** Swap left and right: each part keys its twin, yaw, roll and sideways offsets change sign. */
        boolean mirror;

        Keys(KeyframeAnimation.AnimationBuilder b) {
            this.b = b;
        }

        private static void key(KeyframeAnimation.StateCollection.State s, int tick, float value, Ease ease) {
            s.setEnabled(true);
            s.addKeyFrame(tick, value, ease);
        }

        /**
         * Key a whole-body pose: every part's rotation and bend and the body's offset and tilt, except the parts the pose
         * leaves free. Unless the pose turns the head itself, the head turns back against the torso and the tilt so the
         * eyes stay on the target.
         */
        public Keys pose(int tick, Pose p, Ease ease) {
            for (String part : PARTS) {
                if (p.free.contains(part)) continue;
                float[] r = p.rot.get(part);
                if (part.equals("head") && !p.headSet) {
                    float[] t = p.rot.getOrDefault("torso", ZERO);
                    r = new float[]{-t[0] * 0.7f - p.tilt * 0.8f, -t[1] * 0.85f, -t[2] * 0.5f};
                }
                if (r == null) r = ZERO;
                rot(part, tick, r[0], r[1], r[2], ease);
            }
            for (String limb : LIMBS) if (!p.free.contains(limb)) bend(limb, tick, p.bend.getOrDefault(limb, 0f), ease);
            double t = Math.toRadians(p.tilt), mid = 3.2;
            rot("body", tick, p.tilt, 0, 0, ease);
            return pos("body", tick, p.dx, (float) (mid * (1 - Math.cos(t))) * -1 + p.dy, (float) (mid * Math.sin(t)) + p.dz, ease);
        }

        public Keys rot(String part, int tick, float pitch, float yaw, float roll, Ease ease) {
            if (part.equals("body")) pitch = -pitch;                 // the renderer tips the whole figure back for a positive pitch: keep "positive leans forward"
            if (mirror) {
                part = twin(part);
                yaw = -yaw;
                roll = -roll;
            }
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
            if (mirror) {
                part = twin(part);
                dx = -dx;
            }
            KeyframeAnimation.StateCollection p = b.getPart(part);
            if (part.equals("body")) {
                dx /= -16f;                                               // the renderer's x points to the figure's right
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
        private static String twin(String part) {
            if (part.startsWith("right")) return "left" + part.substring(5);
            if (part.startsWith("left")) return "right" + part.substring(4);
            return part;
        }

        public Keys tilt(int tick, float pitch, float dy, Ease ease) {
            double r = Math.toRadians(pitch), mid = 3.2;  // pixels from the pivot up to the middle of the figure
            rot("body", tick, pitch, 0, 0, ease);
            return pos("body", tick, 0, (float) (mid * (1 - Math.cos(r))) * -1 + dy, (float) (mid * Math.sin(r)), ease);
        }

        /** Flex an elbow or knee. bendy-lib bends every limb backward for a positive angle: right for knees, so elbows are flipped to fold forward. */
        public Keys bend(String part, int tick, float degrees, Ease ease) {
            if (mirror) part = twin(part);
            KeyframeAnimation.StateCollection p = b.getPart(part);
            if (part.endsWith("Arm")) degrees = -degrees;
            if (p.isBendable) key(p.bend, tick, (float) Math.toRadians(degrees), ease);
            return this;
        }
    }
}
