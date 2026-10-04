package com.dbzenith.client;

import com.dbzenith.data.PlayerData;
import com.dbzenith.skill.Technique;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The DBZ HUD: body / ki / stamina bars, power level, release %, status (charging, flying, guard),
 * combo counter and the selected technique with its cooldown. Drawn with flat fills (placeholder style;
 * see ASSETS_TODO.md for the textured version).
 */
public final class DbzHud implements IGuiOverlay {
    private static final int BAR_W = 112;
    private static final int BAR_H = 7;
    private static final int FRAME = 0xC0101018;
    private static final int TRACK = 0xFF2A2A33;
    private static final int BODY = 0xFFE0453A;
    private static final int KI = 0xFF3CC8FF;
    private static final int STAMINA = 0xFFF2C43A;
    private static final int RELEASE = 0xFFFF8A2A;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug || mc.player == null || !ClientPlayerData.hasData()) return;
        PlayerData d = ClientPlayerData.get();
        DerivedStats s = d.getDerived();
        Font font = mc.font;
        long time = mc.level == null ? 0 : mc.level.getGameTime();

        int x = 6;
        int y = 6;
        boolean formLine = d.isTransformed() || d.getOverdriveLevel() > 0;
        g.fill(x - 3, y - 3, x + BAR_W + 52, y + (formLine ? 76 : 64), FRAME);

        g.drawString(font, Component.translatable("hud.dbzenith.power_level", String.format("%,d", StatCalculator.battlePower(d))), x, y, TEXT);
        y += 11;
        y = bar(g, font, x, y, "BODY", d.getBody(), s.maxBody(), BODY);
        y = bar(g, font, x, y, "KI", d.getKi(), s.maxKi(), KI);
        y = bar(g, font, x, y, "STA", d.getStamina(), s.maxStamina(), STAMINA);

        // release %
        Component release = Component.translatable("hud.dbzenith.release", d.getReleasePercent());
        g.drawString(font, release, x, y, RELEASE);
        int rx = x + font.width(release) + 4;
        int rEnd = x + 26 + BAR_W;
        g.fill(rx, y + 2, rEnd, y + 6, TRACK);
        g.fill(rx, y + 2, rx + (int) ((rEnd - rx) * d.getReleasePercent() / 100f), y + 6, RELEASE);
        y += 12;

        // form + overdrive
        Form form = Forms.byId(d.getFormId());
        if (!form.isBase() || d.getOverdriveLevel() > 0) {
            int fx = x;
            if (!form.isBase()) {
                Component name = Component.translatable(form.translationKey());
                g.drawString(font, name, fx, y, 0xFF000000 | (form.hairColor() >= 0 ? form.hairColor() : form.auraColor()));
                fx += font.width(name) + 4;
                g.drawString(font, String.format("M%.0f%%", d.getMastery(form.id())), fx, y, DIM);
                fx += 30;
            }
            if (d.getOverdriveLevel() > 0) {
                chip(g, font, fx, y, Component.translatable("hud.dbzenith.overdrive", String.format("%.0f", FormMath.overdriveMultiplier(d))), 0xFFFF4030);
            }
            y += 12;
        }

        // status chips
        int cx = x;
        if (d.isCharging()) {
            int pulse = 0xFF000000 | (int) (180 + 75 * Mth.sin(time * 0.6f)) << 8 | 0xFF;
            cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.charging"), pulse);
        }
        if (d.isFlying()) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.flying"), KI);
        if (d.isGuarding()) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.guard"), STAMINA);
        if (d.isChargingHeavy()) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.heavy_charging"), 0xFFFF6040);
        else if (d.getHeavyArmedMultiplier() > 0) {
            cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.heavy_ready", String.format("%.1f", d.getHeavyArmedMultiplier())), 0xFFFF6040);
        }

        // combo
        if (d.getComboHits() >= 2) {
            String combo = d.getComboHits() + " HIT";
            g.pose().pushPose();
            g.pose().translate(width / 2f + 24, height / 2f - 24, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.drawString(font, combo, 0, 0, 0xFFFFD040);
            g.pose().popPose();
        }

        // technique panel, right of the hotbar
        Technique t = ClientCombatState.selected();
        int tx = width / 2 + 96;
        int ty = height - 23;
        int tw = 104;
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + 20, FRAME);
        if (t == null) {
            g.drawString(font, Component.translatable("hud.dbzenith.empty_deck"), tx + 2, ty + 5, DIM);
        } else {
            g.drawString(font, Component.translatable(t.translationKey()), tx + 2, ty + 1, 0xFF000000 | t.color());
            g.drawString(font, Component.translatable("hud.dbzenith.technique_keys", ClientCombatState.selectedSlot() + 1, d.deckView().size()),
                    tx + 2, ty + 10, DIM);
            float cd = ClientCombatState.cooldownFraction(t, time);
            if (cd > 0) g.fill(tx, ty + 18, tx + (int) (tw * cd), ty + 20, 0xFFFFFFFF);
        }
    }

    private static int bar(GuiGraphics g, Font font, int x, int y, String label, double value, double max, int color) {
        g.drawString(font, label, x, y, DIM);
        int bx = x + 26;
        g.fill(bx, y, bx + BAR_W, y + BAR_H, TRACK);
        float frac = max <= 0 ? 0 : (float) Mth.clamp(value / max, 0, 1);
        g.fill(bx, y, bx + (int) (BAR_W * frac), y + BAR_H, color);
        g.fill(bx, y, bx + (int) (BAR_W * frac), y + 2, 0x40FFFFFF); // highlight
        g.drawString(font, compact(value), bx + BAR_W + 4, y, TEXT);
        return y + BAR_H + 3;
    }

    private static int chip(GuiGraphics g, Font font, int x, int y, Component text, int color) {
        int w = font.width(text) + 6;
        g.fill(x, y - 1, x + w, y + 9, 0xA0000000);
        g.drawString(font, text, x + 3, y, color);
        return x + w + 3;
    }

    private static String compact(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fk", v / 1_000);
        return String.valueOf((int) Math.round(v));
    }
}
