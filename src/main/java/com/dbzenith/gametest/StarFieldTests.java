package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.galaxy.GalaxyService;
import com.dbzenith.galaxy.IconicSystems;
import com.dbzenith.galaxy.StarField;
import com.dbzenith.galaxy.StarNavigation;
import com.dbzenith.network.StarFieldPacket;
import com.dbzenith.network.StarSelectPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Random;

/**
 * Every star in the sky is a catalogue system (the space rework, phase 2): the star field is the catalogue's own
 * heads, it survives the wire with its identities, targeting accepts only stars of your sky, and seeing a star does
 * not reveal its planets.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarFieldTests {
    private StarFieldTests() {}

    private static long seed(GameTestHelper h) {
        return h.getLevel().getServer().overworld().getSeed();
    }

    @GameTest(template = "empty")
    public static void starHeadsAreTheCataloguesOwnSystems(GameTestHelper h) {
        long seed = seed(h), salt = GalaxyData.get(h.getLevel().getServer()).salt();
        Random r = new Random(77);
        var ids = new java.util.ArrayList<>(java.util.List.of(GalaxyCatalog.EARTH, GalaxyCatalog.NAMEK, GalaxyCatalog.NORTH, GalaxyCatalog.BEERUS, IconicSystems.VEGETA, IconicSystems.FRIEZA));
        for (int i = 0; i < 200; i++) ids.add(new GalaxyCatalog.Id(r.nextInt(2049) - 1024, r.nextInt(2049) - 1024, r.nextInt(2049) - 1024, r.nextInt(8)));
        for (var id : ids) {
            var s = GalaxyCatalog.system(seed, salt, id);
            var head = GalaxyCatalog.head(seed, salt, id);
            h.assertTrue(head.name().equals(s.name()) && head.x() == s.x() && head.y() == s.y() && head.z() == s.z() && head.stars().equals(s.stars()),
                    "the sky's star is the atlas's system: " + id);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theFieldIsEveryNearbySystemAndNothingElse(GameTestHelper h) {
        long seed = seed(h), salt = GalaxyData.get(h.getLevel().getServer()).salt();
        long began = System.nanoTime();
        var field = StarField.near(seed, salt, GalaxyCatalog.EARTH);
        long ms = (System.nanoTime() - began) / 1_000_000;
        h.assertTrue(field.size() > 3000 && field.size() < StarField.MAX, "thousands of real stars within six sectors: " + field.size());
        h.assertTrue(ms < 4000, "computing a field stays bounded (" + ms + " ms off the server thread)");
        var here = GalaxyCatalog.head(seed, salt, GalaxyCatalog.EARTH);
        var seen = new HashSet<GalaxyCatalog.Id>();
        double last = -1;
        for (var e : field) {
            var id = e.head().id();
            h.assertTrue(!id.equals(GalaxyCatalog.EARTH) && seen.add(id), "each system once, never the observer's own");
            var again = GalaxyCatalog.head(seed, salt, id);
            h.assertTrue(again.equals(e.head()) && Math.abs(e.dx() - (again.x() - here.x())) < 1e-9 && Math.abs(e.dz() - (again.z() - here.z())) < 1e-9, "true offsets");
            double d = Math.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
            h.assertTrue(d <= StarField.RADIUS + 1e-9 && d >= last, "within the radius, nearest first");
            last = d;
        }
        for (int slot = 0; slot < 8; slot++) {                                   // completeness: the home sector and a neighbour
            for (var id : new GalaxyCatalog.Id[]{new GalaxyCatalog.Id(0, 0, 0, slot), new GalaxyCatalog.Id(1, -1, 2, slot)}) {
                if (id.equals(GalaxyCatalog.EARTH)) continue;
                var head = GalaxyCatalog.head(seed, salt, id);
                double d = Math.sqrt(Math.pow(head.x() - here.x(), 2) + Math.pow(head.y() - here.y(), 2) + Math.pow(head.z() - here.z(), 2));
                h.assertTrue((d <= StarField.RADIUS) == seen.contains(id), "every system in range is a star, no other: " + id);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void starIdentitiesAndDirectionsSurviveTheWire(GameTestHelper h) {
        long seed = seed(h), salt = GalaxyData.get(h.getLevel().getServer()).salt();
        var center = GalaxyCatalog.NAMEK;
        var field = StarField.near(seed, salt, center);
        var packet = StarFieldPacket.of(center, GalaxyCatalog.EARTH, field);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        StarFieldPacket.encode(packet, buf);
        int bytes = buf.readableBytes();
        var back = StarFieldPacket.decode(buf);
        buf.release();
        h.assertTrue(bytes < 24 * field.size() + 128 && bytes < 400_000, "about 18 bytes a star: " + bytes + " for " + field.size());
        h.assertTrue(back.count() == field.size() && back.waypoint().equals(GalaxyCatalog.EARTH.toString()), "every star arrives with the waypoint");
        double worst = 0;
        for (int i = 0; i < back.count(); i++) {
            var id = field.get(i).head().id();
            var got = new GalaxyCatalog.Id(center.x() + back.sectors()[i * 4], center.y() + back.sectors()[i * 4 + 1], center.z() + back.sectors()[i * 4 + 2], back.sectors()[i * 4 + 3]);
            h.assertTrue(got.equals(id), "the star's identity is its catalogue address: " + id);
            var e = field.get(i);
            double dx = back.positions()[i * 3], dy = back.positions()[i * 3 + 1], dz = back.positions()[i * 3 + 2];
            double dot = (dx * e.dx() + dy * e.dy() + dz * e.dz()) / Math.sqrt((dx * dx + dy * dy + dz * dz) * (e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz()));
            worst = Math.max(worst, Math.toDegrees(Math.acos(Math.min(1, dot))));
            short types = StarFieldPacket.types(e.head().stars());
            h.assertTrue(back.stars()[i] == types && ((types >> 9) & 3) == Math.min(3, e.head().stars().size()), "star types and multiplicity");
        }
        h.assertTrue(worst < .05, "directions within 0.05 degrees of the catalogue: " + worst);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void targetingAcceptsOnlyStarsOfYourSky(GameTestHelper h) {
        var p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        var server = p.server;
        var data = GalaxyData.get(server);
        long seed = server.overworld().getSeed();
        var near = StarField.near(seed, data.salt(), GalaxyCatalog.EARTH).get(5).head().id();
        var far = new GalaxyCatalog.Id(900, -900, 900, 3);
        h.assertTrue(StarField.visible(server, GalaxyCatalog.EARTH, near) && !StarField.visible(server, GalaxyCatalog.EARTH, far), "visibility is the field radius");
        h.assertTrue(GalaxyService.UNOBSERVED.equals(GalaxyService.state(p, data, near)), "a star nobody targeted is unobserved");
        StarNavigation.request(p, far.toString(), StarSelectPacket.TARGET);
        h.assertTrue(!data.observed(p.getUUID(), far), "a star outside your sky cannot be targeted");
        StarNavigation.request(p, near.toString(), StarSelectPacket.TARGET);
        h.assertTrue(data.observed(p.getUUID(), near) && GalaxyService.OBSERVED.equals(GalaxyService.state(p, data, near)), "targeting observes the star");
        var info = StarNavigation.info(p, near);
        h.assertTrue(info.getString("name").equals(GalaxyCatalog.head(seed, data.salt(), near).name()) && !info.contains("planets"), "an observed star shows its star data, not its planets");
        StarNavigation.request(p, near.toString(), StarSelectPacket.WAYPOINT);
        h.assertTrue(near.equals(data.waypoint(p.getUUID()).system()), "the same star again is the waypoint");
        StarNavigation.request(p, "not:an:id", StarSelectPacket.TARGET);
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void seeingAStarDoesNotRevealItsPlanets(GameTestHelper h) {
        var p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        var data = GalaxyData.get(p.server);
        var id = new GalaxyCatalog.Id(-401, 377, 222, 5);                        // somewhere nobody has been
        CompoundTag sys = GalaxyService.survey(p, GalaxyService.SYSTEM, id, -1, "", 0).getCompound("system");
        h.assertTrue(!sys.getBoolean("charted") && sys.getList("bodies", Tag.TAG_COMPOUND).isEmpty() && sys.getInt("planets") < 0, "an uncharted system shows no planets");
        h.assertTrue(!sys.getList("starsDetail", Tag.TAG_COMPOUND).isEmpty(), "its stars are still there to read");
        var sector = GalaxyService.survey(p, GalaxyService.SECTOR, id, -1, "", 0).getList("entries", Tag.TAG_COMPOUND);
        for (Tag t : sector) h.assertTrue(((CompoundTag) t).getInt("planets") < 0 || ((CompoundTag) t).getBoolean("charted"), "sector entries hide uncharted planet counts");
        data.observe(p.getUUID(), id);
        h.assertTrue(GalaxyService.survey(p, GalaxyService.SYSTEM, id, -1, "", 0).getCompound("system").getList("bodies", Tag.TAG_COMPOUND).isEmpty(), "observing is not charting");
        data.chart(p.getUUID(), id);
        var charted = GalaxyService.survey(p, GalaxyService.SYSTEM, id, -1, "", 0).getCompound("system");
        h.assertTrue(charted.getBoolean("charted") && !charted.getList("bodies", Tag.TAG_COMPOUND).isEmpty(), "having been there charts its planets");
        var home = GalaxyService.survey(p, GalaxyService.SYSTEM, GalaxyData.current(p), -1, "", 0).getCompound("system");
        h.assertTrue(!home.getList("bodies", Tag.TAG_COMPOUND).isEmpty(), "your own system is always charted");
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void observedAndChartedSystemsPersistWithinBounds(GameTestHelper h) {
        var data = new GalaxyData();
        var player = java.util.UUID.randomUUID();
        for (int i = 0; i < GalaxyData.OBSERVED_CAP + 20; i++) data.observe(player, new GalaxyCatalog.Id(i % 1000, i / 1000, 7, 2));
        data.chart(player, GalaxyCatalog.NAMEK);
        var loaded = GalaxyData.load(data.save(new CompoundTag()));
        h.assertTrue(!loaded.observed(player, new GalaxyCatalog.Id(0, 0, 7, 2)), "the oldest observations are forgotten past the cap");
        h.assertTrue(loaded.observed(player, new GalaxyCatalog.Id((GalaxyData.OBSERVED_CAP + 19) % 1000, (GalaxyData.OBSERVED_CAP + 19) / 1000, 7, 2)), "the newest are kept over a save");
        h.assertTrue(loaded.charted(player, GalaxyCatalog.NAMEK) && !loaded.charted(java.util.UUID.randomUUID(), GalaxyCatalog.NAMEK), "charting is per player and saved");
        h.succeed();
    }
}
