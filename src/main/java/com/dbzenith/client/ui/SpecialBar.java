package com.dbzenith.client.ui;

import com.dbzenith.combat.engine.SpecialMeter;
import com.dbzenith.data.PlayerData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The special meter on the HUD (CX-19): a row of slanted segments, one a bar of 100. Full bars burn gold and pulse;
 * the bar filling is pale. The number of full bars sits at the end. Shared by every HUD style.
 */
public final class SpecialBar {
    public static final int SEG_W = 24, SEG_H = 4, GAP = 3, SKEW = 2;
    public static final int FULL = 0xFFFFC83A, FULL_HOT = 0xFFFFF0A0, FILLING = 0xFFB89A50, EMPTY = 0xB0080B12, EDGE = 0xF0040508;

    private SpecialBar() {}

    /** Draws the meter with its top-left at x, y; returns the y below it. */
    public static int draw(GuiGraphics g, Font font, int x, int y, PlayerData d, float t) {
        double v = d.getSpecial();
        int bars = Math.max((int) Math.round(SpecialMeter.max() / SpecialMeter.BAR), (int) Math.ceil(v / SpecialMeter.BAR));
        int full = (int) Math.floor(v / SpecialMeter.BAR + 1e-6);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 0.35f);
        for (int i = 0; i < bars; i++) {
            int sx = x + i * (SEG_W + GAP);
            DbzTheme.slant(g, sx - 1, y - 1, SEG_W + 2, SEG_H + 2, SKEW, EDGE, EDGE);
            DbzTheme.slantBar(g, sx, y, SEG_W, SEG_H, SKEW, 1f, EMPTY);
            float f = i < full ? 1f : i == full ? (float) ((v - full * SpecialMeter.BAR) / SpecialMeter.BAR) : 0f;
            if (f > 0) DbzTheme.slantBar(g, sx, y, SEG_W, SEG_H, SKEW, f, i < full ? DbzTheme.mix(FULL, FULL_HOT, pulse) : FILLING);
        }
        if (full > 0) {
            String n = "x" + full;
            DbzTheme.text(g, font, n, x + bars * (SEG_W + GAP) + 1, y - 1.5f, DbzTheme.mix(FULL, FULL_HOT, pulse), 0.62f);
        }
        return y + SEG_H + 4;
    }
}
