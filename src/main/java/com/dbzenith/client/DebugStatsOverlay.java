package com.dbzenith.client;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Phase 0 text readout of the synced player data, proving the server-to-client sync path.
 * Toggle with {@code hud.showDebugOverlay} in dbzenith-client.toml. The real DBZ HUD arrives in Phase 1.
 */
public final class DebugStatsOverlay implements IGuiOverlay {
    private static final int TEXT = 0xFFE8E8E8;
    private static final int ACCENT = 0xFFFFB330;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug || !ClientPlayerData.hasData()) return;
        if (!DBZConfig.CLIENT.showDebugOverlay.get()) return;

        PlayerData d = ClientPlayerData.get();
        DerivedStats s = d.getDerived();
        Font font = mc.font;
        int x = 4;
        int y = 80;
        int line = font.lineHeight + 1;

        g.drawString(font, "Dragon Block Zenith [debug]", x, y, ACCENT);
        y += line;
        g.drawString(font, String.format("%s / %s   BP %,d", d.getRace().id(), d.getPath().id(), StatCalculator.battlePower(d)), x, y, TEXT);
        y += line;
        g.drawString(font, String.format("Body %.0f/%.0f  Ki %.0f/%.0f  Sta %.0f/%.0f",
                d.getBody(), s.maxBody(), d.getKi(), s.maxKi(), d.getStamina(), s.maxStamina()), x, y, TEXT);
        y += line;
        g.drawString(font, String.format("Release %d%%   TP %,d", d.getReleasePercent(), d.getTrainingPoints()), x, y, TEXT);
        y += line;
        StringBuilder attrs = new StringBuilder();
        for (Attribute a : Attribute.values()) {
            attrs.append(a.shortName().toUpperCase()).append(' ').append(d.getAttribute(a)).append("  ");
        }
        g.drawString(font, attrs.toString().trim(), x, y, TEXT);
    }
}
