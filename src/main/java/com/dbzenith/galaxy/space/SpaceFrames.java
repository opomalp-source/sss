package com.dbzenith.galaxy.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import net.minecraft.world.phys.Vec3;

/**
 * Every transform between a planet's surface and its orbital space, in one place (docs/SPACE_FRAMES.md). Pure math:
 * no level access, so the server, the client and the GameTests all agree.
 * <ul>
 *   <li><b>Surface</b>: block coordinates on the planet's (flat, finite) surface world. East is +x, south is +z, up is
 *       +y. Altitude is measured from the datum {@link #DATUM}.</li>
 *   <li><b>Orbital</b>: block coordinates in the system's orbital dimension. The planet is a sphere of
 *       {@link SpaceMath#radius} blocks at {@link SpaceMath#center}.</li>
 *   <li><b>Local tangent frame</b>: at a point on the sphere, {@link Basis#east}, {@link Basis#up} (outward) and
 *       {@link Basis#south}. Velocities and look directions carry over component by component in this frame, so you
 *       keep flying the same way relative to the ground below.</li>
 * </ul>
 * <p>Scale: one orbital block above the sphere stands for {@link Planet#scale} surface blocks of altitude, the same
 * ratio the surface maps onto the sphere's circumference with. Speeds are kept in blocks per tick (perceived speed),
 * not scaled.
 * <p>Mappings: procedural and legacy planets with a finite surface use the equirectangular map their worlds always used
 * (x to longitude, z to latitude; the north and south border edges collapse to the poles). Earth, whose surface is
 * effectively endless, maps a disc of {@link #EARTH_DISC} blocks around a centre (the departure point when known, or
 * world spawn) with an azimuthal equidistant projection; farther points wrap round the sphere (approximate).
 */
public final class SpaceFrames {
    private SpaceFrames() {}

    /** Surface altitude datum (sea level). */
    public static final double DATUM = 64;
    /** Surface height of the ascent crossing, and of the re-entry arrival (lower: a dead band between them). */
    public static final double ASCENT_Y = SpaceMath.ASCENT, ENTRY_Y = 364;
    /** Earth's mapped disc radius in blocks: points this far from the centre map to the far side of the sphere. */
    public static final double EARTH_DISC = 32768;
    /** Latitude clamp of the equirectangular map, short of the poles (as the original mapping). */
    private static final double POLE = .999;

    /** How a planet's surface wraps onto its orbital sphere. */
    public enum Mapping { EQUIRECT, DISC }

    /**
     * One planet's frame: its orbital centre and radius, its surface mapping ({@code size}: the half-width of a
     * finite surface, or the disc radius), and the disc centre for {@link Mapping#DISC}.
     */
    public record Planet(Vec3 center, double orbitalRadius, Mapping mapping, double size, double cx, double cz, Spin spin) {
        /** A planet frame that doesn't turn (the body-fixed frame itself). */
        public Planet(Vec3 center, double orbitalRadius, Mapping mapping, double size, double cx, double cz) {
            this(center, orbitalRadius, mapping, size, cx, cz, Spin.IDENTITY);
        }

        /** Surface blocks per orbital block: the surface's east-west extent over the sphere's circumference. */
        public double scale() {
            return size / (Math.PI * orbitalRadius);
        }

        /** Orbital altitude (above the sphere) of the ascent crossing. */
        public double ascentAltitude() {
            return (ASCENT_Y - DATUM) / scale();
        }

        /** Orbital altitude at which an inbound flight crosses into the atmosphere (it arrives at {@link #ENTRY_Y}). */
        public double entryAltitude() {
            return (ENTRY_Y - DATUM) / scale();
        }
    }

    /** The local tangent frame at a point of the sphere (all unit vectors in orbital space). */
    public record Basis(Vec3 east, Vec3 up, Vec3 south) {}

    /**
     * How a planet is turned at a moment: the rotation from its body-fixed frame (where the surface map lives) into
     * orbital space, as the images of the body's x, y and z axes (phase 3). The surface sky, the orbital sphere and
     * every crossing use the same one, so the ground under you, the planet below you and the stars agree.
     */
    public record Spin(Vec3 x, Vec3 y, Vec3 z) {
        public static final Spin IDENTITY = new Spin(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));

        /** Body-fixed to orbital. */
        public Vec3 apply(Vec3 v) {
            return x.scale(v.x).add(y.scale(v.y)).add(z.scale(v.z));
        }

        /** Orbital to body-fixed. */
        public Vec3 invert(Vec3 v) {
            return new Vec3(v.dot(x), v.dot(y), v.dot(z));
        }

