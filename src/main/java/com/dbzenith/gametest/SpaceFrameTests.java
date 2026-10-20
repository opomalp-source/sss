package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.galaxy.space.SpaceMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The space frames (docs/SPACE_FRAMES.md): surface and orbit map onto each other exactly, velocities and views carry
 * through the local ground frame, the crossing heights leave a dead band, and Earth returns land near the departure
 * (the space rework, phase 1: each of these was a reproduced bug).
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceFrameTests {
    private SpaceFrameTests() {}

    private static final Vec3 CENTER = new Vec3(12000, 128, -4000);

    private static SpaceFrames.Planet surface(int radius) {
        return new SpaceFrames.Planet(CENTER, SpaceMath.radius(radius), SpaceFrames.Mapping.EQUIRECT, radius, 0, 0);
    }

    private static SpaceFrames.Planet earth(double cx, double cz) {
        return new SpaceFrames.Planet(CENTER, SpaceMath.radius(29999984), SpaceFrames.Mapping.DISC, SpaceFrames.EARTH_DISC, cx, cz);
    }

    private static SpaceFrames.Planet[] planets() {
        return new SpaceFrames.Planet[]{surface(256), surface(2048), surface(9216), surface(16384), earth(0, 0), earth(5000.5, -3000.5), earth(-2.1e7, 1.3e7)};
    }

    @GameTest(template = "empty")
    public static void surfaceAndOrbitMapOntoEachOtherExactly(GameTestHelper h) {
        for (var p : planets()) {
            for (double fx : new double[]{-.9, -.31, 0, .07, .5, .93}) for (double fz : new double[]{-.9, -.4, 0, .66, .9}) for (double y : new double[]{SpaceFrames.ENTRY_Y, SpaceFrames.ASCENT_Y, 700}) {
                double x = p.cx() + fx * p.size() * (p.mapping() == SpaceFrames.Mapping.DISC ? .7 : 1), z = p.cz() + fz * p.size() * (p.mapping() == SpaceFrames.Mapping.DISC ? .7 : 1);
                Vec3 orbit = SpaceFrames.toOrbit(p, new Vec3(x, y, z)), back = SpaceFrames.toSurface(p, orbit);
                h.assertTrue(Math.abs(back.x - x) < 1e-4 && Math.abs(back.z - z) < 1e-4 && Math.abs(back.y - y) < 1e-6,
                        "surface -> orbit -> surface is exact: " + p.mapping() + " " + x + "," + y + "," + z + " came back " + back);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void velocityAndViewCarryThroughTheGroundFrame(GameTestHelper h) {
        for (var p : planets()) for (double fx : new double[]{-.8, 0, .45}) for (double fz : new double[]{-.6, 0, .8}) {
            double x = p.cx() + fx * p.size() * .6, z = p.cz() + fz * p.size() * .6;
            var b = SpaceFrames.basis(p, x, z);
            h.assertTrue(Math.abs(b.east().length() - 1) < 1e-9 && Math.abs(b.up().length() - 1) < 1e-9 && Math.abs(b.east().dot(b.up())) < 1e-9
                    && Math.abs(b.south().dot(b.up())) < 1e-9 && Math.abs(b.east().dot(b.south())) < 1e-9, "the local frame is orthonormal at "+p.mapping()+" "+x+","+z+": "+b);
            h.assertTrue(b.up().distanceTo(SpaceFrames.normal(p, x, z)) < 1e-12, "up is the outward normal under the point");
            Vec3 ahead = SpaceFrames.toOrbit(p, new Vec3(x + 1, 400, z)).subtract(SpaceFrames.toOrbit(p, new Vec3(x - 1, 400, z)));
            h.assertTrue(SpaceFrames.angle(ahead, b.east()) < .5, "east in orbit points the way the surface's +x runs");
            Vec3 v = new Vec3(.7, -1.3, .25), o = SpaceFrames.toOrbitVector(b, v);
            h.assertTrue(Math.abs(o.length() - v.length()) < 1e-9 && SpaceFrames.toSurfaceVector(b, o).distanceTo(v) < 1e-9, "velocity keeps its speed and comes back unchanged");
            for (float yaw : new float[]{-170, -35, 0, 90, 133}) for (float pitch : new float[]{-60, -10, 0, 25, 80}) {
                float[] up = SpaceFrames.look(b, yaw, pitch, true), down = SpaceFrames.look(b, up[0], up[1], false);
                h.assertTrue(SpaceFrames.angle(SpaceFrames.forward(down[0], down[1]), SpaceFrames.forward(yaw, pitch)) < .01, "the view comes back within 0.01 degrees");
            }
        }
        var top = SpaceFrames.basis(surface(4096), 0, 0);
        h.assertTrue(top.east().distanceTo(new Vec3(1, 0, 0)) < 1e-9 && top.up().distanceTo(new Vec3(0, 1, 0)) < 1e-9 && top.south().distanceTo(new Vec3(0, 0, 1)) < 1e-9,
                "over the map's centre the orbital frame is the surface's own (no turn at all)");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crossingHeightsLeaveADeadBand(GameTestHelper h) {
        for (var p : planets()) {
            h.assertTrue(p.entryAltitude() > 0 && p.entryAltitude() < p.ascentAltitude(), "entry lies below the ascent crossing in orbit too");
            Vec3 in = p.center().add(0, p.orbitalRadius() + p.entryAltitude(), 0);
            h.assertTrue(Math.abs(SpaceFrames.toSurface(p, in).y - SpaceFrames.ENTRY_Y) < 1e-6, "the entry crossing arrives at the entry height");
            Vec3 out = p.center().add(0, p.orbitalRadius() + p.ascentAltitude(), 0);
            h.assertTrue(Math.abs(SpaceFrames.toSurface(p, out).y - SpaceFrames.ASCENT_Y) < 1e-6, "the ascent crossing is the same height seen from both sides");
        }
        h.assertTrue(SpaceFrames.ASCENT_Y - SpaceFrames.ENTRY_Y >= 128, "a re-entry has to climb well over a hundred blocks before it can leave again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void earthReturnsLandNearTheDeparture(GameTestHelper h) {
        var p = earth(5000.5, -3000.5);                                           // the departure point is the disc's centre
        h.assertTrue(SpaceFrames.normal(p, 5000.5, -3000.5).distanceTo(new Vec3(0, 1, 0)) < 1e-12, "Earth departures leave from the top of the sphere");
        for (double deg : new double[]{1, 5, 20, 45, 90, 179}) {
            double t = Math.toRadians(deg);
            double[] at = SpaceFrames.surface(p, new Vec3(Math.sin(t), Math.cos(t), 0));
            double d = Math.hypot(at[0] - 5000.5, at[1] + 3000.5), want = deg / 180 * SpaceFrames.EARTH_DISC;
            h.assertTrue(Math.abs(d - want) < 1e-6 && d <= SpaceFrames.EARTH_DISC, "an entry " + deg + " degrees away lands " + Math.round(want) + " blocks away, not millions (was " + Math.round(d) + ")");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedAscentsAndEntriesDoNotDrift(GameTestHelper h) {
        for (var p : planets()) {
            double x = p.cx() + .37 * p.size() * .5, z = p.cz() - .21 * p.size() * .5;
            for (int i = 0; i < 10; i++) {
                Vec3 orbit = SpaceFrames.toOrbit(p, new Vec3(x, SpaceFrames.ASCENT_Y, z));   // leave, coast straight back down, re-enter
                Vec3 n = orbit.subtract(p.center()).normalize();
                Vec3 entry = p.center().add(n.scale(p.orbitalRadius() + p.entryAltitude()));
                Vec3 landed = SpaceFrames.toSurface(p, entry);
                h.assertTrue(Math.hypot(landed.x - x, landed.z - z) < 1e-4, "cycle " + i + " lands where it left (" + p.mapping() + ")");
                x = landed.x;
                z = landed.z;
            }
        }
        h.succeed();
    }
}
