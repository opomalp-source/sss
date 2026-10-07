package com.dbzenith.client.ui;

import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The combat log (CX-19 phase 9): the last few lines (knockouts, big combos, guard breaks, bursts, ultimates, duels) on
 * the right below the enemy panel, each fading after eight seconds. Client config {@code combatLog}.
 */
public final class CombatLogOverlay implements IGuiOverlay {
    private static final int MAX = 6, LIFE = 160;
    private static final float SCALE = 0.7f;

    private record Entry(Component line, long at) {}

    private static final List<Entry> LINES = new ArrayList<>();

    public static void add(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DBZConfig.CLIENT.combatLog.get()) return;
        LINES.add(new Entry(line, mc.level.getGameTime()));
        while (LINES.size() > MAX) LINES.remove(0);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (LINES.isEmpty() || mc.level == null || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        LINES.removeIf(e -> now - e.at > LIFE);
        Font font = mc.font;
        float y = 64;
        for (Entry e : LINES) {
            float age = now - e.at + partial;
            float alpha = age > LIFE - 30 ? Math.max(0, (LIFE - age) / 30f) : 1;
            float slide = Math.min(1, age / 4f);
            int w = (int) (font.width(e.line) * SCALE);
            float x = width - 8 - w + (1 - slide) * 40;
            int a = (int) (alpha * 160);
            g.fill((int) x - 3, (int) y - 2, width - 5, (int) (y + 9 * SCALE + 1), a << 24 | 0x080B12);
            if (alpha > 0.05f) DbzTheme.text(g, font, e.line, x, y, DbzTheme.withAlpha(0xF0F0F0, (int) (alpha * 255)), SCALE);
            y += 9 * SCALE + 4;
        }
    }
}
