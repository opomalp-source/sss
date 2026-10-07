package com.dbzenith.client.ui;

import com.dbzenith.config.DBZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat callouts (CX-19 phase 8): VANISH!, PERFECT GUARD!, COUNTER!, CLASH!, BURST!, GUARD BROKEN!... slashing in
 * above the crosshair on a slanted band in their colour, holding a moment and fading. The newest sits on top; up to
 * three at once. Client config {@code combatCallouts}.
 */
public final class CalloutOverlay implements IGuiOverlay {
    private static final float IN = 3, LIFE = 26;
    private static final int MAX = 3;

    private record Callout(Component text, int color, float born) {}

    private static final List<Callout> SHOWN = new ArrayList<>();

    public static void show(String key, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DBZConfig.CLIENT.combatCallouts.get()) return;
        float now = mc.level.getGameTime();
        SHOWN.removeIf(c -> c.text.getString().equals(Component.translatable(key).getString()));   // the same again: restart it
        SHOWN.add(0, new Callout(Component.translatable(key), 0xFF000000 | color, now));
        while (SHOWN.size() > MAX) SHOWN.remove(SHOWN.size() - 1);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (SHOWN.isEmpty() || mc.level == null || mc.options.hideGui) return;
        float now = mc.level.getGameTime() + partial;
        SHOWN.removeIf(c -> now - c.born > LIFE);
        Font font = mc.font;
        float y = height * 0.19f;                                             // above the combo counter
        for (Callout c : SHOWN) {
            float age = now - c.born;
            float in = Mth.clamp(age / IN, 0, 1), ease = 1 - (1 - in) * (1 - in) * (1 - in);
            float alpha = age < LIFE - 6 ? 1 : Math.max(0, (LIFE - age) / 6f);
            float scale = 1.6f + 0.6f * (1 - ease);
            float tw = font.width(c.text) * scale, bandW = tw + 34, bandH = 9 * scale + 6;
            float x = width / 2f - bandW / 2 - (1 - ease) * width * 0.35f;     // slashes in from the left
            int a = (int) (alpha * 255);
            DbzTheme.slant(g, x, y, bandW, bandH, 8, DbzTheme.withAlpha(DbzTheme.darken(c.color, 0.35f), (int) (a * 0.75f)),
                    DbzTheme.withAlpha(DbzTheme.darken(c.color, 0.2f), (int) (a * 0.75f)));
            DbzTheme.slant(g, x + 2, y + bandH - 2, bandW - 4, 2, 2, DbzTheme.withAlpha(c.color, a), DbzTheme.withAlpha(c.color, a));
            if (a > 8) DbzTheme.text(g, font, c.text, x + bandW / 2 - tw / 2 + 4, y + 3.5f, DbzTheme.withAlpha(DbzTheme.brighten(c.color, 1.3f), a), scale);
            y += bandH + 3;
        }
    }
}
