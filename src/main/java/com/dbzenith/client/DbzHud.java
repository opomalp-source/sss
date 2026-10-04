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
 * combo counter and the selected technique with its cooldown. Drawn from the sprite sheet textures/gui/hud.png
 * (tools/ArtGen.java).
 */
public final class DbzHud implements IGuiOverlay {
    private static final net.minecraft.resources.ResourceLocation HUD = new net.minecraft.resources.ResourceLocation(com.dbzenith.DBZenith.MOD_ID, "textures/gui/hud.png");
    private static final int BAR_W = 112;
    private static final int BAR_H = 7;
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
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        panel(g, x - 4, y - 4, BAR_W + 52, formLine ? 82 : 70);

        g.drawString(font, Component.translatable("hud.dbzenith.power_level", String.format("%,d", StatCalculator.battlePower(d))), x, y, TEXT);
        y += 11;
        y = bar(g, font, x, y, 0, d.getBody(), s.maxBody());
        y = bar(g, font, x, y, 1, d.getKi(), s.maxKi());
        y = bar(g, font, x, y, 2, d.getStamina(), s.maxStamina());

        // release %
        g.blit(HUD, x, y, 56, 0, 8, 8, 256, 256);                                         // release burst icon
        g.blit(HUD, x + 11, y + 1, 0, 16, BAR_W, 5, 256, 256);
        g.blit(HUD, x + 11, y + 1, 0, 48, (int) (BAR_W * d.getReleasePercent() / 100f), 5, 256, 256);
        g.drawString(font, d.getReleasePercent() + "%", x + 15 + BAR_W, y, RELEASE);
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
        if (mc.player != null && com.dbzenith.registry.ModEffects.isStunned(mc.player)) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.stunned"), 0xFFF2E94E);
        if (mc.player != null && com.dbzenith.registry.ModEffects.isKiSealed(mc.player)) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.sealed"), 0xFF8A5FD0);
        if (d.isGuarding()) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.guard"), STAMINA);
        if (com.dbzenith.config.DBZConfig.SERVER_SPEC.isLoaded() && com.dbzenith.config.DBZConfig.SERVER.thirstEnabled.get()
                && d.getThirst() < com.dbzenith.world.Needs.THIRSTY_BELOW) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.thirsty"), 0xFF60B0FF);
        if (d.getTemperature() > 0) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.hot"), 0xFFFF8030);
        if (d.getTemperature() < 0) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.cold"), 0xFFA0E0FF);
        if (d.getMajinStacks() > 0) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.absorbed", d.getMajinStacks()), 0xFFFF80C0);
        if (d.isMeditating()) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.meditating"), 0xFFC8A0FF);
        if (d.getGravity() > 1) cx = chip(g, font, cx, y, Component.translatable("hud.dbzenith.gravity", (int) d.getGravity()), 0xFFB070FF);
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
        panel(g, tx - 3, ty - 3, tw + 6, 26);
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

    /** Icon, bevelled track and glossy fill from the HUD sheet (kind: 0 body, 1 ki, 2 stamina). */
    private static int bar(GuiGraphics g, Font font, int x, int y, int kind, double value, double max) {
        g.blit(HUD, x, y, 32 + kind * 8, 0, 8, 8, 256, 256);
        int bx = x + 11;
        g.blit(HUD, bx, y, 0, 16, BAR_W, BAR_H, 256, 256);
        float frac = max <= 0 ? 0 : (float) Mth.clamp(value / max, 0, 1);
        g.blit(HUD, bx, y, 0, 24 + kind * 8, (int) (BAR_W * frac), BAR_H, 256, 256);
        g.drawString(font, compact(value), bx + BAR_W + 4, y, TEXT);
        return y + BAR_H + 4;
    }

    /** The gold-trimmed glass panel (nine-slice). */
    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitNineSliced(HUD, x, y, w, h, 4, 4, 16, 16, 0, 0);
    }

    private static int chip(GuiGraphics g, Font font, int x, int y, Component text, int color) {
        int w = font.width(text) + 6;
        g.blitNineSliced(HUD, x, y - 1, w, 10, 3, 3, 16, 16, 16, 0);
        g.drawString(font, text, x + 3, y, color);
        return x + w + 3;
    }

    private static String compact(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 10_000) return String.format("%.1fk", v / 1_000);
        return String.valueOf((int) Math.round(v));
    }
}
