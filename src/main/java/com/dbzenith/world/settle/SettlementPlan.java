package com.dbzenith.world.settle;

import com.dbzenith.living.Poi;
import com.dbzenith.world.landmark.LandmarkPlan;
import com.dbzenith.world.landmark.LandmarkSites;
import net.minecraft.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A landmark people live in (docs/WORLD_STRUCTURES.md): drawn chunk by chunk like any landmark, and also a list of
 * every place its residents use (homes, shops, labs, the dojo, gathering spots, the patrol round, gates; each where
 * the plan puts a floor) and who lives there by trade. {@link SettlementSites} registers it with the living world the
 * first time a player comes near. Plans are shared between generation threads: the lists are filled in the
 * constructor and never changed.
 */
public abstract class SettlementPlan extends LandmarkPlan {
    protected final List<Poi> places = new ArrayList<>();
    protected final List<String> people = new ArrayList<>();

    protected SettlementPlan(LandmarkSites.Site site) {
        super(site);
    }

    /** Its name, as residents say it ("West City"). */
    public abstract String name();

    /** Its civilization's look ("capsule", "saiyan"...), for what is said there. */
    public abstract String style();

    /** How far from its centre it counts as the settlement (blocks). */
    public abstract int reach();

    /** Its centre, standing height (where it is registered). */
    public abstract BlockPos centre();

    public List<Poi> places() {
        return places;
    }

    /** Who lives there, in the order they move in (occupation ids). */
    public List<String> people() {
        return people;
    }

    /** Its stable identity in the living world (the same every time, from its site). */
    public UUID settlementId() {
        return UUID.nameUUIDFromBytes(("dbzenith:settlement:" + type.id + ":" + x + ":" + z).getBytes(StandardCharsets.UTF_8));
    }

    protected void place(Poi.Kind kind, int px, int py, int pz, String label) {
        places.add(new Poi(kind, new BlockPos(px, py, pz), label));
    }

    /** Its waypoints for long walks ({@link com.dbzenith.living.Routes}): street points and the step before each door. */
    public List<BlockPos> ways() {
        return ways;
    }

    protected final List<BlockPos> ways = new ArrayList<>();
    protected final List<com.dbzenith.living.Routes.Room> rooms = new ArrayList<>();

    /** Its buildings: the box each fills and the waypoint at its door (walks in and out go by the door). */
    public List<com.dbzenith.living.Routes.Room> rooms() {
        return rooms;
    }

    /** A waypoint outdoors (where someone stands), once; its index. */
    protected int way(int px, int py, int pz) {
        BlockPos p = new BlockPos(px, py, pz);
        int i = ways.indexOf(p);
        if (i >= 0) return i;
        ways.add(p);
        return ways.size() - 1;
    }

    /** A building filling the box between two corners (any order), entered by the waypoint {@code door} (outside its door). */
    protected void room(int ax, int ay, int az, int bx, int by, int bz, int door) {
        rooms.add(new com.dbzenith.living.Routes.Room(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by),
                Math.max(az, bz), door, false));
    }

    /** A round building (a dome, a tower) of radius r about cx, cz, from y0 to y1, entered by the waypoint {@code door}. */
    protected void roundRoom(int cx, int cz, int r, int y0, int y1, int door) {
        rooms.add(new com.dbzenith.living.Routes.Room(cx - r, y0, cz - r, cx + r, y1, cz + r, door, true));
    }
}
