package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.galaxy.GalaxyService;
import com.dbzenith.galaxy.IconicSystems;
import com.dbzenith.galaxy.planet.PlanetRuntime;
import com.dbzenith.galaxy.space.Cruise;
import com.dbzenith.galaxy.space.PlanetMaps;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.galaxy.space.SpaceMath;
import com.dbzenith.galaxy.space.SpaceRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The space rework, phase 6: a destroyed world is debris everywhere you can see or reach it, a world that can't take a
 * landing turns you back at its atmosphere instead of letting you fly into it, and a stale landing never lands.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafetyTests {
    private SafetyTests() {}

    private static ServerPlayer at(GameTestHelper h, GalaxyCatalog.Id system, Vec3 where, GameType mode) {
        var p = TestPlayers.create(h);
        p.setGameMode(mode);
        p.setNoGravity(true);
        p.teleportTo(SpaceRuntime.open(h.getLevel().getServer(), system), where.x, where.y, where.z, 0, 0);
        return p;
    }

    private static void tick(ServerPlayer p) {
        SpaceRuntime.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    @GameTest(template = "empty", batch = "safety_beerus")
    public static void aWorldThatCantTakeYouTurnsYouBack(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var spec = GalaxyCatalog.system(server.overworld().getSeed(), GalaxyData.get(server).salt(), GalaxyCatalog.BEERUS);
        var body = spec.planets().get(0);
        var p = at(h, GalaxyCatalog.BEERUS, SpaceMath.center(body).add(0, SpaceMath.radius(body.radius()) + 2, 0), GameType.SURVIVAL);   // deep in its air, without god ki
        var f = SpaceRuntime.frame(p, new GalaxyCatalog.Ref(GalaxyCatalog.BEERUS, 0), body);
        SpaceRuntime.velocityHint(p, new Vec3(0, -1, 0));
        tick(p);
        h.assertTrue(!PlanetRuntime.pending(p), "no landing is prepared on a world you may not reach");
        double alt = SpaceFrames.altitude(f, p.position());
        h.assertTrue(Math.abs(alt - f.entryAltitude()) < 1, "the atmosphere puts you back at its edge: " + alt + " for " + f.entryAltitude());
        h.assertTrue(p.getDeltaMovement().dot(p.position().subtract(f.center()).normalize()) > 0, "moving outward");
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "safety_debris")
    public static void aDestroyedWorldIsDebrisEverywhere(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = GalaxyData.get(server);
        var spec = GalaxyCatalog.system(server.overworld().getSeed(), data.salt(), GalaxyCatalog.EARTH);
        var body = spec.planets().stream().filter(b -> b.kind().landable() && b.kind() != GalaxyCatalog.Kind.ASTEROIDS && PlanetRuntime.legacy(new GalaxyCatalog.Ref(GalaxyCatalog.EARTH, b.index())) == null).reduce((a, b) -> b).orElse(null);
        if (body == null) { h.succeed(); return; }
        var ref = new GalaxyCatalog.Ref(GalaxyCatalog.EARTH, body.index());
        boolean known = data.discovery(ref) != null;
        if (!known) data.discover(ref, java.util.UUID.randomUUID(), "", 0, "");
        data.destruction(ref, true, false, "");
        try {
            var p = at(h, GalaxyCatalog.EARTH, SpaceMath.center(body).add(0, SpaceMath.radius(body.radius()) * 3, 0), GameType.CREATIVE);
            CompoundTag seen = null;
            for (Tag raw : GalaxyService.survey(p, GalaxyService.SYSTEM, GalaxyCatalog.EARTH, -1, "", 0).getCompound("system").getList("bodies", Tag.TAG_COMPOUND))
                if (((CompoundTag) raw).getInt("index") == body.index()) seen = (CompoundTag) raw;
            h.assertTrue(seen != null && seen.getBoolean("destroyed") && !seen.getBoolean("landable"), "the system's survey shows debris, not a landable world");
            h.assertTrue(PlanetMaps.ground(server, ref) == null, "it has no ground to map");
            Vec3 to = Cruise.destination(spec, body.index(), p.position().add(40000, 0, 0));
            h.assertTrue(to == null || to.distanceTo(SpaceMath.center(body)) > SpaceMath.radius(body.radius()) * 1.5 + 14, "a cruise there stops clear of the debris");
            p.teleportTo(SpaceMath.center(body).x, SpaceMath.center(body).y + SpaceMath.radius(body.radius()) + 2, SpaceMath.center(body).z);
            SpaceRuntime.velocityHint(p, new Vec3(0, -1, 0));
            tick(p);
            h.assertTrue(!PlanetRuntime.pending(p), "nothing prepares a landing on a sealed world");
            h.assertTrue(p.position().distanceTo(SpaceMath.center(body)) < SpaceMath.radius(body.radius()) + 10, "and you may fly among its remnants");
            TestPlayers.remove(h, p);
        } finally {
            data.destruction(ref, true, true, "");                              // restored: as it was
        }
        h.assertTrue(!data.destroyed(ref) && PlanetMaps.ground(server, ref) != null, "restored, it has its ground (and its map) again");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "safety_leave", timeoutTicks = 200)
    public static void leavingMidTransitLeavesNothingBehind(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var spec = GalaxyCatalog.system(server.overworld().getSeed(), GalaxyData.get(server).salt(), GalaxyCatalog.EARTH);
        if (spec.planets().size() < 2) { h.succeed(); return; }
        var cruising = at(h, GalaxyCatalog.EARTH, SpaceMath.center(spec.planets().get(0)).add(0, 3000, 0), GameType.CREATIVE);
        h.assertTrue(Cruise.begin(cruising, 1), "under way");
        var landing = at(h, GalaxyCatalog.EARTH, SpaceMath.center(spec.planets().get(0)).add(0, 900, 0), GameType.CREATIVE);
        h.assertTrue(PlanetRuntime.reenter(landing, new GalaxyCatalog.Ref(GalaxyCatalog.EARTH, 0), new Vec3(0, 364, 0), new Vec3(0, -1, 0), 0, 0), "a landing being prepared");
        TestPlayers.remove(h, cruising);                                          // both disconnect mid-transit
        TestPlayers.remove(h, landing);
        Delays.after(h, 3, () -> {
            h.assertTrue(!Cruise.active(cruising) && !PlanetRuntime.pending(landing), "nothing is left pending for a player who left");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "safety_stale", timeoutTicks = 200)
    public static void aStaleLandingNeverLands(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = GalaxyData.get(server);
        var spec = GalaxyCatalog.system(server.overworld().getSeed(), data.salt(), IconicSystems.VEGETA);
        var body = spec.planets().get(spec.planets().size() - 1);
        var ref = new GalaxyCatalog.Ref(IconicSystems.VEGETA, body.index());
        if (!body.kind().landable() || PlanetRuntime.legacy(ref) != null) { h.succeed(); return; }
        boolean known = data.discovery(ref) != null;
        if (!known) data.discover(ref, java.util.UUID.randomUUID(), "", 0, "");
        var p = at(h, IconicSystems.VEGETA, SpaceMath.center(body).add(0, SpaceMath.radius(body.radius()) + 400, 0), GameType.CREATIVE);
        h.assertTrue(PlanetRuntime.reenter(p, ref, new Vec3(0, 364, 0), new Vec3(0, -1, 0), 0, 0), "a landing is being prepared");
        data.destruction(ref, true, false, "");                                  // and then the world is destroyed
        Delays.after(h, 5, () -> {
            try {
                h.assertTrue(!PlanetRuntime.pending(p) && SpaceRuntime.isSpace(p.level()), "the stale preparation is dropped and you stay in orbit");
            } finally {
                data.destruction(ref, true, true, "");
                TestPlayers.remove(h, p);
            }
            h.succeed();
        });
    }
}
