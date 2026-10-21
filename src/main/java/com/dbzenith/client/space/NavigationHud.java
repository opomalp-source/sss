package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.space.SpaceFrames;
import com.dbzenith.galaxy.space.SpaceMath;
import com.dbzenith.galaxy.space.SpaceRuntime;
import com.dbzenith.network.CruisePacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Navigation in orbit (the space rework, phase 5), quiet unless you ask: M on a planet targets it (a bracket round it,
 * its name, distance, whether you can land, and the engage key); {@code .} cruises there, or with no planet targeted
 * goes to hyperspeed for your waypoint star, and disengages either. Cues appear only when they matter: the atmosphere
 * coming up, a star's heat, and your speed for a moment after you steer or brake. Distances: light years between
 * stars, AU within a system (Earth's orbit is 1 AU, 12,000 orbital blocks).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class NavigationHud {
    private NavigationHud() {}

    /** Orbital blocks in an AU: Earth's orbit (planet 0 orbits at 2 orbit units of 6,000 blocks). */
    public static final double AU = 12000;

    private static int body = -1;
    private static String bodySystem = "";
    private static long speedUntil, holdUntil;
    private static boolean wasBraking;
    private static final Matrix4f view = new Matrix4f(), projection = new Matrix4f();
    private static boolean captured;

    /** M in orbit: the planet under the crosshair (its disc, or within 1.5° of a small one), nearest first. */
    public static boolean targetBody() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !SpaceRuntime.isSpace(mc.level)) return false;
        Vec3 eye = eye(mc.getFrameTime()), look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        int best = -1;
        double nearest = Double.MAX_VALUE;
        for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) {
            var b = (CompoundTag) raw;
            Vec3 rel = ClientFrames.center(b).subtract(eye);
            double d = rel.length(), r = SpaceMath.radius(b.getInt("radius"));
            double angle = Math.acos(Math.max(-1, Math.min(1, rel.dot(look) / d))), size = Math.asin(Math.min(1, r / d));
            if (angle < Math.max(size, Math.toRadians(1.5)) && d < nearest) {
                nearest = d;
                best = b.getInt("index");
            }
        }
        if (best < 0) return false;
        body = best;
        bodySystem = SpaceClient.system().getString("id");
        return true;
    }

    public static void clearBody() {
        body = -1;
    }

    private static CompoundTag targeted() {
        if (body < 0 || !bodySystem.equals(SpaceClient.system().getString("id"))) return null;
        for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) if (((CompoundTag) raw).getInt("index") == body) return (CompoundTag) raw;
        return null;
    }

    /** {@code .}: cruise to the targeted planet, hyperspeed to the waypoint, or (under way) disengage; the server decides. */
    public static void engagePressed() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean underWay = CruiseClient.active() || JourneyClient.active();
        ModNetwork.sendToServer(new CruisePacket.Engage(!underWay && targeted() != null && SpaceRuntime.isSpace(mc.level) ? body : -1));
    }

    private static Vec3 eye(float pt) {
        var p = Minecraft.getInstance().player;
        return CruiseClient.view(p.getEyePosition(pt), pt);
    }

    /** The world's camera matrices, for placing markers on screen exactly (field of view and the space camera's roll included). */
    @SubscribeEvent
    public static void capture(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        view.set(e.getPoseStack().last().pose());
        projection.set(e.getProjectionMatrix());
        captured = true;
    }

    /** Screen position of a direction from the camera, or null behind it. */
    private static float[] screen(Vec3 rel, int w, int h) {
        if (!captured) return null;
        Vector4f v = projection.transform(view.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1)));
        if (v.w <= 1e-4f) return null;
        return new float[]{(v.x / v.w * .5f + .5f) * w, (.5f - v.y / v.w * .5f) * h};
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        body = -1;
        captured = false;
    }

    public static String distance(double blocks) {
        double au = blocks / AU;
        return String.format(Locale.ROOT, au < .1 ? "%.3f AU" : "%.2f AU", au);
    }

    public static final class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || !SpaceRuntime.isSpace(mc.level)) return;
            var font = mc.font;
            int y = h - 90;                                                      // just above the planet readout (h-77)
            if (CruiseClient.active()) {
                g.drawCenteredString(font, "Cruising to " + CruiseClient.name() + "  ·  " + String.format(Locale.ROOT, "%.1f s", CruiseClient.remaining()) + "  ·  Sneak: stop here", w / 2, y, 0xFFB9D9EC);
                return;
            }
            if (JourneyClient.active()) return;                                  // its own line
            Vec3 eye = eye(pt);
            var b = targeted();
            if (b != null) {
                Vec3 rel = ClientFrames.center(b).subtract(eye);
                double d = rel.length(), r = SpaceMath.radius(b.getInt("radius"));
                float[] at = screen(rel, w, h);
                String name = b.getString("name");
                if (at != null && at[0] > -40 && at[0] < w + 40 && at[1] > -40 && at[1] < h + 40) {
                    float[] edge = screen(rel.add(perpendicular(rel).scale(r)), w, h);
                    float size = edge == null ? 8 : (float) Math.max(8, Math.hypot(edge[0] - at[0], edge[1] - at[1]) + 4);
                    bracket(g, at[0], at[1], size, 0xCC9FE6FF);
                    g.drawCenteredString(font, name, (int) at[0], (int) (at[1] + size + 4), 0xFFD5EDFF);
                } else if (at != null || rel.lengthSqr() > 0) arrow(g, rel, w, h);
                String what = b.getBoolean("destroyed") ? "debris" : b.getBoolean("landable") ? "landable" : "upper clouds only";
                g.drawCenteredString(font, name + "  ·  " + distance(Math.max(0, d - r)) + "  ·  " + what + "  ·  .: cruise", w / 2, y, 0xFFB9D9EC);
                y -= 11;
            }
            cues(g, eye, w, y);
        }

        /** Only when they matter: the atmosphere coming up, a star's heat, speed after steering or braking. */
        private void cues(GuiGraphics g, Vec3 eye, int w, int y) {
            var mc = Minecraft.getInstance();
            var font = mc.font;
            long now = System.currentTimeMillis();
            Vec3 motion = mc.player.getDeltaMovement();
            for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) {
                var b = (CompoundTag) raw;
                if (!b.getBoolean("landable")) continue;
                var f = ClientFrames.frame(b, 0);
                double alt = SpaceFrames.altitude(f, mc.player.position()) - f.entryAltitude();
                Vec3 n = mc.player.position().subtract(f.center()).normalize();
                double inbound = -motion.dot(n);
                if (alt > 0 && inbound > .05 && alt / inbound < 200) {
                    g.drawCenteredString(font, String.format(Locale.ROOT, "Atmosphere of %s in %.0f s", b.getString("name"), Math.ceil(alt / inbound / 20)), w / 2, y, 0xFFFFD08A);
                    y -= 11;
                }
            }
            int stars = SpaceClient.system().getList("starsDetail", Tag.TAG_COMPOUND).size();
            for (int i = 0; i < stars; i++) if (eye.distanceTo(SpaceMath.star(i, stars)) < 1500 && !mc.player.isCreative()) {
                g.drawCenteredString(font, "Stellar heat: turn away from the star", w / 2, y, 0xFFFF8A5C);
                y -= 11;
                break;
            }
            if (SpaceClient.steering() || SpaceClient.braking()) speedUntil = now + 2000;
            if (wasBraking && !SpaceClient.braking() && SpaceClient.speed() < .02) holdUntil = now + 1500;
            wasBraking = SpaceClient.braking();
            if (now < speedUntil) {
                double s = SpaceClient.speed();
                int width = 90, left = w / 2 - width / 2;
                String label = SpaceClient.braking() ? (s < .02 ? "Holding position" : "Braking") : String.format(Locale.ROOT, "%.0f blocks/s", s * 20);
                g.drawCenteredString(font, label, w / 2, y, 0xFF9FB4C4);
                g.fill(left, y + 10, left + width, y + 11, 0x80243951);
                g.fill(left, y + 10, left + (int) Math.min(width, s / 5.5 * width), y + 11, 0xFF9FD9ED);
            } else if (now < holdUntil) g.drawCenteredString(font, "Holding position", w / 2, y, 0xFF9FB4C4);
        }

        private static Vec3 perpendicular(Vec3 v) {
            Vec3 a = Math.abs(v.normalize().y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            return v.cross(a).normalize();
        }

        private static void bracket(GuiGraphics g, float x, float y, float s, int color) {
            int l = (int) (x - s), r = (int) (x + s), t = (int) (y - s), b = (int) (y + s), k = (int) Math.max(3, s / 3);
            g.fill(l, t, l + k, t + 1, color); g.fill(l, t, l + 1, t + k, color);
            g.fill(r - k, t, r, t + 1, color); g.fill(r - 1, t, r, t + k, color);
            g.fill(l, b - 1, l + k, b, color); g.fill(l, b - k, l + 1, b, color);
            g.fill(r - k, b - 1, r, b, color); g.fill(r - 1, b - k, r, b, color);
        }

        /** Off screen: a small chevron at the edge, pointing the way to turn. */
        private static void arrow(GuiGraphics g, Vec3 rel, int w, int h) {
            Vector4f v = view.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 0));
            double ax = v.x, ay = -v.y, len = Math.hypot(ax, ay);
            if (len < 1e-6) return;
            ax /= len;
            ay /= len;
            float cx = w / 2f + (float) ax * (w / 2f - 24), cy = h / 2f + (float) ay * (h / 2f - 24);
            for (int i = 0; i < 5; i++) g.fill((int) (cx - ax * i - ay * (5 - i)), (int) (cy - ay * i + ax * (5 - i)), (int) (cx - ax * i + ay * (5 - i)) + 1, (int) (cy - ay * i - ax * (5 - i)) + 1, 0xCC9FE6FF);
        }
    }
}
