package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.OtherworldPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Before Enma's desk (CX-12): the judge reads your case. A soul waits out its time, then may go back among the living;
 * a living visitor (come by Instant Transmission) is simply shown the way home.
 */
public class JudgementScreen extends Screen {
    private static final int W = 260, H = 150;
    private final boolean dead;
    private float secondsLeft;
    private int left, top;
    private Button back;

    public JudgementScreen(boolean dead, int secondsLeft) {
        super(Component.translatable("screen.dbzenith.judgement"));
        this.dead = dead;
        this.secondsLeft = secondsLeft;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        back = addRenderableWidget(ThemedButton.of(Component.translatable(dead ? "screen.dbzenith.judgement_return" : "screen.dbzenith.judgement_home"), b -> {
            ModNetwork.sendToServer(new OtherworldPackets.Return());
            onClose();
        }).bounds(left + 16, top + H - 30, W - 112, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.judgement_leave"), b -> onClose())
                .bounds(left + W - 88, top + H - 30, 72, 18).build());
    }

    @Override
    public void tick() {
        if (secondsLeft > 0) secondsLeft = Math.max(0, secondsLeft - 0.05f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        back.active = !dead || secondsLeft <= 0;
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        Component line1 = Component.translatable(dead ? "screen.dbzenith.judgement_dead" : "screen.dbzenith.judgement_alive");
        DbzTheme.wrapped(g, font, line1, left + 16, top + 30, W - 32, DbzTheme.TEXT);
        Component line2;
        int color;
        if (!dead) {
            line2 = Component.translatable("screen.dbzenith.judgement_visitor");
            color = DbzTheme.DIM;
        } else if (secondsLeft > 0) {
            int s = (int) Math.ceil(secondsLeft);
            line2 = Component.translatable("screen.dbzenith.judgement_wait", s / 60, String.format("%02d", s % 60));
            color = DbzTheme.TITLE;
        } else {
            line2 = Component.translatable("screen.dbzenith.judgement_served");
            color = DbzTheme.GOOD;
        }
        DbzTheme.wrapped(g, font, line2, left + 16, top + 74, W - 32, color);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
