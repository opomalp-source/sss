package com.dbzenith.client.space;

import com.dbzenith.client.ui.DbzTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The space HUD's visual language (the space UI rework): dark glass panels with a hairline ki-blue accent, small-caps
 * labels under bright values, key chips, hairline gauges, and every element easing in and out instead of popping.
 * One palette: ki blue for information, amber for caution, red for danger.
 */
public final class SpaceUi {
    private SpaceUi() {}

    public static final int ACCENT = 0x8FE3FF, CAUTION = 0xFFC46B, DANGER = 0xFF6E5A, VALUE = 0xF2F7FA, LABEL = 0x8EA3B5, GLASS = 0x08111B;

    private static final Map<String, double[]> FADES = new HashMap<>();

    /** An element's opacity easing towards {@code shown} (about a fifth of a second either way), on its own clock. */
    public static float fade(String key, boolean shown) {
        double now = System.nanoTime() / 1e9;
        double[] a = FADES.computeIfAbsent(key, k -> new double[]{0, now});
        double step = Math.min(.1, now - a[1]) * 5;
        a[1] = now;
        a[0] = shown ? Math.min(1, a[0] + step) : Math.max(0, a[0] - step);
        return (float) (a[0] * a[0] * (3 - 2 * a[0]));
    }

    public static int argb(int rgb, float alpha) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A dark glass panel: translucent body, a hairline accent along the top that fades out at both ends, a faint base line. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h, float alpha, int accent) {
        if (alpha <= .01f) return;
        g.fill(x, y, x + w, y + h, argb(GLASS, .72f * alpha));
        int mid = x + w / 2;
        DbzTheme.hGradient(g, x, y, mid, y + 1, argb(accent, 0), argb(accent, .9f * alpha));
        DbzTheme.hGradient(g, mid, y, x + w, y + 1, argb(accent, .9f * alpha), argb(accent, 0));
        g.fill(x, y + h - 1, x + w, y + h, argb(0xFFFFFF, .06f * alpha));
    }

    /** Small capitals: a label. */
    public static void caps(GuiGraphics g, String text, float x, float y, int rgb, float alpha) {
        if (alpha <= .01f) return;
        DbzTheme.text(g, font(), text.toUpperCase(Locale.ROOT), x, y, argb(rgb, alpha), .62f);
    }

    public static int capsWidth(String text) {
        return Math.round(font().width(text.toUpperCase(Locale.ROOT)) * .62f);
    }

    /** A value, at a scale. */
    public static void value(GuiGraphics g, String text, float x, float y, int rgb, float alpha, float scale) {
        if (alpha <= .01f) return;
        DbzTheme.text(g, font(), text, x, y, argb(rgb, alpha), scale);
    }

    public static int valueWidth(String text, float scale) {
        return Math.round(font().width(text) * scale);
    }

    /** A key chip: the key in a hairline box, its action in small capitals beside it; returns the width used. */
    public static int chip(GuiGraphics g, String key, String action, int x, int y, float alpha) {
        int kw = capsWidth(key) + 6, aw = capsWidth(action);
        if (alpha > .01f) {
            g.fill(x, y, x + kw, y + 9, argb(0xFFFFFF, .08f * alpha));
            g.renderOutline(x, y, kw, 9, argb(ACCENT, .55f * alpha));
            caps(g, key, x + 3, y + 2.5f, VALUE, alpha);
            caps(g, action, x + kw + 3, y + 2.5f, LABEL, alpha);
        }
        return kw + 3 + aw;
    }

    public static int chipWidth(String key, String action) {
        return capsWidth(key) + 6 + 3 + capsWidth(action);
    }

    /** A hairline gauge with a soft glow at its tip. */
    public static void gauge(GuiGraphics g, int x, int y, int w, float fraction, int rgb, float alpha) {
        if (alpha <= .01f) return;
        g.fill(x, y, x + w, y + 1, argb(0xFFFFFF, .1f * alpha));
        int f = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (f > 0) {
            DbzTheme.hGradient(g, x, y, x + f, y + 1, argb(rgb, .35f * alpha), argb(rgb, alpha));
            g.fill(x + f - 1, y - 1, x + f + 1, y + 2, argb(rgb, .5f * alpha));
        }
    }

    /** A target bracket: four corners round a square of half-size {@code s}. */
    public static void bracket(GuiGraphics g, float cx, float cy, float s, int rgb, float alpha) {
        if (alpha <= .01f) return;
        int c = argb(rgb, alpha), l = Math.round(cx - s), r = Math.round(cx + s), t = Math.round(cy - s), b = Math.round(cy + s), k = (int) Math.max(4, s / 3);
        g.fill(l, t, l + k, t + 1, c); g.fill(l, t, l + 1, t + k, c);
        g.fill(r - k, t, r, t + 1, c); g.fill(r - 1, t, r, t + k, c);
        g.fill(l, b - 1, l + k, b, c); g.fill(l, b - k, l + 1, b, c);
        g.fill(r - k, b - 1, r, b, c); g.fill(r - 1, b - k, r, b, c);
    }

    /** A small filled chevron pointing along (dx, dy). */
    public static void chevron(GuiGraphics g, float x, float y, double dx, double dy, int rgb, float alpha) {
        float s = 6, px = (float) -dy, py = (float) dx;
        DbzTheme.quad(g, x + (float) dx * s, y + (float) dy * s, x + px * s * .7f, y + py * s * .7f, x - (float) dx * s * .2f, y - (float) dy * s * .2f,
                x - px * s * .7f, y - py * s * .7f, argb(rgb, alpha), argb(rgb, alpha), argb(rgb, alpha * .6f), argb(rgb, alpha * .6f));
    }

    /** A caution or danger banner: a glass pill with a coloured lead and small-caps text, centred. */
    public static void banner(GuiGraphics g, String text, int cx, int y, int rgb, float alpha) {
        if (alpha <= .01f) return;
        int w = capsWidth(text) + 18;
        int x = cx - w / 2;
        g.fill(x, y, x + w, y + 11, argb(GLASS, .75f * alpha));
        g.fill(x, y, x + 2, y + 11, argb(rgb, alpha));
        caps(g, text, x + 10, y + 3.5f, rgb, alpha);
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** A key binding's short name ("Left Shift" is Shift, "Left Control" Ctrl). */
    public static String key(net.minecraft.client.KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString().replaceFirst("^(Left|Right) ", "").replace("Control", "Ctrl");
        return s.length() > 9 ? s.substring(0, 9) : s;
    }
}
