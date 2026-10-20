package com.dbzenith.client.space;

import com.dbzenith.DBZenith;
import com.dbzenith.galaxy.GalaxyCatalog;
import com.dbzenith.galaxy.StarField;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.StarFieldPacket;
import com.dbzenith.network.StarSelectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The night sky is the galaxy (the space rework, phase 2): every star point is a catalogue system near the
 * observer's ({@link StarField}, streamed by the server), in its true direction, coloured by its star type and as
 * bright as its luminosity over its distance squared. Binary and trinary systems show a faint companion beside
 * their primary and target as one system. The same field is drawn in orbit (the galaxy's own axes), on Earth (turning
 * with vanilla's celestial sphere, in place of vanilla's random stars) and on other planets (turning with that
 * planet's day, like its suns).
 * <p>Targeting ({@code M}): the star nearest the crosshair (within 2.5°, bright stars a little easier) is targeted; the
 * server checks it and replies with what can be read from here. Target the same star again to make it your
 * waypoint. {@code ,} opens the atlas on the targeted star. A ring marks the target, a small diamond the waypoint.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class StarSky {
    private StarSky() {}

    /** The sphere the stars sit on (inside the space sky's 190-block box). */
    private static final float SHELL = 170;
    private static final double PICK = Math.toRadians(2.5);

    private static String center = "";
    private static String[] ids = new String[0];
    private static float[] dir = new float[0];
    private static float[] flux = new float[0];
    private static short[] types = new short[0];
    private static VertexBuffer buffer;
    private static boolean dirty;

    private static String target = "", waypoint = "";
    private static CompoundTag info = new CompoundTag();
    private static long infoUntil;
    /** Galactic directions into the current sky's world directions, as of the last frame drawn (for targeting). */
    private static final Matrix3f lastFrame = new Matrix3f();
    private static boolean framed;

    // ------------------------------------------------------------------ data

    public static void receive(StarFieldPacket m) {
        GalaxyCatalog.Id c;
        try {
            c = GalaxyCatalog.Id.parse(m.center());
        } catch (IllegalArgumentException ex) {
            return;
        }
        int n = m.count();
        String[] newIds = new String[n];
        float[] newDir = new float[n * 3], newFlux = new float[n];
        for (int i = 0; i < n; i++) {
            int x = c.x() + m.sectors()[i * 4], y = c.y() + m.sectors()[i * 4 + 1], z = c.z() + m.sectors()[i * 4 + 2], slot = m.sectors()[i * 4 + 3];
            newIds[i] = x + ":" + y + ":" + z + ":" + slot;
            double dx = m.positions()[i * 3], dy = m.positions()[i * 3 + 1], dz = m.positions()[i * 3 + 2];
            double d = Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz));
            newDir[i * 3] = (float) (dx / d);
            newDir[i * 3 + 1] = (float) (dy / d);
            newDir[i * 3 + 2] = (float) (dz / d);
            double light = 0;
            for (int k = 0; k < count(m.stars()[i]); k++) light += GalaxyCatalog.StarType.values()[type(m.stars()[i], k)].light;
            newFlux[i] = (float) (light / Math.pow(d / 1000, 2));
        }
        center = m.center();
        ids = newIds;
        dir = newDir;
        flux = newFlux;
        types = m.stars();
        waypoint = m.waypoint();
        if (!target.isEmpty() && index(target) < 0) clearTarget();
        dirty = true;
    }

    /** Whether the sky has the catalogue's stars yet (until then the old decorative field stands in). */
    public static boolean ready() {
        return ids.length > 0;
    }

    public static void info(CompoundTag t) {
        if (t.isEmpty() || !t.contains("id")) {
            clearTarget();
            return;
        }
        target = t.getString("id");
        info = t;
        infoUntil = System.currentTimeMillis() + 8000;
        if (t.getBoolean("waypoint")) waypoint = target;
    }

    private static void clearTarget() {
        target = "";
        info = new CompoundTag();
    }

    private static int count(short s) {
        return Math.max(1, (s >> 9) & 3);
    }

    private static int type(short s, int k) {
        return Math.min(GalaxyCatalog.StarType.values().length - 1, (s >> (k * 3)) & 7);
    }

    private static int index(String id) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ frames

    /** Earth's sky: vanilla's celestial sphere turning with the time of day (galactic axes are its axes). */
    public static Matrix3f earthFrame(ClientLevel level, float pt) {
        return new Matrix3f().rotate(Axis.YP.rotationDegrees(-90)).rotate(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360));
    }

    /** A sky frame from a function taking galactic directions to world directions (a rotation). */
    public static Matrix3f frame(java.util.function.UnaryOperator<Vec3> local) {
        Vec3 x = local.apply(new Vec3(1, 0, 0)).normalize(), y = local.apply(new Vec3(0, 1, 0)).normalize(), z = local.apply(new Vec3(0, 0, 1)).normalize();
        return new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the field: {@code view} is the sky pass's camera rotation, {@code frame} takes galactic directions into
     * world directions, {@code alpha} fades the whole field (day, atmosphere).
     */
    public static void render(Matrix4f view, Matrix4f projection, Matrix3f frame, float alpha) {
        render(view, projection, frame, alpha, false);
    }

    /** {@code behindTerrain}: drawn far away with the depth test on, after the world's solid blocks (Earth's sky). */
    private static void render(Matrix4f view, Matrix4f projection, Matrix3f frame, float alpha, boolean behindTerrain) {
        lastFrame.set(frame);
        framed = true;
        if (!ready() || alpha <= .01f) return;
        build();
        Matrix4f m = new Matrix4f(view).mul(new Matrix4f(frame));
        if (behindTerrain) {
            m.scale(Minecraft.getInstance().gameRenderer.getDepthFar() * .85f / SHELL);   // just inside the far plane: behind all terrain the world draws (4x the render distance)
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        } else RenderSystem.disableDepthTest();
        RenderSystem.disableCull();                                            // the quads face inward; the world pass culls back faces
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        buffer.bind();
        buffer.drawWithShader(m, projection, GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        markers(m, alpha);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    private static void build() {
        if (buffer != null && !dirty) return;
        dirty = false;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < ids.length; i++) {
            Vector3f n = new Vector3f(dir[i * 3], dir[i * 3 + 1], dir[i * 3 + 2]);
            double t = Math.max(0, Math.min(1, (Math.log10(Math.max(1e-6, flux[i])) + 2.6) / 4.2));   // dim red dwarfs far away .. near blue giants
            float size = (float) (.06 + .3 * t * t), a = (float) (.4 + .6 * t);
            int primary = GalaxyCatalog.StarType.values()[type(types[i], 0)].color;
            star(b, n, size, primary, a);
            if (count(types[i]) > 1) {                                              // a companion just beside it: one system, two suns
                Vector3f side = perpendicular(n, ids[i].hashCode());
                int second = GalaxyCatalog.StarType.values()[type(types[i], 1)].color;
                star(b, new Vector3f(n).add(side.mul(size * 1.6f / SHELL)).normalize(), size * .7f, second, a * .75f);
            }
        }
        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(b.end());
        VertexBuffer.unbind();
    }

    private static Vector3f perpendicular(Vector3f n, int seed) {
        Vector3f a = Math.abs(n.y) < .9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f s = new Vector3f(n).cross(a).normalize();
        Vector3f u = new Vector3f(n).cross(s).normalize();
        double angle = (seed & 1023) / 1023d * Math.PI * 2;
        return s.mul((float) Math.cos(angle)).add(u.mul((float) Math.sin(angle)));
    }

    /** A soft point: a bright core with a faint halo, facing the centre. */
    private static void star(BufferBuilder b, Vector3f n, float size, int rgb, float alpha) {
        Vector3f c = new Vector3f(n).mul(SHELL);
        Vector3f a = Math.abs(n.y) < .9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f side = new Vector3f(n).cross(a).normalize(), up = new Vector3f(n).cross(side).normalize();
        quad(b, c, side, up, size, rgb, alpha);
        if (size > .14f) quad(b, c, side, up, size * 2.4f, rgb, alpha * .12f);
    }

    private static void quad(BufferBuilder b, Vector3f c, Vector3f side, Vector3f up, float s, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        for (int k = 0; k < 4; k++) {
            float sx = k == 0 || k == 3 ? -s : s, sy = k < 2 ? -s : s;
            b.vertex(c.x + side.x * sx + up.x * sy, c.y + side.y * sx + up.y * sy, c.z + side.z * sx + up.z * sy).color(r, g, bl, alpha).endVertex();
        }
    }

    /** The target's ring and the waypoint's diamond, at their stars. */
    private static void markers(Matrix4f m, float alpha) {
        int t = target.isEmpty() ? -1 : index(target), w = waypoint.isEmpty() ? -1 : index(waypoint);
        if (t < 0 && w < 0) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float pulse = (float) (.75 + .25 * Math.sin(System.currentTimeMillis() / 300d));
        if (t >= 0) ring(b, m, t, 1.15f, 24, 0x9FE6FF, Math.max(.6f, alpha) * pulse);
        if (w >= 0) ring(b, m, w, w == t ? 1.7f : 1.0f, 4, 0xFFD36B, Math.max(.6f, alpha));
        BufferUploader.drawWithShader(b.end());
    }

    private static void ring(BufferBuilder b, Matrix4f m, int i, float radius, int segments, int rgb, float alpha) {
        Vector3f n = new Vector3f(dir[i * 3], dir[i * 3 + 1], dir[i * 3 + 2]), c = new Vector3f(n).mul(SHELL);
        Vector3f a = Math.abs(n.y) < .9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f side = new Vector3f(n).cross(a).normalize(), up = new Vector3f(n).cross(side).normalize();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f, w = .08f;
        for (int k = 0; k < segments; k++) {
            double a0 = k * Math.PI * 2 / segments + (segments == 4 ? Math.PI / 4 : 0), a1 = (k + 1) * Math.PI * 2 / segments + (segments == 4 ? Math.PI / 4 : 0);
            Vector3f p0 = point(c, side, up, a0, radius), p1 = point(c, side, up, a1, radius), q1 = point(c, side, up, a1, radius + w), q0 = point(c, side, up, a0, radius + w);
            for (Vector3f p : new Vector3f[]{p0, p1, q1, q0}) b.vertex(m, p.x, p.y, p.z).color(r, g, bl, alpha).endVertex();
        }
    }

    private static Vector3f point(Vector3f c, Vector3f side, Vector3f up, double angle, float radius) {
        return new Vector3f(c).add(new Vector3f(side).mul((float) Math.cos(angle) * radius)).add(new Vector3f(up).mul((float) Math.sin(angle) * radius));
    }

    // ------------------------------------------------------------------ Earth: in place of vanilla's random stars

    private static VertexBuffer vanillaStars, emptyStars;

    /** Swaps vanilla's random star buffer for an empty one while the catalogue's stars are drawn (and back). */
    private static void vanilla(boolean hide) {
        var mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) return;
        try {
            var field = ObfuscationReflectionHelper.findField(LevelRenderer.class, "f_109471_");
            var current = (VertexBuffer) field.get(mc.levelRenderer);
            if (hide && current != emptyStars) {
                if (emptyStars == null) {
                    BufferBuilder b = Tesselator.getInstance().getBuilder();
                    b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                    for (int k = 0; k < 4; k++) b.vertex(0, -1000, 0).endVertex();         // one invisible point
                    emptyStars = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    emptyStars.bind();
                    emptyStars.upload(b.end());
                    VertexBuffer.unbind();
                }
                vanillaStars = current;
                field.set(mc.levelRenderer, emptyStars);
            } else if (!hide && current == emptyStars && vanillaStars != null) {
                field.set(mc.levelRenderer, vanillaStars);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            DBZenith.LOGGER.warn("Could not swap vanilla's star buffer; Earth keeps vanilla stars", ex);
        }
    }

    @SubscribeEvent
    public static void earthSky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;   // after the sky (which would cover them) and solid terrain (which hides them by depth)
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        boolean earth = level.dimension().equals(Level.OVERWORLD) && mc.gameRenderer.getMainCamera().getPosition().y <= 180;
        vanilla(earth && ready());
        if (!earth || !ready()) return;
        float pt = e.getPartialTick();
        float bright = Math.min(1, level.getStarBrightness(pt) * (1 - level.getRainLevel(pt)) * 2);   // vanilla night stars peak at 0.5
        render(rotationOnly(e.getPoseStack().last().pose()), e.getProjectionMatrix(), earthFrame(level, pt), bright, true);
        RenderSystem.depthMask(true);
    }

    /** The view's rotation alone (no camera translation): the stars are at infinity. */
    private static Matrix4f rotationOnly(Matrix4f pose) {
        return new Matrix4f(pose).setTranslation(0, 0, 0);
    }

    // ------------------------------------------------------------------ targeting

    /** {@code M}: target the star under the crosshair; the same star again makes it the waypoint; nothing there clears it. */
    public static void targetPressed() {
        var mc = Minecraft.getInstance();
        if (!ready() || !framed || mc.player == null) return;
        var look = mc.gameRenderer.getMainCamera().getLookVector();
        Vector3f g = new Matrix3f(lastFrame).transpose().transform(new Vector3f(look));   // into galactic directions
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < ids.length; i++) {
            double dot = g.x * dir[i * 3] + g.y * dir[i * 3 + 1] + g.z * dir[i * 3 + 2];
            if (dot < Math.cos(PICK)) continue;
            double angle = Math.acos(Math.min(1, dot));
            double ease = 1 + .35 * Math.max(0, Math.min(4, Math.log10(Math.max(1e-6, flux[i])) + 2.6));   // brighter stars are easier to pick
            if (angle / ease < bestScore) {
                bestScore = angle / ease;
                best = i;
            }
        }
        if (best < 0) {
            if (!target.isEmpty()) ModNetwork.sendToServer(new StarSelectPacket("", StarSelectPacket.CLEAR));
            clearTarget();
            return;
        }
        boolean again = ids[best].equals(target);
        ModNetwork.sendToServer(new StarSelectPacket(ids[best], again ? StarSelectPacket.WAYPOINT : StarSelectPacket.TARGET));
    }

    /** {@code ,}: the atlas, opened on the targeted star when there is one. */
    public static void mapPressed() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!target.isEmpty()) ModNetwork.sendToServer(new StarSelectPacket(target, StarSelectPacket.MAP));
        else mc.player.connection.sendCommand("galaxy");
    }

    /** The label under the crosshair, shown for a few seconds after targeting (null when quiet). */
    public static String label() {
        if (target.isEmpty() || info.isEmpty() || System.currentTimeMillis() > infoUntil) return null;
        StringBuilder s = new StringBuilder(info.getString("name"));
        var types = info.getList("types", Tag.TAG_STRING);
        s.append("  ·  ").append(types.isEmpty() ? "Star" : types.getString(0));
        if (types.size() == 2) s.append(" binary");
        if (types.size() >= 3) s.append(" trinary");
        s.append("  ·  ").append(String.format(Locale.ROOT, "%,.0f ly", info.getDouble("distance")));
        String state = info.getString("state");
        s.append("  ·  ").append(switch (state) {
            case "charted" -> info.contains("planets") ? "Charted, " + info.getInt("planets") + " planets" : "Charted";
            case "observed" -> "Observed";
            default -> "Unobserved";
        });
        s.append(info.getBoolean("waypoint") ? "  ·  Waypoint" : "  ·  M: waypoint");
        return s.toString();
    }

    public static int labelColor() {
        return 0xFF000000 | (info.contains("color") ? info.getInt("color") : 0xC6E4EF);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        vanilla(false);
        center = "";
        ids = new String[0];
        dir = flux = new float[0];
        types = new short[0];
        target = waypoint = "";
        info = new CompoundTag();
        framed = false;
        dirty = true;
    }
}
