package com.dbzenith.client.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every number that sets the feel of the motion engine (CX-18). Defaults are here; {@code motion/tuning.json} in any
 * resource pack overrides them on reload, and {@code /dbzanim set <name> <value>} changes them live. Speeds are in
 * blocks per tick (a walk is about 0.22, a sprint 0.28, fast flight 1+), angles in degrees, times in ticks.
 */
public final class Tuning {
    // ---------------------------------------------------------------- blending (ticks to cross-fade into a state)
    public static float blendDefault = 4f;
    public static float blendGait = 5f;
    public static float blendFly = 7f;
    public static float blendLand = 2f;
    public static float blendAir = 4f;
    public static float blendEngine = 6f;          // the whole engine fading in or out (vanilla poses, items, swimming)

    // ---------------------------------------------------------------- thresholds
    public static float moveThreshold = 0.012f;    // slower than this is standing
    public static float walkSpeed = 0.215f;        // full walking swing from here
    public static float sprintSpeed = 0.26f;       // a sprint reads as a sprint from here
    public static float flyCruise = 0.12f;         // hovering below this
    public static float flyFast = 0.85f;           // superman flight above this
    public static float flyVertical = 0.18f;       // climbing or dropping, nearly straight
    public static float landImpact = 0.42f;        // falling faster than this lands with a crouch
    public static float stateHold = 3f;            // ticks a new state must hold before it takes over

    // ---------------------------------------------------------------- procedural feel
    public static float runLean = 34f;             // forward lean per block-per-tick over walking speed
    public static float runLeanMax = 11f;
    public static float turnLean = 1.6f;           // roll into a turn, per degree of turn per tick (ground)
    public static float turnLeanMax = 9f;
    public static float flyPitchMax = 84f;         // body pitch at full flight speed
    public static float flyPitchCruise = 58f;      // body pitch at cruising speed
    public static float flyClimb = 34f;            // pitch change per block-per-tick of climb or dive
    public static float flyBank = 2.4f;            // bank per degree of turn per tick in flight
    public static float flyBankMax = 42f;
    public static float strafeBank = 55f;          // bank per block-per-tick of sideways flight
    public static float hoverBob = 1.1f;           // pixels
    public static float breathe = 1f;              // idle breath strength
    public static float headCounter = 0.85f;       // how much the head counters the body pitch to keep looking ahead
    public static float armLag = 0.55f;            // arms trail sudden turns and stops
    public static float landCrouch = 1f;           // strength of the landing crouch
    public static float smoothing = 0.32f;         // how quickly procedural angles follow (0..1 per tick)

    // ---------------------------------------------------------------- level of detail (blocks from the camera)
    public static float lodSimple = 40f;           // beyond: no procedural layers, half-rate updates
    public static float lodOff = 80f;              // beyond: vanilla poses

    private Tuning() {}

    /** Every tunable by name, with its value. */
    public static Map<String, Float> all() {
        Map<String, Float> out = new TreeMap<>();
        for (Field f : Tuning.class.getFields()) {
            if (f.getType() != float.class || !Modifier.isStatic(f.getModifiers())) continue;
            try {
                out.put(f.getName(), f.getFloat(null));
            } catch (IllegalAccessException ignored) {
            }
        }
        return out;
    }

    /** Sets one by name. Returns false if there is no such value. */
    public static boolean set(String name, float value) {
        try {
            Field f = Tuning.class.getField(name);
            if (f.getType() != float.class) return false;
            f.setFloat(null, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Applies a tuning.json object. */
    public static void apply(JsonObject json) {
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) set(e.getKey(), e.getValue().getAsFloat());
        }
    }
}
