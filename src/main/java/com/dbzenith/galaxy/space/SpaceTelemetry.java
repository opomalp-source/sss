package com.dbzenith.galaxy.space;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What each space transfer did to the player's position, motion and view (docs/SPACE_FRAMES.md "Continuity"). Every
 * committed crossing records the state on both sides and its errors against the {@link SpaceFrames} mapping: how far
 * the arrival is from the mapped departure (blocks), how much the heading and pitch moved relative to the ground, and
 * how the velocity's direction and speed changed. Bounded: the last {@link #KEEP} records per server, nothing saved.
 * Read with {@code /dbz galaxy transitions}; the GameTests assert on it.
 */
public final class SpaceTelemetry {
    private SpaceTelemetry() {}

    public static final int KEEP = 64;

    public enum Kind { ASCENT, ENTRY, HYPERSPEED, APPROACH, RESTORE }

    /** One side of a crossing. */
    public record Sample(String dimension, Vec3 position, Vec3 velocity, float yaw, float pitch) {
        public static Sample of(ServerPlayer p, Vec3 velocity) {
            return new Sample(p.level().dimension().location().toString(), p.position(), velocity, p.getYRot(), p.getXRot());
        }
    }

    /**
     * A committed crossing: {@code positionError} blocks between the arrival and the mapped departure, the change of
     * view direction and of pitch in degrees (against the departure view carried through the local ground frame), the
     * velocity's direction change in degrees and its
     * speed change as a fraction ({@code 0.1} = 10% faster).
     */
    public record Record(Kind kind, UUID player, long tick, Sample from, Sample to, int preloadTicks,
                         double positionError, double headingError, double pitchError, double directionError, double speedError) {
        public String line() {
            return String.format(Locale.ROOT, "%s t%d %s -> %s | pos %.2f blocks, heading %.2f, pitch %.2f, dir %.2f deg, speed %+.1f%%, preload %d ticks",
                    kind, tick, from.dimension, to.dimension, positionError, headingError, pitchError, directionError, speedError * 100, preloadTicks);
        }
    }

    private static final Map<MinecraftServer, Deque<Record>> LOG = new WeakHashMap<>();

    /**
     * Records a crossing. {@code expectedPosition}, {@code expectedVelocity} and {@code expectedLook} are the departure
     * state carried into the arrival frame by {@link SpaceFrames}; the errors compare them with what actually happened.
     */
    public static Record record(ServerPlayer p, Kind kind, Sample from, Sample to, int preloadTicks,
                                Vec3 expectedPosition, Vec3 expectedVelocity, float[] expectedLook) {
        Vec3 look = SpaceFrames.forward(to.yaw, to.pitch), want = SpaceFrames.forward(expectedLook[0], expectedLook[1]);
        double heading = SpaceFrames.angle(look, want);                         // the whole view direction
        double direction = expectedVelocity.lengthSqr() < 1e-8 || to.velocity.lengthSqr() < 1e-8 ? 0 : SpaceFrames.angle(expectedVelocity, to.velocity);
        double speed = expectedVelocity.length() < 1e-4 ? 0 : to.velocity.length() / expectedVelocity.length() - 1;
        Record r = new Record(kind, p.getUUID(), p.server.getTickCount(), from, to, preloadTicks,
                expectedPosition == null ? 0 : expectedPosition.distanceTo(to.position), heading,
                Math.abs(Math.abs(to.pitch) > 89.9f && Math.abs(expectedLook[1]) > 89.9f ? 0 : to.pitch - expectedLook[1]), direction, speed);
        Deque<Record> log = LOG.computeIfAbsent(p.server, k -> new ArrayDeque<>());
        synchronized (log) {
            log.addLast(r);
            while (log.size() > KEEP) log.removeFirst();
        }
        if (Boolean.getBoolean("dbzenith.spaceTrace")) com.dbzenith.DBZenith.LOGGER.info("[space-transition] {} {}", p.getGameProfile().getName(), r.line());
        return r;
    }

    /** The latest records (newest last), for one player or ({@code null}) everyone. */
    public static List<Record> recent(MinecraftServer server, UUID player, int max) {
        Deque<Record> log = LOG.get(server);
        if (log == null) return List.of();
        List<Record> out = new ArrayList<>();
        synchronized (log) {
            for (Record r : log) if (player == null || r.player.equals(player)) out.add(r);
        }
        return out.subList(Math.max(0, out.size() - max), out.size());
    }

    /** The newest record of a kind for a player, or null. */
    public static Record last(MinecraftServer server, UUID player, Kind kind) {
        List<Record> all = recent(server, player, KEEP);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).kind == kind) return all.get(i);
        return null;
    }
}
