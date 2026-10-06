package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
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
    private static final int W = 320;
    private final int dragonId;

    public WishScreen(int dragonId) {
        super(Component.translatable("screen.dbzenith.wish"));
        this.dragonId = dragonId;
    }

    /** The dragon's own wishes (its set, or only lifting the Black Star curse). */
    private java.util.List<Wish> wishes() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(dragonId) instanceof com.dbzenith.dragonball.DragonSpiritEntity d) return d.wishes();
        return Wish.of(com.dbzenith.dragonball.BallSet.EARTH);
    }

    private com.dbzenith.dragonball.BallSet set() {
        return minecraft != null && minecraft.level != null && minecraft.level.getEntity(dragonId) instanceof com.dbzenith.dragonball.DragonSpiritEntity d
                ? d.set() : com.dbzenith.dragonball.BallSet.EARTH;
    }

    @Override
    protected void init() {
        Wish[] wishes = wishes().toArray(new Wish[0]);
        int cols = columns(wishes.length), rows = (wishes.length + cols - 1) / cols, bw = cols == 1 ? W : (W - 6) / 2;
        int left = (width - W) / 2;
        int top = (height - (rows * 24 + 40)) / 2;
        for (int i = 0; i < wishes.length; i++) {
            Wish w = wishes[i];
            int bx = left + (i / rows) * (bw + 6), by = top + 30 + (i % rows) * 24;
            addRenderableWidget(ThemedButton.of(Component.translatable(w.translationKey()), b -> {
                        ModNetwork.sendToServer(new MakeWishPacket(dragonId, w));
                        onClose();
                    })
                    .bounds(bx, by, bw, 20)
                    .tooltip(Tooltip.create(Component.translatable(w.translationKey() + ".desc")))
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        int n = wishes().size(), rows = (n + columns(n) - 1) / columns(n);
        int top = (height - (rows * 24 + 40)) / 2;
        com.dbzenith.dragonball.BallSet set = set();
        Component heading = Component.translatable("screen.dbzenith.wish_" + set.id());
        DbzTheme.window(g, font, heading, (width - W) / 2 - 12, top, W + 24, rows * 24 + 44);
        int sub = set == com.dbzenith.dragonball.BallSet.BLACK_STAR ? 0xFFFF8A7A : set == com.dbzenith.dragonball.BallSet.SUPER ? 0xFFFFE08A : 0xFFB0FFB0;
        g.drawCenteredString(font, Component.translatable("screen.dbzenith.wish_sub_" + set.id()), width / 2, top + 18, sub);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Two columns once the list would run off a small screen (the Super dragon's). */
    private static int columns(int n) {
        return n > 8 ? 2 : 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
