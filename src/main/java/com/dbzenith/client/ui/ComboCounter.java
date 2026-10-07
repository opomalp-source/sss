package com.dbzenith.client.ui;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The combo counter (CX-19 phase 8): beside the crosshair, the hits of your running combo popping as they land, the
 * damage it has done, and a thin bar running down to when it drops. Client config {@code comboCounter}.
 */
public final class ComboCounter {
    private static int lastCombo;
    private static float comboAt;
    private static double damage;
    private static long lastHitTick;

    private ComboCounter() {}

    /** A damage number came in: if it was ours, it counts toward the combo. */
    public static void damage(int attackerId, float amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || attackerId != mc.player.getId()) return;
        if (ClientPlayerData.get().getComboHits() <= 1 && mc.level.getGameTime() - lastHitTick > window()) damage = 0;
        damage += amount;
        lastHitTick = mc.level.getGameTime();
    }

    private static int window() {
        try {
            return DBZConfig.SERVER.comboResetTicks.get();
        } catch (IllegalStateException e) {
            return 30;
        }
    }

    public static void render(GuiGraphics g, Font font, int width, int height, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DBZConfig.CLIENT.comboCounter.get()) return;
        int combo = ClientPlayerData.get().getComboHits();
        if (combo != lastCombo) {
            if (combo > lastCombo) comboAt = t;
            if (combo <= 1) damage = combo == 1 ? damage : 0;
            lastCombo = combo;
        }
        if (combo < 2) return;
        float pop = Math.max(0, 1 - (t - comboAt) / 5f);
        float scale = 2.2f + 0.9f * pop * pop;
        String n = String.valueOf(combo);
        int color = combo >= 10 ? 0xFFFF5030 : combo >= 5 ? DbzTheme.ACCENT : DbzTheme.TITLE;
        float cx = width / 2f + 22, cy = height / 2f - 30;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, n, -font.width(n) / 2, -4, color, true);
        g.pose().popPose();
        float right = cx + font.width(n) * scale / 2 + 2;
        DbzTheme.text(g, font, Component.translatable("hud.dbzenith.hits"), right, cy - 4, DbzTheme.TEXT, 0.9f);
        if (damage > 0) DbzTheme.text(g, font, Component.translatable("hud.dbzenith.combo_damage", compact(damage)), right, cy + 5, DbzTheme.DIM, 0.7f);
        float left = 1 - Mth.clamp((mc.level.getGameTime() - lastHitTick) / (float) window(), 0, 1);  // until the combo drops
        if (lastHitTick > 0 && left > 0) {
            float bx = cx - 10, by = cy + 14, bw = 44;
            g.fill((int) bx, (int) by, (int) (bx + bw), (int) by + 2, 0x90000000);
            g.fill((int) bx, (int) by, (int) (bx + bw * left), (int) by + 2, color);
        }
    }

    static String compact(double v) {
        if (v < 1000) return String.valueOf(Math.round(v));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format("%.1f", v) : String.valueOf((int) v)) + units[u];
    }
}
