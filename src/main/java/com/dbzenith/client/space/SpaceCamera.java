package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.space.SpaceMath;
import com.dbzenith.galaxy.space.SpaceRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The space camera (the space rework, phase 3). In orbit your "up" is not the world's vertical: near a planet it
 * is the planet's outward direction, so the planet you left is below you and its horizon level; in deep space it
 * eases back to the system's north (the orbital plane as the horizon). Up is a pure function of where you are
 * ({@link #up}), so every client orients every player the same way without any packets.
 * <p>You look round as on a planet: the mouse turns your heading about your up and tilts your view up and down
 * (to straight up or down, no further). Moving round a planet carries your heading with you, so the view never spins
 * on its own. The camera gets the roll the frame needs; the player entity keeps an ordinary yaw and pitch pointing
 * where you look, so aiming, the server and other players see your real look direction.
 * <p>How the mouse is read without touching Minecraft's code: vanilla clamps pitch to ±90°, which would freeze the
 * view whenever you look along the world's vertical. So after each frame the player's pitch is parked at 0 (and put
 * back before every game tick, so ticks and packets see the real look), and the next frame reads the mouse's turn
 * from how far vanilla moved it.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class SpaceCamera {
    private SpaceCamera() {}

    private static boolean active, parked;
    /** Up, the heading (perpendicular to up) and the pitch in degrees (Minecraft's sign: positive looks down). */
    private static Vec3 up = new Vec3(0, 1, 0), heading = new Vec3(0, 0, 1);
    private static long lastFrame;
    private static double pitch;
    /** What the entity was given last (to tell the mouse's turn from a teleport's). */
    private static float setYaw, setPitch;
    private static float yaw, viewPitch, roll;

    /** The planet whose frame you are in, and how much (1 within two radii of its centre, fading to 0 at five). */
    public record Anchor(CompoundTag body, double weight) {}

    public static Anchor anchor(Vec3 at) {
        CompoundTag nearest = null;
        double best = Double.MAX_VALUE;
        for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) {
            var b = (CompoundTag) raw;
            double r = SpaceMath.radius(b.getInt("radius")), d = at.distanceTo(SpaceMath.center(b.getDouble("orbit"), b.getDouble("phase"))) / r;
            if (d < best) {
                best = d;
                nearest = b;
            }
        }
        if (nearest == null) return new Anchor(null, 0);
        double w = Mth.clamp((5 - best) / 3, 0, 1);
        return new Anchor(nearest, w * w * (3 - 2 * w));
    }

    /** Up at a place in orbital space: a planet's outward direction near it, the system's north far from any. */
    public static Vec3 up(Vec3 at) {
        Vec3 north = new Vec3(0, 1, 0);
        var a = anchor(at);
        if (a.body() == null || a.weight() <= 0) return north;
        Vec3 radial = at.subtract(SpaceMath.center(a.body().getDouble("orbit"), a.body().getDouble("phase")));
        if (radial.lengthSqr() < 1e-6) return north;
        return rotateTowards(north, radial.normalize(), a.weight());
    }

    /** Turns the heading with the ground under you: the planet's spin from one tick to the next, by the frame's weight. */
    public static void carry(com.dbzenith.galaxy.space.SpaceFrames.Spin now, com.dbzenith.galaxy.space.SpaceFrames.Spin next, double weight) {
        if (!active || weight <= 0) return;
        Vec3 turned = next.apply(now.invert(heading));
        heading = heading.add(turned.subtract(heading).scale(weight)).normalize();
    }

    /** {@code from} turned a fraction {@code t} of the way to {@code to} (about their common perpendicular). */
    static Vec3 rotateTowards(Vec3 from, Vec3 to, double t) {
        double angle = Math.acos(Mth.clamp(from.dot(to), -1, 1));
        if (angle < 1e-9) return from;
        Vec3 axis = from.cross(to);
        if (axis.lengthSqr() < 1e-12) axis = Math.abs(from.x) < .9 ? new Vec3(1, 0, 0).cross(from) : new Vec3(0, 0, 1).cross(from);
        return rotate(from, axis.normalize(), angle * t);
    }

    /** Rodrigues' rotation of {@code v} about a unit {@code axis}. */
    static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    public static boolean active() {
        return active;
    }

    /** Where you look and the frame round it, for thrust: {forward, left, up}. */
    public static Vec3[] basis() {
        Vec3 f = forward(), u = cameraUp();
        return new Vec3[]{f, u.cross(f).normalize(), u};
    }

    static Vec3 forward() {
        double p = Math.toRadians(pitch);
        return heading.scale(Math.cos(p)).subtract(up.scale(Math.sin(p)));
    }

    static Vec3 cameraUp() {
        double p = Math.toRadians(pitch);
        return up.scale(Math.cos(p)).add(heading.scale(Math.sin(p)));
    }

    /** Dev automation: look straight at the nearest planet's centre (the camera then starts from that look). */
    public static void lookAtNearestPlanet() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        var a = anchor(p.position());
        if (a.body() == null) return;
        Vec3 d = SpaceMath.center(a.body().getDouble("orbit"), a.body().getDouble("phase")).subtract(p.getEyePosition()).normalize();
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(d.y, -1, 1))));
        parked = false;
        reset();
    }

    /** Starts over from the player's current look (after a teleport, a transfer, or entering space). */
    public static void reset() {
        active = false;
    }

    private static void start(Entity player) {
        up = up(CruiseClient.view(player.position(), 0));
        Vec3 f = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        pitch = Math.toDegrees(-Math.asin(Mth.clamp(f.dot(up), -1, 1)));
        Vec3 h = f.subtract(up.scale(f.dot(up)));
        heading = h.lengthSqr() > 1e-8 ? h.normalize() : perpendicular(up);
        active = true;
    }

    private static Vec3 perpendicular(Vec3 u) {
        Vec3 h = new Vec3(0, 0, 1).subtract(u.scale(u.z));
        return h.lengthSqr() > 1e-6 ? h.normalize() : new Vec3(1, 0, 0);
    }

    // ------------------------------------------------------------------ the frame loop

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void frame(TickEvent.RenderTickEvent e) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || !SpaceRuntime.isSpace(mc.level) || p.isSpectator()) {
            if (parked && p != null) unpark(p);
            active = false;
            return;
        }
        if (e.phase == TickEvent.Phase.START) {
            if (!active) {
                if (parked) unpark(p);
                start(p);
            } else {
                double dyaw = Mth.wrapDegrees(p.getYRot() - setYaw), dpitch = p.getXRot() - (parked ? 0 : setPitch);
                if (Math.abs(dyaw) > 150 || Math.abs(dpitch) > 150) start(p);  // not the mouse (a fast flick stays the mouse): the server set our look
                else {
                    heading = rotate(heading, up, Math.toRadians(-dyaw));
                    pitch = Mth.clamp(pitch + dpitch, -90, 90);
                }
            }
            Vec3 target = up(CruiseClient.view(p.getPosition(e.renderTickTime), e.renderTickTime));                // carry the heading as up turns with where we are
            long now = System.nanoTime();double dt = lastFrame == 0 ? 0 : Math.min(.1, (now - lastFrame) / 1e9);lastFrame = now;
            double gap = Math.acos(Mth.clamp(up.dot(target), -1, 1)), turn = Math.toRadians(70) * dt;                    // never faster than 70 degrees a second: no sudden roll
            Vec3 next = gap <= turn || gap < 1e-9 ? target : rotateTowards(up, target, turn / gap);
            Vec3 axis = up.cross(next);
            if (axis.lengthSqr() > 1e-14) heading = rotate(heading, axis.normalize(), Math.asin(Math.min(1, axis.length())));
            up = next;
            heading = heading.subtract(up.scale(heading.dot(up)));
            heading = heading.lengthSqr() > 1e-10 ? heading.normalize() : perpendicular(up);
            apply(p);
        } else if (active && !parked) {                                        // park the pitch for the next mouse read
            p.setXRot(0);
            p.xRotO = 0;
            parked = true;
        }
    }

    /** Before every game tick: the real look back on the entity (movement, packets, aiming). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent e) {
        var p = Minecraft.getInstance().player;
        if (p == null || !parked) return;
        if (e.phase == TickEvent.Phase.START) {
            p.setXRot(setPitch);
            p.xRotO = setPitch;
        } else {
            p.setXRot(0);
            p.xRotO = 0;
        }
    }

    private static void unpark(Entity p) {
        p.setXRot(setPitch);
        p.xRotO = setPitch;
        parked = false;
    }

    /** The entity's yaw and pitch point where we look; the camera also gets the roll. */
    private static void apply(Entity p) {
        Vec3 f = forward(), u = cameraUp();
        float y = Math.abs(f.y) > .9999 ? (float) Math.toDegrees(Math.atan2(-heading.x, heading.z)) : (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        float x = (float) Math.toDegrees(-Math.asin(Mth.clamp(f.y, -1, 1)));
        p.setYRot(y);
        p.yRotO = y;
        p.setXRot(x);
        p.xRotO = x;
        setYaw = y;
        setPitch = x;
        parked = false;
        yaw = y;
        viewPitch = x;
        Vec3 f0 = Vec3.directionFromRotation(x, y);                           // vanilla's camera up for this yaw and pitch
        Vec3 right0 = f0.cross(new Vec3(0, 1, 0));
        if (right0.lengthSqr() < 1e-8) right0 = new Vec3(-Math.cos(Math.toRadians(y)), 0, -Math.sin(Math.toRadians(y)));
        right0 = right0.normalize();
        Vec3 up0 = right0.cross(f0).normalize();
        double sin = up0.cross(u).dot(f0), cos = up0.dot(u);
        roll = (float) Math.toDegrees(Math.atan2(sin, cos));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (!active || !(e.getCamera().getEntity() == Minecraft.getInstance().player)) return;
        e.setRoll(e.getRoll() + ROLL_SIGN * roll);
    }

    /** The direction Forge's camera roll turns (checked in the dev client: the planet below you stays below). */
    static float ROLL_SIGN = 1;

    /** Turns a player model so its up is {@link #up} at its place (other players too); called inside the model's own pose. */
    public static void orient(com.mojang.blaze3d.vertex.PoseStack pose, Entity e, float partial) {
        if (!SpaceRuntime.isSpace(e.level())) return;
        Vec3 u = up(CruiseClient.view(e.getPosition(partial), partial));
        Vec3 axis = new Vec3(0, 1, 0).cross(u);
        double angle = Math.acos(Mth.clamp(u.y, -1, 1));
        if (angle < 1e-4) return;
        if (axis.lengthSqr() < 1e-12) axis = new Vec3(1, 0, 0);
        float half = e.getBbHeight() / 2;
        pose.translate(0, half, 0);
        pose.mulPose(new org.joml.Quaternionf().rotateAxis((float) angle, (float) axis.normalize().x, (float) axis.normalize().y, (float) axis.normalize().z));
        pose.translate(0, -half, 0);
    }
}
