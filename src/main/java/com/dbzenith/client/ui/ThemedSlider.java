package com.dbzenith.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider in the mod's skin: a slanted track filled in the accent colour, with a gold handle. */
public abstract class ThemedSlider extends AbstractSliderButton {
    protected ThemedSlider(int x, int y, int w, int h, Component message, double value) {
        super(x, y, w, h, message, value);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        DbzTheme.button(g, x, y, w, h, active ? 0 : 2);
        DbzTheme.slantBar(g, x + 3, y + 3, w - 9, h - 6, 3, (float) value, DbzTheme.ACCENT);
        int hx = x + 2 + (int) (value * (w - 10));
        DbzTheme.button(g, hx, y, 8, h, isHoveredOrFocused() ? 1 : 3);
        renderScrollingString(g, Minecraft.getInstance().font, 2, (isHoveredOrFocused() ? 0xFFFFE6A0 : DbzTheme.TEXT) | (int) (alpha * 255) << 24);
    }
}
