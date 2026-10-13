package com.dbzenith.ki;

/**
 * Ki flight as a body with momentum (CX-38), pure math so both sides and the tests can run it. Units are blocks per
 * tick. You fly where you look: forward follows the view up and down, strafing stays level, jump and sneak climb and
 * drop. Speed builds over about a second, carries when you let go (a glide to a stop), and reversing brakes harder.
 * Holding sprint boosts: a burst on the first tick, then the speed keeps building for a while. Hovering bobs gently.
 */
public final class FlightModel {
    /** Cruise speed per unit of the flight speed stat (vanilla's terminal flight speed is about 11x its speed value). */
    public static final double CRUISE = 11.0;
    /** Boost: how much faster than cruise at once, and at most after building up. */
    public static final double BOOST = 2.2, BOOST_MAX = 3.0;
    /** Ticks of holding the boost to build from BOOST to BOOST_MAX. */
    public static final double BOOST_BUILD = 40;
    /** How quickly velocity closes on what you ask for: speeding up, coasting to a stop, turning back. */
    public static final double ACCEL = .11, COAST = .045, BRAKE = .17;
    /** The first tick of a boost kicks you forward by this share of cruise speed. */
    public static final double BURST = .7;

    private FlightModel() {}

    /** One flier's state between ticks. */
    public static final class State {
        public double vx, vy, vz;
        /** Ticks the boost has been held (0 when not boosting). */
        public double boostTicks;
        public double time;

        public double speed() {
            return Math.sqrt(vx * vx + vy * vy + vz * vz);
        }
    }

    /**
     * Steps the velocity one tick. {@code fwd}/{@code strafe}/{@code up} are the inputs (-1..1, strafe +1 = left);
     * {@code yaw}/{@code pitch} the view in degrees (Minecraft's: yaw 0 looks +z, pitch +90 looks down);
     * {@code speed} the flight speed stat (0 when stunned). Returns true on the first tick of a boost.
     */
    public static boolean step(State s, double fwd, double strafe, double up, boolean boost, float yaw, float pitch, double speed) {
        s.time++;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);   // the view
        double rx = Math.cos(yr), rz = Math.sin(yr);                                                      // level, to the left
        double dx = lx * fwd + rx * strafe, dy = ly * fwd + up * .75, dz = lz * fwd + rz * strafe;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1) { dx /= len; dy /= len; dz /= len; len = 1; }
        boolean moving = len > 1e-3;

        boolean burst = false;
        if (boost && moving && fwd > 0) {
            if (s.boostTicks == 0) burst = true;
            s.boostTicks++;
        } else {
            s.boostTicks = 0;
        }
        double cruise = speed * CRUISE;
        double mult = s.boostTicks > 0 ? BOOST + (BOOST_MAX - BOOST) * Math.min(1, s.boostTicks / BOOST_BUILD) : 1;
        double tx = dx * cruise * mult, ty = dy * cruise * mult, tz = dz * cruise * mult;
        if (!moving) ty = Math.sin(s.time * .085) * .012 * (speed > 0 ? 1 : 0);   // hovering: a slow bob

        double k;
        double dot = tx * s.vx + ty * s.vy + tz * s.vz;
        if (!moving) k = COAST;                                                     // let go: glide to a stop
        else if (dot < 0) k = BRAKE;                                                // turning back on yourself
        else k = ACCEL;
        if (burst) {                                                                // the launch
            s.vx += lx * cruise * BURST;
            s.vy += ly * cruise * BURST;
            s.vz += lz * cruise * BURST;
        }
        s.vx += (tx - s.vx) * k;
        s.vy += (ty - s.vy) * k;
        s.vz += (tz - s.vz) * k;
        if (speed <= 0) { s.vx *= .8; s.vy *= .8; s.vz *= .8; }                    // stunned: stalls in the air
        return burst;
    }
}