        /** The axis it turns about (the body's z axis, the map's north-south axis). */
        public Vec3 axis() {
            return z;
        }
    }

    /**
     * A planet's spin: one turn per day about the body's z axis (the poles of the surface map), tilted about x by its
     * axial tilt. {@code turns} is the game time over the day length.
     */
    public static Spin spin(double tiltDegrees, double turns) {
        double a = turns * Math.PI * 2, c = Math.cos(a), s = Math.sin(a), t = Math.toRadians(tiltDegrees), ct = Math.cos(t), st = Math.sin(t);
        Vec3 x = tilt(new Vec3(c, s, 0), ct, st), y = tilt(new Vec3(-s, c, 0), ct, st), z = tilt(new Vec3(0, 0, 1), ct, st);
        return new Spin(x, y, z);
    }

    private static Vec3 tilt(Vec3 v, double c, double s) {
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    /**
     * Earth's spin follows vanilla's sky: its stars turn as {@code Ry(-90°)·Rx(time of day)} (the turn vanilla draws its
     * Sun, Moon and stars with), and a fixed turn puts vanilla's Sun where the system's star really is. This is the
     * inverse of the frame Earth's stars are drawn in ({@link #earthSky}).
     */
    public static Spin earthSpin(double timeOfDay, Vec3 sunDirection) {
        var sky = earthSkyMatrix(timeOfDay, sunDirection);
        return new Spin(new Vec3(sky.m00, sky.m10, sky.m20), new Vec3(sky.m01, sky.m11, sky.m21), new Vec3(sky.m02, sky.m12, sky.m22));
    }

    /** The rotation taking orbital (galactic) directions into Earth's sky: {@code Ry(-90°)·Rx(time of day)·A^T}. */
    public static org.joml.Matrix3d earthSkyMatrix(double timeOfDay, Vec3 sunDirection) {
        Vec3 sun = unit(sunDirection);
        var a = new org.joml.Matrix3d().rotation(new org.joml.Quaterniond().rotationTo(0, 1, 0, sun.x, sun.y, sun.z));
        var sky = new org.joml.Matrix3d().rotateY(Math.toRadians(-90)).rotateX(Math.toRadians(timeOfDay * 360));
        return sky.mul(a.transpose());
    }

    /** Vanilla's overworld time of day (0..1, 0 noon... as {@code DimensionType.timeOfDay}) for a day time. */
    public static double earthTimeOfDay(double dayTime) {
        double d = dayTime / 24000.0 - .25;
        d -= Math.floor(d);
        double e = .5 - Math.cos(d * Math.PI) / 2;
        return (d * 2 + e) / 3;
    }

    public static boolean earth(GalaxyCatalog.Body body, GalaxyCatalog.Ref ref) {
        return ref != null && ref.system().equals(GalaxyCatalog.EARTH) && ref.planet() == 0;
    }

    /** A planet's frame, not turning. For Earth, {@code (cx, cz)} is the disc centre (departure point or spawn). */
    public static Planet planet(GalaxyCatalog.Body body, GalaxyCatalog.Ref ref, double cx, double cz) {
        return planet(body, ref, cx, cz, Spin.IDENTITY);
    }

    /** A planet's frame as it is turned at a moment ({@link #spin}, {@link #earthSpin}). */
    public static Planet planet(GalaxyCatalog.Body body, GalaxyCatalog.Ref ref, double cx, double cz, Spin spin) {
        return planet(SpaceMath.center(body), body.radius(), earth(body, ref), cx, cz, spin);
    }

    /** A planet's frame from its orbital centre and catalogue radius (what the client has). */
    public static Planet planet(Vec3 center, int radius, boolean earth, double cx, double cz, Spin spin) {
        double r = SpaceMath.radius(radius);
        if (earth) return new Planet(center, r, Mapping.DISC, EARTH_DISC, cx, cz, spin);
        return new Planet(center, r, Mapping.EQUIRECT, radius, 0, 0, spin);
    }

    /** A planet's spin at a moment: Earth's from vanilla's day time, every other planet's from its day length. */
    public static Spin spinAt(boolean earth, double tiltDegrees, int dayTicks, double gameTime, double dayTime, Vec3 center) {
        if (earth) return earthSpin(earthTimeOfDay(dayTime), SpaceMath.star(0, 1).subtract(center));
        return spin(tiltDegrees, gameTime / Math.max(1, dayTicks));
    }

    /** The outward unit normal (relative to the planet centre, orbital axes) under a surface point. */
    public static Vec3 normal(Planet p, double x, double z) {
        return p.spin.apply(bodyNormal(p, x, z));
    }

    /** The normal in the planet's own (body-fixed) frame. */
    public static Vec3 bodyNormal(Planet p, double x, double z) {
        if (p.mapping == Mapping.DISC) {
            double dx = x - p.cx, dz = z - p.cz, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1e-9) return new Vec3(0, 1, 0);
            double t = Math.PI * d / p.size, s = Math.sin(t) / d;
            return new Vec3(dx * s, Math.cos(t), dz * s);
        }
        double lon = clamp(x / p.size, -POLE, POLE) * Math.PI, lat = clamp(z / p.size, -POLE, POLE) * Math.PI / 2;
        return new Vec3(Math.sin(lon) * Math.cos(lat), Math.cos(lon) * Math.cos(lat), Math.sin(lat));
    }

    /** The surface point (x, z) under a normal; the inverse of {@link #normal}. */
    public static double[] surface(Planet p, Vec3 normal) {
        Vec3 n = unit(p.spin.invert(normal));
        if (p.mapping == Mapping.DISC) {
            double t = Math.acos(clamp(n.y, -1, 1)), h = Math.sqrt(n.x * n.x + n.z * n.z);
            if (h < 1e-12) return new double[]{p.cx, p.cz};
            double d = t / Math.PI * p.size;
            return new double[]{p.cx + n.x / h * d, p.cz + n.z / h * d};
        }
        return new double[]{Math.atan2(n.x, n.y) / Math.PI * p.size, Math.asin(clamp(n.z, -1, 1)) * 2 / Math.PI * p.size};
    }

    /** The local tangent frame under a surface point: east along +x, south along +z, up outward. */
    public static Basis basis(Planet p, double x, double z) {
        Vec3 up = normal(p, x, z);
        double h = Math.max(.25, p.size * 1e-6);
        Vec3 east = normal(p, x + h, z).subtract(normal(p, x - h, z));
        east = east.subtract(up.scale(east.dot(up)));
        if (east.lengthSqr() < 1e-20) east = Math.abs(up.y) < .9 ? new Vec3(0, 1, 0).cross(up) : new Vec3(1, 0, 0);
        east = unit(east);
        Vec3 south = unit(east.cross(up));
        return new Basis(east, up, south);
    }

    /** The orbital position over a surface position (its altitude above the datum scaled down to orbital blocks). */
    public static Vec3 toOrbit(Planet p, Vec3 surface) {
        double altitude = (surface.y - DATUM) / p.scale();
        return p.center.add(normal(p, surface.x, surface.z).scale(p.orbitalRadius + altitude));
    }

    /** The surface position under an orbital position (inverse of {@link #toOrbit}). */
    public static Vec3 toSurface(Planet p, Vec3 orbital) {
        Vec3 rel = orbital.subtract(p.center);
        double[] at = surface(p, rel);
        return new Vec3(at[0], DATUM + (rel.length() - p.orbitalRadius) * p.scale(), at[1]);
    }

    /** Altitude above the orbital sphere. */
    public static double altitude(Planet p, Vec3 orbital) {
        return orbital.distanceTo(p.center) - p.orbitalRadius;
    }

    /** A surface vector (velocity or direction, surface axes) in orbital axes, through the local frame. */
    public static Vec3 toOrbitVector(Basis b, Vec3 v) {
        return b.east.scale(v.x).add(b.up.scale(v.y)).add(b.south.scale(v.z));
    }

    /** An orbital vector in surface axes, through the local frame (inverse of {@link #toOrbitVector}). */
    public static Vec3 toSurfaceVector(Basis b, Vec3 v) {
        return new Vec3(v.dot(b.east), v.dot(b.up), v.dot(b.south));
    }

    /** Minecraft's look vector for a yaw and pitch (degrees), in full precision (Vec3.directionFromRotation uses a sine table). */
    public static Vec3 forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    /** {yaw, pitch} in degrees of a direction (Minecraft's conventions; pitch positive looking down). */
    public static float[] rotation(Vec3 direction) {
        Vec3 d = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.asin(clamp(d.y, -1, 1)));
        return new float[]{yaw, pitch};
    }

    /** A look direction carried between frames as {yaw, pitch}; the heading relative to the ground is kept. */
    public static float[] look(Basis b, float yaw, float pitch, boolean toOrbit) {
        Vec3 f = forward(yaw, pitch);
        return rotation(toOrbit ? toOrbitVector(b, f) : toSurfaceVector(b, f));
    }

    /** Bounds a velocity to a speed (keeping its direction). */
    public static Vec3 cap(Vec3 v, double speed) {
        if (!Double.isFinite(v.lengthSqr())) return Vec3.ZERO;
        return v.lengthSqr() > speed * speed ? v.normalize().scale(speed) : v;
    }

    /** Angle between two directions in degrees (0 for a zero vector). */
    public static double angle(Vec3 a, Vec3 b) {
        double l = a.length() * b.length();
        return l < 1e-12 ? 0 : Math.toDegrees(Math.acos(clamp(a.dot(b) / l, -1, 1)));
    }

    /** A unit vector (Vec3.normalize gives zero below a length of 1e-4, which small differences on big planets reach). */
    static Vec3 unit(Vec3 v) {
        double l = v.length();
        return l < 1e-300 ? Vec3.ZERO : v.scale(1 / l);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
