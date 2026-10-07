package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ui.PortraitRenderer;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The Saga HUD (CX-16e), the default: a round portrait in a glowing ring (the hair breaks out over the top of it), a
 * Release tab over a smoky main bar carrying BP and Ki, and under it the health bar (red) and the stamina bar (orange to
 * yellow), each with a slanted right end, a white outline and a soft glow, and its value inside.
 * <p>
 * Everything is drawn from textures in textures/gui/hud/ at four times their size on screen ({@link #TEX}); the fills are
 * cut to the bar's shape at the current value instead of being stretched, so their gradient stays as they drain.
 * Stats are only read from {@link PlayerData}. Positions, sizes and colours are the constants below (GUI pixels, before
 * the HUD size setting; the whole HUD also follows the game's GUI scale).
 */
public final class SagaHud {
    // ---------------------------------------------------------- layout (GUI pixels from the top-left corner)
    public static final int X = 4, Y = 4;
    /** The portrait: ring centre, the ring texture's size on screen, the face circle's radius, and room above it for hair. */
    public static final int RING_CX = 24, RING_CY = 26, RING_SIZE = 48, FACE_R = 17, HAIR_ROOM = 16;
    /** Pixels per block for the portrait (how big the head is in the ring) and how far the head sits below the ring centre. */
    public static final float FACE_SCALE = 40f, FACE_DROP = 3f, FACE_TURN = 14f;
    public static final int TAB_X = 50, TAB_Y = 3, TAB_W = 58, TAB_H = 10;
    public static final int MAIN_X = 40, MAIN_Y = 13, MAIN_W = 150, MAIN_H = 14, MAIN_SKEW = 6;
    public static final int HP_X = 50, HP_Y = 30, HP_W = 128, HP_H = 10, HP_SKEW = 5;
    public static final int ST_X = 56, ST_Y = 43, ST_W = 112, ST_H = 9, ST_SKEW = 5;
    /** Text sizes: the tab, the main bar, the bars' values. */
    public static final float TAB_TEXT = 0.7f, MAIN_TEXT = 0.85f, BAR_TEXT = 0.72f;

    // ---------------------------------------------------------- colours
    public static final int TEXT = 0xFFFFFFFF, MAIN_LABEL = 0xFFFFFFFF, OVER_RELEASE = 0xFFFF7050;
    /** The pale trail a bar leaves where it just lost value. */
    public static final float LOSS_TRAIL_ALPHA = 0.55f;
    /** Health under this share pulses. */
    public static final float LOW_HEALTH = 0.25f;

    // ---------------------------------------------------------- textures (4x)
    public static final int TEX = 4;
    private static final ResourceLocation RING = tex("portrait_ring"), TAB = tex("release_tab"), MAIN = tex("main_bar"),
            HP_FRAME = tex("health_frame"), HP_FILL = tex("health_fill"), ST_FRAME = tex("stamina_frame"), ST_FILL = tex("stamina_fill");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(DBZenith.MOD_ID, "textures/gui/hud/" + name + ".png");
    }

    private static final float[] SHOWN = {1, 1}, GHOST = {1, 1}, HOLD = {0, 0};
    private static TextureTarget portraitTarget;

    private SagaHud() {}

    /** Draws the HUD; returns {x, y} where the status chips go. */
    public static int[] render(GuiGraphics g, Font font, Minecraft mc, PlayerData d, DerivedStats s, Form form, boolean held, int aura,
                               float t, float dt, float hudScale) {
        RenderSystem.enableBlend();
        // the Release tab and the main bar
        blit(g, TAB, X + TAB_X, Y + TAB_Y, TAB_W, TAB_H);
        String release = Component.translatable("hud.dbzenith.release", d.getReleasePercent()).getString();
        text(g, font, release, X + TAB_X + TAB_W / 2f - font.width(release) * TAB_TEXT / 2, Y + TAB_Y + 2.5f, d.getReleasePercent() > 100 ? OVER_RELEASE : TEXT, TAB_TEXT);
        blit(g, MAIN, X + MAIN_X - 2, Y + MAIN_Y - 2, MAIN_W + 4, MAIN_H + 4);
        long bp = StatCalculator.battlePower(d);
        String bps = "BP: " + (bp < 0 ? "???" : big(bp));
        String kis = "Ki: " + big(d.getKi());
        float my = Y + MAIN_Y + (MAIN_H - 8 * MAIN_TEXT) / 2f + 0.5f;
        text(g, font, bps, X + RING_CX + RING_SIZE / 2f, my,                             // just right of the ring, which covers the bar's left end
                held ? com.dbzenith.client.ui.DbzTheme.brighten(aura, 1.2f) : MAIN_LABEL, MAIN_TEXT);
        text(g, font, kis, X + MAIN_X + MAIN_W - MAIN_SKEW - 6 - font.width(kis) * MAIN_TEXT, my, MAIN_LABEL, MAIN_TEXT);

        // health and stamina
        float hp = frac(d.getBody(), s.maxBody()), st = frac(d.getStamina(), s.maxStamina());
        ease(0, hp, t, dt);
        ease(1, st, t, dt);
        bar(g, font, HP_FRAME, HP_FILL, X + HP_X, Y + HP_Y, HP_W, HP_H, HP_SKEW, 0, d.getBody(),
                hp < LOW_HEALTH ? 0.75f + 0.25f * Mth.sin(t * 0.6f) : 1f);
        bar(g, font, ST_FRAME, ST_FILL, X + ST_X, Y + ST_Y, ST_W, ST_H, ST_SKEW, 1, d.getStamina(), 1f);
        int below = com.dbzenith.client.ui.SpecialBar.draw(g, font, X + ST_X + 2, Y + ST_Y + ST_H + 4, d, t);   // the special meter (CX-19)
        if (!form.isBase()) {                                                             // the form, small, under the bars
            Component name = Component.literal(Component.translatable(form.translationKey()).getString().toUpperCase());
            text(g, font, name.getString(), X + ST_X + 2, below - 1, com.dbzenith.client.ui.DbzTheme.brighten(0xFF000000 | aura, 1.2f), 0.65f);
            below += 7;
        }
        if (d.isGuarding() || d.getGuardMeter() < 100) {                                  // the guard, as a thin line
            float gf = (float) (d.getGuardMeter() / 100);
            g.fill(X + ST_X + 2, below, X + ST_X + 2 + 84, below + 2, 0xA0000000);
            g.fill(X + ST_X + 2, below, X + ST_X + 2 + (int) (84 * gf), below + 2, gf < 0.3f ? 0xFFFF6A5A : 0xFFAEE6FF);
            below += 4;
        }

        // the portrait last: its ring over the bars' left ends, and the hair over the ring
        if (held || d.isCharging()) {
            float pulse = 0.5f + 0.5f * Mth.sin(t * (d.isCharging() ? 0.8f : 0.25f));
            com.dbzenith.client.ui.DbzTheme.arc(g, X + RING_CX, Y + RING_CY, RING_SIZE / 2f - 2, RING_SIZE / 2f + 4, 0, 360,
                    com.dbzenith.client.ui.DbzTheme.withAlpha(aura, (int) (170 * pulse)), com.dbzenith.client.ui.DbzTheme.withAlpha(aura, 0));
        }
        blit(g, RING, X + RING_CX - RING_SIZE / 2f, Y + RING_CY - RING_SIZE / 2f, RING_SIZE, RING_SIZE);
        portrait(g, mc, X + RING_CX, Y + RING_CY, hudScale);
        return new int[]{X + HP_X, below};
    }

    // ---------------------------------------------------------- pieces

    /** A frame, the fill cut to the value (over a pale trail of what was just lost), and the value in the middle. */
    private static void bar(GuiGraphics g, Font font, ResourceLocation frame, ResourceLocation fill, int x, int y, int w, int h, int skew,
                            int i, double value, float brightness) {
        blit(g, frame, x - 2, y - 2, w + 4, h + 4);
        float fx = x + 1, fy = y + 1, fw = w - 2, fh = h - 2, fskew = skew * (h - 2) / (float) h;
        if (GHOST[i] > SHOWN[i] + 0.002f) {
            RenderSystem.setShaderColor(1.4f, 1.4f, 1.4f, LOSS_TRAIL_ALPHA);
            cut(g, fill, fx, fy, fw, fh, fskew, GHOST[i]);
        }
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1f);
        cut(g, fill, fx, fy, fw, fh, fskew, SHOWN[i]);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        String v = big(value);
        text(g, font, v, x + (w - skew) / 2f - font.width(v) * BAR_TEXT / 2, y + (h - 8 * BAR_TEXT) / 2f + 0.5f, TEXT, BAR_TEXT);
    }

    /**
     * Draws the part of a fill texture up to {@code frac} of the bar, cut along a line parallel to the bar's slanted end,
     * so the end stays slanted and the texture is never stretched.
     */
    private static void cut(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, float skew, float frac) {
        if (frac <= 0.001f) return;
        g.flush();
        float top = w * Math.min(1f, frac);                                               // how far the fill reaches along the top
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        if (top >= skew) {                                                                // a quad: the cut runs the full height
            uv(b, m, x, y, x, y, w, h);
            uv(b, m, x + top - skew, y + h, x, y, w, h);
            uv(b, m, x + top, y, x, y, w, h);
            uv(b, m, x, y, x, y, w, h);
            uv(b, m, x, y + h, x, y, w, h);
            uv(b, m, x + top - skew, y + h, x, y, w, h);
        } else {                                                                          // nearly empty: a sliver of a triangle
            uv(b, m, x, y, x, y, w, h);
            uv(b, m, x, y + h * top / skew, x, y, w, h);
            uv(b, m, x + top, y, x, y, w, h);
        }
        BufferUploader.drawWithShader(b.end());
    }

    private static void uv(BufferBuilder b, Matrix4f m, float px, float py, float x, float y, float w, float h) {
        b.vertex(m, px, py, 0).uv((px - x) / w, (py - y) / h).endVertex();
    }

    /**
     * The live portrait, masked round: drawn into an offscreen buffer, then copied back through a shape that is the face
     * circle below the ring's middle and open above it, so the hair can rise over the ring.
     */
    private static void portrait(GuiGraphics g, Minecraft mc, float cx, float cy, float hudScale) {
        if (mc.player == null) return;
        Window win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight();
        if (portraitTarget == null) portraitTarget = new TextureTarget(W, H, true, Minecraft.ON_OSX);
        else if (portraitTarget.width != W || portraitTarget.height != H) portraitTarget.resize(W, H, Minecraft.ON_OSX);
        g.flush();
        portraitTarget.setClearColor(0, 0, 0, 0);
        portraitTarget.clear(Minecraft.ON_OSX);
        portraitTarget.bindWrite(true);
        int[] clip = {(int) ((cx - FACE_R - 8) * hudScale), (int) ((cy - FACE_R - HAIR_ROOM) * hudScale),
                (int) ((cx + FACE_R + 8) * hudScale), (int) ((cy + FACE_R + 1) * hudScale)};
        PortraitRenderer.draw(g, mc.player, (int) cx, (int) (cy + FACE_DROP), FACE_SCALE, FACE_TURN, clip);
        mc.getMainRenderTarget().bindWrite(true);

        Matrix4f m = g.pose().last().pose();
        double gs = win.getGuiScale();
        RenderSystem.setShaderTexture(0, portraitTarget.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        float r = FACE_R, top = cy - r - HAIR_ROOM;
        screen(b, m, gs, W, H, cx - r, top);                                            // open above the middle: room for the hair
        screen(b, m, gs, W, H, cx - r, cy);
        screen(b, m, gs, W, H, cx + r, top);
        screen(b, m, gs, W, H, cx + r, top);
        screen(b, m, gs, W, H, cx - r, cy);
        screen(b, m, gs, W, H, cx + r, cy);
        int seg = 24;                                                                     // round below it
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * k / seg, a1 = Math.PI * (k + 1) / seg;
            screen(b, m, gs, W, H, cx, cy);
            screen(b, m, gs, W, H, cx + (float) (Math.cos(a1) * r), cy + (float) (Math.sin(a1) * r));
            screen(b, m, gs, W, H, cx + (float) (Math.cos(a0) * r), cy + (float) (Math.sin(a0) * r));
        }
        BufferUploader.drawWithShader(b.end());
    }

    /** A vertex at a GUI point, sampling the offscreen buffer at the same spot on screen. */
    private static void screen(BufferBuilder b, Matrix4f m, double guiScale, int W, int H, float x, float y) {
        Vector4f p = m.transform(new Vector4f(x, y, 0, 1));
        float u = (float) (p.x * guiScale / W), v = 1f - (float) (p.y * guiScale / H);
        b.vertex(m, x, y, 0).uv(u, v).endVertex();
    }

    /** A texture drawn at a quarter of its size, so 4 texture pixels make one GUI pixel. */
    private static void blit(GuiGraphics g, ResourceLocation tex, float x, float y, int w, int h) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1f / TEX, 1f / TEX, 1);
        g.blit(tex, 0, 0, 0, 0, w * TEX, h * TEX, w * TEX, h * TEX);
        g.pose().popPose();
    }

    /** Minecraft's own font with its shadow, at a scale. */
    private static void text(GuiGraphics g, Font font, String s, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Big numbers in words: 9,240,000,000 is "9.24 Billion", 44,000,000 "44 Million"; under 10,000 as they are. */
    public static String big(double v) {
        double a = Math.abs(v);
        String[] names = {"Thousand", "Million", "Billion", "Trillion", "Quadrillion"};
        if (a < 10_000) return String.valueOf((long) Math.floor(v));
        int i = Math.min(names.length - 1, (int) (Math.log10(a) / 3) - 1);
        double n = v / Math.pow(1000, i + 1);
        String num = String.format(java.util.Locale.ROOT, "%.2f", n).replaceAll("\\.?0+$", "");
        return num + " " + names[i];
    }

    private static float frac(double v, double max) {
        return max <= 0 ? 0 : (float) Mth.clamp(v / max, 0, 1);
    }

    /** Drops at once on a loss and leaves a trail that drains after a moment; rises smoothly. */
    private static void ease(int i, float f, float t, float dt) {
        if (f < SHOWN[i]) {
            if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];
            SHOWN[i] = f;
            HOLD[i] = t + 12;
        } else {
            SHOWN[i] += (f - SHOWN[i]) * Math.min(1f, dt * 0.35f);
        }
        if (t > HOLD[i]) GHOST[i] = Math.max(SHOWN[i], GHOST[i] - dt * 0.025f);
        if (GHOST[i] < SHOWN[i]) GHOST[i] = SHOWN[i];
    }
}
