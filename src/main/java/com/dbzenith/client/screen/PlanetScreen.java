package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
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
        addRenderableWidget(ThemedButton.of(Component.literal("Galaxy map"), b -> minecraft.player.connection.sendCommand("galaxy"))
                .bounds(width / 2 - 100, height / 2 + planets.length * 12 + 26, 200, 20).build());
        int top = height / 2 - planets.length * 12;
        var orbit=ThemedButton.of(Component.literal("Launch into orbit"),b->{ModNetwork.sendToServer(new com.dbzenith.network.GalaxyPackets.Request(0,7,"0:0:0:0",0,"",0));onClose();}).bounds(width/2-100,top+planets.length*24,200,20).build();
        orbit.active=minecraft.player!=null&&minecraft.player.isPassenger()&&!com.dbzenith.galaxy.space.SpaceRuntime.isSpace(minecraft.level);addRenderableWidget(orbit);
        for (int i = 0; i < planets.length; i++) {
            Planet p = planets[i];
            Button b = ThemedButton.of(Component.translatable(p.translationKey()), x -> {
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
        DbzTheme.screenBackground(g, width, height);
        int top = height / 2 - Planet.values().length * 12;
        DbzTheme.window(g, font, title, width / 2 - 112, top - 18, 224, Planet.values().length * 24 + 75);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
