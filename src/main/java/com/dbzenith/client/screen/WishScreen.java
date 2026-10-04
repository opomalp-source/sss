package com.dbzenith.client.screen;

import com.dbzenith.dragonball.Wish;
import com.dbzenith.network.MakeWishPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Speak your wish." One button per wish; closing it lets you reopen it by right-clicking the dragon. */
public class WishScreen extends Screen {
    private static final int W = 300;
    private final int dragonId;

    public WishScreen(int dragonId) {
        super(Component.translatable("screen.dbzenith.wish"));
        this.dragonId = dragonId;
    }

    @Override
    protected void init() {
        Wish[] wishes = Wish.values();
        int left = (width - W) / 2;
        int top = (height - (wishes.length * 24 + 40)) / 2;
        for (int i = 0; i < wishes.length; i++) {
            Wish w = wishes[i];
            addRenderableWidget(Button.builder(Component.translatable(w.translationKey()), b -> {
                        ModNetwork.sendToServer(new MakeWishPacket(dragonId, w));
                        onClose();
                    })
                    .bounds(left, top + 30 + i * 24, W, 20)
                    .tooltip(Tooltip.create(Component.translatable(w.translationKey() + ".desc")))
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int top = (height - (Wish.values().length * 24 + 40)) / 2;
        g.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFD040);
        g.drawCenteredString(font, Component.translatable("screen.dbzenith.wish_sub"), width / 2, top + 18, 0xFFB0FFB0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
