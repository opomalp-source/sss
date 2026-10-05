package com.dbzenith.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** A button in the mod's skin. {@link #of} mirrors {@code Button.builder}, so screens swap one for the other. */
public class ThemedButton extends Button {
    private boolean selected;

    protected ThemedButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    /** Draw in the gold "selected" skin (a toggle that is on, the chosen option of a group). */
    public ThemedButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean hot = isHoveredOrFocused();
        int state = !active ? 2 : selected ? 3 : hot ? 1 : 0;
        DbzTheme.button(g, getX(), getY(), getWidth(), getHeight(), state);
        int color = !active ? 0xFF6A6A78 : selected ? 0xFFFFF4D8 : hot ? 0xFFFFE6A0 : DbzTheme.TEXT;
        renderString(g, Minecraft.getInstance().font, color | (int) (alpha * 255) << 24);
    }

    public static Builder of(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x, y, w = 150, h = 20;
        private Tooltip tooltip;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int w, int h) {
            this.w = w;
            this.h = h;
            return this;
        }

        public Builder width(int w) {
            this.w = w;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ThemedButton build() {
            ThemedButton b = new ThemedButton(x, y, w, h, message, onPress);
            if (tooltip != null) b.setTooltip(tooltip);
            return b;
        }
    }
}
