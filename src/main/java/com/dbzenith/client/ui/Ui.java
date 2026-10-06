package com.dbzenith.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * UI v3 (CX-16): the clean look. Flat dark glass cards with soft rounded corners and a hairline border, generous
 * spacing, one warm gold accent for what is selected or primary, muted text for labels, and a calm backdrop (a deep
 * gradient with one soft glow) instead of busy textures. Widgets: {@link UiButton}, {@link UiSlider}.
 */
public final class Ui {
    public static final int BG_TOP = 0xFF0B1220, BG_BOTTOM = 0xFF060910;
    public static final int CARD = 0xCC121826, CARD_HOVER = 0xD81A2232;
    public static final int LINE = 0x30FFFFFF, LINE_SOFT = 0x16FFFFFF;
    public static final int GOLD = 0xFFFFB547, GOLD_DEEP = 0xFFE08A2A;
    public static final int TEXT = 0xFFEDEFF5, MUTED = 0xFF8E97AB, FAINT = 0xFF5C6478, DARK_TEXT = 0xFF1C1408;

    private Ui() {}

    /** The full-screen backdrop: a deep blue-black gradient, a soft glow where the eye should go, a darker floor. */
    public static void backdrop(GuiGraphics g, int width, int height, float glowX, float glowY, int glowRgb) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        RenderSystem.enableBlend();
        int gw = (int) (Math.min(width, height) * 1.3f);
        g.setColor(((glowRgb >> 16) & 255) / 255f, ((glowRgb >> 8) & 255) / 255f, (glowRgb & 255) / 255f, 0.22f);
        g.blit(DbzTheme.UI_HD, (int) (glowX - gw / 2f), (int) (glowY - gw / 2f), gw, gw, 256, 0, 256, 256, 512, 512);
        g.setColor(1, 1, 1, 1);
        g.fillGradient(0, (int) (height * 0.72f), width, height, 0x00000000, 0x50000000);
    }

    /** A filled rectangle with its corners rounded off by {@code r} pixels (1 or 2). */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0 || w < 2 * r + 1 || h < 2 * r + 1) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {                                         // the rows that round the corners
            int inset = r - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** A one-pixel outline that follows {@link #round}. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 2 || h <= 2) return;
        r = Math.min(r, 2);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        if (r == 2) {                                                         // the corner pixels
            g.fill(x + 1, y + 1, x + 2, y + 2, color);
            g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
            g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
            g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
        }
    }

    /** A glass card: dark fill, hairline border, a faint lighter rim along the top. */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x, y, w, h, 2, CARD);
        outline(g, x, y, w, h, 2, LINE_SOFT);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x14FFFFFF);
    }

    /** A selectable tile (race, build, path): glass, lighter on hover, gold-rimmed and warm when selected. */
    public static void tile(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        round(g, x, y, w, h, 2, selected ? 0x40FFB547 : hovered ? 0x22FFFFFF : 0x12FFFFFF);
        outline(g, x, y, w, h, 2, selected ? GOLD : hovered ? LINE : LINE_SOFT);
    }

    /** A section heading: small gold capitals with a hairline running on to the right. */
    public static void section(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        String s = title.getString().toUpperCase(java.util.Locale.ROOT);
        float scale = 0.75f;
        DbzTheme.text(g, font, s, x, y, GOLD, scale);
        int tw = (int) (font.width(s) * scale);
        if (tw + 6 < w) g.fill(x + tw + 5, y + 3, x + w, y + 4, LINE_SOFT);
    }

    public static void text(GuiGraphics g, Font font, Component text, float x, float y, int color, float scale) {
        DbzTheme.text(g, font, text, x, y, color, scale);
    }

    public static void centered(GuiGraphics g, Font font, Component text, float cx, float y, int color, float scale) {
        DbzTheme.text(g, font, text, cx - font.width(text) * scale / 2f, y, color, scale);
    }

    /** Word-wrapped text; returns the height used. */
    public static int paragraph(GuiGraphics g, Font font, Component text, int x, int y, int w, int color, float scale, int maxLines) {
        var lines = font.split(text, (int) (w / scale));
        int n = Math.min(lines.size(), maxLines), lh = (int) Math.ceil(10 * scale);
        for (int i = 0; i < n; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y + i * lh, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, lines.get(i), 0, 0, color, false);
            g.pose().popPose();
        }
        return n * lh;
    }

    /**
     * A colour swatch: a rounded square with a dark rim; the chosen one wears a white ring and a gold halo.
     * {@code color} -1 draws "none / your own" (a slashed square).
     */
    public static void swatch(GuiGraphics g, int x, int y, int size, int color, boolean selected, boolean hovered) {
        if (selected) round(g, x - 2, y - 2, size + 4, size + 4, 2, GOLD);
        round(g, x - 1, y - 1, size + 2, size + 2, 1, selected ? 0xFFFFFFFF : hovered ? 0xFFB8C0D0 : 0xFF000000);
        if (color < 0) {
            round(g, x, y, size, size, 1, 0xFF3A4152);
            for (int i = 1; i < size - 1; i++) g.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, 0xFFC86060);
        } else {
            round(g, x, y, size, size, 1, 0xFF000000 | color);
            g.fill(x + 1, y + 1, x + size - 1, y + 2, 0x30FFFFFF);
        }
    }

    /** A row of swatches; returns the index under the mouse, or -1. {@code chosen} is the selected colour. */
    public static int swatches(GuiGraphics g, int x, int y, int size, int gap, int[] colors, int chosen, double mx, double my) {
        int hover = -1;
        for (int i = 0; i < colors.length; i++) {
            int sx = x + i * (size + gap);
            boolean over = mx >= sx && mx < sx + size && my >= y && my < y + size;
            if (over) hover = i;
            swatch(g, sx, y, size, colors[i], colors[i] == chosen, over);
        }
        return hover;
    }

    /** The swatch index at a point, or -1 (for mouse clicks). */
    public static int swatchAt(double mx, double my, int x, int y, int size, int gap, int count) {
        for (int i = 0; i < count; i++) {
            int sx = x + i * (size + gap);
            if (mx >= sx && mx < sx + size && my >= y && my < y + size) return i;
        }
        return -1;
    }

    /** A soft oval of light on the floor under a figure. */
    public static void platform(GuiGraphics g, int cx, int cy, int rx, int ry, int rgb) {
        for (int k = 0; k < 4; k++) {                                         // nested ovals, each adding light towards the heart
            int a = rx * (4 - k) / 4, b = Math.max(1, ry * (4 - k) / 4), alpha = 22 + k * 10;
            for (int dy = -b; dy <= b; dy++) {
                double t = dy / (double) b;
                int half = (int) Math.round(a * Math.sqrt(Math.max(0, 1 - t * t)));
                if (half > 0) g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, alpha << 24 | rgb);
            }
        }
    }
}
