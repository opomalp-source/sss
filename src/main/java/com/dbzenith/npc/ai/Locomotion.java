package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.ki.FlightModel;
import com.dbzenith.npc.KiFighter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC fighter moves: on the ground by the vanilla navigator (paths renewed at most every half second, or when
 * the goal moves), strafing round its foe by the move control, and in the air by the players' own flight model
 * ({@link FlightModel}: momentum, easing in and out, a boost when far). Flight is checked every tick before it moves:
 * <ul>
 *   <li>nothing in unloaded chunks: it never flies towards a chunk that is not loaded, and stops;</li>
 *   <li>no walls or ceilings: a look-ahead box along its velocity must be clear; if not it climbs, then slides
 *       sideways, then stops;</li>
 *   <li>an altitude band: never more than 20 blocks over the ground (or 8 over its foe), never under the floor or near
 *       the build limit.</li>
 * </ul>
 * Ground steps are checked too: no strafing off a drop of more than three blocks or into lava. A fighter with bounds
 * (the tournament ring) keeps its goals inside them.
 */
public final class Locomotion {
    private final KiFighter mob;
    private final FlightModel.State flight = new FlightModel.State();
    boolean flying;
    long landedAt = Long.MIN_VALUE / 2;
    private long nextPath;
    private Vec3 lastGoal = Vec3.ZERO;
    private int strafeSide = 1;
    private long strafeFlipAt;

    Locomotion(KiFighter mob) {
        this.mob = mob;
    }

    public boolean flying() {
        return flying;
    }

    // ------------------------------------------------------------------ the ground

    /** Walks (or flies) toward {@code goal}, renewing the path when it is due or the goal moved. */
    void moveTo(Vec3 goal, double speed, long now) {
        goal = clampToBounds(goal);
        if (flying) {
            steer(goal, speed > 1.15, now);
            return;
        }
        if (now >= nextPath || goal.distanceToSqr(lastGoal) > 4) {
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
            nextPath = now + 10;
            lastGoal = goal;
        }
    }

    /** Toward a foe, stopping at {@code range}; flanking ({@code angle} degrees round the foe from the line to it). */
    void approach(LivingEntity foe, double range, double speed, double angle, long now) {
        double dist = mob.distanceTo(foe);
        if (dist <= range && Math.abs(angle) < 1) {
            stop();
            return;
        }
        Vec3 goal = foe.position();
        if (Math.abs(angle) >= 1) {                                               // a flank: a point beside the foe
            Vec3 from = mob.position().subtract(foe.position()).multiply(1, 0, 1);
            if (from.lengthSqr() < 1e-4) from = new Vec3(1, 0, 0);
            from = from.normalize().yRot((float) Math.toRadians(angle)).scale(Math.max(1.5, range - 0.5));
            goal = foe.position().add(from);
            if (mob.position().distanceToSqr(goal) < 1.0 && dist <= range + 0.5) {
                stop();
                return;
            }
        }
        if (flying) goal = goal.add(0, foe.getBbHeight() * 0.3, 0);
        moveTo(goal, speed, now);
    }

    /** Keeps between {@code min} and {@code max} blocks from the foe, sidestepping as it waits. */
    void holdRange(LivingEntity foe, double min, double max, long now) {
        double dist = mob.distanceTo(foe);
        if (dist < min) retreatFrom(foe, min + 2, now);
        else if (dist > max) approach(foe, max - 1, 1.0, 0, now);
        else strafe(foe, now, 0.5f);
    }

