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
 * coming up and a star's heat; the flight strip dims while you drift. Distances: light years between
 * stars, AU within a system (Earth's orbit is 1 AU, 12,000 orbital blocks).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class NavigationHud {
    private NavigationHud() {}

    /** Orbital blocks in an AU: Earth's orbit (planet 0 orbits at 2 orbit units of 6,000 blocks). */
    public static final double AU = 12000;

    private static int body = -1;
    private static String bodySystem = "";
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

    /**
     * The space HUD (the space UI rework): one instrument set in one visual language ({@link SpaceUi}). A flight strip
     * above the hotbar (system, speed, nearest body and your height over it, air when it runs low), dimming while you
     * idle; a card beside whatever you target; a travel card at the top during hyperspeed, a cruise or a crossing;
     * caution and danger banners only while they apply; the flight controls for ten seconds each time you reach orbit.
     */
    public static final class Overlay implements IGuiOverlay {
        private static long movedAt;
        private static CompoundTag lastPlanet, lastStar;
        private static int lastBody = -1;
        private static long bodyAt;
        private static String lastAtmosphere = "";

        @Override
        public void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
            travel(g, w);
            star(g, w, h, SpaceRuntime.isSpace(mc.level) && !JourneyClient.active() && targeted() != null);   // a targeted planet comes first, as it does for the engage key
            if (!SpaceRuntime.isSpace(mc.level) || JourneyClient.active()) return;   // between the stars, only the travel card
            long now = System.currentTimeMillis();
            if (SpaceClient.steering() || SpaceClient.speed() > .05) movedAt = now;
            Vec3 eye = eye(pt);
            planet(g, eye, w, h, now);
            int strip = h - 70, y = cues(g, eye, w, strip - 15);
            if (SpaceClient.hinting()) controls(g, w, y);
            strip(g, eye, w, strip, now);
        }

        // ---- the flight strip ----

        private static void strip(GuiGraphics g, Vec3 eye, int w, int y, long now) {
            var mc = Minecraft.getInstance();
            float a = SpaceUi.fade("strip", true) * (now - movedAt < 4000 ? 1f : .55f);
            int width = Math.min(300, w - 16), x = w / 2 - width / 2, third = width / 3;
            SpaceUi.glass(g, x, y, width, 24, a, SpaceUi.ACCENT);
            g.fill(x + third, y + 5, x + third + 1, y + 19, SpaceUi.argb(0xFFFFFF, .08f * a));
            g.fill(x + 2 * third, y + 5, x + 2 * third + 1, y + 19, SpaceUi.argb(0xFFFFFF, .08f * a));
            String system = SpaceClient.system().getString("name");
            SpaceUi.caps(g, "System", x + 8, y + 5, SpaceUi.LABEL, a);
            SpaceUi.value(g, mc.font.plainSubstrByWidth(system.isEmpty() ? "Deep space" : system, (int) ((third - 12) / .8f)), x + 8, y + 12, SpaceUi.VALUE, a, .8f);
            String speed = String.format(Locale.ROOT, "%.0f", SpaceClient.speed() * 20);
            int sw = SpaceUi.valueWidth(speed, 1.2f), cx = x + third + third / 2;
            SpaceUi.value(g, speed, cx - sw / 2f - 6, y + 4, SpaceUi.VALUE, a, 1.2f);
            SpaceUi.caps(g, "b/s", cx + sw / 2f - 3, y + 9, SpaceUi.LABEL, a);
            SpaceUi.gauge(g, cx - 30, y + 19, 60, (float) (SpaceClient.speed() / SpaceMath.SPRINT), SpaceUi.ACCENT, a);
            CompoundTag nearest = null;
            double best = Double.MAX_VALUE;
            for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) {
                var b = (CompoundTag) raw;
                double d = eye.distanceTo(ClientFrames.center(b)) - SpaceMath.radius(b.getInt("radius"));
                if (d < best) { best = d; nearest = b; }
            }
            int rx = x + 2 * third + 8;
            SpaceUi.caps(g, "Nearest", rx, y + 5, SpaceUi.LABEL, a);
            if (nearest != null) {
                String where = range(best);
                String name = mc.font.plainSubstrByWidth(nearest.getString("name"), (int) Math.max(10, (third - 16 - SpaceUi.valueWidth(where, .8f)) / .8f));
                SpaceUi.value(g, name, rx, y + 12, SpaceUi.VALUE, a, .8f);
                SpaceUi.value(g, where, x + width - 8 - SpaceUi.valueWidth(where, .8f), y + 12, SpaceUi.ACCENT, a, .8f);
            }
            var env = com.dbzenith.client.ClientPlanet.environment(mc.level);
            boolean low = env != null && env.protection().isEmpty() && env.air() < 300;
            float air = SpaceUi.fade("air", low);
            if (air > .01f && env != null) {
                int c = env.air() < 80 ? SpaceUi.DANGER : SpaceUi.CAUTION;
                SpaceUi.gauge(g, x + 8, y + 27, width - 16, env.air() / 300f, c, air);
                SpaceUi.caps(g, "Air " + Math.round(env.air() / 3f) + "%", x + 8, y + 30, c, air);
            }
        }

        // ---- the targeted planet ----

        private static void planet(GuiGraphics g, Vec3 eye, int w, int h, long now) {
            var b = targeted();
            if (b != null) {
                if (b.getInt("index") != lastBody) { lastBody = b.getInt("index"); bodyAt = now; }
                lastPlanet = b;
            }
            float a = SpaceUi.fade("planet", b != null);
            if (a <= .01f || lastPlanet == null) return;
            b = lastPlanet;
            Vec3 rel = ClientFrames.center(b).subtract(eye);
            double d = rel.length(), r = SpaceMath.radius(b.getInt("radius"));
            float[] at = screen(rel, w, h);
            float[] edge = at == null ? null : screen(rel.add(perpendicular(rel).scale(r)), w, h);
            float size = edge == null ? 8 : (float) Math.max(8, Math.hypot(edge[0] - at[0], edge[1] - at[1]) + 4);
            if (Math.asin(Math.min(1, r / d)) > Math.toRadians(22) || at != null && size > h * .4) {
                card(g, b, d - r, rel, w / 2 + 24, h / 2 - 21, 134, a);         // a world filling the view: no bracket, the card beside the crosshair
            } else if (at != null && at[0] > -40 && at[0] < w + 40 && at[1] > -40 && at[1] < h + 40) {
                float intro = Math.min(1, (now - bodyAt) / 250f);
                size *= 1.5f - .5f * intro * intro * (3 - 2 * intro);           // the corners close in on a new target
                SpaceUi.bracket(g, at[0], at[1], size, SpaceUi.ACCENT, .85f * a);
                int cw = 134, cx = (int) (at[0] + size + 8), cy = (int) Math.max(8, at[1] - size);
                if (cx + cw > w - 8) cx = (int) (at[0] - size - 8 - cw);
                cx = Math.max(8, cx);
                cy = Math.min(cy, h - 120);
                if (cx < 200 && cy < 72) cy = 72;                               // clear of the stats panel
                card(g, b, d - r, rel, cx, cy, cw, a);
            } else {
                Vector4f v = view.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 0));
                double ax = v.x, ay = -v.y, len = Math.hypot(ax, ay);
                if (len < 1e-6) return;
                ax /= len;
                ay /= len;
                float ex = w / 2f + (float) ax * (w / 2f - 26), ey = h / 2f + (float) ay * (h / 2f - 26);
                SpaceUi.chevron(g, ex, ey, ax, ay, SpaceUi.ACCENT, .9f * a);
                String label = b.getString("name") + "  " + range(d - r);
                float lx = (float) (ex - ax * 14 - (ax < -.3 ? 0 : ax > .3 ? SpaceUi.capsWidth(label) : SpaceUi.capsWidth(label) / 2f));
                SpaceUi.caps(g, label, lx, (float) (ey - ay * 14 - 2), SpaceUi.VALUE, a);
            }
        }

        /** A target's card: name, what it is, distance and time to reach it, the key that goes there. */
        private static void card(GuiGraphics g, CompoundTag b, double gap, Vec3 rel, int x, int y, int w, float a) {
            var mc = Minecraft.getInstance();
            SpaceUi.glass(g, x, y, w, 42, a, SpaceUi.ACCENT);
            SpaceUi.value(g, mc.font.plainSubstrByWidth(b.getString("name"), (int) ((w - 12) / .9f)), x + 6, y + 5, SpaceUi.VALUE, a, .9f);
            String what = b.getBoolean("destroyed") ? "Debris field" : b.getString("type") + (b.getBoolean("landable") ? "  ·  Landable" : "  ·  Upper clouds only");
            SpaceUi.caps(g, what, x + 6, y + 15, b.getBoolean("destroyed") ? SpaceUi.DANGER : SpaceUi.LABEL, a);
            String far = range(gap);
            SpaceUi.value(g, far, x + 6, y + 22, SpaceUi.ACCENT, a, .8f);
            double closing = mc.player.getDeltaMovement().dot(rel.normalize());
            if (closing > .05) SpaceUi.caps(g, String.format(Locale.ROOT, "ETA %.0f s", Math.max(0, gap) / closing / 20), x + 10 + SpaceUi.valueWidth(far, .8f), y + 24, SpaceUi.LABEL, a);
            SpaceUi.chip(g, SpaceUi.key(com.dbzenith.client.ModKeys.ENGAGE), "Cruise", x + 6, y + 31, a);
        }

        /** Metres close in (under a tenth of an AU), AU beyond. */
        private static String range(double blocks) {
            return blocks < AU * .1 ? String.format(Locale.ROOT, "%,.0f m", Math.max(0, blocks)) : distance(blocks);
        }

        private static Vec3 perpendicular(Vec3 v) {
            Vec3 a = Math.abs(v.normalize().y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            return v.cross(a).normalize();
        }

        // ---- the targeted star ----

        private static void star(GuiGraphics g, int w, int h, boolean planet) {
            var info = StarSky.card();
            if (info != null) lastStar = info;
            float a = SpaceUi.fade("star", info != null && !planet);
            if (a <= .01f || lastStar == null) return;
            var s = lastStar;
            var types = s.getList("types", Tag.TAG_STRING);
            String kind = (types.isEmpty() ? "Star" : types.getString(0)) + (types.size() == 2 ? " binary" : types.size() >= 3 ? " trinary" : "");
            String state = switch (s.getString("state")) {
                case "charted" -> s.contains("planets") ? "Charted  ·  " + s.getInt("planets") + " planets" : "Charted";
                case "observed" -> "Observed";
                default -> "Unobserved";
            };
            boolean waypoint = s.getBoolean("waypoint");
            String ly = String.format(Locale.ROOT, "%,.0f ly", s.getDouble("distance"));
            int cw = Math.max(150, Math.max(SpaceUi.capsWidth(kind + "  ·  " + state) + 14, SpaceUi.valueWidth(s.getString("name"), .9f) + SpaceUi.valueWidth(ly, .8f) + 20));
            int x = Math.max(8, Math.min(w / 2 + 24, w - 8 - cw)), y = h / 2 - 16;   // beside the crosshair, where a near planet's card goes
            SpaceUi.glass(g, x, y, cw, 33, a, waypoint ? SpaceUi.CAUTION : SpaceUi.ACCENT);
            SpaceUi.value(g, s.getString("name"), x + 6, y + 5, SpaceUi.VALUE, a, .9f);
            SpaceUi.value(g, ly, x + cw - 6 - SpaceUi.valueWidth(ly, .8f), y + 6, SpaceUi.ACCENT, a, .8f);
            SpaceUi.caps(g, kind + "  ·  " + state, x + 6, y + 15, SpaceUi.LABEL, a);
            if (waypoint) SpaceUi.chip(g, SpaceUi.key(com.dbzenith.client.ModKeys.ENGAGE), "Hyperspeed", x + 6, y + 22, a);
            else SpaceUi.chip(g, SpaceUi.key(com.dbzenith.client.ModKeys.TARGET_STAR), "Set waypoint", x + 6, y + 22, a);
        }

        // ---- travel ----

        private static String travelTitle = "", travelName = "", travelRight = "", travelKey = "", travelAction = "";
        private static float travelProgress;

        private static void travel(GuiGraphics g, int w) {
            var mc = Minecraft.getInstance();
            boolean on = true;
            String sneak = SpaceUi.key(mc.options.keyShift);
            if (JourneyClient.active()) {
                travelTitle = "Hyperspeed";
                travelName = JourneyClient.name();
                travelProgress = JourneyClient.progress();
                travelRight = JourneyClient.spooling() ? "Spooling up" : JourneyClient.stabilizing() ? "Arriving" : String.format(Locale.ROOT, "%.1f s", JourneyClient.remaining());
                travelKey = sneak;
                travelAction = "Disengage";
            } else if (CruiseClient.active()) {
                travelTitle = "Cruise";
                travelName = CruiseClient.name();
                travelProgress = CruiseClient.progress();
                travelRight = String.format(Locale.ROOT, "%.1f s", CruiseClient.remaining());
                travelKey = sneak;
                travelAction = "Stop here";
            } else if (SpaceClient.status() != null) {
                travelTitle = "Atmosphere";
                travelName = SpaceClient.status();
                travelProgress = (System.currentTimeMillis() % 1600) / 1600f;          // working: a sweep, not a measure
                travelRight = "";
                travelKey = "";
            } else on = false;
            float a = SpaceUi.fade("travel", on);
            if (a <= .01f) return;
            int cw = 210, x = w / 2 - cw / 2, y = 8;
            SpaceUi.glass(g, x, y, cw, 36, a, SpaceUi.ACCENT);
            SpaceUi.caps(g, travelTitle, x + 7, y + 5, SpaceUi.ACCENT, a);
            if (!travelRight.isEmpty()) SpaceUi.caps(g, travelRight, x + cw - 7 - SpaceUi.capsWidth(travelRight), y + 5, SpaceUi.LABEL, a);
            SpaceUi.value(g, mc.font.plainSubstrByWidth(travelName, (int) ((cw - 14) / .9f)), x + 7, y + 12, SpaceUi.VALUE, a, .9f);
            SpaceUi.gauge(g, x + 7, y + 23, cw - 14, travelProgress, SpaceUi.ACCENT, a);
            if (!travelKey.isEmpty()) SpaceUi.chip(g, travelKey, travelAction, x + cw - 7 - SpaceUi.chipWidth(travelKey, travelAction), y + 26, a);
        }

        // ---- cues and controls ----

        /** Banners stacked upwards from {@code y}, only while they apply; returns where the next row may go. */
        private static int cues(GuiGraphics g, Vec3 eye, int w, int y) {
            var mc = Minecraft.getInstance();
            String atmosphere = null;
            Vec3 motion = mc.player.getDeltaMovement();
            for (Tag raw : SpaceClient.system().getList("bodies", Tag.TAG_COMPOUND)) {
                var b = (CompoundTag) raw;
                if (!b.getBoolean("landable")) continue;
                var f = ClientFrames.frame(b, 0);
                double alt = SpaceFrames.altitude(f, mc.player.position()) - f.entryAltitude();
                double inbound = -motion.dot(mc.player.position().subtract(f.center()).normalize());
                if (alt > 0 && inbound > .05 && alt / inbound < 200)
                    atmosphere = String.format(Locale.ROOT, "Entering the atmosphere of %s  ·  %.0f s", b.getString("name"), Math.ceil(alt / inbound / 20));
            }
            if (atmosphere != null) lastAtmosphere = atmosphere;
            float aa = SpaceUi.fade("cue-air", atmosphere != null);
            if (aa > .01f) { SpaceUi.banner(g, lastAtmosphere, w / 2, y, SpaceUi.CAUTION, aa); y -= 14; }
            boolean hot = false;
            int stars = SpaceClient.system().getList("starsDetail", Tag.TAG_COMPOUND).size();
            for (int i = 0; i < stars; i++) if (eye.distanceTo(SpaceMath.star(i, stars)) < 1500 && !mc.player.isCreative()) hot = true;
            float ha = SpaceUi.fade("cue-heat", hot);
            if (ha > .01f) { SpaceUi.banner(g, "Stellar heat  ·  turn away from the star", w / 2, y, SpaceUi.DANGER, ha); y -= 14; }
            return y;
        }

        private static void controls(GuiGraphics g, int w, int y) {
            var mc = Minecraft.getInstance();
            float a = SpaceUi.fade("controls", SpaceClient.hinting());
            String[][] chips = {{"WASD", "Fly"}, {SpaceUi.key(mc.options.keyJump), "Rise"}, {SpaceUi.key(mc.options.keyShift), "Sink"},
                    {SpaceUi.key(mc.options.keySprint), "Full speed"}, {SpaceUi.key(com.dbzenith.client.ModKeys.TARGET_STAR), "Target"},
                    {SpaceUi.key(com.dbzenith.client.ModKeys.ENGAGE), "Engage"}, {SpaceUi.key(com.dbzenith.client.ModKeys.FLY), "Ki flight"}};
            int total = 0;
            for (String[] c : chips) total += SpaceUi.chipWidth(c[0], c[1]) + 8;
            int x = w / 2 - total / 2;
            for (String[] c : chips) x += SpaceUi.chip(g, c[0], c[1], x, y, a) + 8;
        }
    }
}
