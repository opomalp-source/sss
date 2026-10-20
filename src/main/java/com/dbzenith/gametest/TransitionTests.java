package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.planet.PlanetRuntime;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.galaxy.space.SpaceMath;
import com.dbzenith.galaxy.space.SpaceRuntime;
import com.dbzenith.world.SpacePodEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The space rework, phase 3: one spin for each planet shared by every view and crossing, the ground frame that
 * turns with it, and the transfer state machine (prepare ahead, cross only at the crossing, give up when the
 * climb turns back, carry the pod).
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransitionTests {
    private TransitionTests() {}

    private static final Vec3 CENTER = new Vec3(12000, 128, 0);

    @GameTest(template = "empty")
    public static void everyPlanetTurnsOneWayForEveryView(GameTestHelper h) {
        for (var spin : new SpaceFrames.Spin[]{SpaceFrames.spin(23, .37), SpaceFrames.spin(0, .9), SpaceFrames.earthSpin(.31, new Vec3(-1, .1, .3))}) {
            Vec3 x = spin.x(), y = spin.y(), z = spin.z();
            h.assertTrue(Math.abs(x.length() - 1) < 1e-9 && Math.abs(y.dot(z)) < 1e-9 && Math.abs(x.dot(y)) < 1e-9 && x.cross(y).distanceTo(z) < 1e-9, "a spin is a proper rotation");
            var p = new SpaceFrames.Planet(CENTER, 300, SpaceFrames.Mapping.EQUIRECT, 4096, 0, 0, spin);
            for (double fx : new double[]{-.7, 0, .4}) for (double fz : new double[]{-.5, .3}) {
                Vec3 s = new Vec3(fx * 4096, 450, fz * 4096), back = SpaceFrames.toSurface(p, SpaceFrames.toOrbit(p, s));
                h.assertTrue(back.distanceTo(s) < 1e-4, "a turning planet still maps both ways exactly");
            }
        }
        // Earth's stars: vanilla's Sun direction in its sky is where the system's real star is
        Vec3 sun = new Vec3(-.8, .05, .6).normalize();
        var sky = SpaceFrames.earthSkyMatrix(.27, sun);
        var seen = sky.transform(new org.joml.Vector3d(sun.x, sun.y, sun.z));
        var vanilla = new org.joml.Matrix3d().rotateY(Math.toRadians(-90)).rotateX(Math.toRadians(.27 * 360)).transform(new org.joml.Vector3d(0, 1, 0));
        h.assertTrue(seen.distance(vanilla) < 1e-9, "the catalogue star sits where vanilla draws Earth's Sun");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stoppingNearAPlanetKeepsYouOverTheSameGround(GameTestHelper h) {
        var now = SpaceFrames.spin(10, .200);
        var next = SpaceFrames.spin(10, .200 + 1 / 24000d);
        var p0 = new SpaceFrames.Planet(CENTER, 300, SpaceFrames.Mapping.EQUIRECT, 4096, 0, 0, now);
        var p1 = new SpaceFrames.Planet(CENTER, 300, SpaceFrames.Mapping.EQUIRECT, 4096, 0, 0, next);
        Vec3 at = SpaceFrames.toOrbit(p0, new Vec3(900, 600, -400));
        Vec3 dragged = CENTER.add(next.apply(now.invert(at.subtract(CENTER))));     // what SpaceClient adds to a stopped player each tick
        Vec3 under0 = SpaceFrames.toSurface(p0, at), under1 = SpaceFrames.toSurface(p1, dragged), still = SpaceFrames.toSurface(p1, at);
        h.assertTrue(under0.distanceTo(under1) < 1e-6, "dragged with the spin, you stay over the same ground");
        h.assertTrue(under0.distanceTo(still) > .05, "without it the ground would slide away under you");
        h.succeed();
    }

    private static ServerPlayer flyer(GameTestHelper h, double y, Vec3 velocity) {
        var p = TestPlayers.create(h);
        p.setGameMode(GameType.CREATIVE);
        p.setNoGravity(true);
        p.getAbilities().flying = true;
        BlockPos at = h.absolutePos(new BlockPos(2, 3, 2));
        p.teleportTo(p.serverLevel(), at.getX() + .5, y, at.getZ() + .5, 0, -20);
        SpaceRuntime.velocityHint(p, velocity);
        return p;
    }

    private static void tick(ServerPlayer p) {
        SpaceRuntime.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    @GameTest(template = "empty", batch = "space_transition", timeoutTicks = 800)
    public static void aClimbIsPreparedAheadButCrossesOnlyAtTheCrossing(GameTestHelper h) {
        var p = flyer(h, SpaceFrames.ASCENT_Y - 30, new Vec3(0, 1.5, 0));
        tick(p);
        h.assertTrue(SpaceRuntime.pending(p), "a fast climb starts preparing orbit before the crossing");
        Delays.after(h, 60, () -> {
            h.assertTrue(!SpaceRuntime.isSpace(p.level()) && SpaceRuntime.pending(p), "prepared, it waits below the crossing");
            p.teleportTo(p.getX(), SpaceFrames.ASCENT_Y + 2, p.getZ());
            SpaceRuntime.velocityHint(p, new Vec3(0, 1.5, 0));
            waitFor(h, p, 0, () -> SpaceRuntime.isSpace(p.level()), () -> {
                h.assertTrue(!SpaceRuntime.pending(p), "one transfer, nothing left pending");
                TestPlayers.remove(h, p);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "space_transition", timeoutTicks = 400)
    public static void turningBackGivesThePreparationUp(GameTestHelper h) {
        var p = flyer(h, SpaceFrames.ASCENT_Y - 30, new Vec3(0, 1.5, 0));
        tick(p);
        h.assertTrue(SpaceRuntime.pending(p), "preparing");
        p.teleportTo(p.getX(), SpaceFrames.ASCENT_Y - 300, p.getZ());
        SpaceRuntime.velocityHint(p, new Vec3(0, -1, 0));
        tick(p);
        h.assertTrue(!SpaceRuntime.pending(p), "a dive away from the boundary releases the preparation");
        Delays.after(h, 40, () -> {
            h.assertTrue(!SpaceRuntime.isSpace(p.level()), "and nothing transfers later");
            TestPlayers.remove(h, p);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "space_transition", timeoutTicks = 800)
    public static void theSamePodGoesUpWithItsPilot(GameTestHelper h) {
        var p = flyer(h, 200, Vec3.ZERO);
        var pod = SpacePodEntity.create(p.level(), p.position());
        pod.setCustomName(Component.literal("Capsule 7"));
        p.level().addFreshEntity(pod);
        p.startRiding(pod, true);
        h.assertTrue(SpaceRuntime.ascend(p), "a pod launch goes at once");
        waitFor(h, p, 0, () -> SpaceRuntime.isSpace(p.level()), () -> {
            h.assertTrue(p.getVehicle() instanceof SpacePodEntity carried && carried.getCustomName() != null
                    && carried.getCustomName().getString().equals("Capsule 7"), "the pod keeps its name across the transfer");
            TestPlayers.remove(h, p);
            h.succeed();
        });
    }

    private static void waitFor(GameTestHelper h, ServerPlayer p, int tries, java.util.function.BooleanSupplier done, Runnable then) {
        Delays.after(h, 5, () -> {
            if (!done.getAsBoolean()) {
                h.assertTrue(tries < 120, "transfer completes");
                SpaceRuntime.velocityHint(p, p.getDeltaMovement());
                waitFor(h, p, tries + 1, done, then);
                return;
            }
            then.run();
        });
    }

    @GameTest(template = "empty")
    public static void aDiveUnderThrustIsForecastByWhenItReallyArrives(GameTestHelper h) {
        for (double[] c : new double[][]{{150, .5, .15}, {40, 2, .05}, {300, 3, 0}, {80, .2, .2}}) {
            double distance = c[0], v = c[1], a = c[2], covered = 0, speed = v;
            int ticks = 0;
            while (covered < distance) { covered += speed; speed = Math.min(Math.max(v, 3), speed + a); ticks++; }   // the flight model: thrust adds speed each tick up to the cap
            double forecast = SpaceRuntime.arrival(distance, v, a);
            h.assertTrue(Math.abs(forecast - ticks) <= 2, "the forecast arrival is within two ticks of the flight's: " + forecast + " vs " + ticks);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void earthAndProceduralSpinsUseTheirOwnClocks(GameTestHelper h) {
        var body = GalaxyCatalog.system(h.getLevel().getServer().overworld().getSeed(), com.dbzenith.galaxy.GalaxyData.get(h.getLevel().getServer()).salt(), GalaxyCatalog.EARTH).planets().get(0);
        var a = SpaceFrames.spinAt(true, body.tilt(), body.dayTicks(), 100, 6000, SpaceMath.center(body));
        var b = SpaceFrames.spinAt(true, body.tilt(), body.dayTicks(), 999999, 6000, SpaceMath.center(body));
        h.assertTrue(a.x().distanceTo(b.x()) < 1e-12, "Earth turns with vanilla's day time (a stopped day cycle stops it), not the game clock");
        var c = SpaceFrames.spinAt(false, 10, 12000, 0, 0, CENTER);
        var d = SpaceFrames.spinAt(false, 10, 12000, 3000, 0, CENTER);
        h.assertTrue(Math.abs(Math.toDegrees(Math.acos(c.x().dot(d.x()))) - 90) < 1e-6, "a procedural planet turns a quarter in a quarter of its day");
        h.succeed();
    }
}
