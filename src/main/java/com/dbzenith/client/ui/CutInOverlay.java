package com.dbzenith.client.ui;

import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Transformation cut-in: a slanted band in your aura colour slashes across the screen with speed lines, your live
 * portrait on it and the form's name sliding in, then sweeps away. About two seconds; the game never stops.
 */
public final class CutInOverlay implements IGuiOverlay {
    private static final float IN = 5, HOLD_END = 30, OUT = 38;
    private static float startedAt = -1;
    private static Component name = Component.empty();
    private static int color = 0xFFFFFFFF;

    /** Start a cut-in for your own transformation. */
    public static void play(Component formName, int auraColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DBZConfig.CLIENT.transformCutIn.get()) return;
        startedAt = mc.level.getGameTime();
        name = formName;
        color = 0xFF000000 | auraColor;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (startedAt < 0 || mc.level == null || mc.player == null || mc.options.hideGui) return;
        float e = mc.level.getGameTime() + partial - startedAt;
        if (e > OUT || e < 0) {
            startedAt = -1;
            return;
        }
        float slideIn = 1 - ease(Math.min(1f, e / IN));                   // 1 -> 0
        float slideOut = e > HOLD_END ? ease((e - HOLD_END) / (OUT - HOLD_END)) : 0;
        float shift = -slideIn * width * 1.2f + slideOut * width * 1.2f;
        float alpha = 1 - slideOut;

        float bandH = Math.min(78, height * 0.3f), skew = bandH * 0.55f;
        float top = height * 0.42f - bandH / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // dim the world a touch while the band holds
        int dim = (int) (70 * alpha * (1 - slideIn));
        g.fill(0, 0, width, height, dim << 24);

        // the band (and its speed lines) cut across at a slight angle
        g.pose().pushPose();
        g.pose().translate(width / 2f, top + bandH / 2, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-5));
        g.pose().translate(-width / 2f, -(top + bandH / 2), 0);
        float x0 = -skew - 40 + shift, w = width + skew * 2 + 80;
        int a = (int) (235 * alpha);
        DbzTheme.slant(g, x0, top - 3, w, bandH + 6, skew, DbzTheme.withAlpha(0xFFE0B0, a), DbzTheme.withAlpha(0xD8A040, a));
        DbzTheme.slant(g, x0, top, w, bandH, skew, DbzTheme.withAlpha(DbzTheme.darken(color, 0.85f), a), DbzTheme.withAlpha(DbzTheme.darken(color, 0.3f), a));
        // speed lines racing left along the band
        for (int i = 0; i < 18; i++) {
            float ly = top + 4 + (bandH - 8) * ((i * 37 % 17) / 17f);
            float len = 40 + (i * 53 % 90);
            float sx = Math.floorMod((int) (i * 211 - e * (28 + i % 5 * 6)), width + 200) - 100;
            DbzTheme.hGradient(g, sx, ly, sx + len, ly + (i % 3 == 0 ? 2 : 1), DbzTheme.withAlpha(0xFFFFFF, (int) (210 * alpha)), 0x00FFFFFF);
        }
        g.pose().popPose();
        // the fighter, framed by the band
        int pcx = (int) (width * 0.27f + shift);
        PortraitRenderer.draw(g, mc.player, pcx, (int) (top + bandH * 0.55f), bandH * 1.05f, 28f,
                new int[]{0, (int) top, width, (int) (top + bandH)});
        // the name sliding in from the right, a beat later
        float textIn = 1 - ease(Mth.clamp((e - 3) / 6f, 0f, 1f));
        float scale = Math.min(3f, (width * 0.5f) / Math.max(1, mc.font.width(name)));
        float tx = width * 0.46f + textIn * width * 0.6f + shift;
        float ty = top + bandH / 2 - 4 * scale;
        Component label = Component.translatable("cutin.dbzenith.transform");
        DbzTheme.text(g, mc.font, label, tx + 4, ty - 10, DbzTheme.withAlpha(0xFFE6A0, (int) (255 * alpha)), 0.9f);
        DbzTheme.text(g, mc.font, name, tx, ty, DbzTheme.withAlpha(0xFFFFFF, Math.max(5, (int) (255 * alpha))), scale);
        g.pose().popPose();
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
