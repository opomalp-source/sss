package com.dbzenith.client.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.galaxy.space.SpaceMath;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * The client's view of {@link SpaceFrames} (the space rework, phase 3): a planet's frame and spin built from the
 * survey data the scene carries, at the client's own time (all dimensions share the server's clock), so the orbital
 * sphere, the surface sky and the stars turn exactly as the server's crossings assume.
 */
public final class ClientFrames {
    private ClientFrames() {}

    public static Vec3 center(CompoundTag body) {
        return SpaceMath.center(body.getDouble("orbit"), body.getDouble("phase"));
    }

    public static boolean earth(CompoundTag body) {
        return GalaxyCatalog.EARTH.toString().equals(SpaceClient.system().getString("id")) && body.getInt("index") == 0;
    }

    public static SpaceFrames.Spin spin(CompoundTag body, float partial) {
        var level = Minecraft.getInstance().level;
        double game = level == null ? 0 : level.getGameTime() + partial, day = level == null ? 0 : level.getDayTime() + partial;
        return SpaceFrames.spinAt(earth(body), body.getDouble("tilt"), body.getInt("day"), game, day, center(body));
    }

    /** Earth's map is centred on this player's departure point (the server sends it with the scene, phase 4). */
    public static SpaceFrames.Planet frame(CompoundTag body, float partial) {
        return SpaceFrames.planet(center(body), body.getInt("radius"), earth(body), body.getInt("anchorX"), body.getInt("anchorZ"), spin(body, partial));
    }

    /** Earth's frame centred on a surface point of its own (the view far below, phase 4: its day there is vanilla's). */
    public static SpaceFrames.Planet frameAt(CompoundTag body, float partial, double cx, double cz) {
        return SpaceFrames.planet(center(body), body.getInt("radius"), earth(body), cx, cz, spin(body, partial));
    }

    /** The planet's frame unturned: the one its map and cloud layer are drawn in. */
    public static SpaceFrames.Planet unturned(CompoundTag body) {
        return SpaceFrames.planet(center(body), body.getInt("radius"), earth(body), body.getInt("anchorX"), body.getInt("anchorZ"), SpaceFrames.Spin.IDENTITY);
    }

    /** Earth's star frame (galactic to its sky), from vanilla's time of day and the system's real Sun. */
    public static org.joml.Matrix3f earthSky(float partial) {
        var level = Minecraft.getInstance().level;
        CompoundTag earth = null;
        for (var raw : SpaceClient.system().getList("bodies", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var b = (CompoundTag) raw;
            if (b.getInt("index") == 0) earth = b;
        }
        Vec3 sun = earth == null ? new Vec3(0, 1, 0) : SpaceMath.star(0, 1).subtract(center(earth));
        double tod = level == null ? 0 : SpaceFrames.earthTimeOfDay(level.getDayTime() + partial);
        var d = SpaceFrames.earthSkyMatrix(tod, sun);
        return new org.joml.Matrix3f((float) d.m00, (float) d.m01, (float) d.m02, (float) d.m10, (float) d.m11, (float) d.m12, (float) d.m20, (float) d.m21, (float) d.m22);
    }
}
