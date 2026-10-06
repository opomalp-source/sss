package com.dbzenith.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A UI v3 slider ({@link Ui}): the label on the left and the value on the right above a thin track; the part
 * already slid over glows gold, and the knob is a small white disc with a gold rim. Subclasses set the value text
 * through {@link #setMessage} and react in {@link #applyValue}.
 */
public abstract class UiSlider extends AbstractSliderButton {
    private final Component label;
    private int fill = Ui.GOLD;

    protected UiSlider(int x, int y, int w, Component label, double value) {
        super(x, y, w, 20, Component.empty(), value);
        this.label = label;
    }

    /** The colour of the slid-over part (the alignment slider shades from good to evil). */
    protected void fillColor(int argb) {
        fill = argb;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth();
        boolean hot = isHoveredOrFocused();
        Ui.text(g, font, label, x, y + 1, Ui.MUTED, 0.85f);
        Component v = getMessage();
        Ui.text(g, font, v, x + w - font.width(v) * 0.85f, y + 1, hot ? Ui.TEXT : 0xFFD8DCE6, 0.85f);
        int ty = y + 14, kx = x + 3 + (int) Math.round(value * (w - 6));
        Ui.round(g, x, ty - 1, w, 3, 1, 0x30FFFFFF);
        Ui.round(g, x, ty - 1, kx - x, 3, 1, fill);
        Ui.round(g, kx - 4, ty - 4, 8, 8, 2, fill);
        Ui.round(g, kx - 3, ty - 3, 6, 6, 1, hot ? 0xFFFFFFFF : 0xFFE6E8EE);
    }
}
