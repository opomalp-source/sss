package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.ki.FlightModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-38: ki flight with momentum. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FlightTests {
    private static final String EMPTY = "empty";
    private static final double SPEED = .05;

    private FlightTests() {}

    private static void run(FlightModel.State s, int ticks, double fwd, double up, boolean boost, float pitch) {
        for (int i = 0; i < ticks; i++) FlightModel.step(s, fwd, 0, up, boost, 0, pitch, SPEED);
    }

    @GameTest(template = EMPTY)
    public static void flightHasMomentum(GameTestHelper helper) {
        double cruise = SPEED * FlightModel.CRUISE;
        FlightModel.State s = new FlightModel.State();
        FlightModel.step(s, 1, 0, 0, false, 0, 0, SPEED);
        helper.assertTrue(s.speed() < cruise * .25, "speed builds, it doesn't snap: " + s.speed());
        run(s, 40, 1, 0, false, 0);
        helper.assertTrue(Math.abs(s.speed() - cruise) < cruise * .05, "it reaches cruise: " + s.speed());
        helper.assertTrue(s.vz > 0 && Math.abs(s.vx) < 1e-6, "it flies where you look (yaw 0: +z)");

        FlightModel.State glide = new FlightModel.State();
        run(glide, 60, 1, 0, false, 0);
        run(glide, 10, 0, 0, false, 0);
        helper.assertTrue(glide.speed() > cruise * .5, "let go: it glides on: " + glide.speed());
        run(glide, 80, 0, 0, false, 0);
        helper.assertTrue(glide.speed() < .03, "and comes to a stop (a hover bob): " + glide.speed());

        FlightModel.State coast = new FlightModel.State(), brake = new FlightModel.State();
        run(coast, 60, 1, 0, false, 0);
        run(brake, 60, 1, 0, false, 0);
        run(coast, 6, 0, 0, false, 0);
        run(brake, 6, -1, 0, false, 0);
        helper.assertTrue(brake.vz < coast.vz, "turning back brakes harder than coasting");

        FlightModel.State boost = new FlightModel.State();
        run(boost, 60, 1, 0, false, 0);
        boolean burst = FlightModel.step(boost, 1, 0, 0, true, 0, 0, SPEED);
        helper.assertTrue(burst && boost.speed() > cruise * 1.4, "a boost launches at once: " + boost.speed());
        run(boost, 80, 1, 0, true, 0);
        helper.assertTrue(boost.speed() > cruise * FlightModel.BOOST * 1.2, "and keeps building while held: " + boost.speed());

        FlightModel.State climb = new FlightModel.State();
        run(climb, 40, 1, 0, false, -45);
        helper.assertTrue(climb.vy > cruise * .6, "looking up, forward climbs: " + climb.vy);
        FlightModel.State stunned = new FlightModel.State();
        run(stunned, 40, 1, 0, false, 0);
        for (int i = 0; i < 20; i++) FlightModel.step(stunned, 1, 0, 0, false, 0, 0, 0);
        helper.assertTrue(stunned.speed() < .01, "stunned: stalls in the air");
        helper.succeed();
    }
}
