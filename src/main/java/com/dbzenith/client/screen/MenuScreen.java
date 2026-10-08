package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The character menu, UI v3 (CX-16d): one look and one row of tabs for Stats, Techniques, Skills and Forms. Each page
 * is its own screen built on this base: the calm backdrop, the gold title, the tabs, the training points, and a card
 * the page fills. Esc closes the menu (or returns to whatever opened it).
 */
public abstract class MenuScreen extends Screen {
    public enum Page { STATS, TECHNIQUES, SKILLS, FORMS, STYLES }

    private final Page page;
    protected final Screen parent;
    /** The composition (at most 600 x 340, centred) and the card the page draws in. */
    protected int ox, oy, W, H, cardX, cardY, cardW, cardH;

    protected MenuScreen(Component title, Page page, Screen parent) {
        super(title);
        this.page = page;
        this.parent = parent;
    }

    /** Opens a page of the menu. */
    public static Screen open(Page p, Screen parent) {
        return switch (p) {
            case STATS -> new StatScreen();
            case TECHNIQUES -> new DeckScreen(parent);
            case SKILLS -> new RacialScreen(parent);
            case FORMS -> new FormScreen(parent);
            case STYLES -> new StyleScreen(parent);
        };
    }

    @Override
    protected final void init() {
        W = Math.min(width, 600);
        H = Math.min(height, 340);
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        cardX = ox + 8;
        cardY = oy + 42;
        cardW = W - 16;
        cardH = H - 42 - 8;
        int tx = ox + 8;
        for (Page p : Page.values()) {
            Component label = Component.translatable("screen.dbzenith.menu_" + p.name().toLowerCase());
            int w = (int) (font.width(label.getString().toUpperCase()) * 0.85f) + 16;
            addRenderableWidget(UiButton.of(UiButton.Style.TAB, label, tx, oy + 22, w, 16, b -> {
                if (p != page) minecraft.setScreen(open(p, parent));
            }).selected(p == page));
            tx += w + 2;
        }
        initPage();
    }

    /** Add the page's widgets (the card is cardX, cardY, cardW, cardH). */
    protected abstract void initPage();

    /** Draw the page inside its card. */
    protected abstract void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial);

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var state = minecraft.player == null ? null : ClientPublicStates.get(minecraft.player.getId());
        int glow = state == null ? 0x3A5A9A : state.auraColor();
        Ui.backdrop(g, width, height, ox + W / 2f, oy + H * 0.45f, glow);
        Ui.text(g, font, Component.literal(title.getString().toUpperCase()), ox + 10, oy + 9, Ui.GOLD, 0.85f);
        Component tp = Component.translatable("screen.dbzenith.tp", String.format("%,d", ClientPlayerData.get().getTrainingPoints()));
        Ui.text(g, font, tp, ox + W - 10 - font.width(tp) * 0.8f, oy + 9.5f, 0xFF8CE08C, 0.8f);
        g.fill(ox + 8, oy + 39, ox + W - 8, oy + 40, Ui.LINE_SOFT);
        Ui.card(g, cardX, cardY, cardW, cardH);
        renderPage(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
