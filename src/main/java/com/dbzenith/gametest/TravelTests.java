package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.galaxy.IconicSystems;
import com.dbzenith.galaxy.space.Cruise;
import com.dbzenith.galaxy.space.SpaceMath;
import com.dbzenith.galaxy.space.SpaceRuntime;
import com.dbzenith.world.SpacePodEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The space rework, phase 5: courses within a system and between the stars arrive somewhere safe and sensible, a
 * cruise really goes (and stops where it is when cancelled), and a pod's passenger crosses with the pilot.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravelTests {
    private TravelTests() {}

    private static GalaxyCatalog.SystemSpec system(GameTestHelper h, GalaxyCatalog.Id id) {
        var server = h.getLevel().getServer();
        return GalaxyCatalog.system(server.overworld().getSeed(), GalaxyData.get(server).salt(), id);
    }

    private static boolean safe(GalaxyCatalog.SystemSpec spec, Vec3 at, int target) {
        for (int i = 0; i < spec.stars().size(); i++) if (at.distanceTo(SpaceMath.star(i, spec.stars().size())) < 1500) return false;
        for (var o : spec.planets()) if (o.index() != target && at.distanceTo(SpaceMath.center(o)) < SpaceMath.radius(o.radius()) * 2) return false;
        return true;
    }

    @GameTest(template = "empty")
    public static void courseTimesAndEasingAreBounded(GameTestHelper h) {
        h.assertTrue(Cruise.duration(0) == 60 && Cruise.duration(1e9) == 300, "a cruise takes three to fifteen seconds");
        h.assertTrue(Cruise.duration(10000) < Cruise.duration(60000), "a longer cruise takes longer");
        double last = -1;
        for (int i = 0; i <= 20; i++) {
            double e = Cruise.ease(i / 20d);
            h.assertTrue(e >= last, "the course only goes forward");
            last = e;
        }
        h.assertTrue(Cruise.ease(0) == 0 && Cruise.ease(1) == 1, "it starts where you are and ends at the destination");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cruisesArriveClearOfStarsAndBodies(GameTestHelper h) {
        int checked = 0;
        for (var id : new GalaxyCatalog.Id[]{GalaxyCatalog.EARTH, IconicSystems.VEGETA, IconicSystems.FRIEZA, GalaxyCatalog.NAMEK}) {
            var spec = system(h, id);
            for (var b : spec.planets()) for (Vec3 here : new Vec3[]{new Vec3(0, 128, 9000), new Vec3(30000, 400, -20000), new Vec3(-60000, -300, 5000)}) {
                Vec3 to = Cruise.destination(spec, b.index(), here);
                if (to == null) continue;                                         // refused: no clear course from there
                checked++;
                double r = SpaceMath.radius(b.radius()), d = to.distanceTo(SpaceMath.center(b));
                h.assertTrue(Math.abs(d - (r + 700)) < 1e-6, "a cruise ends 700 blocks over the planet: " + d);
                h.assertTrue(safe(spec, to, b.index()), "and clear of every star and other body");
            }
        }
        h.assertTrue(checked > 10, "most courses are clear: " + checked);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hyperspeedArrivesOnTheCourseItCameAlong(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (var to : new GalaxyCatalog.Id[]{IconicSystems.VEGETA, IconicSystems.FRIEZA, GalaxyCatalog.NAMEK}) {
            var ref = new GalaxyCatalog.Ref(to, 0);
            var course = SpaceRuntime.course(server, GalaxyCatalog.EARTH, to);
            var a = system(h, GalaxyCatalog.EARTH);
            var b = system(h, to);
            h.assertTrue(course.distanceTo(new Vec3(b.x() - a.x(), b.y() - a.y(), b.z() - a.z())) < 1e-9, "the course is the catalogue's own light years");
            var arrival = SpaceRuntime.arrival(server, GalaxyCatalog.EARTH, ref, Vec3.ZERO);
            var spec = system(h, to);
            Vec3 c = SpaceMath.center(spec.planets().get(0));
            h.assertTrue(safe(spec, arrival.position(), 0), "arrival is clear of stars and other bodies");
            h.assertTrue(Math.abs(arrival.position().distanceTo(c) - SpaceMath.radius(spec.planets().get(0).radius()) - 900) < 1e-6, "900 blocks short of the planet");
            h.assertTrue(arrival.velocity().normalize().dot(c.subtract(arrival.position()).normalize()) > .999, "still heading for it");
            double along = arrival.velocity().normalize().dot(course.normalize());
            h.assertTrue(along > .999 || along < .999 && !safe(spec, c.subtract(course.normalize().scale(SpaceMath.radius(spec.planets().get(0).radius()) + 900)), 0),
                    "arriving along the course it came (unless that line is unsafe): " + along);
        }
        h.succeed();
    }

    private static ServerPlayer inOrbit(GameTestHelper h, Vec3 at) {
        var p = TestPlayers.create(h);
        p.setGameMode(GameType.CREATIVE);
        p.setNoGravity(true);
        p.teleportTo(SpaceRuntime.open(h.getLevel().getServer(), GalaxyCatalog.EARTH), at.x, at.y, at.z, 0, 0);
        return p;
    }

    @GameTest(template = "empty", batch = "travel_cruise", timeoutTicks = 900)
    public static void aCruiseGoesAndACancelledOneStopsOnTheWay(GameTestHelper h) {
        var spec = system(h, GalaxyCatalog.EARTH);
        int target = spec.planets().size() > 1 ? 1 : 0;
        Vec3 start = SpaceMath.center(spec.planets().get(0)).add(0, 3000, 0);
        var p = inOrbit(h, start);
        h.assertTrue(Cruise.begin(p, target), "a cruise starts");
        h.assertTrue(!Cruise.begin(p, target), "one course at a time");
        Vec3 to = Cruise.destination(spec, target, start);
        Delays.after(h, 20, () -> {
            h.assertTrue(p.position().distanceTo(start) < 1, "under way, you stay where you were (the sky moves)");
            waitCruise(h, p, 0, () -> {
                h.assertTrue(p.position().distanceTo(to) < 1, "set down at the course's end: " + p.position() + " for " + to);
                p.teleportTo(start.x, start.y, start.z);   // and again from the start, cancelled on the way
                Vec3 back = start;
                h.assertTrue(Cruise.begin(p, target), "a second cruise");
                Delays.after(h, 30, () -> {
                    h.assertTrue(Cruise.cancel(p), "cancelled");
                    h.assertTrue(p.position().distanceTo(back) > 1 && p.position().distanceTo(to) > 100, "stopped part of the way: " + p.position());
                    TestPlayers.remove(h, p);
                    h.succeed();
                });
            });
        });
    }

    private static void waitCruise(GameTestHelper h, ServerPlayer p, int tries, Runnable then) {
        Delays.after(h, 10, () -> {
            if (Cruise.active(p)) {
                h.assertTrue(tries < 40, "a cruise ends");
                waitCruise(h, p, tries + 1, then);
                return;
            }
            then.run();
        });
    }

    @GameTest(template = "empty", batch = "travel_pod")
    public static void aPassengerCrossesWithThePilot(GameTestHelper h) {
        var pilot = TestPlayers.create(h);
        var passenger = TestPlayers.create(h);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(2, 3, 2)));
        var pod = SpacePodEntity.create(h.getLevel(), at);
        h.getLevel().addFreshEntity(pod);
        h.assertTrue(pilot.startRiding(pod) && passenger.startRiding(pod), "a pod seats two");
        h.assertTrue(pod.getFirstPassenger() == pilot && SpaceRuntime.passenger(passenger) && !SpaceRuntime.passenger(pilot), "the first in pilots, the second rides along");
        var third = TestPlayers.create(h);
        h.assertTrue(!third.startRiding(pod), "and no more");
        pilot.stopRiding();
        Vec3 there = at.add(10, 5, 0);
        var carried = SpacePodEntity.carry(pod, pilot, there, Vec3.ZERO, false);
        h.assertTrue(carried.getFirstPassenger() == pilot && passenger.getVehicle() == carried, "the passenger is in the carried pod with the pilot");
        h.assertTrue(passenger.position().distanceTo(there) < 2, "beside them");
        TestPlayers.remove(h, pilot);
        TestPlayers.remove(h, passenger);
        TestPlayers.remove(h, third);
        h.succeed();
    }
}
