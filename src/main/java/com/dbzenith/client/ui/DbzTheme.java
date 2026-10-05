package com.dbzenith.client.ui;

import com.dbzenith.DBZenith;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The one look every Dragon Block Zenith screen and HUD element shares: dark navy glass framed in gold, slanted
 * ("/") bars and ribbons with an orange accent, and the sprite sheet textures/gui/ui.png (tools/ArtGen.java).
 */
public final class DbzTheme {
    public static final ResourceLocation UI = new ResourceLocation(DBZenith.MOD_ID, "textures/gui/ui.png");

    public static final int TITLE = 0xFFFFD27A;
    public static final int TEXT = 0xFFF0F0F0;
    public static final int DIM = 0xFFA0A8C0;
    public static final int ACCENT = 0xFFFF9A2A;
    public static final int GOOD = 0xFF7CE07C;
    public static final int BAD = 0xFFFF6A5A;
    public static final int BODY = 0xFFE8453A;
    public static final int KI = 0xFF3CC8FF;
    public static final int STAMINA = 0xFFF2C43A;

    /** Radial / HUD icon indices on the sheet (16x16 at (index * 16, 64)). */
    public static final int ICON_TRANSFORM = 0, ICON_POWER_DOWN = 1, ICON_FLY = 2, ICON_OVERDRIVE = 3, ICON_OVERDRIVE_OFF = 4,
            ICON_STATS = 5, ICON_FORMS = 6, ICON_TECHNIQUES = 7, ICON_LIFE = 8, ICON_CLOSE = 9, ICON_ORB = 10, ICON_SETTINGS = 11, ICON_RACIAL = 12;

    private DbzTheme() {}

    // ------------------------------------------------------------------ screens

    /** The Zenith UI sheet (512x512, drawn at a quarter scale): frame, hex tile, buttons, glow. See ArtGen.GuiHd. */
    public static final ResourceLocation UI_HD = new ResourceLocation(DBZenith.MOD_ID, "textures/gui/ui_hd.png");
    private static final int HD = 4;

    /** The ornate Zenith look (the default), or the classic flat one (Settings > Style > UI style). */
    public static boolean zenith() {
        try {
            return com.dbzenith.config.DBZConfig.CLIENT.uiStyle.get() == 0;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Darkened world behind a screen, a little heavier at the bottom; in Zenith, a glow and drifting ki motes. */
    public static void screenBackground(GuiGraphics g, int width, int height) {
        if (!zenith()) {
            g.fillGradient(0, 0, width, height, 0xB0060812, 0xD8020308);
            return;
        }
        g.fillGradient(0, 0, width, height, 0xC8050812, 0xE8010206);
        RenderSystem.enableBlend();
        int gw = (int) (width * 1.1), gh = (int) (height * 1.2);
        g.setColor(0.25f, 0.42f, 0.85f, 0.32f);                                  // a deep blue glow behind the window
        g.blit(UI_HD, (width - gw) / 2, (height - gh) / 2, gw, gh, 256, 0, 256, 256, 512, 512);
        g.setColor(1, 1, 1, 1);
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 46; i++) {                                           // ki motes drifting up
            double seed = i * 12.9898;
            double fx = (Math.sin(seed) * 43758.5453) % 1, speed = 6 + 10 * Math.abs((Math.sin(seed * 1.7) * 9631.1) % 1);
            double x = Math.abs(fx) * width + Math.sin(t * 0.6 + i) * 6;
            double y = height - ((t * speed + i * 37) % (height + 20));
            float a = (float) (0.25 + 0.35 * Math.abs(Math.sin(t * 1.3 + i)));
            int col = i % 3 == 0 ? 0xFFD27A : 0x7CC8FF;
            int s = i % 5 == 0 ? 2 : 1;
            g.fill((int) x, (int) y, (int) x + s, (int) y + s, (int) (a * 255) << 24 | col);
        }
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        if (!zenith()) {
            g.blitNineSliced(UI, x, y, w, h, 8, 8, 32, 32, 112, 0);
            return;
        }
        g.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, 0xEE141C34, 0xF2060A14);  // dark glass
        g.enableScissor(x + 3, y + 3, x + w - 3, y + h - 3);
        for (int ty = y; ty < y + h; ty += 40) for (int tx = x; tx < x + w; tx += 40) {   // the hex lattice, faint
            g.blit(UI_HD, tx, ty, 40, 40, 0, 128, 64, 64, 512, 512);
        }
        g.disableScissor();
        g.fillGradient(x + 3, y + 3, x + w - 3, y + 3 + Math.min(40, h / 3), 0x302A4A8A, 0x002A4A8A);   // light from above
        nine(g, x, y, w, h, 0, 0, 128, 128, 32, false);
    }

