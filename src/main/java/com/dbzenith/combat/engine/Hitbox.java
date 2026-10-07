package com.dbzenith.combat.engine;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A move's reach (CX-19), tested against a target's box without allocating:
 * <ul>
 *   <li>{@code sphere}: a ball of {@code radius} centred {@code range} x 0.6 ahead along the look;</li>
 *   <li>{@code cone}: within {@code range} of the eyes and {@code angle} degrees wide around the look;</li>
 *   <li>{@code box}: {@code range} deep along the facing (horizontal), {@code radius} each side, {@code height} tall.</li>
 * </ul>
 */
public final class Hitbox {
    private Hitbox() {}

    public static boolean contains(Move m, Vec3 eye, Vec3 look, float yawDeg, AABB target) {
        return switch (m.shape) {
            case SPHERE -> sphere(eye.x + look.x * m.range * 0.6, eye.y + look.y * m.range * 0.6, eye.z + look.z * m.range * 0.6, m.radius, target);
            case CONE -> cone(eye, look, m.range, m.angle, target);
            case BOX -> box(eye, yawDeg, m.range, m.radius, m.height, target);
        };
    }

    static boolean sphere(double cx, double cy, double cz, double r, AABB b) {
        double x = Mth.clamp(cx, b.minX, b.maxX), y = Mth.clamp(cy, b.minY, b.maxY), z = Mth.clamp(cz, b.minZ, b.maxZ);
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    static boolean cone(Vec3 eye, Vec3 look, double range, double angleDeg, AABB b) {
        double tx = (b.minX + b.maxX) / 2 - eye.x, ty = (b.minY + b.maxY) / 2 - eye.y, tz = (b.minZ + b.maxZ) / 2 - eye.z;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double half = Math.max(b.getXsize(), b.getZsize()) / 2;
        if (len - half > range) return false;
        if (len < 1e-4) return true;
        double cos = (tx * look.x + ty * look.y + tz * look.z) / len;
        double slack = Math.atan2(half, len);                                // a wide body is easier to clip at the edge
        return Math.acos(Mth.clamp(cos, -1, 1)) <= Math.toRadians(angleDeg / 2) + slack;
    }

    static boolean box(Vec3 eye, float yawDeg, double depth, double half, double height, AABB b) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);                        // forward, and right-hand side
        double rx = -fz, rz = fx;
        double tx = (b.minX + b.maxX) / 2 - eye.x, tz = (b.minZ + b.maxZ) / 2 - eye.z;
        double w = Math.max(b.getXsize(), b.getZsize()) / 2;
        double forward = tx * fx + tz * fz, side = tx * rx + tz * rz;
        if (forward < -w || forward > depth + w || Math.abs(side) > half + w) return false;
        double feet = eye.y - 1.62;
        return b.maxY >= feet - 0.2 && b.minY <= feet + height;
    }
}
