package com.dbzenith.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A UI v3 button ({@link Ui}). Styles:
 * <ul>
 * <li>{@link Style#PRIMARY}: the one main action, warm gold with dark text.</li>
 * <li>{@link Style#SECONDARY}: glass with light text.</li>
 * <li>{@link Style#GHOST}: just the label, brightening on hover.</li>
 * <li>{@link Style#CHIP}: a small pill for one option of a group; gold-rimmed when selected.</li>
 * <li>{@link Style#TAB}: a tab label; the selected one is bright with a gold bar under it.</li>
 * </ul>
 */
public class UiButton extends Button {
    public enum Style { PRIMARY, SECONDARY, GHOST, CHIP, TAB }

    private final Style style;
    private boolean selected;
    private float textScale = 1f;

    public UiButton(int x, int y, int w, int h, Component message, Style style, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.style = style;
        if (style == Style.CHIP || style == Style.TAB) textScale = 0.85f;
    }

    public static UiButton of(Style style, Component message, int x, int y, int w, int h, OnPress onPress) {
        return new UiButton(x, y, w, h, message, style, onPress);
    }

    public UiButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public UiButton textScale(float scale) {
        this.textScale = scale;
        return this;
    }

    public UiButton tip(Component tip) {
        setTooltip(Tooltip.create(tip));
        return this;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
        com.dbzenith.client.ClientSounds.uiClick();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused() && active;
        int textColor;
        switch (style) {
            case PRIMARY -> {
                Ui.round(g, x, y, w, h, 2, !active ? 0xFF4A4436 : hot ? 0xFFFFC870 : Ui.GOLD);
                g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0x40000000);
                g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x50FFFFFF);
                textColor = active ? Ui.DARK_TEXT : 0xFF8A8270;
            }
            case SECONDARY -> {
                Ui.round(g, x, y, w, h, 2, hot ? 0x34FFFFFF : 0x1CFFFFFF);
                Ui.outline(g, x, y, w, h, 2, hot ? Ui.LINE : Ui.LINE_SOFT);
                textColor = active ? Ui.TEXT : Ui.FAINT;
            }
            case GHOST -> textColor = !active ? Ui.FAINT : hot ? Ui.TEXT : Ui.MUTED;
            case CHIP -> {
                Ui.round(g, x, y, w, h, 2, selected ? 0x40FFB547 : hot ? 0x26FFFFFF : 0x12FFFFFF);
                Ui.outline(g, x, y, w, h, 2, selected ? Ui.GOLD : hot ? Ui.LINE : Ui.LINE_SOFT);
                textColor = selected ? 0xFFFFE2B0 : hot ? Ui.TEXT : Ui.MUTED;
            }
            default -> {                                                     // TAB
                if (selected) g.fill(x + 3, y + h - 2, x + w - 3, y + h, Ui.GOLD);
                else if (hot) g.fill(x + 3, y + h - 1, x + w - 3, y + h, Ui.LINE);
                textColor = selected ? Ui.TEXT : hot ? 0xFFC8CEDC : Ui.MUTED;
            }
        }
        Font font = Minecraft.getInstance().font;
        Component msg = getMessage();
        if (style == Style.TAB) msg = Component.literal(msg.getString().toUpperCase(java.util.Locale.ROOT));
        float tw = font.width(msg) * textScale;
        float tx = x + (w - tw) / 2f, ty = y + (h - 8 * textScale) / 2f + (style == Style.TAB ? -1 : 0);
        DbzTheme.text(g, font, msg, tx, ty, textColor | 0xFF000000, textScale);
    }
}
