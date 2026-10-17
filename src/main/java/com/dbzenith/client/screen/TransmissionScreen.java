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
    private int left, top, h,page;

    public TransmissionScreen(List<RacialPackets.Destination> targets) {
        super(Component.translatable("screen.dbzenith.transmission"));
        this.targets = targets;
    }

    @Override
    protected void init() {
        int capacity=Math.max(1,Math.min(MAX_ROWS,(height-90)/ROW));page=Math.min(page,Math.max(0,(targets.size()-1)/capacity));
        int rows = Math.min(capacity,targets.size()-page*capacity);
        h = 40 + rows * ROW + 26;
        left = (width - W) / 2;
        top = (height - h) / 2;
        for (int i = 0; i < rows; i++) {
            RacialPackets.Destination t = targets.get(page*capacity+i);
            Component label = t.key().equals("home") ? Component.translatable("screen.dbzenith.transmission_home", t.distance())
                    : Component.translatable("screen.dbzenith.transmission_target", t.name(), t.distance(),
                    t.power() < 0 ? Component.literal("???") : Component.literal(String.format("%,d", t.power())));
            if(t.key().startsWith("planet:"))label=Component.literal("World / "+t.name());
            else if(t.distance()<0)label=Component.literal(t.name()+" / distant ki");
            addRenderableWidget(ThemedButton.of(label, b -> {
                ModNetwork.sendToServer(new RacialPackets.Transmit(t.key()));
                onClose();
            }).bounds(left + 10, top + 34 + i * ROW, W - 20, ROW - 3).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W - 70, top + h - 24, 60, 16).build());
        var previous=addRenderableWidget(ThemedButton.of(Component.literal("Previous"),b->{page--;rebuildWidgets();}).bounds(left+10,top+h-24,62,16).build());previous.active=page>0;
        var next=addRenderableWidget(ThemedButton.of(Component.literal("Next"),b->{page++;rebuildWidgets();}).bounds(left+77,top+h-24,55,16).build());next.active=(page+1)*capacity<targets.size();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, h);
        Component sub = Component.literal("Sensed ki and familiar worlds / "+(page+1));
        DbzTheme.text(g, font, sub, left + W / 2f - font.width(sub) * 0.75f / 2, top + 20, DbzTheme.DIM, 0.75f);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