    /**
     * A nine-slice from the HD sheet: corners at true size (a quarter scale), edges and the middle stretched.
     * {@code border} is in sheet pixels; {@code middle} draws the centre too.
     */
    public static void nine(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, int border, boolean middle) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1f / HD, 1f / HD, 1);
        int W = w * HD, H = h * HD, b = border, cw = uw - 2 * b, ch = vh - 2 * b;
        int bw = Math.min(b, W / 2), bh = Math.min(b, H / 2);
        g.blit(UI_HD, 0, 0, bw, bh, u, v, bw, bh, 512, 512);
        g.blit(UI_HD, W - bw, 0, bw, bh, u + uw - bw, v, bw, bh, 512, 512);
        g.blit(UI_HD, 0, H - bh, bw, bh, u, v + vh - bh, bw, bh, 512, 512);
        g.blit(UI_HD, W - bw, H - bh, bw, bh, u + uw - bw, v + vh - bh, bw, bh, 512, 512);
        if (W > 2 * bw) {
            g.blit(UI_HD, bw, 0, W - 2 * bw, bh, u + b, v, cw, bh, 512, 512);
            g.blit(UI_HD, bw, H - bh, W - 2 * bw, bh, u + b, v + vh - bh, cw, bh, 512, 512);
        }
        if (H > 2 * bh) {
            g.blit(UI_HD, 0, bh, bw, H - 2 * bh, u, v + b, bw, ch, 512, 512);
            g.blit(UI_HD, W - bw, bh, bw, H - 2 * bh, u + uw - bw, v + b, bw, ch, 512, 512);
        }
        if (middle && W > 2 * bw && H > 2 * bh) g.blit(UI_HD, bw, bh, W - 2 * bw, H - 2 * bh, u + b, v + b, cw, ch, 512, 512);
        g.pose().popPose();
    }

    /** Panel with a slanted title ribbon across its top edge. */
    public static void window(GuiGraphics g, Font font, Component title, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        header(g, font, title, x + w / 2, y - 6);
    }

    /** A slanted orange ribbon, centred on {@code cx}, carrying a title. */
    public static void header(GuiGraphics g, Font font, Component title, int cx, int y) {
        y = Math.max(1, y);                                     // a full-height window keeps its ribbon on screen
        int tw = font.width(title);
        int w = tw + 34, h = 14;
        int x = cx - w / 2;
        slant(g, x - 1, y - 1, w + 2, h + 2, 6, 0xFF2A1404, 0xFF2A1404);
        slant(g, x, y, w, h, 6, 0xFFFFB040, 0xFFB0400E);
        slant(g, x + 3, y + 1, w - 6, 2, 1, 0x70FFFFFF, 0x30FFFFFF);
        if (zenith()) {                                                        // flared, gem-tipped ends
            for (int side = -1; side <= 1; side += 2) {
                float ex = side < 0 ? x - 1 : x + w + 6, ey = y + h / 2f;
                quad(g, ex, ey - 7, ex + side * 9, ey, ex, ey + 7, ex - side * 2, ey, 0xFF2A1404, 0xFF2A1404, 0xFF2A1404, 0xFF2A1404);
                quad(g, ex, ey - 5, ex + side * 7, ey, ex, ey + 5, ex - side * 1, ey, 0xFFFFC860, 0xFFE07018, 0xFFB0400E, 0xFFE07018);
                g.fill((int) (ex + side * 3) - 1, (int) ey - 1, (int) (ex + side * 3) + 2, (int) ey + 2, 0xFFFFF6E0);
            }
        }
        g.drawString(font, title, cx - tw / 2 + 3, y + 3, 0xFFFFF4D8, true);
    }

    /** A thin gold rule with fading ends. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        int mid = x + w / 2;
        hGradient(g, x, y, mid, y + 1, 0x00D8A040, 0xFFD8A040);
        hGradient(g, mid, y, x + w, y + 1, 0xFFD8A040, 0x00D8A040);
        if (zenith()) {                                                        // a diamond at the middle
            quad(g, mid, y - 3, mid + 4, y + 0.5f, mid, y + 4, mid - 4, y + 0.5f, 0xFFFFE6A0, 0xFFD8A040, 0xFF8A5A10, 0xFFD8A040);
            hGradient(g, x + w / 6f, y + 2, mid - 6, y + 3, 0x004AA8E0, 0x604AA8E0);
            hGradient(g, mid + 6, y + 2, x + w - w / 6f, y + 3, 0x604AA8E0, 0x004AA8E0);
        }
    }

    /** Row highlight: a slanted glass strip, gold when selected. */
    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        if (selected) slant(g, x, y, w, h, 4, 0x70FFB040, 0x40B0400E);
        else if (hovered) slant(g, x, y, w, h, 4, 0x40FFFFFF, 0x18FFFFFF);
    }

    /** A button skin: 0 normal, 1 hover, 2 disabled, 3 selected. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, int state) {
        RenderSystem.enableBlend();
        if (zenith() && h >= 8) {
            nine(g, x, y, w, h, 128, state * 48, 96, 48, 12, true);
            return;
        }
        g.blitNineSliced(UI, x, y, w, h, 4, 4, 32, 16, 144, state * 16);
    }

    public static void icon(GuiGraphics g, int index, int x, int y, int size) {
        RenderSystem.enableBlend();
        g.blit(UI, x, y, size, size, index * 16, 64, 16, 16, 256, 256);
    }

    public static void icon(GuiGraphics g, int index, int x, int y, int size, int tint) {
        g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        icon(g, index, x, y, size);
        g.setColor(1, 1, 1, 1);
    }

    // ------------------------------------------------------------------ shapes

    /**
     * A "/" parallelogram: the top edge is shifted {@code skew} pixels right of the bottom edge. Colours run top to
     * bottom.
     */
    public static void slant(GuiGraphics g, float x, float y, float w, float h, float skew, int top, int bottom) {
        quad(g, x + skew, y, x + skew + w, y, x + w, y + h, x, y + h, top, top, bottom, bottom);
    }

    /** A slanted bar filled to {@code frac}, gradient-shaded, with a gloss line along its top. */
    public static void slantBar(GuiGraphics g, float x, float y, float w, float h, float skew, float frac, int color) {
        if (frac <= 0) return;
        float fw = w * Mth.clamp(frac, 0f, 1f);
        // the slant applies to the fill's own height, so a short fill keeps a slanted right edge
        slant(g, x, y, fw, h, skew, brighten(color, 1.25f), darken(color, 0.62f));
        slant(g, x + skew * 0.15f, y + 1, Math.max(0, fw - 2), Math.max(1, h * 0.22f), skew * 0.22f, 0x70FFFFFF, 0x20FFFFFF);
    }

    /** A flat ring segment from {@code startDeg} (0 = right, clockwise) sweeping {@code sweepDeg}. */
    public static void arc(GuiGraphics g, float cx, float cy, float r0, float r1, float startDeg, float sweepDeg, int inner, int outer) {
        if (Math.abs(sweepDeg) < 0.01f) return;
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(4, (int) (Math.abs(sweepDeg) / 4));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * i / steps);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            color(bb.vertex(m, cx + c * r1, cy + s * r1, 0), outer).endVertex();
            color(bb.vertex(m, cx + c * r0, cy + s * r0, 0), inner).endVertex();
        }
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
    }

    /** Any quad, corners clockwise from the top left, one colour per corner. */
    public static void quad(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                            int c0, int c1, int c2, int c3) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        color(bb.vertex(m, x3, y3, 0), c3).endVertex();
        color(bb.vertex(m, x2, y2, 0), c2).endVertex();
        color(bb.vertex(m, x1, y1, 0), c1).endVertex();
        color(bb.vertex(m, x0, y0, 0), c0).endVertex();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
    }

    /** Left-to-right gradient rectangle. */
    public static void hGradient(GuiGraphics g, float x0, float y0, float x1, float y1, int left, int right) {
        quad(g, x0, y0, x1, y0, x1, y1, x0, y1, left, right, right, left);
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer color(com.mojang.blaze3d.vertex.VertexConsumer vc, int argb) {
        return vc.color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24);
    }

    // ------------------------------------------------------------------ text and colour

    /** Text drawn at a scale, left-aligned at (x, y). */
    public static void text(GuiGraphics g, Font font, Component text, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Font font, String text, float x, float y, int color, float scale) {
        text(g, font, Component.literal(text), x, y, color, scale);
    }

    public static void wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        int line = 0;
        for (FormattedCharSequence s : font.split(text, width)) g.drawString(font, s, x, y + line++ * 10, color, false);
    }

    public static int brighten(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f + 18 * (f - 1) * 4));
        int gr = Math.min(255, (int) (((argb >> 8) & 255) * f + 18 * (f - 1) * 4));
        int b = Math.min(255, (int) ((argb & 255) * f + 18 * (f - 1) * 4));
        return (argb & 0xFF000000) | r << 16 | gr << 8 | b;
    }

    public static int darken(int argb, float f) {
        return (argb & 0xFF000000) | (int) (((argb >> 16) & 255) * f) << 16 | (int) (((argb >> 8) & 255) * f) << 8 | (int) ((argb & 255) * f);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        int al = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        return al << 24 | r << 16 | gr << 8 | bl;
    }
}
