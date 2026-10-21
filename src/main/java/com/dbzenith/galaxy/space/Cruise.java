package com.dbzenith.galaxy.space;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.GalaxyData;
import com.dbzenith.network.CruisePacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.world.SpacePodEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * In-system cruise (the space rework, phase 5): from orbit to a planet of the same system in a few seconds. Nothing
 * moves faster than flight allows: the server keeps you where you are while your client flies the sky along the
 * course, then sets you down at its end (or, cancelled, where you had got to), short of the planet, on your side of
 * it and still drifting towards it. The course must be clear of the star's glare and of other bodies.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Cruise {
    private Cruise() {}

    private record Leg(ResourceKey<Level> level, Vec3 from, Vec3 to, Vec3 velocity, long start, int duration, String name) {}

    private static final Map<MinecraftServer, Map<UUID, Leg>> LEGS = new WeakHashMap<>();

    private static Map<UUID, Leg> legs(MinecraftServer s) {
        return LEGS.computeIfAbsent(s, k -> new HashMap<>());
    }

    public static boolean active(ServerPlayer p) {
        return legs(p.server).containsKey(p.getUUID());
    }

    /** Ticks for a course of {@code distance} orbital blocks: three seconds at least, fifteen at most. */
    public static int duration(double distance) {
        return (int) Math.max(60, Math.min(300, 40 + Math.sqrt(distance) * .8));
    }

    /** How far along a course is at fraction {@code t} of its time: it speeds up, cruises and slows down again. */
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Where a course to planet {@code index} ends, or null when no clear course reaches it from {@code here}. */
    public static Vec3 destination(GalaxyCatalog.SystemSpec spec, int index, Vec3 here) {
        var b = spec.planets().get(index);
        Vec3 c = SpaceMath.center(b);
        double r = SpaceMath.radius(b.radius());
        Vec3 out = here.subtract(c);
        Vec3 to = c.add((out.lengthSqr() < 1 ? new Vec3(0, 1, 0) : out.normalize()).scale(r + 700));
        if (clear(spec, here, to, index)) return to;
        to = c.add(new Vec3(0, r + 700, 0));                                   // round over the top instead
        return clear(spec, here, to, index) ? to : null;
    }

    /** The straight course from {@code a} to {@code b} keeps out of every star's glare and clear of every body. */
    static boolean clear(GalaxyCatalog.SystemSpec spec, Vec3 a, Vec3 b, int target) {
        for (int i = 0; i < spec.stars().size(); i++) if (distance(SpaceMath.star(i, spec.stars().size()), a, b) < 1500) return false;
        for (var o : spec.planets()) {
            double r = SpaceMath.radius(o.radius());
            if (distance(SpaceMath.center(o), a, b) < (o.index() == target ? r + 300 : r * 2 + 200)) return false;
        }
        return true;
    }

    private static double distance(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1e-9 ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(ab) / ab.lengthSqr()));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    /**
     * The engage key: under way, disengage; with a planet of this system targeted, cruise to it; otherwise hyperspeed
     * to the waypoint star (the space rework, phase 5).
     */
    public static boolean engage(ServerPlayer p, int index) {
        if (active(p)) return cancel(p);
        if (Interstellar.active(p)) return Interstellar.cancel(p);
        if (index >= 0) return begin(p, index);
        var waypoint = GalaxyData.get(p.server).waypoint(p.getUUID());
        if (waypoint == null) return refuse(p, "Set a waypoint first: M on a star, then M again.");
        if (waypoint.system().equals(GalaxyData.current(p))) return refuse(p, "You are in that system: target a planet (M) to cruise there.");
        return Interstellar.begin(p, waypoint);
    }

    public static boolean begin(ServerPlayer p, int index) {
        if (!SpaceRuntime.isSpace(p.level())) return refuse(p, "Cruise works in orbit.");
        if (active(p) || Interstellar.active(p) || SpaceRuntime.pending(p)) return refuse(p, "Already under way.");
        if (SpaceRuntime.passenger(p)) return refuse(p, "Only the pilot can set the pod's course.");
        var d = ModCapabilities.getOrThrow(p);
        if (!p.isCreative() && !(p.getVehicle() instanceof SpacePodEntity) && !d.isFlying()) return refuse(p, "Cruise needs a piloted Space Pod or ki flight.");
        var spec = SpaceRuntime.system(p.serverLevel());
        if (index < 0 || index >= spec.planets().size()) return false;
        var b = spec.planets().get(index);
        Vec3 here = p.position();
        if (here.distanceTo(SpaceMath.center(b)) < SpaceMath.radius(b.radius()) + 1200) return refuse(p, "You are already there.");
        Vec3 to = destination(spec, index, here);
        String name = GalaxyData.get(p.server).name(new GalaxyCatalog.Ref(spec.id(), index), b.name());
        if (to == null) return refuse(p, "No clear course to " + name + ": the star lies in the way.");
        var leg = new Leg(p.level().dimension(), here, to, SpaceMath.center(b).subtract(to).normalize(), p.server.getTickCount(), duration(here.distanceTo(to)), name);
        legs(p.server).put(p.getUUID(), leg);
        send(p, leg, true, 0);
        return true;
    }

    /** Stops a cruise where it has got to. */
    public static boolean cancel(ServerPlayer p) {
        var leg = legs(p.server).remove(p.getUUID());
        if (leg == null) return false;
        long elapsed = p.server.getTickCount() - leg.start;
        Vec3 at = leg.from.add(leg.to.subtract(leg.from).scale(ease(elapsed / (double) leg.duration)));
        place(p, leg, at, Vec3.ZERO);
        return true;
    }

    private static void place(ServerPlayer p, Leg leg, Vec3 at, Vec3 velocity) {
        if (p.getVehicle() instanceof SpacePodEntity pod) {                   // the pod and everyone in it
            pod.teleportTo(at.x, at.y, at.z);
            pod.setDeltaMovement(velocity);
        } else {
            p.teleportTo(p.serverLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(velocity);
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        SpaceRuntime.velocityHint(p, velocity);
        for (var q : Interstellar.crew(p)) ModNetwork.sendTo(q, new CruisePacket(false, leg.from, at, 0, leg.duration, leg.name, velocity));
    }

    private static void send(ServerPlayer p, Leg leg, boolean active, int elapsed) {
        for (var q : Interstellar.crew(p)) ModNetwork.sendTo(q, new CruisePacket(active, leg.from, leg.to, elapsed, leg.duration, leg.name, Vec3.ZERO));
    }

    private static boolean refuse(ServerPlayer p, String message) {
        p.displayClientMessage(Component.literal(message), true);
        return false;
    }

    @SubscribeEvent
    public static void player(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !active(p)) return;
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        if (p.getVehicle() != null) p.getVehicle().setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.getTickCount();
        for (var it = legs(server).entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var leg = entry.getValue();
            var p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !p.isAlive() || !p.level().dimension().equals(leg.level) || ModCapabilities.getOrThrow(p).isDead()) {
                it.remove();                                                    // a disconnect or death: you were never moved, nothing to undo
                if (p != null) send(p, leg, false, 0);
                continue;
            }
            long elapsed = now - leg.start;
            if (elapsed >= leg.duration) {
                it.remove();
                place(p, leg, leg.to, leg.velocity);
                continue;
            }
            if (elapsed % 20 == 0) send(p, leg, true, (int) elapsed);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        LEGS.remove(e.getServer());
    }
}