    /** Circles the foe, now one way, now the other (deterministic: flips every two to three seconds). */
    void strafe(LivingEntity foe, long now, float speed) {
        if (now >= strafeFlipAt) {
            strafeSide = -strafeSide;
            strafeFlipAt = now + 40 + Math.floorMod(mob.getId() * 31 + now, 21);
        }
        if (flying) {
            Vec3 to = foe.position().subtract(mob.position());
            Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(strafeSide * 4);
            steer(mob.position().add(side).add(0, (foe.getY() + 1.5 - mob.getY()) * 0.3, 0), false, now);
            return;
        }
        Vec3 dir = sideways(foe, strafeSide);
        if (!safeStep(mob.position().add(dir.scale(1.2)))) {                      // a drop or a wall that way: the other
            strafeSide = -strafeSide;
            dir = sideways(foe, strafeSide);
            if (!safeStep(mob.position().add(dir.scale(1.2)))) {
                stop();
                return;
            }
        }
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0, strafeSide * speed);
    }

    private Vec3 sideways(LivingEntity foe, int side) {
        Vec3 to = foe.position().subtract(mob.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) to = new Vec3(0, 0, 1);
        to = to.normalize();
        return new Vec3(-to.z * side, 0, to.x * side);
    }

    /** Away from a foe, to about {@code dist} blocks (up and away if flying). */
    void retreatFrom(LivingEntity foe, double dist, long now) {
        Vec3 away = mob.position().subtract(foe.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) away = new Vec3(strafeSide, 0, 0);
        Vec3 goal = mob.position().add(away.normalize().scale(dist));
        if (flying) {
            goal = goal.add(0, 3, 0);
        } else if (mob.level().hasChunkAt(BlockPos.containing(goal))) {
            int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(goal.x), Mth.floor(goal.z));
            if (Math.abs(y - mob.getY()) < 8) goal = new Vec3(goal.x, y, goal.z);
        } else {
            goal = mob.position().add(away.normalize().scale(4));                // never into an unloaded chunk
        }
        moveTo(goal, 1.2, now);
    }

    void stop() {
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0, 0);
        if (flying) {
            flight.vx *= 0.6;
            flight.vy *= 0.6;
            flight.vz *= 0.6;
        }
    }

    /** A step to {@code at} keeps its feet: no wall at body height, ground within three blocks, no lava. */
    boolean safeStep(Vec3 at) {
        Level level = mob.level();
        BlockPos feet = BlockPos.containing(at);
        if (!level.hasChunkAt(feet)) return false;
        if (!level.noCollision(mob, mob.getBoundingBox().move(at.subtract(mob.position())).deflate(0.05))) return false;
        if (outOfBounds(at)) return false;
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos b = feet.below(dy);
            var state = level.getBlockState(b);
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
            if (!state.getCollisionShape(level, b).isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ flight

    /** Whether it can take off now (and has the ki for it). */
    static boolean mayFly(KiFighter mob, NpcBrain brain) {
        if (!brain.profile.canFly() || !flightOn() || brain.res.kiShare() < 0.1) return false;
        if (com.dbzenith.registry.ModEffects.isStunned(mob) || com.dbzenith.registry.ModEffects.isKiSealed(mob)) return false;
        return brain.forms.current().allowsFlight() && mob.aiBounds() == null;
    }

    static boolean flightOn() {
        try {
            return DBZConfig.SERVER.aiFlight.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    void takeOff(long now) {
        if (flying) return;
        flying = true;
        mob.getNavigation().stop();
        mob.setNoGravity(true);
        Vec3 v = mob.getDeltaMovement();
        flight.vx = v.x;
        flight.vy = Math.max(0.25, v.y);
        flight.vz = v.z;
        flight.boostTicks = 0;
        mob.setAiFlag(KiFighter.AI_FLYING, true);
    }

    void land() {
        if (!flying) return;
        flying = false;
        mob.setNoGravity(false);
        landedAt = mob.level().getGameTime();
        mob.resetFallDistance();
        flight.vx = flight.vy = flight.vz = 0;
        mob.setAiFlag(KiFighter.AI_FLYING, false);
    }

    /** One tick of flight toward {@code goal}: the flight model's velocity, made safe, applied. */
    void steer(Vec3 goal, boolean boost, long now) {
        Vec3 to = goal.subtract(mob.position());
        double dist = to.length();
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        double fwd = dist > 1.2 ? Math.min(1, dist / 4.0) : 0;
        double speed = com.dbzenith.registry.ModEffects.isStunned(mob) ? 0 : speedStat();
        FlightModel.step(flight, fwd, 0, 0, boost && dist > 14, yaw, pitch, speed);
        Vec3 v = safe(new Vec3(flight.vx, flight.vy, flight.vz), goal);
        flight.vx = v.x;
        flight.vy = v.y;
        flight.vz = v.z;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setDeltaMovement(v);
        mob.hasImpulse = true;
        mob.resetFallDistance();
    }

    /** Hovers in place (charging, casting): the model glides to a stop. */
    void hover(long now) {
        if (!flying) return;
        FlightModel.step(flight, 0, 0, 0, false, mob.getYRot(), mob.getXRot(), speedStat());
        Vec3 v = safe(new Vec3(flight.vx, flight.vy, flight.vz), mob.position());
        flight.vx = v.x;
        flight.vy = v.y;
        flight.vz = v.z;
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
        mob.setDeltaMovement(v);
        mob.resetFallDistance();
    }

    /** The flight speed stat (the players' {@code flightBaseSpeed}), a little more for stronger fighters, less when hurt. */
    double speedStat() {
        double base;
        try {
            base = DBZConfig.SERVER.flightBaseSpeed.get();
        } catch (IllegalStateException e) {
            base = 0.05;
        }
        return base * Math.min(1.6, 0.85 + 0.01 * mob.fighterLevel()) * com.dbzenith.combat.Wounds.flight(mob);
    }

    /**
     * Makes a flight velocity safe: no flying into an unloaded chunk, a wall or a ceiling, nor out of the altitude
     * band. Public for tests.
     */
    public Vec3 safe(Vec3 v, Vec3 goal) {
        Level level = mob.level();
        Vec3 pos = mob.position();
        Vec3 ahead = pos.add(v.scale(10));
        if (!level.hasChunkAt(BlockPos.containing(ahead)) || !level.hasChunkAt(BlockPos.containing(pos.add(v.scale(3))))) {
            return new Vec3(0, Math.min(0, v.y) * 0.2, 0);                       // the edge of the loaded world: stop
        }
        int ground = groundBelow(pos);
        double maxY = Math.min(level.getMaxBuildHeight() - 4, Math.max(ground + 20, goal.y + 8));
        if (pos.y + v.y > maxY && v.y > 0) v = new Vec3(v.x, 0, v.z);
        if (pos.y + v.y < level.getMinBuildHeight() + 2 && v.y < 0) v = new Vec3(v.x, 0, v.z);
        AABB box = mob.getBoundingBox();
        if (clear(box, v.scale(4))) return v;
        Vec3 up = new Vec3(v.x * 0.5, Math.max(0.3, Math.abs(v.y)), v.z * 0.5);  // a wall: over it
        if (clear(box, new Vec3(0, 1.6, 0)) && clear(box, up.scale(3))) return up;
        Vec3 side = new Vec3(-v.z, 0, v.x);                                       // no room above: round it
        if (clear(box, side.scale(4))) return side;
        if (clear(box, side.scale(-4))) return side.scale(-1);
        return v.scale(0.05);                                                     // boxed in: hold still
    }

    private boolean clear(AABB box, Vec3 move) {
        return mob.level().noCollision(mob, box.expandTowards(move).deflate(0.02));
    }

    int groundBelow(Vec3 pos) {
        Level level = mob.level();
        BlockPos at = BlockPos.containing(pos);
        if (!level.hasChunkAt(at)) return (int) pos.y;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
    }

    /** Height over the ground beneath it. */
    double heightAboveGround() {
        return mob.getY() - groundBelow(mob.position());
    }

    // ------------------------------------------------------------------ bounds

    boolean outOfBounds(Vec3 at) {
        AABB b = mob.aiBounds();
        return b != null && !b.contains(at.x, b.minY + 0.5, at.z);
    }

    Vec3 clampToBounds(Vec3 goal) {
        AABB b = mob.aiBounds();
        if (b == null) return goal;
        return new Vec3(Mth.clamp(goal.x, b.minX + 1.5, b.maxX - 1.5), goal.y, Mth.clamp(goal.z, b.minZ + 1.5, b.maxZ - 1.5));
    }
}
