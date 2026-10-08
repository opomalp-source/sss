package com.dbzenith.client.ui;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPvp;
import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The crosshair (CX-20), in place of vanilla's:
 * <ul>
 *   <li><b>in PvP</b>: a small dot (with a faint dark rim so it reads on anything). In third person it sits where your
 *       character is actually aiming (what they look at, seen from the shoulder camera), not at the middle of the screen;</li>
 *   <li><b>out of PvP</b>: a small four-pointed star, like the stars on a Dragon Ball (or the dot, or vanilla's).</li>
 * </ul>
 * Client config: {@code crosshairDotMode} (pvp only, or always), {@code crosshairStyle} (out of PvP: star, dot, vanilla),
 * {@code crosshairSize}, {@code crosshairColor}, {@code crosshairOpacity}. While the F3 screen is up, vanilla's axes stay.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class Crosshair implements IGuiOverlay {
    /** The colour choices (Settings → Style). */
    public static final int[] COLORS = {0xB4B4B4, 0xFFFFFF, 0xFFD34A, 0xFF5A4A, 0x7CE0FF, 0x7CE07C, 0xFF8AE0};
    public static final String[] COLOR_NAMES = {"grey", "white", "gold", "red", "cyan", "green", "pink"};
    public static final int STAR = 0, DOT = 1, VANILLA = 2;

    private static double fov = 70;

    private enum Shape { DOT, STAR, VANILLA }

    static Shape shape() {
        DBZConfig.Client c = DBZConfig.CLIENT;
        if (ClientPvp.on() || c.crosshairDotMode.get() == 1) return Shape.DOT;
        return switch (c.crosshairStyle.get()) {
            case DOT -> Shape.DOT;
            case VANILLA -> Shape.VANILLA;
            default -> Shape.STAR;
        };
    }

    /** Ours replaces vanilla's (except vanilla's own style, and the F3 axes). */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug && !mc.options.reducedDebugInfo().get()) return;
        if (shape() != Shape.VANILLA) event.setCanceled(true);
    }

    /** The field of view as it ends up (after every mod's changes): the aim point in third person needs it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        fov = event.getFOV();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || com.dbzenith.client.ClientFusion.cinematic()) return;
        if (mc.options.renderDebug && !mc.options.reducedDebugInfo().get()) return;
        Shape s = shape();
        if (s == Shape.VANILLA) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        boolean first = mc.options.getCameraType().isFirstPerson();
        if (!first && !(ClientPvp.on() && !mc.options.getCameraType().isMirrored())) return;   // third person: only the PvP view aims
        if (mc.player.isSpectator() && mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.ENTITY) return;
        float x = width / 2f, y = height / 2f;
        if (!first) {                                                       // where the character really aims
            Vec3 aim = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation()
                    : mc.player.getEyePosition(partial).add(mc.player.getViewVector(partial).scale(64));
            Vec3 v = aim.subtract(cam.getPosition());
            Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
            double z = v.x * look.x() + v.y * look.y() + v.z * look.z();
            if (z > 0.1) {
                double focal = (height / 2.0) / Math.tan(Math.toRadians(fov) / 2);
                double rx = -(v.x * left.x() + v.y * left.y() + v.z * left.z()), uy = v.x * up.x() + v.y * up.y() + v.z * up.z();
                x = (float) Mth.clamp(width / 2.0 + rx / z * focal, 0, width);
                y = (float) Mth.clamp(height / 2.0 - uy / z * focal, 0, height);
            }
        }
        DBZConfig.Client c = DBZConfig.CLIENT;
        int a = (int) (Mth.clamp(c.crosshairOpacity.get(), 0, 1) * 255);
        if (a <= 2) return;
        int color = a << 24 | COLORS[Mth.clamp(c.crosshairColor.get(), 0, COLORS.length - 1)];
        int size = c.crosshairSize.get();
        // drawn on the real screen pixels, not the GUI's coarse grid, so the dot is round and the star's points taper
        double gs = mc.getWindow().getGuiScale();
        g.pose().pushPose();
        g.pose().scale((float) (1 / gs), (float) (1 / gs), 1);
        int cx = (int) Math.round(x * gs), cy = (int) Math.round(y * gs);
        int rimColor = (int) (a * 0.4f) << 24 | 0x101018;
        if (s == Shape.DOT) dot(g, cx, cy, Math.max(1, size * gs / 6), color, rimColor);   // a small dot: size 3 is about one GUI pixel across
        else star(g, cx, cy, size * gs, gs, color, rimColor);
        g.pose().popPose();
    }

    /** A round dot of radius {@code r} screen pixels, ringed by a one-pixel dark rim. */
    static void dot(GuiGraphics g, int cx, int cy, double r, int color, int rim) {
        disc(g, cx, cy, r + 1, rim);
        disc(g, cx, cy, r, color);
    }

    static void disc(GuiGraphics g, int cx, int cy, double r, int color) {
        int n = (int) Math.ceil(r);
        for (int dy = -n; dy < n; dy++) {
            double yy = dy + 0.5, half = Math.sqrt(Math.max(0, r * r - yy * yy));
            int x0 = (int) Math.round(cx - half), x1 = (int) Math.round(cx + half);
            if (x1 > x0) g.fill(x0, cy + dy, x1, cy + dy + 1, color);
        }
    }

    /**
     * A four-pointed star like a Dragon Ball's: points {@code reach} screen pixels long that taper to a fine tip from a
     * body {@code gs} wide, a one-pixel dark rim round it.
     */
    static void star(GuiGraphics g, int cx, int cy, double reach, double gs, int color, int rim) {
        double base = Math.max(1.5, gs * 0.85);
        points(g, cx, cy, reach + 1, base + 1, rim);
        points(g, cx, cy, reach, base, color);
    }

    static void points(GuiGraphics g, int cx, int cy, double reach, double base, int color) {
        int n = (int) Math.ceil(reach);
        for (int i = -n; i < n; i++) {
            double d = Math.abs(i + 0.5), half = base * Math.max(0, 1 - d / reach);
            if (half < 0.35) continue;
            int lo = (int) Math.round(-half), hi = (int) Math.round(half);
            if (hi <= lo) hi = lo + 1;
            g.fill(cx + lo, cy + i, cx + hi, cy + i + 1, color);              // the vertical points
            if (Math.abs(i + 0.5) > base) g.fill(cx + i, cy + lo, cx + i + 1, cy + hi, color);   // the horizontal ones (not twice over the middle)
        }
    }
}
