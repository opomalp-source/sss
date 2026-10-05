package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.RacialPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Instant Transmission: two fingers to the forehead, feel for a ki, and go. Every ki you can sense in this world
 * (god ki only if yours is too), nearest first, and home.
 */
public class TransmissionScreen extends Screen {
    private static final int W = 240, ROW = 20, MAX_ROWS = 9;
    private final List<RacialPackets.Destination> targets;
    private int left, top, h;

    public TransmissionScreen(List<RacialPackets.Destination> targets) {
        super(Component.translatable("screen.dbzenith.transmission"));
        this.targets = targets;
    }

    @Override
    protected void init() {
        int rows = Math.min(MAX_ROWS, targets.size());
        h = 40 + rows * ROW + 26;
        left = (width - W) / 2;
        top = (height - h) / 2;
        for (int i = 0; i < rows; i++) {
            RacialPackets.Destination t = targets.get(i);
            Component label = t.key().equals("home") ? Component.translatable("screen.dbzenith.transmission_home", t.distance())
                    : Component.translatable("screen.dbzenith.transmission_target", t.name(), t.distance(),
                    t.power() < 0 ? Component.literal("???") : Component.literal(String.format("%,d", t.power())));
            addRenderableWidget(ThemedButton.of(label, b -> {
                ModNetwork.sendToServer(new RacialPackets.Transmit(t.key()));
                onClose();
            }).bounds(left + 10, top + 34 + i * ROW, W - 20, ROW - 3).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W - 70, top + h - 24, 60, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, h);
        Component sub = Component.translatable(targets.size() <= 1 ? "screen.dbzenith.transmission_alone" : "screen.dbzenith.transmission_sub");
        DbzTheme.text(g, font, sub, left + W / 2f - font.width(sub) * 0.75f / 2, top + 20, DbzTheme.DIM, 0.75f);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
