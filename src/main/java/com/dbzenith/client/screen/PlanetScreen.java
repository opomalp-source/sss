package com.dbzenith.client.screen;

import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.TravelPacket;
import com.dbzenith.world.Planet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Space Pod navigation: pick a destination. */
public class PlanetScreen extends Screen {
    public PlanetScreen() {
        super(Component.translatable("screen.dbzenith.planets"));
    }

    @Override
    protected void init() {
        Planet here = minecraft.level == null ? null : Planet.of(minecraft.level);
        Planet[] planets = Planet.values();
        int top = height / 2 - planets.length * 12;
        for (int i = 0; i < planets.length; i++) {
            Planet p = planets[i];
            Button b = Button.builder(Component.translatable(p.translationKey()), x -> {
                        ModNetwork.sendToServer(new TravelPacket(p));
                        onClose();
                    })
                    .bounds(width / 2 - 100, top + i * 24, 200, 20)
                    .tooltip(Tooltip.create(Component.translatable(p.translationKey() + ".desc")))
                    .build();
            b.active = p != here;
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - Planet.values().length * 12 - 18, 0xFFFFD040);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
